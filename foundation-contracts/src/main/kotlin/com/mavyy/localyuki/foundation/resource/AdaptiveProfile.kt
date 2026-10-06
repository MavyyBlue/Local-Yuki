package com.mavyy.localyuki.foundation.resource

import java.time.*

enum class OperatingMode { IDLE_LOW_POWER, BACKGROUND_AWARE, INTERACTIVE, THINKING, HIGH_COGNITION, THERMAL_LIMITED, MEMORY_PRESSURE, BATTERY_CONSERVATION, RECOVERY }
data class SafeRuntimeProfile(val context:Int,val output:Int,val threads:Int,val batch:Int,val maxPasses:Int,
    val deadlineMillis:Long,val memoryBudget:Long,val mode:OperatingMode,val backend:String="CPU",val offload:Int=0) {
    init { require(context in 256..4096 && output in 16..512 && threads in 1..4 && batch in 8..128 && maxPasses in 1..4 && deadlineMillis in 1000..60000 && memoryBudget>0 && backend=="CPU" && offload==0) }
}
/** Observable phone constraints outrank optional background cognition. No GPU/NPU promise is inferred. */
object AdaptiveProfile {
    /** Conservative generated-token budget from the actual admission timing, including startup reserve. */
    fun measuredOutput(profile:SafeRuntimeProfile,startupMs:Long,inferenceMs:Long,tokens:Int):Int? {
        if(startupMs<0 || inferenceMs<=0 || tokens<=0 || startupMs>=profile.deadlineMillis) return null
        val capacity=(tokens.toDouble()/inferenceMs*(profile.deadlineMillis-startupMs)*0.6).toInt()
        return if(capacity<64) null else minOf(profile.output,capacity)
    }
    fun mode(r:DeviceResources,now:Instant,active:Int=0):OperatingMode=when {
        Duration.between(r.capturedAt,now).seconds !in 0..30 || r.availableBytes<128*ResourceGovernor.MIB || r.thermal>=ThermalPressure.SEVERE || (!r.charging&&(r.batteryPercent?:0)<=5) -> OperatingMode.RECOVERY
        r.lowMemory || r.availableBytes<768*ResourceGovernor.MIB -> OperatingMode.MEMORY_PRESSURE
        r.thermal>=ThermalPressure.LIGHT -> OperatingMode.THERMAL_LIMITED
        r.powerSave || !r.charging&&(r.batteryPercent?:0)<=20 -> OperatingMode.BATTERY_CONSERVATION
        r.competingForeground -> OperatingMode.BACKGROUND_AWARE
        !r.foreground -> OperatingMode.IDLE_LOW_POWER
        active>0 -> OperatingMode.THINKING
        else -> OperatingMode.INTERACTIVE
    }
    fun derive(r:DeviceResources,now:Instant,measuredPeak:Long=0):SafeRuntimeProfile? {
        val mode=mode(r,now)
        if(mode==OperatingMode.RECOVERY || r.thermal==ThermalPressure.UNKNOWN || r.batteryPercent==null) return null
        val budget=minOf(r.availableBytes/2, (r.availableBytes-512*ResourceGovernor.MIB).coerceAtLeast(0),2L*1024*ResourceGovernor.MIB)
        if(budget<=0 || measuredPeak>budget) return null
        val constrained=mode in setOf(OperatingMode.MEMORY_PRESSURE,OperatingMode.THERMAL_LIMITED,OperatingMode.BATTERY_CONSERVATION,OperatingMode.BACKGROUND_AWARE,OperatingMode.IDLE_LOW_POWER)
        return SafeRuntimeProfile(if(constrained)512 else 4096,if(constrained)64 else 256,
            if(constrained)1 else minOf(4,maxOf(1,(r.compute.cpuThreads?:2)/2)),if(constrained)16 else 64,
            if(constrained)1 else 4,if(constrained)12000 else 30000,budget,mode)
    }
}
