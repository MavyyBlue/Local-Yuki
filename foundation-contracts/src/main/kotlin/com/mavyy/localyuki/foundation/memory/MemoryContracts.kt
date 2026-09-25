package com.mavyy.localyuki.foundation.memory

import com.mavyy.localyuki.foundation.contracts.Proposal
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.provenance.EvidenceRef
import com.mavyy.localyuki.foundation.provenance.EvidenceSourceKind
import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import java.time.Instant
import java.time.ZoneId

/** Semantic format; physical database schema advances independently. */
@JvmInline value class MemoryFormatVersion(val value: Int) { init { require(value == 1) } }
enum class MemoryKind { FACTUAL, AUTOBIOGRAPHICAL }
enum class MemoryOrigin { APP_VALIDATED, OWNER_UPDATE, OWNER_RESTORE }
enum class MemoryAction { CREATE, UPDATE, OWNER_UPDATE, OWNER_RESTORE }
enum class MemoryStatus { CURRENT, HISTORICAL }

data class MemoryThread(val id: String, val selfId: String, val relationshipId: String, val createdAt: Instant)
data class RawEvidence(val ref: EvidenceRef, val threadId: String?, val sequence: Long?,
    val capturedAt: Instant, val zoneId: ZoneId, val payloadFormat: Int, val payload: String)
data class MemoryRevision(val id: String, val memoryId: String, val number: Long, val content: String,
    val createdAt: Instant, val origin: MemoryOrigin, val supersedes: String?, val restoredFrom: String?,
    val status: MemoryStatus, val evidence: List<EvidenceRef>)
data class DurableMemory(val id: String, val kind: MemoryKind, val createdAt: Instant, val current: MemoryRevision)
data class MemoryAudit(val commandId: String, val memoryId: String, val action: MemoryAction,
    val previousRevision: String?, val resultingRevision: String, val restoredFrom: String?,
    val commandEvidence: EvidenceRef?, val occurredAt: Instant)
/** A derived projection, never a source of evidence. */
data class ConversationCheckpoint(val id: String, val threadId: String, val startSequence: Long,
    val endSequence: Long, val content: String, val createdAt: Instant,
    val producer: CoreSubsystemId, val supersedes: String?)
data class LatestCheckpoint(val checkpoint: ConversationCheckpoint?)

/** Narrow read-only view for cognition; authority implementations retain mutators. */
interface MemoryReader {
    fun thread(id: String): FoundationResult<MemoryThread>
    fun evidence(ref: EvidenceRef): FoundationResult<RawEvidence>
    fun threadEvidence(threadId: String, afterSequence: Long, limit: Int): FoundationResult<List<RawEvidence>>
    fun current(id: String): FoundationResult<DurableMemory>
    fun currentPage(afterId: String?, limit: Int): FoundationResult<List<DurableMemory>>
    fun history(id: String, afterRevision: Long, limit: Int): FoundationResult<List<MemoryRevision>>
    fun provenance(revisionId: String): FoundationResult<List<EvidenceRef>>
    fun audit(memoryId: String, afterCommandId: String?, limit: Int): FoundationResult<List<MemoryAudit>>
    fun latestCheckpoint(threadId: String): FoundationResult<LatestCheckpoint>
    fun checkpoints(threadId: String, afterId: String?, limit: Int): FoundationResult<List<ConversationCheckpoint>>
}

data class NewEvidence(val id: String, val kind: EvidenceSourceKind, val threadId: String?, val payload: String)
data class CreateMemory(val commandId: String, val memoryId: String, val revisionId: String,
    val kind: MemoryKind, val content: String, val evidence: List<EvidenceRef>)
data class UpdateMemory(val commandId: String, val memoryId: String, val revisionId: String,
    val expectedRevision: String, val content: String, val evidence: List<EvidenceRef>)
data class OwnerUpdate(val commandId: String, val memoryId: String, val revisionId: String,
    val expectedRevision: String, val content: String, val ownerEvidence: EvidenceRef)
data class OwnerRestore(val commandId: String, val memoryId: String, val revisionId: String,
    val expectedRevision: String, val historicalRevision: String, val ownerEvidence: EvidenceRef)
data class NewCheckpoint(val id: String, val threadId: String, val startSequence: Long,
    val endSequence: Long, val content: String, val producer: CoreSubsystemId, val supersedes: String?)

/** Future advisory ports return Proposal<T>; none of these DTOs can mutate storage. */
data class CreateMemoryProposal(val memoryId: String, val kind: MemoryKind, val content: String, val evidence: List<EvidenceRef>)
data class UpdateMemoryProposal(val memoryId: String, val expectedRevision: String, val content: String, val evidence: List<EvidenceRef>)
data class CheckpointProposal(val threadId: String, val startSequence: Long, val endSequence: Long,
    val content: String, val evidence: List<EvidenceRef>)
fun <T : Any> memoryProposal(value: T, proposer: CoreSubsystemId, evidence: List<EvidenceRef>): Proposal<T> =
    Proposal(value, proposer, evidence)

object MemoryBounds {
    const val MAX_ID = 128
    const val MAX_EVIDENCE_BYTES = 16 * 1024
    const val MAX_CONTENT_BYTES = 8 * 1024
    const val MAX_PAGE = 100
    const val MAX_PROVENANCE = 32
    fun id(value: String): Boolean = value.isNotBlank() && value == value.trim() && value.length <= MAX_ID &&
        value.all { it.isLetterOrDigit() || it in "._:-" }
    fun text(value: String, max: Int): Boolean = value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size <= max
    fun page(limit: Int, offset: Int): Boolean = limit in 1..MAX_PAGE && offset >= 0
    fun evidence(refs: List<EvidenceRef>): Boolean = refs.isNotEmpty() && refs.size <= MAX_PROVENANCE &&
        refs.distinct().size == refs.size && refs.all { id(it.opaqueId) }
}
