package com.mavyy.localyuki.foundation.cognition

import com.mavyy.localyuki.foundation.contracts.FailureCategory
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.identity.*
import com.mavyy.localyuki.foundation.personality.*

/** Only semantic self-content crosses into future cognition. No authority or executor port. */
data class CognitiveIdentityView(
    val self: SelfIdentityAnchor,
    val primaryRelationship: RelationshipAnchor,
    val honesty: HonestyPolicy,
    val continuityFormatVersion: ContinuityFormatVersion,
    val personality: PersonalityCapsule
)

fun interface CognitiveIdentityReader {
    fun read(): FoundationResult<CognitiveIdentityView>
}

/** Canonical seed projection; Phase 2B will own validated restoration from durable storage. */
object CanonicalCognitiveIdentityReader : CognitiveIdentityReader {
    override fun read(): FoundationResult<CognitiveIdentityView> =
        CognitiveIdentityProjector.project(CanonicalIdentitySeed.snapshot(), CanonicalPersonalityCapsule.snapshot())

    internal fun project(identity: IdentityContinuitySnapshot, capsule: PersonalityCapsule) =
        CognitiveIdentityProjector.project(identity, capsule)
}

/** Shared semantic validator for reference seeds and restored app-owned continuity. */
object CognitiveIdentityProjector {
    fun project(identity: IdentityContinuitySnapshot, capsule: PersonalityCapsule): FoundationResult<CognitiveIdentityView> {
        if (identity != CanonicalIdentitySeed.snapshot() || capsule != CanonicalPersonalityCapsule.snapshot() ||
            identity.personalityCapsuleRef != PersonalityCapsuleRef(capsule.id, capsule.version) ||
            identity.honesty != capsule.honesty
        ) return FoundationResult.Failure(FailureCategory.CONFLICT)
        return FoundationResult.Success(CognitiveIdentityView(identity.self, identity.primaryRelationship,
            identity.honesty, identity.continuityFormatVersion, capsule))
    }
}
