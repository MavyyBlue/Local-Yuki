package com.mavyy.localyuki.foundation.memory

import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.provenance.EvidenceRef
import java.time.Instant

@JvmInline value class LivingMemoryFormatVersion(val value: Int) { init { require(value == 1) } }
enum class AbstractionLevel { DETAILED, COMPACT, SPARSE, TRACE }
enum class IndexCoverage { COMPLETE, PARTIAL, UNAVAILABLE }
enum class RecallBasis { LEXICAL, MOCK }

data class EpisodicMemoryView(val memoryId: String, val revisionId: String, val kind: MemoryKind,
    val status: MemoryStatus, val createdAt: Instant, val terms: List<String>, val evidence: List<EvidenceRef>)
data class LivingMemoryView(val memoryId: String, val sourceRevisionId: String, val stateRevision: Long,
    val abstraction: AbstractionLevel, val terms: List<String>, val importance: Int, val reinforcement: Int,
    val meaningfulRecallCount: Long, val lastMeaningfulRecallAt: Instant?, val reconciledAt: Instant)
data class RecallQuery(val text: String, val limit: Int, val kind: MemoryKind? = null) {
    init { require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 512 && limit in 1..20) }
}
data class RecallCandidateHint(val memoryId: String, val sourceRevisionId: String, val advisoryScore: Int,
    val matchedTerms: List<String>, val basis: RecallBasis)
data class GroundedRecallCandidate(val memory: DurableMemory, val surface: LivingMemoryView,
    val advisoryScore: Int, val matchedTerms: List<String>, val basis: RecallBasis)
data class RecallCandidateSet(val candidates: List<GroundedRecallCandidate>, val coverage: IndexCoverage,
    val reranked: Boolean = false)
interface LivingMemoryReader {
    fun current(memoryId: String): FoundationResult<LivingMemoryView>
    fun episode(memoryId: String, revisionId: String): FoundationResult<EpisodicMemoryView>
}
fun interface SemanticCandidateEngine { fun propose(query: RecallQuery): FoundationResult<List<RecallCandidateHint>> }
fun interface RecallReranker { fun reorder(candidates: List<GroundedRecallCandidate>): List<GroundedRecallCandidate> }

/** An untrusted ranker can only supply an identity permutation. Content always comes from the input. */
object RecallRerankBoundary {
    fun validate(input: List<GroundedRecallCandidate>, output: List<GroundedRecallCandidate>): FoundationResult<List<GroundedRecallCandidate>> {
        val keys = input.map { it.memory.id to it.memory.current.id }
        val proposed = output.map { it.memory.id to it.memory.current.id }
        if (keys.size != keys.toSet().size || proposed.size != keys.size || proposed.toSet() != keys.toSet() || proposed.size != proposed.toSet().size)
            return FoundationResult.Failure(com.mavyy.localyuki.foundation.contracts.FailureCategory.CONFLICT)
        val authoritative = input.associateBy { it.memory.id to it.memory.current.id }
        return FoundationResult.Success(proposed.map { authoritative.getValue(it) })
    }
}
class DeterministicRecallReranker : RecallReranker {
    override fun reorder(candidates: List<GroundedRecallCandidate>) = candidates.sortedWith(
        compareByDescending<GroundedRecallCandidate> { it.advisoryScore }.thenBy { it.memory.id })
}
class MockSemanticCandidateEngine(private val hints: List<RecallCandidateHint>) : SemanticCandidateEngine {
    override fun propose(query: RecallQuery): FoundationResult<List<RecallCandidateHint>> = FoundationResult.Success(hints.take(query.limit))
}
class MockRecallReranker(private val order: List<Int>) : RecallReranker {
    override fun reorder(candidates: List<GroundedRecallCandidate>): List<GroundedRecallCandidate> = order.map { candidates[it] }
}

object LivingMemoryPolicyV1 {
    const val MAX_RECONCILE_PAGE = 50
    const val MAX_QUERY_TERMS = 12
    const val MAX_TERM_LENGTH = 32
    private val budgets = intArrayOf(48, 24, 12, 6)
    fun budget(level: AbstractionLevel): Int = budgets[level.ordinal]
    /** Unicode code points avoid splitting supplementary letters; stable first appearance breaks frequency ties. */
    fun terms(text: String, max: Int): List<String> {
        require(max in 1..48)
        val counts = linkedMapOf<String, Int>()
        val builder = StringBuilder()
        fun flush() { if (builder.isNotEmpty()) { val term = builder.toString(); counts[term] = (counts[term] ?: 0) + 1; builder.setLength(0) } }
        text.lowercase(java.util.Locale.ROOT).codePoints().forEach { point ->
            if (Character.isLetterOrDigit(point)) {
                if (builder.codePointCount(0, builder.length) < MAX_TERM_LENGTH) builder.appendCodePoint(point)
            } else flush()
        }
        flush()
        val first = counts.keys.withIndex().associate { it.value to it.index }
        return counts.keys.sortedWith(compareByDescending<String> { counts.getValue(it) }.thenBy { first.getValue(it) }.thenBy { it }).take(max)
    }
    fun level(createdAt: Instant, now: Instant, importance: Int, reinforcement: Int): AbstractionLevel {
        require(importance in 0..100 && reinforcement in 0..100)
        val days = java.time.Duration.between(createdAt, now).toDays().coerceAtLeast(0)
        var ordinal = when { days < 30 -> 0; days < 90 -> 1; days < 365 -> 2; else -> 3 }
        ordinal += when { importance <= 25 -> 1; importance >= 75 -> -1; else -> 0 }
        if (reinforcement >= 50) ordinal -= 1
        return AbstractionLevel.entries[ordinal.coerceIn(0, 3)]
    }
}
