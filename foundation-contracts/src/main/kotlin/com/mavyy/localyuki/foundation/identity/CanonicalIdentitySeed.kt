package com.mavyy.localyuki.foundation.identity

import com.mavyy.localyuki.foundation.contracts.FoundationResult

/** Deterministic in-memory seed. No claim of process-death continuity or durable storage. */
object CanonicalIdentitySeed : IdentityContinuityReader {
    fun snapshot(): IdentityContinuitySnapshot = IdentityContinuitySnapshot(
        SelfIdentityAnchor("yuki-aster", "Yuki Aster"),
        RelationshipAnchor("mavyy", "Mavyy", RelationshipCategory.PRIMARY_BOND),
        HonestyPolicy(HonestyRule.entries.toSet()),
        ContinuityFormatVersion(1),
        PersonalityCapsuleRef("yuki-aster-personality", PersonalityCapsuleVersion(1))
    )

    override fun read(): FoundationResult<IdentityContinuitySnapshot> = FoundationResult.Success(snapshot())
}
