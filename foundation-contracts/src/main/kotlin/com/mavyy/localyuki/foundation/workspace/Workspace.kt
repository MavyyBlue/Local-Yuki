package com.mavyy.localyuki.foundation.workspace

import com.mavyy.localyuki.foundation.affect.*
import com.mavyy.localyuki.foundation.cognition.CognitiveIdentityView
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import com.mavyy.localyuki.foundation.interoception.InteroceptiveSnapshot
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.state.YukiStateSnapshot
import com.mavyy.localyuki.foundation.temporal.TemporalGroundingSnapshot

/** Semantic experience only. No storage handles, model paths, secrets or raw authority plumbing. */
data class YukiTurnContext(val input: String,val inputEvidence: EvidenceRef,val identity: CognitiveIdentityView,
    val time: TemporalGroundingSnapshot,val state: YukiStateSnapshot,val affect: AffectSnapshot,
    val memories: List<GroundedRecallCandidate>,val capabilities: List<CapabilityView>,
    val interoception: InteroceptiveSnapshot,val uncertainty: Set<String> = emptySet(),val reasoning: String?=null) {
    init { require(MemoryBounds.text(input,4096) && memories.size<=20 && capabilities.size<=16 && uncertainty.size<=16)
        require(uncertainty.all { it.length in 1..160 } && (reasoning==null || MemoryBounds.text(reasoning,2048))) }
}
data class Verification(val grounded: Boolean,val reasons: List<String>)
class RealityVerification(private val memory: MemoryReader,private val capabilities: CapabilityReader,
    private val identity: com.mavyy.localyuki.foundation.cognition.CognitiveIdentityReader=com.mavyy.localyuki.foundation.cognition.CanonicalCognitiveIdentityReader,
    private val state: com.mavyy.localyuki.foundation.state.YukiStateReader?=null,private val affect: AffectReader?=null) {
    fun verify(context: YukiTurnContext): FoundationResult<Verification> {
        val reasons=mutableListOf<String>()
        when(val actual=identity.read()) {
            is FoundationResult.Success -> if(actual.value!=context.identity) reasons+="identity projection changed"
            is FoundationResult.Failure -> return actual
            is FoundationResult.Unavailable -> return actual
        }
        for(actual in listOfNotNull(state?.read(),affect?.read())) {
            when(actual) {
                is FoundationResult.Failure -> return actual
                is FoundationResult.Unavailable -> return actual
                is FoundationResult.Success -> if(actual.value!=context.state && actual.value!=context.affect) reasons+="current state changed"
            }
        }
        when(val input=memory.evidence(context.inputEvidence)) {
            is FoundationResult.Failure -> return input
            is FoundationResult.Unavailable -> return input
            is FoundationResult.Success -> if(input.value.ref.sourceKind!=EvidenceSourceKind.USER_INPUT || input.value.payload!=context.input)
                reasons+="input is not grounded"
        }
        for(candidate in context.memories) {
            when(val current=memory.current(candidate.memory.id)) {
                is FoundationResult.Failure -> return current
                is FoundationResult.Unavailable -> return current
                is FoundationResult.Success -> if(current.value!=candidate.memory ||
                    candidate.memory.current.status!=MemoryStatus.CURRENT || candidate.surface.sourceRevisionId!=current.value.current.id)
                    reasons+="memory source changed"
            }
        }
        when(val actual=capabilities.capabilities()) {
            is FoundationResult.Failure -> return actual
            is FoundationResult.Unavailable -> return actual
            is FoundationResult.Success -> if(actual.value!=context.capabilities) reasons+="capability availability changed"
        }
        if(context.state.updatedAt>context.time.instant || context.affect.updatedAt>context.time.instant) reasons+="clock precedes current state"
        return FoundationResult.Success(Verification(reasons.isEmpty(),reasons.distinct()))
    }
    fun verifyTool(result: ToolObservation): FoundationResult<Verification> {
        val ref=result.evidence ?: return FoundationResult.Success(Verification(false,listOf("tool evidence missing")))
        return when(val source=memory.evidence(ref)) {
            is FoundationResult.Failure -> source
            is FoundationResult.Unavailable -> source
            is FoundationResult.Success -> FoundationResult.Success(Verification(result.succeeded && result.capability==CapabilityId.LOCAL_NOTE &&
                source.value.ref==ToolEvidenceIdentity.ref(result.actionId,result.capability) &&
                source.value.payload==result.summary && source.value.capturedAt==result.observedAt,listOf("tool result checked against evidence")))
        }
    }
}
enum class DecisionKind { RESPOND, REMAIN_QUIET, REASON }
data class Decision(val kind: DecisionKind,val confidence: Int) { init { require(confidence in 0..100) } }
data class ReasoningProposal(val plan: String,val evidence: List<EvidenceRef>) { init { require(MemoryBounds.text(plan,2048) && evidence.size<=32) } }
data class Expression(val text: String,val evidence: List<EvidenceRef>) { init { require(MemoryBounds.text(text,4096) && evidence.size<=32) } }
fun interface SystemOneEngine { fun decide(context: YukiTurnContext): FoundationResult<Decision> }
fun interface SystemTwoEngine { fun reason(context: YukiTurnContext): FoundationResult<ReasoningProposal> }
/** App/cognition-prepared meaning. Language chooses wording, not plans, actions or factual authority. */
data class PreparedMeaning(val points: List<String>,val evidence: List<EvidenceRef>,val uncertainty: Set<String> = emptySet()) {
    init {
        require(points.size in 1..16 && points.all { MemoryBounds.text(it,2048) })
        require(points.sumOf { it.toByteArray(Charsets.UTF_8).size }<=4096)
        require(evidence.size in 1..32 && uncertainty.size<=16 && uncertainty.all { it.length in 1..160 })
    }
}
/** Deliberately excludes raw input, memory stores, capabilities, reasoning workspace and tool handles. */
data class ExpressionRequest(val meaning: PreparedMeaning)
fun interface MeaningComposer {
    fun compose(context: YukiTurnContext,decision: Decision): FoundationResult<PreparedMeaning>
}
/** Foundation fallback only: acknowledges receipt; never invents an answer to a question. */
object AcknowledgementComposer : MeaningComposer {
    override fun compose(context: YukiTurnContext,decision: Decision)=FoundationResult.Success(
        PreparedMeaning(listOf("Your message has been received."),listOf(context.inputEvidence),context.uncertainty.toSet()))
}
fun interface LanguageExpressionEngine { fun express(request: ExpressionRequest): FoundationResult<Expression> }
fun interface EmbeddingEngine { fun embed(text: String): FoundationResult<List<Float>> }
fun interface VisionEngine { fun describe(inputId: String): FoundationResult<String> }
fun interface SpeechEngine { fun transcribe(inputId: String): FoundationResult<String> }
class MockSystemOneEngine : SystemOneEngine {
    override fun decide(context: YukiTurnContext)=FoundationResult.Success(Decision(if(context.uncertainty.isEmpty()) DecisionKind.RESPOND else DecisionKind.REASON,100))
}
object UnavailableLanguageEngine : LanguageExpressionEngine {
    override fun express(request: ExpressionRequest)=FoundationResult.Unavailable(UnavailableReason.NOT_IMPLEMENTED)
}
object UnavailableSystemTwoEngine : SystemTwoEngine {
    override fun reason(context: YukiTurnContext)=FoundationResult.Unavailable(UnavailableReason.NOT_IMPLEMENTED)
}
class MockSystemTwoEngine : SystemTwoEngine {
    override fun reason(context: YukiTurnContext)=FoundationResult.Success(ReasoningProposal(
        "Use the available evidence; preserve uncertainty where grounding is incomplete.",listOf(context.inputEvidence)))
}
class MockLanguageEngine(private val response: String) : LanguageExpressionEngine {
    override fun express(request: ExpressionRequest)=FoundationResult.Success(Expression(response,request.meaning.evidence.toList()))
}
/** Replaceable engines receive verified context and return proposals, never authoritative mutations. */
class TurnCoordinator(private val verifier: RealityVerification,private val systemOne: SystemOneEngine,
    private val language: LanguageExpressionEngine,private val systemTwo: SystemTwoEngine=UnavailableSystemTwoEngine,
    private val composer: MeaningComposer=AcknowledgementComposer) {
    private fun detached(c: YukiTurnContext)=c.copy(
        state=c.state.copy(intentions=c.state.intentions.toList(),topics=c.state.topics.toList(),interactions=c.state.interactions.toList()),
        memories=c.memories.map { m -> m.copy(memory=m.memory.copy(current=m.memory.current.copy(evidence=m.memory.current.evidence.toList())),
            surface=m.surface.copy(terms=m.surface.terms.toList()),matchedTerms=m.matchedTerms.toList()) },
        capabilities=c.capabilities.toList(),uncertainty=c.uncertainty.toSet())
    fun respond(context: YukiTurnContext): FoundationResult<Expression> {
        when(val check=verifier.verify(context)) {
            is FoundationResult.Failure -> return check
            is FoundationResult.Unavailable -> return check
            is FoundationResult.Success -> if(!check.value.grounded) return FoundationResult.Failure(FailureCategory.CONFLICT)
        }
        val decision=try { systemOne.decide(detached(context)) } catch (_: Exception) { return FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        if(decision is FoundationResult.Failure) return decision
        if(decision is FoundationResult.Unavailable) return decision
        decision as FoundationResult.Success
        if(decision.value.kind==DecisionKind.REMAIN_QUIET) return FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
        var integrated=context
        if(decision.value.kind==DecisionKind.REASON) {
            val reasoning=try { systemTwo.reason(detached(context)) } catch (_: Exception) { return FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
            when(reasoning) {
                is FoundationResult.Failure -> return reasoning
                is FoundationResult.Unavailable -> return reasoning
                is FoundationResult.Success -> {
                    val permitted=context.memories.flatMap { it.memory.current.evidence }.toSet()+context.inputEvidence
                    if(reasoning.value.evidence.isEmpty() || reasoning.value.evidence.any { it !in permitted }) return FoundationResult.Failure(FailureCategory.REJECTED)
                    integrated=context.copy(reasoning=reasoning.value.plan)
                }
            }
        }
        // Thought/meaning selection happens before wording, in a separate cognitive subsystem.
        val composed=try { composer.compose(detached(integrated),decision.value) }
            catch (_: Exception) { return FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        val meaning=when(composed) {
            is FoundationResult.Failure -> return composed
            is FoundationResult.Unavailable -> return composed
            is FoundationResult.Success -> composed.value
        }
        val permitted=context.memories.flatMap { it.memory.current.evidence }.toSet()+context.inputEvidence
        if(meaning.evidence.any { it !in permitted }) return FoundationResult.Failure(FailureCategory.REJECTED)
        val groundedEvidence=meaning.evidence.toSet()
        val request=ExpressionRequest(meaning.copy(points=meaning.points.toList(),evidence=meaning.evidence.toList(),uncertainty=meaning.uncertainty.toSet()))
        val output=try { language.express(request) } catch (_: Exception) { return FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
        if(output is FoundationResult.Success) {
            if(output.value.evidence.isEmpty() || output.value.evidence.any { it !in groundedEvidence }) return FoundationResult.Failure(FailureCategory.REJECTED)
        }
        return output
    }
}
