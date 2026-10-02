package com.mavyy.localyuki.brain

import android.content.Context
import com.mavyy.localyuki.affect.AffectStore
import com.mavyy.localyuki.continuity.ContinuityStore
import com.mavyy.localyuki.embodiment.CapabilityStore
import com.mavyy.localyuki.memory.*
import com.mavyy.localyuki.recovery.RecoveryStore
import com.mavyy.localyuki.resource.AndroidResourceProbe
import com.mavyy.localyuki.scheduler.SchedulerStore
import com.mavyy.localyuki.state.*
import com.mavyy.localyuki.foundation.affect.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.recovery.*
import com.mavyy.localyuki.foundation.resource.*
import com.mavyy.localyuki.foundation.state.*
import com.mavyy.localyuki.foundation.interoception.*
import com.mavyy.localyuki.foundation.workspace.*
import java.util.UUID

/** Trusted composition root. Engine-facing code receives only the projected DTOs/readers. */
class BrainRuntime(context: Context,private val temporal: com.mavyy.localyuki.foundation.temporal.TemporalGroundingReader=deviceTemporalGrounding(),
    private val resourceProbe: ((Boolean,java.time.Instant) -> FoundationResult<DeviceResources>)?=null) : AutoCloseable {
    val continuity=ContinuityStore(context)
    val state=YukiStateStore(context,temporal)
    val memory=MemoryStore(context,temporal)
    val living=LivingMemoryStore(context,temporal,memory.reader())
    val affect=AffectStore(context,temporal,memory.reader())
    val capabilities=CapabilityStore(context,{ if(it==CapabilityId.LOCAL_NOTE) true to true else false to false },setOf(CapabilityId.LOCAL_NOTE))
    val recovery=RecoveryStore(context,temporal,state.reader())
    val scheduler=SchedulerStore(context)
    val governor=ResourceGovernor()
    private val probe=AndroidResourceProbe(context)
    private val lexical=DeterministicLexicalRecallEngine(context,memory.reader(),living.reader(),living::coverage)
    private val verifier=RealityVerification(memory.reader(),capabilities,
        com.mavyy.localyuki.foundation.cognition.CognitiveIdentityReader { identityReader?.read() ?: FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE) },state.reader(),affect.reader())
    private val store=BrainDatabase(context)
    private val threadId="owner-main"
    private var identityReader: com.mavyy.localyuki.foundation.cognition.CognitiveIdentityReader?=null
    var ready=false;private set
    fun open(): Boolean {
        val identity=continuity.open()
        identityReader=identity.reader
        val stateStart=state.open();val memoryStart=memory.open();val livingStart=living.open()
        val affectStart=affect.open();val recoveryStart=recovery.open()
        ready=identity.reader.read() is FoundationResult.Success && stateStart.reader.read() is FoundationResult.Success &&
            memoryStart!=MemoryStore.Start.UNAVAILABLE && livingStart!=LivingMemoryStore.Start.UNAVAILABLE &&
            affectStart is FoundationResult.Success && recoveryStart is FoundationResult.Success && capabilities.capabilities() is FoundationResult.Success
        return ready
    }
    private fun ensureThread(): FoundationResult<MemoryThread> {
        val result=memory.getThread(threadId)
        if(result is FoundationResult.Success) return result
        if(result is FoundationResult.Unavailable) return result
        // Only create when genuinely absent, never overwrite a malformed existing thread.
        val exists=store.read { db ->store.rows(db,"SELECT thread_id FROM memory_thread WHERE thread_id=?",threadId).isNotEmpty() }
        if(exists is FoundationResult.Success && !exists.value) return memory.createThread(threadId)
        return FoundationResult.Failure(FailureCategory.CONFLICT)
    }
    fun refreshBody(foreground: Boolean): FoundationResult<ResourceState> {
        val stamp=temporal.ground();if(stamp !is FoundationResult.Success) return when(stamp) {
            is FoundationResult.Failure -> stamp;is FoundationResult.Unavailable -> stamp;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        val body=resourceProbe?.invoke(foreground,stamp.value.instant) ?: probe.read(foreground,stamp.value.instant)
        return when(body) {
            is FoundationResult.Success -> { governor.observe(body.value);FoundationResult.Success(governor.state(stamp.value.instant)) }
            is FoundationResult.Failure -> body
            is FoundationResult.Unavailable -> body
        }
    }
    fun saveInput(text: String): FoundationResult<RawEvidence> {
        if(!ready || !MemoryBounds.text(text,4096)) return FoundationResult.Failure(FailureCategory.INVALID_INPUT)
        val mode=recovery.reader().read()
        if(mode !is FoundationResult.Success || !RecoveryPolicy.mayAct(mode.value.mode)) return FoundationResult.Failure(FailureCategory.REJECTED)
        if(ensureThread() !is FoundationResult.Success) return FoundationResult.Failure(FailureCategory.CONFLICT)
        val input=memory.appendEvidence(NewEvidence("input-${UUID.randomUUID()}",EvidenceSourceKind.USER_INPUT,threadId,text))
        if(input is FoundationResult.Success) {
            val current=state.reader().read()
            if(current is FoundationResult.Success) state.recordInteraction(InteractionKind.USER_INPUT,current.value.revision)
        }
        return input
    }
    fun remember(text: String): FoundationResult<DurableMemory> {
        val input=saveInput(text)
        if(input !is FoundationResult.Success) return when(input) {
            is FoundationResult.Failure -> input;is FoundationResult.Unavailable -> input;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        val id="memory-${UUID.randomUUID()}"
        val result=memory.create(CreateMemory("create-$id",id,"revision-${UUID.randomUUID()}",MemoryKind.FACTUAL,text,listOf(input.value.ref)))
        if(result !is FoundationResult.Success) return when(result) {
            is FoundationResult.Failure -> result;is FoundationResult.Unavailable -> result;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        val surface=living.reconcileOne(id)
        if(surface !is FoundationResult.Success) return when(surface) {
            is FoundationResult.Failure -> surface;is FoundationResult.Unavailable -> surface;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        return memory.getCurrent(id)
    }
    fun updateMemory(current: DurableMemory,text: String): FoundationResult<DurableMemory> {
        val input=saveInput(text)
        if(input !is FoundationResult.Success) return when(input) {
            is FoundationResult.Failure -> input;is FoundationResult.Unavailable -> input;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        val result=memory.ownerUpdate(OwnerUpdate("update-${UUID.randomUUID()}",current.id,"revision-${UUID.randomUUID()}",current.current.id,text,input.value.ref))
        if(result !is FoundationResult.Success) return when(result) {
            is FoundationResult.Failure -> result;is FoundationResult.Unavailable -> result;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        living.reconcileOne(current.id)
        return memory.getCurrent(current.id)
    }
    fun restorePrevious(current: DurableMemory): FoundationResult<DurableMemory> {
        if(current.current.number<=1) return FoundationResult.Failure(FailureCategory.REJECTED)
        val prior=memory.history(current.id,current.current.number-2,1)
        if(prior !is FoundationResult.Success || prior.value.size!=1) return FoundationResult.Failure(FailureCategory.CONFLICT)
        val input=saveInput("Restore memory ${current.id} to revision ${prior.value.single().id}")
        if(input !is FoundationResult.Success) return when(input) {
            is FoundationResult.Failure -> input;is FoundationResult.Unavailable -> input;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        val result=memory.ownerRestore(OwnerRestore("restore-${UUID.randomUUID()}",current.id,"revision-${UUID.randomUUID()}",current.current.id,prior.value.single().id,input.value.ref))
        if(result !is FoundationResult.Success) return when(result) {
            is FoundationResult.Failure -> result;is FoundationResult.Unavailable -> result;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        living.reconcileOne(current.id)
        return memory.getCurrent(current.id)
    }
    fun recall(text: String): FoundationResult<RecallCandidateSet> = if(!ready) FoundationResult.Failure(FailureCategory.CONFLICT)
        else lexical.recall(RecallQuery(text,20))
    fun consume(candidate: GroundedRecallCandidate): FoundationResult<Long> = living.recordMeaningfulRecall(
        "recall-${UUID.randomUUID()}",candidate.memory.id,candidate.memory.current.id,candidate.surface.stateRevision)
    fun context(input: RawEvidence): FoundationResult<YukiTurnContext> {
        val identity=identityReader?.read() ?: FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE);val currentState=state.reader().read()
        val currentAffect=affect.read();val time=temporal.ground();val body=refreshBody(true);val capability=capabilities.capabilities()
        if(identity !is FoundationResult.Success || time !is FoundationResult.Success || currentState !is FoundationResult.Success ||
            currentAffect !is FoundationResult.Success || body !is FoundationResult.Success || capability !is FoundationResult.Success)
            return FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        val recalled=lexical.recall(RecallQuery(input.payload.take(128),5))
        val memories=if(recalled is FoundationResult.Success) recalled.value.candidates else emptyList()
        val uncertainty=if(recalled is FoundationResult.Success && recalled.value.coverage==IndexCoverage.COMPLETE) emptySet() else setOf("recall coverage is incomplete")
        return FoundationResult.Success(YukiTurnContext(input.payload,input.ref,identity.value,time.value,currentState.value,currentAffect.value,
            memories,capability.value,governor.interoception(time.value.instant,if(uncertainty.isEmpty()) MemoryCertainty.CLEAR else MemoryCertainty.UNCERTAIN,
                ScreenAvailability.UNAVAILABLE),uncertainty))
    }
    fun respond(input: RawEvidence,engine: LanguageExpressionEngine=UnavailableLanguageEngine): FoundationResult<Expression> {
        val context=context(input)
        return when(context) {
            is FoundationResult.Success -> TurnCoordinator(verifier,MockSystemOneEngine(),engine).respond(context.value)
            is FoundationResult.Failure -> context
            is FoundationResult.Unavailable -> context
        }
    }
    fun setNotesEnabled(enabled: Boolean): FoundationResult<Long> = when(val prior=capabilities.setting(CapabilityId.LOCAL_NOTE)) {
        is FoundationResult.Success -> capabilities.configure(CapabilityId.LOCAL_NOTE,prior.value.revision,prior.value.policy.copy(enabled=enabled))
        is FoundationResult.Failure -> prior;is FoundationResult.Unavailable -> prior
    }
    fun executeNote(intent: ActionIntent): FoundationResult<ToolObservation> {
        if(intent.targetPackage!=null) return FoundationResult.Failure(FailureCategory.REJECTED)
        val control=ExecutiveActionControl(capabilities,{
            val current=recovery.reader().read();val body=refreshBody(true)
            current is FoundationResult.Success && RecoveryPolicy.mayAct(current.value.mode) &&
                body is FoundationResult.Success && body.value.engagement!=Engagement.RECOVERY
        },mapOf(CapabilityId.LOCAL_NOTE to ActionExecutor { action ->
            // A stable action ID is also an evidence ID. A retry resolves exactly the established evidence.
            val ref=ToolEvidenceIdentity.ref(action.id,action.capability)
            val exists=store.read { db ->store.rows(db,"SELECT evidence_id FROM memory_evidence WHERE evidence_id=?",ref.opaqueId).isNotEmpty() }
            if(exists !is FoundationResult.Success) return@ActionExecutor FoundationResult.Failure(FailureCategory.CONFLICT)
            val evidence=if(exists.value) memory.getEvidence(ref) else memory.appendEvidence(NewEvidence(ref.opaqueId,EvidenceSourceKind.TOOL_RESULT,null,action.payload))
            when(evidence) {
                is FoundationResult.Success -> if(evidence.value.payload!=action.payload) FoundationResult.Failure(FailureCategory.CONFLICT)
                    else FoundationResult.Success(ToolObservation(action.id,action.capability,true,evidence.value.capturedAt,action.payload,evidence.value.ref))
                is FoundationResult.Failure -> evidence;is FoundationResult.Unavailable -> evidence
            }
        }))
        return control.execute(intent)
    }
    fun sleep(): FoundationResult<RecoverySnapshot> {
        val current=recovery.reader().read()
        if(current !is FoundationResult.Success) return current
        governor.quiesce()
        val preparing=recovery.transition(current.value.revision,RecoveryMode.PREPARING_SLEEP)
        if(preparing !is FoundationResult.Success) return preparing
        return recovery.transition(preparing.value.revision,RecoveryMode.SLEEPING)
    }
    fun wake(): FoundationResult<RecoverySnapshot> {
        val current=recovery.reader().read();if(current !is FoundationResult.Success) return current
        val restoring=if(current.value.mode==RecoveryMode.RECOVERING) current else recovery.transition(current.value.revision,RecoveryMode.RECOVERING)
        if(restoring !is FoundationResult.Success) return restoring
        return recovery.transition(restoring.value.revision,RecoveryMode.AWAKE)
    }
    fun backgroundSignal(signal: com.mavyy.localyuki.foundation.scheduler.BackgroundSignal,foreground: Boolean=false): FoundationResult<Boolean> {
        val current=recovery.reader().read()
        val body=refreshBody(foreground)
        val time=temporal.ground()
        if(current !is FoundationResult.Success || body !is FoundationResult.Success || time !is FoundationResult.Success)
            return FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        val decision=com.mavyy.localyuki.foundation.scheduler.DeterministicSalienceEngine().evaluate(signal)
        if(decision !is FoundationResult.Success) return when(decision) {
            is FoundationResult.Failure -> decision;is FoundationResult.Unavailable -> decision;else -> FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        return scheduler.observe(signal,decision.value,time.value.instant,
            if(RecoveryPolicy.mayAct(current.value.mode)) body.value.engagement else Engagement.RECOVERY)
    }
    fun maintenance(): FoundationResult<Boolean> = recovery.maintain(listOf({ affect.normalize() },{
        val cursor=recovery.maintenanceCursor()
        when(cursor) {
            is FoundationResult.Failure -> cursor
            is FoundationResult.Unavailable -> cursor
            is FoundationResult.Success -> when(val page=living.reconcilePage(cursor.value.ifEmpty { null },5)) {
                is FoundationResult.Success -> recovery.advanceMaintenanceCursor(cursor.value,if(page.value.size<5) "" else page.value.last().memoryId)
                is FoundationResult.Failure -> page
                is FoundationResult.Unavailable -> page
            }
        }
    }))
    override fun close() { ready=false;lexical.close();scheduler.close();recovery.close();capabilities.close();affect.close();living.close();memory.close();state.close();continuity.close();store.close();governor.quiesce() }
}
