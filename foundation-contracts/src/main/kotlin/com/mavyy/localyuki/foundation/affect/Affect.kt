package com.mavyy.localyuki.foundation.affect

import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.provenance.EvidenceRef
import java.time.Instant
import java.time.Duration

/** Synthetic state, not a claim of biological emotion or subjective experience. */
data class AffectVector(val valence: Int, val arousal: Int, val affiliation: Int) {
    init { require(valence in -100..100 && arousal in 0..100 && affiliation in -100..100) }
}
data class AffectSnapshot(val revision: Long, val vector: AffectVector, val updatedAt: Instant)
data class AffectiveAssociation(val memoryId: String, val strength: Int, val reinforcementCount: Long)
enum class AffectEventKind { POSITIVE, NEGATIVE, UNCERTAIN, CALM }
data class AffectEvent(val id: String, val expectedRevision: Long, val kind: AffectEventKind,
    val evidence: EvidenceRef, val memoryId: String? = null)
fun interface AffectReader { fun read(): FoundationResult<AffectSnapshot> }
fun interface AffectInterpreter { fun interpret(text: String): FoundationResult<AffectEventKind> }
object AffectPolicy {
    val baseline = AffectVector(20,15,40)
    const val MAX_ASSOCIATIONS = 256
    fun normalize(vector: AffectVector, from: Instant, now: Instant): AffectVector {
        val steps = Duration.between(from,now).toHours().coerceIn(0,100).toInt()*2
        fun toward(value: Int, target: Int) = if (value < target) (value+steps).coerceAtMost(target) else (value-steps).coerceAtLeast(target)
        return AffectVector(toward(vector.valence,baseline.valence),toward(vector.arousal,baseline.arousal),toward(vector.affiliation,baseline.affiliation))
    }
    fun apply(vector: AffectVector, kind: AffectEventKind): AffectVector {
        val delta = when (kind) {
            AffectEventKind.POSITIVE -> intArrayOf(12,5,8)
            AffectEventKind.NEGATIVE -> intArrayOf(-12,10,-4)
            AffectEventKind.UNCERTAIN -> intArrayOf(-3,8,0)
            AffectEventKind.CALM -> intArrayOf(3,-12,2)
        }
        return AffectVector((vector.valence+delta[0]).coerceIn(-100,100),
            (vector.arousal+delta[1]).coerceIn(0,100),(vector.affiliation+delta[2]).coerceIn(-100,100))
    }
    /** Advisory only: does not directly mutate memory importance or schedule an action. */
    fun salienceBias(vector: AffectVector): Int = ((vector.arousal-baseline.arousal)/5).coerceIn(-10,10)
}
