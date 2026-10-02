package com.mavyy.localyuki.foundation.admission

import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.resource.*
import com.mavyy.localyuki.foundation.memory.MemoryBounds

enum class ModelRole { SYSTEM_ONE, SYSTEM_TWO, LANGUAGE_EXPRESSION, EMBEDDING, RERANKER, AFFECT, VISION, SPEECH }
enum class ModelFormat { GGUF, SAFETENSORS, UNKNOWN }
data class ModelDescriptor(val id: String,val role: ModelRole,val sha256: String,val fileBytes: Long,
    val format: ModelFormat,val containerVersion: Int?) {
    init { require(MemoryBounds.id(id) && sha256.matches(Regex("[0-9a-f]{64}")) && fileBytes in 1..4L*1024*1024*1024) }
}
data class RuntimeProfile(val adapterId: String,val requiredMemoryBytes: Long,val supportedFormat: ModelFormat,
    val supportedRoles: Set<ModelRole>) {
    init { require(MemoryBounds.id(adapterId) && requiredMemoryBytes>0 && supportedRoles.isNotEmpty()) }
}
data class AdmissionReceipt(val model: ModelDescriptor,val adapterId: String,val accepted: Boolean,val reason: String)
interface ModelRuntimeAdapter {
    val id: String
    fun profile(model: ModelDescriptor): FoundationResult<RuntimeProfile>
    fun smokeTest(model: ModelDescriptor,lease: WorkloadLease): FoundationResult<Boolean>
    fun unload(): FoundationResult<Boolean>
}
/** Format detection alone never claims a model is loaded or runtime-compatible. */
class ModelAdmission(private val governor: ResourceGovernor,adapters: List<ModelRuntimeAdapter>) {
    private val adapters=adapters.associateBy { it.id }.also { require(it.size==adapters.size) }
    fun admit(model: ModelDescriptor,adapterId: String,now: java.time.Instant): FoundationResult<AdmissionReceipt> {
        val adapter=adapters[adapterId] ?: return FoundationResult.Unavailable(UnavailableReason.NOT_IMPLEMENTED)
        return try {
            val profile=adapter.profile(model)
            if(profile is FoundationResult.Failure) return profile
            if(profile is FoundationResult.Unavailable) return profile
            profile as FoundationResult.Success
            if(profile.value.adapterId!=adapterId || profile.value.supportedFormat!=model.format || model.role !in profile.value.supportedRoles)
                return FoundationResult.Success(AdmissionReceipt(model,adapterId,false,"incompatible role or format"))
            val lease=governor.reserve(Workload("admit-${model.id}".take(128),profile.value.requiredMemoryBytes,10_000,true),now)
            if(lease is FoundationResult.Failure) return FoundationResult.Success(AdmissionReceipt(model,adapterId,false,"resource budget rejected"))
            if(lease is FoundationResult.Unavailable) return lease
            lease as FoundationResult.Success
            if(governor.residency.loading(model.id,profile.value.requiredMemoryBytes) !is FoundationResult.Success) {
                governor.release(lease.value.workload.id)
                return FoundationResult.Failure(FailureCategory.CONFLICT)
            }
            val smoke=try { adapter.smokeTest(model,lease.value) }
                catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
            if(smoke is FoundationResult.Success && smoke.value) governor.residency.loaded(model.id)
            governor.residency.beginUnload(model.id)
            val unloaded=try { adapter.unload() } catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
            governor.release(lease.value.workload.id)
            if(unloaded !is FoundationResult.Success || !unloaded.value) {
                governor.residency.fault(model.id);governor.reportFault()
                return FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE)
            }
            governor.residency.unloaded(model.id)
            when(smoke) {
                is FoundationResult.Success -> FoundationResult.Success(AdmissionReceipt(model,adapterId,smoke.value,if(smoke.value) "runtime smoke test passed" else "runtime smoke test failed"))
                is FoundationResult.Failure -> smoke
                is FoundationResult.Unavailable -> smoke
            }
        } catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    }
}

/** Select the lowest measured compatible runtime profile; unknown runtime/format stays explicit. */
class AutomaticModelAdmission(private val governor: ResourceGovernor,private val adapters: List<ModelRuntimeAdapter>) {
    init { require(adapters.map { it.id }.distinct().size==adapters.size) }
    fun prepare(model: ModelDescriptor,now: java.time.Instant): FoundationResult<AdmissionReceipt> {
        val compatible=adapters.mapNotNull { adapter ->
            val profile=try { adapter.profile(model) } catch (_: Exception) { null }
            if(profile is FoundationResult.Success && profile.value.adapterId==adapter.id &&
                profile.value.supportedFormat==model.format && model.role in profile.value.supportedRoles)
                adapter to profile.value else null
        }.sortedWith(compareBy<Pair<ModelRuntimeAdapter,RuntimeProfile>> { it.second.requiredMemoryBytes }.thenBy { it.first.id })
        if(compatible.isEmpty()) return FoundationResult.Unavailable(UnavailableReason.NOT_IMPLEMENTED)
        return ModelAdmission(governor,compatible.map { it.first }).admit(model,compatible.first().first.id,now)
    }
}
