package com.mavyy.localyuki.admission

import android.content.Context
import com.mavyy.localyuki.foundation.admission.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.MemoryBounds
import com.mavyy.localyuki.foundation.resource.ResourceGovernor
import java.io.InputStream
import java.io.File
import java.time.Instant
import java.util.UUID

/** App-owned model intake/adaptation edge. No model-specific paths reach ordinary cognition. */
class ModelOrganManager(context: Context,private val governor: ResourceGovernor,
    private val adapters: List<ModelRuntimeAdapter> = emptyList(),private val foundationCertified: () -> Boolean = { false }) : AutoCloseable {
    private val directory=File(context.filesDir,"model-organs").apply { mkdirs() }
    private val store=ModelCandidateStore(context)
    fun importOwnerModel(input: InputStream,role: ModelRole,now: Instant): FoundationResult<ModelDescriptor> {
        if(!foundationCertified()) { input.close();return FoundationResult.Failure(FailureCategory.REJECTED) }
        val id="model-${UUID.randomUUID()}";val file=File(directory,id)
        val diskBudget=(directory.usableSpace-64L*1024*1024).coerceAtMost(ModelFiles.MAX_BYTES)
        if(diskBudget<8) { input.close();return FoundationResult.Failure(FailureCategory.REJECTED) }
        val copied=ModelFiles.import(input,file,diskBudget)
        if(copied is FoundationResult.Failure) return copied
        if(copied is FoundationResult.Unavailable) return copied
        val inspected=ModelFiles.inspect(file,id,role)
        if(inspected !is FoundationResult.Success) { file.delete();return when(inspected) {
            is FoundationResult.Failure -> inspected;is FoundationResult.Unavailable -> inspected;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) } }
        val registered=store.register(inspected.value,now)
        if(registered !is FoundationResult.Success) { file.delete();return when(registered) {
            is FoundationResult.Failure -> registered;is FoundationResult.Unavailable -> registered;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) } }
        return inspected
    }
    fun prepare(id: String,now: Instant): FoundationResult<AdmissionReceipt> {
        if(!foundationCertified()) return FoundationResult.Failure(FailureCategory.REJECTED)
        if(!MemoryBounds.id(id)) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val candidates=store.candidates()
        if(candidates !is FoundationResult.Success) return when(candidates) {
            is FoundationResult.Failure -> candidates;is FoundationResult.Unavailable -> candidates;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        val model=candidates.value.find { it.id==id } ?: return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val checked=ModelFiles.inspect(File(directory,id),id,model.role)
        if(checked !is FoundationResult.Success || checked.value!=model) return FoundationResult.Failure(FailureCategory.CONFLICT)
        val result=AutomaticModelAdmission(governor,adapters).prepare(model,now)
        if(result is FoundationResult.Success) {
            val persisted=store.receipt("receipt-${UUID.randomUUID()}",result.value,now)
            if(persisted is FoundationResult.Failure) return persisted
            if(persisted is FoundationResult.Unavailable) return persisted
        }
        return result
    }
    override fun close()=store.close()
}
