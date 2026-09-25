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
        const val TERM_QUERY = "SELECT memory_id,source_revision_id FROM living_memory_term INDEXED BY idx_living_term_lookup WHERE term=? ORDER BY term,memory_id LIMIT ?"
        const val KIND_TERM_QUERY = "SELECT t.memory_id,t.source_revision_id FROM living_memory_term t INDEXED BY idx_living_term_lookup JOIN durable_memory d ON d.memory_id=t.memory_id WHERE t.term=? AND d.kind=? ORDER BY t.term,t.memory_id LIMIT ?"
        const val MAX_TERM_MATCHES = 200
    }
    override fun propose(query: RecallQuery): FoundationResult<List<RecallCandidateHint>> {
      return try {
        val terms = LivingMemoryPolicyV1.terms(query.text,LivingMemoryPolicyV1.MAX_QUERY_TERMS)
        val matches = linkedMapOf<Pair<String,String>,MutableSet<String>>()
        val db = helper.readableDatabase
        val meta = db.rawQuery("SELECT format_version,opened FROM living_memory_metadata",null).use { c ->
            if (!c.moveToFirst()) false else c.getInt(0)==1 && c.getInt(1)==1 && !c.moveToNext()
        }
        if (!meta) return FoundationResult.Failure(FailureCategory.CONFLICT)
        val kind = query.kind
        for (term in terms) db.rawQuery(if (kind == null) TERM_QUERY else KIND_TERM_QUERY,
            if (kind == null) arrayOf(term,MAX_TERM_MATCHES.toString()) else arrayOf(term,kind.name,MAX_TERM_MATCHES.toString())).use { c ->
            while (c.moveToNext()) matches.getOrPut(c.getString(0) to c.getString(1)) { linkedSetOf() }.add(term)
        }
        FoundationResult.Success(matches.map { (key, found) ->
            RecallCandidateHint(key.first,key.second,found.size,found.toList(),RecallBasis.LEXICAL)
        }.sortedWith(compareByDescending<RecallCandidateHint> { it.advisoryScore }.thenBy { it.memoryId }.thenBy { it.sourceRevisionId }).take(query.limit))
      } catch (_: SQLiteException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    }

    /** Hints may come from a later neural socket; only Phase 4 can supply content and evidence. */
    fun ground(hints: List<RecallCandidateHint>, coverage: IndexCoverage = IndexCoverage.PARTIAL): FoundationResult<RecallCandidateSet> {
        if (hints.size > 20 || hints.map { it.memoryId }.size != hints.map { it.memoryId }.toSet().size)
            return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val accepted = mutableListOf<GroundedRecallCandidate>()
        for (hint in hints) {
            if (!MemoryBounds.id(hint.memoryId) || !MemoryBounds.id(hint.sourceRevisionId) || hint.advisoryScore !in 0..100 ||
                hint.matchedTerms.size > LivingMemoryPolicyV1.MAX_QUERY_TERMS) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
            val memory = deep.current(hint.memoryId)
            if (memory is FoundationResult.Unavailable) return memory
            if (memory !is FoundationResult.Success || memory.value.current.id != hint.sourceRevisionId) continue
            val surface = living.current(hint.memoryId)
            if (surface is FoundationResult.Unavailable) return surface
            if (surface is FoundationResult.Failure) return surface
            if (surface !is FoundationResult.Success || surface.value.sourceRevisionId != hint.sourceRevisionId) continue
            accepted += GroundedRecallCandidate(memory.value,surface.value,hint.advisoryScore,hint.matchedTerms,hint.basis)
        }
        return FoundationResult.Success(RecallCandidateSet(accepted,coverage))
    }
    fun recall(query: RecallQuery, reranker: RecallReranker = DeterministicRecallReranker()): FoundationResult<RecallCandidateSet> {
        val hints = propose(query)
        if (hints !is FoundationResult.Success) return when (hints) {
            is FoundationResult.Failure -> hints
            is FoundationResult.Unavailable -> hints
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        val coverage = indexCoverage()
        if (coverage !is FoundationResult.Success) return when (coverage) {
            is FoundationResult.Failure -> coverage
            is FoundationResult.Unavailable -> coverage
            else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
        }
        val grounded = ground(hints.value,coverage.value)
        if (grounded !is FoundationResult.Success) return grounded
        val ranked = try { reranker.reorder(grounded.value.candidates) }
            catch (_: Exception) { return FoundationResult.Success(grounded.value) }
        val validated = RecallRerankBoundary.validate(grounded.value.candidates,ranked)
        return if (validated is FoundationResult.Success) FoundationResult.Success(grounded.value.copy(candidates=validated.value,reranked=true))
            else FoundationResult.Success(grounded.value)
    }
    override fun close() = helper.close()
}
