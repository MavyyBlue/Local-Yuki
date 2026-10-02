package com.mavyy.localyuki.foundation.resource

import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.MemoryBounds
import com.mavyy.localyuki.foundation.interoception.*
import java.time.Instant
import java.time.Duration

enum class ThermalPressure { UNKNOWN, NORMAL, LIGHT, MODERATE, SEVERE, CRITICAL }
enum class Engagement { LOW_POWER_AWARE, ATTENTIVE, THINKING, FULLY_ENGAGED, RECOVERY }
data class ComputeCapabilities(val abis: List<String> = emptyList(),val cpuThreads: Int?=null,
    val gpuAvailable: Boolean?=null,val npuAvailable: Boolean?=null) {
    init { require(abis.size<=8 && abis.all { it.length in 1..64 } && (cpuThreads==null || cpuThreads in 1..256)) }
}
data class DeviceResources(val totalBytes: Long,val availableBytes: Long,val batteryPercent: Int?,
    val charging: Boolean,val thermal: ThermalPressure,val foreground: Boolean,val capturedAt: Instant,val compute: ComputeCapabilities=ComputeCapabilities()) {
    init { require(totalBytes>0 && availableBytes in 0..totalBytes && (batteryPercent==null || batteryPercent in 0..100)) }
}
data class Workload(val id: String,val memoryBytes: Long,val maxDurationMillis: Long,val neural: Boolean=false) {
    init { require(MemoryBounds.id(id) && memoryBytes>0 && maxDurationMillis in 1..10_000) }
}
data class WorkloadLease(val workload: Workload,val expiresAt: Instant)
data class ResourceState(val engagement: Engagement,val pressure: ResourcePressure,val activeWorkloads: Int)

/** Physical resource authority; no engine can grant itself a lease or change the limits. */
class ResourceGovernor {
    companion object { const val MIB=1024L*1024;const val MAX_LEASES=4 }
    private var body: DeviceResources?=null
    private var faulted=false
    val residency=ModelResidencyRegistry()
    private val leases=linkedMapOf<String,WorkloadLease>()
    @Synchronized fun observe(resources: DeviceResources) { body=resources }
    private fun expire(now: Instant) { leases.entries.removeAll { now>=it.value.expiresAt } }
    @Synchronized fun state(now: Instant): ResourceState {
        expire(now)
        val r=body
        val critical=faulted || residency.faulted() || r==null || Duration.between(r.capturedAt,now).seconds !in 0..30 ||
            r.availableBytes<128*MIB || r.thermal>=ThermalPressure.SEVERE || !r.charging && (r.batteryPercent ?: 0)<=5
        val low=!critical && (!r.foreground || !r.charging && (r.batteryPercent ?: 0)<=20)
        val constrained=r==null || r.availableBytes<512*MIB || r.thermal>=ThermalPressure.MODERATE
        val engagement=when {
            critical -> Engagement.RECOVERY
            low -> Engagement.LOW_POWER_AWARE
            leases.values.any { it.workload.neural } -> Engagement.FULLY_ENGAGED
            leases.isNotEmpty() -> Engagement.THINKING
            else -> Engagement.ATTENTIVE
        }
        return ResourceState(engagement,if(critical || constrained) ResourcePressure.CONSTRAINED
            else if(r!!.thermal>=ThermalPressure.LIGHT) ResourcePressure.WARM else ResourcePressure.NORMAL,leases.size)
    }
    @Synchronized fun reserve(workload: Workload,now: Instant): FoundationResult<WorkloadLease> {
        val state=state(now);val r=body ?: return FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        if (workload.id in leases || leases.size>=MAX_LEASES) return FoundationResult.Failure(FailureCategory.CONFLICT)
        if (state.engagement==Engagement.RECOVERY) return FoundationResult.Failure(FailureCategory.REJECTED)
        val cheap=workload.memoryBytes<=16*MIB && workload.maxDurationMillis<=1_000 && !workload.neural
        if (state.engagement==Engagement.LOW_POWER_AWARE && !cheap || workload.neural &&
            (r.thermal==ThermalPressure.UNKNOWN || state.pressure!=ResourcePressure.NORMAL || r.batteryPercent==null))
            return FoundationResult.Failure(FailureCategory.REJECTED)
        val reserved=leases.values.sumOf { it.workload.memoryBytes }+residency.reservedBytes()
        if (workload.memoryBytes>r.availableBytes/2-reserved) return FoundationResult.Failure(FailureCategory.REJECTED)
        val lease=WorkloadLease(workload,now.plusMillis(workload.maxDurationMillis))
        leases[workload.id]=lease;return FoundationResult.Success(lease)
    }
    @Synchronized fun release(id: String) { leases.remove(id) }
    @Synchronized fun reportFault() { faulted=true }
    @Synchronized fun quiesce() { leases.clear() }
    fun interoception(now: Instant,memory: MemoryCertainty,screen: ScreenAvailability)=
        InteroceptiveSnapshot(state(now).pressure,memory,screen)
}
