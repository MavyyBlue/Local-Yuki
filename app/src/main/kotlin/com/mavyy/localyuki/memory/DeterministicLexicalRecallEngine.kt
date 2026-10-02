package com.mavyy.localyuki.memory

import android.content.Context
import android.database.sqlite.SQLiteException
import com.mavyy.localyuki.continuity.ContinuityHelper
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*

/** Indexed candidate lookup; the advisory ordering never becomes a source of truth. */
class DeterministicLexicalRecallEngine(context: Context, private val deep: MemoryReader,
    private val living: LivingMemoryReader,
    private val indexCoverage: () -> FoundationResult<IndexCoverage> = { FoundationResult.Success(IndexCoverage.PARTIAL) }) : SemanticCandidateEngine, AutoCloseable {
    private val app = context.applicationContext
    private val helper = ContinuityHelper(app,!app.getDatabasePath(ContinuitySchema.NAME).exists())
    companion object {
        const val TERM_QUERY = "SELECT t.memory_id,t.source_revision_id FROM living_memory_term t INDEXED BY idx_living_term_lookup JOIN durable_memory d ON d.memory_id=t.memory_id AND d.current_revision=t.source_revision_id WHERE t.term=? ORDER BY t.term,t.memory_id LIMIT ?"
        const val KIND_TERM_QUERY = "SELECT t.memory_id,t.source_revision_id FROM living_memory_term t INDEXED BY idx_living_term_lookup JOIN durable_memory d ON d.memory_id=t.memory_id AND d.current_revision=t.source_revision_id WHERE t.term=? AND d.kind=? ORDER BY t.term,t.memory_id LIMIT ?"
        const val MAX_TERM_MATCHES = 200
    }
    private data class Lookup(val hints: List<RecallCandidateHint>, val truncated: Boolean)
    private fun lookup(query: RecallQuery): FoundationResult<Lookup> = try {
        val terms = LivingMemoryPolicyV1.terms(query.text,LivingMemoryPolicyV1.MAX_QUERY_TERMS)
        val matches = linkedMapOf<Pair<String,String>,MutableSet<String>>()
        val db = helper.readableDatabase
        val meta = db.rawQuery("SELECT format_version,opened FROM living_memory_metadata",null).use { c ->
            c.moveToFirst() && c.getString(0)=="1" && c.getString(1)=="1" && !c.moveToNext()
        }
        if (!meta) FoundationResult.Failure(FailureCategory.CONFLICT) else {
            var truncated = false
            val kind = query.kind
            for (term in terms) db.rawQuery(if (kind == null) TERM_QUERY else KIND_TERM_QUERY,
                if (kind == null) arrayOf(term,(MAX_TERM_MATCHES+1).toString())
                else arrayOf(term,kind.name,(MAX_TERM_MATCHES+1).toString())).use { c ->
                var count = 0
                while (c.moveToNext()) {
                    if (++count > MAX_TERM_MATCHES) { truncated = true; break }
                    matches.getOrPut(c.getString(0) to c.getString(1)) { linkedSetOf() }.add(term)
                }
            }
            FoundationResult.Success(Lookup(matches.map { (key, found) ->
                RecallCandidateHint(key.first,key.second,found.size,found.toList(),RecallBasis.LEXICAL)
            }.sortedWith(compareByDescending<RecallCandidateHint> { it.advisoryScore }
                .thenBy { it.memoryId }.thenBy { it.sourceRevisionId }).take(query.limit),truncated))
        }
    } catch (_: SQLiteException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }

    override fun propose(query: RecallQuery): FoundationResult<List<RecallCandidateHint>> = when (val result = lookup(query)) {
        is FoundationResult.Success -> FoundationResult.Success(result.value.hints)
        is FoundationResult.Failure -> result
        is FoundationResult.Unavailable -> result
    }

    private fun validHints(hints: List<RecallCandidateHint>, limit: Int): Boolean = hints.size <= limit &&
        hints.map { it.memoryId }.distinct().size == hints.size && hints.all { hint ->
            MemoryBounds.id(hint.memoryId) && MemoryBounds.id(hint.sourceRevisionId) && hint.advisoryScore in 0..100 &&
                hint.matchedTerms.size <= LivingMemoryPolicyV1.MAX_QUERY_TERMS &&
                hint.matchedTerms.distinct().size == hint.matchedTerms.size &&
                hint.matchedTerms.all(LivingMemoryPolicyV1::validTerm)
        }

    /** Hints may come from a later neural socket; only Phase 4 can supply content and evidence. */
    fun ground(hints: List<RecallCandidateHint>, coverage: IndexCoverage = IndexCoverage.PARTIAL): FoundationResult<RecallCandidateSet> {
        if (!validHints(hints,20)) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        return try {
            val accepted = mutableListOf<GroundedRecallCandidate>()
            for (hint in hints) {
                // Distinguish absent/stale hints from a failed read of an existing current authority.
                val head = helper.readableDatabase.rawQuery("SELECT current_revision FROM durable_memory WHERE memory_id=?",
                    arrayOf(hint.memoryId)).use { if (it.moveToFirst()) it.getString(0) else null }
                if (head == null || head != hint.sourceRevisionId) continue
                val memory = deep.current(hint.memoryId)
                when (memory) {
                    is FoundationResult.Failure -> return memory
                    is FoundationResult.Unavailable -> return memory
                    is FoundationResult.Success -> {
                        if (memory.value.current.id != hint.sourceRevisionId) continue
                        when (val surface = living.current(hint.memoryId)) {
                            is FoundationResult.Failure -> return surface
                            is FoundationResult.Unavailable -> return surface
                            is FoundationResult.Success -> {
                                if (surface.value.sourceRevisionId != hint.sourceRevisionId) continue
                                accepted += GroundedRecallCandidate(memory.value,surface.value,
                                    hint.advisoryScore,hint.matchedTerms.toList(),hint.basis)
                            }
                        }
                    }
                }
            }
            FoundationResult.Success(RecallCandidateSet(accepted,coverage))
        } catch (_: SQLiteException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
          catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    }

    /** Optional adapters have no write route; unavailable/invalid advisory results use lexical recall. */
    fun recall(query: RecallQuery, reranker: RecallReranker = DeterministicRecallReranker(),
        candidateEngine: SemanticCandidateEngine? = null): FoundationResult<RecallCandidateSet> {
        val external = try { candidateEngine?.propose(query) } catch (_: Exception) { null }
        val usableExternal = external is FoundationResult.Success && validHints(external.value,query.limit)
        val lexical = if (usableExternal) null else lookup(query)
        if (lexical is FoundationResult.Failure) return lexical
        if (lexical is FoundationResult.Unavailable) return lexical
        val hints = if (usableExternal) (external as FoundationResult.Success).value
            else (lexical as FoundationResult.Success).value.hints
        val coverage = try { indexCoverage() } catch (_: Exception) {
            FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        }
        if (coverage is FoundationResult.Failure) return coverage
        // Index availability does not remove the app's separate exact/deep reader.
        val reportedCoverage = if (coverage is FoundationResult.Success) coverage.value else IndexCoverage.UNAVAILABLE
        val effectiveCoverage = if (reportedCoverage == IndexCoverage.COMPLETE &&
            (usableExternal || (lexical as? FoundationResult.Success)?.value?.truncated == true))
            IndexCoverage.PARTIAL else reportedCoverage
        val grounded = ground(hints,effectiveCoverage)
        if (grounded !is FoundationResult.Success) return grounded
        val bounded = grounded.value.copy(candidates=grounded.value.candidates.filter {
            query.kind == null || it.memory.kind == query.kind
        })
        // Detach every collection; a hostile adapter may cast Kotlin's read-only List to MutableList.
        val detached = bounded.candidates.map { candidate -> candidate.copy(
            memory=candidate.memory.copy(current=candidate.memory.current.copy(evidence=candidate.memory.current.evidence.toList())),
            surface=candidate.surface.copy(terms=candidate.surface.terms.toList()),
            matchedTerms=candidate.matchedTerms.toList()) }
        val ranked = try { reranker.reorder(detached) }
            catch (_: Exception) { return FoundationResult.Success(bounded) }
        val validated = RecallRerankBoundary.validate(bounded.candidates,ranked)
        return if (validated is FoundationResult.Success)
            FoundationResult.Success(bounded.copy(candidates=validated.value,reranked=true))
        else FoundationResult.Success(bounded)
    }
    override fun close() = helper.close()
}
