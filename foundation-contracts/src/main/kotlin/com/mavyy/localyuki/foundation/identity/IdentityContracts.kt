package com.mavyy.localyuki.foundation.identity

import com.mavyy.localyuki.foundation.contracts.FoundationResult
import java.util.Collections

data class SelfIdentityAnchor(val stableId: String, val canonicalName: String) {
    init { require(stableId.isNotBlank() && canonicalName.isNotBlank()) }
}

enum class RelationshipCategory { PRIMARY_BOND }

data class RelationshipAnchor(
    val stableId: String,
    val displayName: String,
    val category: RelationshipCategory
) {
    init { require(stableId.isNotBlank() && displayName.isNotBlank()) }
}

data class ContinuityFormatVersion(val value: Int) {
    init { require(value > 0) }
}

data class PersonalityCapsuleVersion(val value: Int) {
    init { require(value > 0) }
}

data class PersonalityCapsuleRef(val stableId: String, val version: PersonalityCapsuleVersion) {
    init { require(stableId.isNotBlank()) }
}

enum class HonestyRule {
    CAPABILITY_GROUNDING,
    MODALITY_GROUNDING,
    CONTINUITY_GROUNDING,
    ACTION_GROUNDING
}

/** All four rules are mandatory; a partial policy is an invalid identity. */
class HonestyPolicy(rules: Set<HonestyRule>) {
    val rules: Set<HonestyRule> = Collections.unmodifiableSet(rules.toSet())

    init { require(this.rules == HonestyRule.entries.toSet()) }

    override fun equals(other: Any?): Boolean = other is HonestyPolicy && rules == other.rules
    override fun hashCode(): Int = rules.hashCode()
}

/** The identity authority's immutable semantic snapshot; this is not a storage record. */
data class IdentityContinuitySnapshot(
    val self: SelfIdentityAnchor,
    val primaryRelationship: RelationshipAnchor,
    val honesty: HonestyPolicy,
    val continuityFormatVersion: ContinuityFormatVersion,
    val personalityCapsuleRef: PersonalityCapsuleRef
) {
    init { require(primaryRelationship.category == RelationshipCategory.PRIMARY_BOND) }
}

fun interface IdentityContinuityReader {
    fun read(): FoundationResult<IdentityContinuitySnapshot>
}
