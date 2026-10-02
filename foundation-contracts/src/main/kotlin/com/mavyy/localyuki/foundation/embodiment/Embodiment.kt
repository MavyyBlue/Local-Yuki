package com.mavyy.localyuki.foundation.embodiment

import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.MemoryBounds
import com.mavyy.localyuki.foundation.provenance.EvidenceRef
import java.time.Instant

enum class CapabilityId { LOCAL_NOTE, SCREEN, NOTIFICATIONS, SPEECH_INPUT, SPEECH_OUTPUT }
data class CapabilityPolicy(val enabled: Boolean=false, val allowedPackages: Set<String> = emptySet(),
    val deniedPackages: Set<String> = emptySet()) {
    init { require(allowedPackages.size<=32 && deniedPackages.size<=32)
        require((allowedPackages+deniedPackages).all { it.length in 1..160 && it.matches(Regex("[A-Za-z0-9_.]+")) }) }
}
data class CapabilityView(val id: CapabilityId, val enabled: Boolean, val supported: Boolean,
    val platformGranted: Boolean, val executorAvailable: Boolean) {
    val available get() = enabled && supported && platformGranted && executorAvailable
}
interface CapabilityReader {
    fun capabilities(): FoundationResult<List<CapabilityView>>
    fun permits(capability: CapabilityId, targetPackage: String?): FoundationResult<Boolean>
}
data class ActionIntent(val id: String, val capability: CapabilityId, val payload: String, val targetPackage: String?=null) {
    init { require(MemoryBounds.id(id) && MemoryBounds.text(payload,512))
        require(targetPackage==null || targetPackage.length<=160 && targetPackage.matches(Regex("[A-Za-z0-9_.]+"))) }
}
data class ToolObservation(val actionId: String, val capability: CapabilityId, val succeeded: Boolean,
    val observedAt: Instant, val summary: String, val evidence: EvidenceRef?)
fun interface ActionExecutor { fun execute(intent: ActionIntent): FoundationResult<ToolObservation> }

/** Platform/owner availability is rechecked at the point of dispatch. No per-action approval. */
class ExecutiveActionControl(private val registry: CapabilityReader, private val mayExecute: () -> Boolean,
    private val executors: Map<CapabilityId,ActionExecutor>) {
    fun execute(intent: ActionIntent): FoundationResult<ToolObservation> {
        if (!mayExecute()) return FoundationResult.Failure(FailureCategory.REJECTED)
        when (val permission=registry.permits(intent.capability,intent.targetPackage)) {
            is FoundationResult.Failure -> return permission
            is FoundationResult.Unavailable -> return permission
            is FoundationResult.Success -> if (!permission.value) return FoundationResult.Unavailable(UnavailableReason.CAPABILITY_UNAVAILABLE)
        }
        val executor=executors[intent.capability] ?: return FoundationResult.Unavailable(UnavailableReason.CAPABILITY_UNAVAILABLE)
        val result=try { executor.execute(intent) } catch (_: Exception) { return FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        if (result is FoundationResult.Success && (result.value.actionId!=intent.id || result.value.capability!=intent.capability ||
            !MemoryBounds.text(result.value.summary,512) || result.value.evidence?.let { !MemoryBounds.id(it.opaqueId) }==true))
            return FoundationResult.Failure(FailureCategory.CONFLICT)
        return result
    }
}

/** A grounded local-note receipt is bound to its action/capability, not an arbitrary evidence ID. */
object ToolEvidenceIdentity {
    fun ref(id: String,capability: CapabilityId): EvidenceRef {
        val key=java.security.MessageDigest.getInstance("SHA-256").digest("$id\u0000${capability.name}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return EvidenceRef("tool-$key",com.mavyy.localyuki.foundation.provenance.EvidenceSourceKind.TOOL_RESULT)
    }
}
