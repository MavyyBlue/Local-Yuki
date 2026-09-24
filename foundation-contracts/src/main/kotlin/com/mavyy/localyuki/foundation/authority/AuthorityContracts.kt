package com.mavyy.localyuki.foundation.authority

import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId

enum class AuthorityDomain {
    IDENTITY_CONTINUITY, PERSONALITY_CAPSULE, YUKI_STATE, TEMPORAL_GROUNDING,
    MEMORY_HISTORY, EVIDENCE_PROVENANCE, AFFECTIVE_STATE, CAPABILITY_AVAILABILITY,
    EXECUTIVE_ACTION_CONTROL, RESOURCE_LIMITS, SCHEDULES, VAULT_CONTINUITY,
    PLATFORM_PERMISSION_STATE
}

/** READ and PROPOSE are not mutation rights. A grant here is a declaration, not a live port. */
enum class AccessRight { READ, PROPOSE, MUTATE }

sealed interface AuthorityOwner {
    data class AppSubsystem(val id: CoreSubsystemId) : AuthorityOwner
    data object ExternalPlatform : AuthorityOwner
}

data class AuthorityDeclaration(val domain: AuthorityDomain, val owner: AuthorityOwner)
