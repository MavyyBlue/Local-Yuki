package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.affect.*
import com.mavyy.localyuki.foundation.admission.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import com.mavyy.localyuki.foundation.resource.*
import com.mavyy.localyuki.foundation.recovery.*
import com.mavyy.localyuki.foundation.scheduler.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class BrainPolicyTest {
    private val now=Instant.parse("2026-10-02T12:00:00Z")
    private fun resources(battery: Int?=80,available: Long=2048*ResourceGovernor.MIB,
        thermal: ThermalPressure=ThermalPressure.NORMAL,foreground: Boolean=true)=
        DeviceResources(4096*ResourceGovernor.MIB,available,battery,false,thermal,foreground,now)
    @Test fun affectSaturatesAndDecaysToCoreDispositions() {
        var vector=AffectPolicy.baseline
        repeat(100) { vector=AffectPolicy.apply(vector,AffectEventKind.POSITIVE) }
        assertEquals(AffectVector(100,100,100),vector)
        assertEquals(AffectPolicy.baseline,AffectPolicy.normalize(vector,now,now.plusSeconds(100*3600)))
        assertEquals(vector,AffectPolicy.normalize(vector,now,now.minusSeconds(60)))
        repeat(100) { vector=AffectPolicy.apply(vector,AffectEventKind.NEGATIVE) }
        assertEquals(-100,vector.valence);assertEquals(-100,vector.affiliation)
    }
    @Test fun unknownStaleOrCriticalBodyRejectsWork() {
        val g=ResourceGovernor();val work=Workload("work",ResourceGovernor.MIB,500)
        assertTrue(g.reserve(work,now) is FoundationResult.Unavailable)
        for(r in listOf(resources(battery=3),resources(thermal=ThermalPressure.SEVERE),resources(available=64*ResourceGovernor.MIB))) {
            g.observe(r);assertEquals(Engagement.RECOVERY,g.state(now).engagement)
            assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),g.reserve(work,now))
        }
        g.observe(resources());assertEquals(Engagement.RECOVERY,g.state(now.plusSeconds(31)).engagement)
    }
    @Test fun lowPowerAllowsOnlyCheapBoundedWorkAndThermalUnknownRejectsNeural() {
        val g=ResourceGovernor();g.observe(resources(foreground=false))
        assertTrue(g.reserve(Workload("cheap",ResourceGovernor.MIB,500),now) is FoundationResult.Success)
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),g.reserve(Workload("expensive",100*ResourceGovernor.MIB,500,true),now))
        g.quiesce();g.observe(resources(thermal=ThermalPressure.UNKNOWN))
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),g.reserve(Workload("unknown",ResourceGovernor.MIB,500,true),now))
    }
    @Test fun leasesAccountForCombinedMemoryAndExpire() {
        val g=ResourceGovernor();g.observe(resources(available=512*ResourceGovernor.MIB))
        assertTrue(g.reserve(Workload("one",200*ResourceGovernor.MIB,500),now) is FoundationResult.Success)
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),g.reserve(Workload("two",100*ResourceGovernor.MIB,500),now))
        assertTrue(g.reserve(Workload("two",100*ResourceGovernor.MIB,500),now.plusMillis(500)) is FoundationResult.Success)
        g.release("two");assertEquals(0,g.state(now.plusMillis(500)).activeWorkloads)
    }
    private fun registry(enabled: Boolean)=object: CapabilityReader {
        override fun capabilities()=FoundationResult.Success(listOf(CapabilityView(CapabilityId.LOCAL_NOTE,enabled,true,true,true)))
        override fun permits(capability: CapabilityId,targetPackage: String?)=FoundationResult.Success(enabled)
    }
    @Test fun executiveRejectsUnavailableAndQuiescentActionsWithoutCallingExecutor() {
        var called=0
        val executor=ActionExecutor { called++;FoundationResult.Success(ToolObservation(it.id,it.capability,true,now,"saved",null)) }
        val intent=ActionIntent("action",CapabilityId.LOCAL_NOTE,"note")
        assertTrue(ExecutiveActionControl(registry(false),{true},mapOf(CapabilityId.LOCAL_NOTE to executor)).execute(intent) is FoundationResult.Unavailable)
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),ExecutiveActionControl(registry(true),{false},mapOf(CapabilityId.LOCAL_NOTE to executor)).execute(intent))
        assertEquals(0,called)
        assertTrue(ExecutiveActionControl(registry(true),{true},mapOf(CapabilityId.LOCAL_NOTE to executor)).execute(intent) is FoundationResult.Success)
        assertEquals(1,called)
    }
    @Test fun executiveRejectsForgedResultsAndContainsExecutorFailures() {
        val intent=ActionIntent("action",CapabilityId.LOCAL_NOTE,"note")
        val forged=ActionExecutor { FoundationResult.Success(ToolObservation("wrong",it.capability,true,now,"saved",null)) }
        assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),ExecutiveActionControl(registry(true),{true},mapOf(CapabilityId.LOCAL_NOTE to forged)).execute(intent))
        val throws=ActionExecutor { throw IllegalStateException() }
        assertEquals(FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE),ExecutiveActionControl(registry(true),{true},mapOf(CapabilityId.LOCAL_NOTE to throws)).execute(intent))
    }
    @Test fun schedulerUsesFreshAvailableSignalsCooldownAndPhysicalGate() {
        val signal=BackgroundSignal("signal",SignalKind.FOREGROUND_DURATION,now,1800)
        val decision=(DeterministicSalienceEngine().evaluate(signal) as FoundationResult.Success).value
        assertTrue(SchedulerPolicy.mayEscalate(signal,decision,null,now,Engagement.ATTENTIVE))
        assertFalse(SchedulerPolicy.mayEscalate(signal,decision,now.minusSeconds(899),now,Engagement.ATTENTIVE))
        assertFalse(SchedulerPolicy.mayEscalate(signal,decision,null,now.plusSeconds(31),Engagement.ATTENTIVE))
        assertFalse(SchedulerPolicy.mayEscalate(signal,decision,null,now,Engagement.RECOVERY))
        assertFalse(SchedulerPolicy.mayEscalate(signal.copy(platformAvailable=false),decision,null,now,Engagement.ATTENTIVE))
    }
    @Test fun recoveryHasNoDirectSleepToAwakeJump() {
        assertFalse(RecoveryPolicy.permits(RecoveryMode.SLEEPING,RecoveryMode.AWAKE))
        assertTrue(RecoveryPolicy.permits(RecoveryMode.SLEEPING,RecoveryMode.RECOVERING))
        assertFalse(RecoveryPolicy.mayAct(RecoveryMode.RECOVERING))
        assertTrue(RecoveryPolicy.mayAct(RecoveryMode.AWAKE))
    }
    @Test fun admissionRequiresRuntimeCompatibilityAndMeasuredResources() {
        val model=ModelDescriptor("model",ModelRole.SYSTEM_ONE,"a".repeat(64),1024,ModelFormat.GGUF,3)
        val g=ResourceGovernor();g.observe(resources())
        assertEquals(FoundationResult.Unavailable(UnavailableReason.NOT_IMPLEMENTED),ModelAdmission(g,emptyList()).admit(model,"missing",now))
        var unloads=0
        val adapter=object: ModelRuntimeAdapter {
            override val id="mock-runtime"
            override fun profile(model: ModelDescriptor)=FoundationResult.Success(RuntimeProfile(id,64*ResourceGovernor.MIB,ModelFormat.GGUF,setOf(ModelRole.SYSTEM_ONE)))
            override fun smokeTest(model: ModelDescriptor,lease: WorkloadLease)=FoundationResult.Success(true)
            override fun unload(): FoundationResult<Boolean> { unloads++;return FoundationResult.Success(true) }
        }
        val automatic=AutomaticModelAdmission(g,listOf(adapter))
        assertTrue((automatic.prepare(model,now) as FoundationResult.Success).value.accepted)
        assertTrue(g.residency.snapshot().isEmpty())
        unloads=0
        val admission=ModelAdmission(g,listOf(adapter))
        assertTrue((admission.admit(model,adapter.id,now) as FoundationResult.Success).value.accepted)
        assertEquals(1,unloads);assertEquals(0,g.state(now).activeWorkloads)
        assertFalse((admission.admit(model.copy(role=ModelRole.LANGUAGE_EXPRESSION),adapter.id,now) as FoundationResult.Success).value.accepted)
        g.observe(resources(thermal=ThermalPressure.SEVERE))
        assertFalse((admission.admit(model,adapter.id,now) as FoundationResult.Success).value.accepted)
        assertEquals(1,unloads)
    }
    @Test fun failedUnloadRejectsAdmissionAndForcesRecovery() {
        val g=ResourceGovernor();g.observe(resources())
        val adapter=object: ModelRuntimeAdapter {
            override val id="bad-unload"
            override fun profile(model: ModelDescriptor)=FoundationResult.Success(RuntimeProfile(id,ResourceGovernor.MIB,ModelFormat.GGUF,setOf(ModelRole.SYSTEM_ONE)))
            override fun smokeTest(model: ModelDescriptor,lease: WorkloadLease)=FoundationResult.Success(true)
            override fun unload(): FoundationResult<Boolean> = throw IllegalStateException()
        }
        val model=ModelDescriptor("model",ModelRole.SYSTEM_ONE,"a".repeat(64),1024,ModelFormat.GGUF,3)
        assertEquals(FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE),ModelAdmission(g,listOf(adapter)).admit(model,adapter.id,now))
        assertEquals(Engagement.RECOVERY,g.state(now).engagement)
        assertEquals(0,g.state(now).activeWorkloads)
    }

}
