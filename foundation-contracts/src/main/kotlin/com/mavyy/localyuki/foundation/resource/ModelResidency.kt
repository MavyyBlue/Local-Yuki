package com.mavyy.localyuki.foundation.resource

import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.MemoryBounds

enum class Residency { LOADING, LOADED, UNLOADING, FAULTED }
data class ResidentModel(val id: String,val reservedBytes: Long,val state: Residency)
/** Trusted physical residency ledger. Ordinary cognition receives pressure, never these internals. */
class ModelResidencyRegistry {
    private val models=linkedMapOf<String,ResidentModel>()
    @Synchronized fun loading(id: String,bytes: Long): FoundationResult<Boolean> {
        if(!MemoryBounds.id(id) || bytes !in 1..16L*1024*1024*1024) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        if(id in models || models.size>=8) return FoundationResult.Failure(FailureCategory.CONFLICT)
        models[id]=ResidentModel(id,bytes,Residency.LOADING);return FoundationResult.Success(true)
    }
    @Synchronized fun loaded(id: String): FoundationResult<Boolean> {
        val prior=models[id] ?: return FoundationResult.Failure(FailureCategory.CONFLICT)
        if(prior.state!=Residency.LOADING) return FoundationResult.Failure(FailureCategory.CONFLICT)
        models[id]=prior.copy(state=Residency.LOADED);return FoundationResult.Success(true)
    }
    @Synchronized fun beginUnload(id: String): FoundationResult<Boolean> {
        val prior=models[id] ?: return FoundationResult.Failure(FailureCategory.CONFLICT)
        if(prior.state !in setOf(Residency.LOADING,Residency.LOADED)) return FoundationResult.Failure(FailureCategory.CONFLICT)
        models[id]=prior.copy(state=Residency.UNLOADING);return FoundationResult.Success(true)
    }
    @Synchronized fun unloaded(id: String): FoundationResult<Boolean> {
        if(models[id]?.state!=Residency.UNLOADING) return FoundationResult.Failure(FailureCategory.CONFLICT)
        models.remove(id);return FoundationResult.Success(true)
    }
    @Synchronized fun fault(id: String) { models[id]?.let { models[id]=it.copy(state=Residency.FAULTED) } }
    @Synchronized fun snapshot(): List<ResidentModel> = models.values.toList()
    @Synchronized fun reservedBytes(): Long=models.values.sumOf { it.reservedBytes }
    @Synchronized fun faulted(): Boolean=models.values.any { it.state==Residency.FAULTED }
}
