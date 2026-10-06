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
    private val app=context.applicationContext
    private val body=com.mavyy.localyuki.embodiment.AndroidBody(app)
    val capabilities=CapabilityStore(context,body::grant,body.executors)
    val plans=com.mavyy.localyuki.presence.PlanStore(context,memory.reader())
    val life=com.mavyy.localyuki.presence.LifeStore(context,memory.reader())
    val recovery=RecoveryStore(context,temporal,state.reader())
    val scheduler=SchedulerStore(context)
    val governor=ResourceGovernor()
    val models=com.mavyy.localyuki.admission.ModelSubsystem(context,governor)
    val semantic=com.mavyy.localyuki.semantic.SemanticMemory(context,models,memory.reader(),living.reader())
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
            affectStart is FoundationResult.Success && recoveryStart is FoundationResult.Success && capabilities.capabilities() is FoundationResult.Success &&
            life.policy() is FoundationResult.Success && life.records() is FoundationResult.Success && models.models() is FoundationResult.Success && plans.list() is FoundationResult.Success
        if(ready)plans.recover()
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
        if(models.activeHash(com.mavyy.localyuki.foundation.admission.ModelRole.EMBEDDING)!=null)semantic.maintain()
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
        if(models.activeHash(com.mavyy.localyuki.foundation.admission.ModelRole.EMBEDDING)!=null)semantic.maintain()
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
        if(models.activeHash(com.mavyy.localyuki.foundation.admission.ModelRole.EMBEDDING)!=null)semantic.maintain()
        return memory.getCurrent(current.id)
    }
    fun recall(text: String): FoundationResult<RecallCandidateSet> {
        if(!ready)return FoundationResult.Failure(FailureCategory.CONFLICT)
        val lexicalResult=lexical.recall(RecallQuery(text,20))
        if(models.activeHash(com.mavyy.localyuki.foundation.admission.ModelRole.EMBEDDING)==null)return lexicalResult
        val neural=semantic.recall(RecallQuery(text,10))
        if(neural !is FoundationResult.Success)return lexicalResult
        val exact=(lexicalResult as? FoundationResult.Success)?.value
        return FoundationResult.Success(RecallCandidateSet((exact?.candidates.orEmpty()+neural.value.candidates).distinctBy { it.memory.id }.take(20),IndexCoverage.PARTIAL))
    }
    fun consume(candidate: GroundedRecallCandidate): FoundationResult<Long> = living.recordMeaningfulRecall(
        "recall-${UUID.randomUUID()}",candidate.memory.id,candidate.memory.current.id,candidate.surface.stateRevision)
    fun context(input: RawEvidence): FoundationResult<YukiTurnContext> {
        val identity=identityReader?.read() ?: FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE);val currentState=state.reader().read()
        val currentAffect=affect.read();val time=temporal.ground();val body=refreshBody(true);val capability=capabilities.capabilities()
        if(identity !is FoundationResult.Success || time !is FoundationResult.Success || currentState !is FoundationResult.Success ||
            currentAffect !is FoundationResult.Success || body !is FoundationResult.Success || capability !is FoundationResult.Success)
            return FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        val recalled=recall(input.payload.take(128))
        val memories=if(recalled is FoundationResult.Success) recalled.value.candidates else emptyList()
        val observations=groundedObservations()
        val uncertainty=if(recalled is FoundationResult.Success && recalled.value.coverage==IndexCoverage.COMPLETE) emptySet() else setOf("I may be missing relevant memories.")
        return FoundationResult.Success(YukiTurnContext(input.payload,input.ref,identity.value,time.value,currentState.value,currentAffect.value,
            memories,capability.value,governor.interoception(time.value.instant,if(uncertainty.isEmpty()) MemoryCertainty.CLEAR else MemoryCertainty.UNCERTAIN,
                ScreenAvailability.UNAVAILABLE),uncertainty,observations=observations))
    }
    private fun groundedObservations():List<RawEvidence> {
        val ids=store.read { db->store.rows(db,"SELECT evidence_id,source_kind FROM memory_evidence WHERE source_kind IN ('PERCEPTION_RESULT','TOOL_RESULT') OR thread_id=? ORDER BY captured_at DESC,evidence_id DESC LIMIT 8",threadId) }
        if(ids !is FoundationResult.Success)return emptyList()
        return ids.value.mapNotNull { r->(memory.getEvidence(EvidenceRef(r[0]!!,EvidenceSourceKind.valueOf(r[1]!!))) as? FoundationResult.Success)?.value }
    }
    fun conversationHistory():List<String> = when(val rows=store.read { db->store.rows(db,"SELECT evidence_id,source_kind FROM memory_evidence WHERE thread_id=? ORDER BY sequence DESC LIMIT 8",threadId) }) {
        is FoundationResult.Success->rows.value.reversed().mapNotNull {
            val ref=EvidenceRef(it[0]!!,EvidenceSourceKind.valueOf(it[1]!!))
            (memory.getEvidence(ref) as? FoundationResult.Success)?.value?.let { e->"${e.ref.sourceKind}: ${e.payload}" }
        };else->emptyList()
    }
    fun converse(input:RawEvidence):FoundationResult<Expression> {
        return try { models.beginTurn();converseWithinEnvelope(input) }catch(_:Exception){FoundationResult.Failure(FailureCategory.REJECTED)}finally{models.endTurn()}
    }
    private fun converseWithinEnvelope(input:RawEvidence):FoundationResult<Expression> {
        val turn=context(input);if(turn !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        val pending=life.append(com.mavyy.localyuki.presence.LifeKind.INTENTION,"unfinished-turn",input.payload.take(256),listOf(input.ref),turn.value.time.instant)
        val records=((life.active() as? FoundationResult.Success)?.value.orEmpty().take(8)+(life.records(limit=8) as? FoundationResult.Success)?.value.orEmpty()).distinctBy { it.id }
        val ongoing={ records.filter { !it.resolved }.map { "${it.kind} at ${it.created}: ${it.content}" } }
        val one=com.mavyy.localyuki.cognition.NeuralSystemOne(models,::conversationHistory,ongoing)
        val two=com.mavyy.localyuki.cognition.NeuralSystemTwo(models,::conversationHistory,ongoing)
        val organ=com.mavyy.localyuki.cognition.NeuralExpression(models)
        val toolSources=mutableListOf<EvidenceRef>()
        val expression=LanguageExpressionEngine { request ->
            models.checkTurnActive()
            val policy=life.policy()
            val observations=two.actions.map { intent ->
                models.checkTurnActive()
                if(policy !is FoundationResult.Success || !policy.value.enabled) "Proposed ${intent.capability.name} was not executed because owner autonomy is disabled."
                else when(val actual=executeAction(intent)) {
                    is FoundationResult.Success -> { actual.value.evidence?.let(toolSources::add); actual.value.summary }
                    else -> "${intent.capability.name} did not complete; no successful observation exists."
                }
            }
            val scheduled=two.deferred.map { (intent,due) ->
                models.checkTurnActive()
                if(policy is FoundationResult.Success && policy.value.enabled && plans.enqueue(intent,due,input.ref,turn.value.time.instant) is FoundationResult.Success) "Accepted a deferred ${intent.capability.name} plan for $due. It still requires fresh grants and safe resources when due."
                else "Deferred ${intent.capability.name} plan was not admitted."
            }
            val m=request.meaning
            val supplied=if(observations.isEmpty()&&scheduled.isEmpty())request else ExpressionRequest(m.copy(points=(m.points+observations+scheduled).take(16)))
            organ.express(supplied)
        }
        val result=TurnCoordinator(verifier,one,expression,two,com.mavyy.localyuki.cognition.CognitiveComposer(one,two)).respond(turn.value)
        if(result !is FoundationResult.Success)return result
        val text=result.value.text
        val saved=memory.appendEvidence(NewEvidence("reply-${UUID.randomUUID()}",EvidenceSourceKind.YUKI_OUTPUT,threadId,text))
        if(saved !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        val current=state.reader().read();if(current is FoundationResult.Success)state.recordInteraction(InteractionKind.YUKI_OUTPUT,current.value.revision)
        if(pending is FoundationResult.Success)life.resolve(pending.value.id)
        val refs=com.mavyy.localyuki.cognition.CognitiveJson.refs(turn.value)
        one.updates.forEach { proposal ->
            val prior=(life.records(proposal.kind,100) as? FoundationResult.Success)?.value?.firstOrNull { it.topic==proposal.topic && !it.resolved }
            life.append(proposal.kind,proposal.topic,proposal.content,proposal.sources.map(refs::getValue),turn.value.time.instant,prior?.id)
        }
        life.append(com.mavyy.localyuki.presence.LifeKind.REFLECTION,"conversation",result.value.text.take(1024),listOf(saved.value.ref)+result.value.evidence.take(8),turn.value.time.instant)
        val freshAffect=affect.read();if(freshAffect is FoundationResult.Success)affect.apply(AffectEvent("affect-${UUID.randomUUID()}",freshAffect.value.revision,
            when(one.affect){"warmth"->AffectEventKind.POSITIVE;"frustration"->AffectEventKind.NEGATIVE;else->AffectEventKind.CALM},input.ref))
        return FoundationResult.Success(Expression(text,(result.value.evidence+toolSources).distinct()))
    }
    fun executeAction(intent:ActionIntent):FoundationResult<ToolObservation> {
        if(intent.capability==CapabilityId.LOCAL_NOTE)return executeNote(intent)
        val executor=ActionExecutor { action->
            val request=org.json.JSONObject().put("payload",action.payload).put("target",action.targetPackage).toString()
            val ref=ToolEvidenceIdentity.ref(action.id,action.capability)
            // Claim durably BEFORE dispatch. A process death leaves an uncertain receipt; never repeat a side effect.
            val claim=store.write { db ->
                val prior=store.rows(db,"SELECT intent,capability,result,evidence_id FROM executive_audit WHERE id=?",action.id).firstOrNull()
                if(prior!=null) {
                    if(prior[0]!=request||prior[1]!=action.capability.name)throw BrainDatabase.Conflict()
                    prior
                } else {
                    db.execSQL("INSERT INTO executive_audit VALUES(?,?,?,?,?,?,?)",arrayOf(action.id,request,action.capability.name,"AndroidBody-v1",capabilities.capabilities().toString(),"DISPATCHING",ref.opaqueId))
                    emptyList<String?>()
                }
            }
            if(claim !is FoundationResult.Success)return@ActionExecutor FoundationResult.Failure(FailureCategory.CONFLICT)
            if(claim.value.isNotEmpty()) {
                if(claim.value[2]=="DISPATCHING")return@ActionExecutor FoundationResult.Failure(FailureCategory.CONFLICT)
                val established=memory.getEvidence(ref)
                if(established !is FoundationResult.Success||established.value.payload!=claim.value[2])return@ActionExecutor FoundationResult.Failure(FailureCategory.CONFLICT)
                return@ActionExecutor FoundationResult.Success(ToolObservation(action.id,action.capability,!established.value.payload.startsWith("Execution failed:"),established.value.capturedAt,established.value.payload,ref))
            }
            val budget=if(action.capability==CapabilityId.IMAGE_TEXT)governor.reserve(Workload("ocr-${action.id}",128*ResourceGovernor.MIB,15000),java.time.Instant.now()) else null
            val result=try { if(action.capability==CapabilityId.IMAGE_TEXT && budget !is FoundationResult.Success)error("Image perception deferred by resource governor");body.execute(action) }catch(e:Exception){ "Execution failed: ${e.message?.take(400)?:e.javaClass.simpleName}" }
            if(budget is FoundationResult.Success)governor.release(budget.value.workload.id)
            val succeeded=!result.startsWith("Execution failed:")
            val evidence=memory.appendEvidence(NewEvidence(ref.opaqueId,EvidenceSourceKind.TOOL_RESULT,null,result.take(512)))
            if(evidence !is FoundationResult.Success)return@ActionExecutor FoundationResult.Failure(FailureCategory.CONFLICT)
            val recorded=store.write { db->db.execSQL("UPDATE executive_audit SET result=? WHERE id=? AND result='DISPATCHING'",arrayOf(evidence.value.payload,action.id));true }
            if(recorded !is FoundationResult.Success)return@ActionExecutor FoundationResult.Failure(FailureCategory.CONFLICT)
            FoundationResult.Success(ToolObservation(action.id,action.capability,succeeded,evidence.value.capturedAt,evidence.value.payload,ref))
        }
        return ExecutiveActionControl(capabilities,{
            val current=recovery.reader().read();val resource=refreshBody(true)
            current is FoundationResult.Success && RecoveryPolicy.mayAct(current.value.mode) && resource is FoundationResult.Success && resource.value.engagement!=Engagement.RECOVERY
        },body.executors.associateWith { executor }).execute(intent)
    }
    fun configureCapability(id:CapabilityId,enabled:Boolean,denied:Set<String> = emptySet()):FoundationResult<Long> {
        val prior=capabilities.setting(id);if(prior !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        return capabilities.configure(id,prior.value.revision,prior.value.policy.copy(enabled=enabled,deniedPackages=denied))
    }
    fun observeEvent(kind:String,pkg:String?,description:String):FoundationResult<Boolean> {
        require(kind in setOf("FOREGROUND","NOTIFICATION","SCHEDULED","VOICE"))
        val captured=memory.appendEvidence(NewEvidence("perception-${UUID.randomUUID()}",EvidenceSourceKind.PERCEPTION_RESULT,null,
            "$kind ${pkg?:""}: ${description.take(384)}"))
        if(captured !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        store.write { db->db.execSQL("INSERT INTO body_signal VALUES(?,?,?,?,?)",arrayOf(captured.value.ref.opaqueId,kind,pkg,description.take(384),captured.value.capturedAt.toString()))
            db.execSQL("DELETE FROM body_signal WHERE id NOT IN (SELECT id FROM body_signal ORDER BY created DESC LIMIT 128)");true }
        return reflectAndInitiate(captured.value)
    }
    fun executeDuePlans():FoundationResult<Int> {
        val policy=life.policy();val time=temporal.ground();val body=refreshBody(false)
        if(policy !is FoundationResult.Success||time !is FoundationResult.Success||body !is FoundationResult.Success||!policy.value.enabled||policy.value.quiet(time.value.instant.atZone(time.value.zoneId))||body.value.engagement==Engagement.RECOVERY)return FoundationResult.Success(0)
        val pending=plans.list();if(pending !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        var done=0
        pending.value.filter { it.status=="PENDING"&&it.due<=time.value.instant }.take(2).forEach { plan ->
            if(memory.getEvidence(plan.evidence) !is FoundationResult.Success) {plans.finish(plan.intent.id,"BLOCKED");return@forEach}
            val claimed=plans.claim(plan.intent.id);if(claimed !is FoundationResult.Success||!claimed.value)return@forEach
            when(val result=executeAction(plan.intent)) {
                is FoundationResult.Success->{plans.finish(plan.intent.id,if(result.value.succeeded)"SUCCEEDED" else "FAILED",result.value.evidence);done++}
                is FoundationResult.Unavailable->plans.finish(plan.intent.id,"BLOCKED")
                else->plans.finish(plan.intent.id,"UNKNOWN")
            }
        }
        return FoundationResult.Success(done)
    }
    fun reflectAndInitiate(observation:RawEvidence?=null):FoundationResult<Boolean> {
        val body=refreshBody(false);if(body !is FoundationResult.Success||body.value.engagement==Engagement.RECOVERY)return FoundationResult.Success(false)
        val policy=life.policy();if(policy !is FoundationResult.Success || !policy.value.enabled)return FoundationResult.Success(false)
        val now=temporal.ground();if(now !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        val active=(life.active() as? FoundationResult.Success)?.value.orEmpty()
        val current=state.reader().read();if(current !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        val last=current.value.interactions.find { it.kind==InteractionKind.USER_INPUT }?.instant
        if(last!=null&&java.time.Duration.between(last,now.value.instant).seconds<900)return FoundationResult.Success(false)
        val item=active.firstOrNull()
        val ref=item?.evidence?.firstOrNull() ?: observation?.ref ?: return FoundationResult.Success(false)
        val duration=if(com.mavyy.localyuki.embodiment.YukiAccessibility.foregroundSince>0)(android.os.SystemClock.elapsedRealtime()-com.mavyy.localyuki.embodiment.YukiAccessibility.foregroundSince)/1000 else 0
        val score=if(item!=null)75 else if(duration>=2700)75 else 30
        val conclusion=if(item!=null)"I revisited an unresolved ${item.kind.name.lowercase()}: ${item.content.take(240)}. I still need a follow-up or more evidence."
            else if(duration>=2700)"Accessibility indicates prolonged foreground use. I could offer a gentle check-in; I do not know what the owner is viewing."
            else return FoundationResult.Success(false)
        val prior=(life.records(com.mavyy.localyuki.presence.LifeKind.REFLECTION) as? FoundationResult.Success)?.value?.firstOrNull { it.topic==(item?.topic ?: "foreground-checkin") }
        if(prior!=null && java.time.Duration.between(prior.created,now.value.instant).seconds<3600)return FoundationResult.Success(false)
        val reflection=life.append(com.mavyy.localyuki.presence.LifeKind.REFLECTION,item?.topic ?: "foreground-checkin",conclusion,listOf(ref),now.value.instant)
        if(reflection !is FoundationResult.Success)return FoundationResult.Failure(FailureCategory.CONFLICT)
        val channel=if(policy.value.notifications)"NOTIFICATION" else if(policy.value.overlay)"OVERLAY" else "IN_APP"
        val reserved=life.reserveDelivery(reflection.value,score,channel,now.value.instant.atZone(now.value.zoneId))
        if(reserved !is FoundationResult.Success||!reserved.value)return FoundationResult.Success(false)
        val text=if(item!=null)"Darling, shall we revisit ${item.content.take(160)}?" else "Darling, would a short pause feel good?"
        if(channel=="IN_APP")return FoundationResult.Success(true)
        val result=executeAction(ActionIntent("initiate-${UUID.randomUUID()}",if(channel=="NOTIFICATION")CapabilityId.CONVERSATION_NOTIFY else CapabilityId.COMPANION,text))
        return FoundationResult.Success(result is FoundationResult.Success && result.value.succeeded)
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
        models.unload();com.mavyy.localyuki.embodiment.LocalVoice.stop();governor.quiesce()
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
    override fun close() { ready=false;models.unload();semantic.close();models.close();plans.close();life.close();lexical.close();scheduler.close();recovery.close();capabilities.close();affect.close();living.close();memory.close();state.close();continuity.close();store.close();governor.quiesce() }
}
