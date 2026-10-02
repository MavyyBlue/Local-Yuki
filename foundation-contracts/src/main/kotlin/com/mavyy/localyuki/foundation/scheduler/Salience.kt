package com.mavyy.localyuki.foundation.scheduler

import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.memory.MemoryBounds
import com.mavyy.localyuki.foundation.resource.Engagement
import java.time.Instant
import java.time.Duration

enum class SignalKind { INTERACTION, INACTIVITY, FOREGROUND_DURATION, SCHEDULED, RESOURCE_CHANGE, NOTIFICATION }
data class BackgroundSignal(val id: String,val kind: SignalKind,val capturedAt: Instant,
    val durationSeconds: Long=0,val pendingIntentions: Int=0,val platformAvailable: Boolean=true) {
    init { require(MemoryBounds.id(id) && durationSeconds in 0..86400 && pendingIntentions in 0..32) }
}
data class SalienceDecision(val salient: Boolean,val reason: String,val score: Int)
fun interface SalienceEngine { fun evaluate(signal: BackgroundSignal): FoundationResult<SalienceDecision> }
class DeterministicSalienceEngine : SalienceEngine {
    override fun evaluate(signal: BackgroundSignal): FoundationResult<SalienceDecision> {
        val score=if (!signal.platformAvailable) 0 else when(signal.kind) {
            SignalKind.FOREGROUND_DURATION -> if(signal.durationSeconds>=1800) 70 else 0
            SignalKind.INACTIVITY,SignalKind.SCHEDULED -> if(signal.pendingIntentions>0 && signal.durationSeconds>=900) 60 else 0
            SignalKind.NOTIFICATION -> 20
            else -> 0
        }
        return FoundationResult.Success(SalienceDecision(score>=60,signal.kind.name.lowercase(),score))
    }
}
object SchedulerPolicy {
    const val COOLDOWN_SECONDS=900L
    fun mayEscalate(signal: BackgroundSignal,decision: SalienceDecision,last: Instant?,now: Instant,
        engagement: Engagement): Boolean = signal.platformAvailable && decision.salient && decision.score in 60..100 &&
        engagement !in setOf(Engagement.RECOVERY,Engagement.LOW_POWER_AWARE) &&
        Duration.between(signal.capturedAt,now).seconds in 0..30 &&
        (last==null || Duration.between(last,now).seconds>=COOLDOWN_SECONDS)
}
