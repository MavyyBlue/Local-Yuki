package com.mavyy.localyuki.foundation
import com.mavyy.localyuki.foundation.resource.*
import com.mavyy.localyuki.foundation.contracts.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
class AdaptiveResourceTest {
 private val now=Instant.parse("2026-10-06T00:00:00Z")
 private val body=DeviceResources(8L*1024*ResourceGovernor.MIB,4L*1024*ResourceGovernor.MIB,80,false,ThermalPressure.NORMAL,true,now,ComputeCapabilities(cpuThreads=8))
 @Test fun competingLoadHeatAndBatteryReducePayload() {
  val normal=AdaptiveProfile.derive(body,now)!!
  for(b in listOf(body.copy(competingForeground=true),body.copy(thermal=ThermalPressure.LIGHT),body.copy(powerSave=true),body.copy(lowMemory=true))) {
   val p=AdaptiveProfile.derive(b,now)!!;assertTrue(p.context<normal.context);assertEquals(1,p.threads);assertEquals(1,p.maxPasses)
  }
 }
 @Test fun criticalUnknownAndOversizedDemandFailClosed() {
  assertNull(AdaptiveProfile.derive(body.copy(thermal=ThermalPressure.SEVERE),now))
  assertNull(AdaptiveProfile.derive(body.copy(thermal=ThermalPressure.UNKNOWN),now))
  assertNull(AdaptiveProfile.derive(body,now.plusSeconds(31)))
  assertNull(AdaptiveProfile.derive(body,now,8L*1024*ResourceGovernor.MIB))
 }
 @Test fun neuralConcurrencyCannotOverbook() {
  val g=ResourceGovernor();g.observe(body)
  assertTrue(g.reserve(Workload("a",ResourceGovernor.MIB,1000,true),now) is FoundationResult.Success)
  assertTrue(g.reserve(Workload("b",ResourceGovernor.MIB,1000,true),now) is FoundationResult.Failure)
  g.release("a");assertTrue(g.reserve(Workload("b",ResourceGovernor.MIB,1000,true),now) is FoundationResult.Success)
 }
 @Test fun measuredLatencyLimitsOutputRatherThanAssumingFastHardware() {
  val p=AdaptiveProfile.derive(body,now)!!
  assertEquals(174,AdaptiveProfile.measuredOutput(p,1000,10000,100))
  assertEquals(256,AdaptiveProfile.measuredOutput(p,100,1000,100))
 }
 @Test fun unusablySlowOrMissingMeasurementsCannotAdmitGeneration() {
  val p=AdaptiveProfile.derive(body,now)!!
  assertNull(AdaptiveProfile.measuredOutput(p,20000,10000,20))
  assertNull(AdaptiveProfile.measuredOutput(p,0,0,10))
  assertNull(AdaptiveProfile.measuredOutput(p,30000,1000,100))
 }
}
