package com.mavyy.localyuki.foundation.authority

import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import com.mavyy.localyuki.foundation.subsystem.SubsystemRole
import java.util.Collections

/** Immutable architectural declarations; this object contains no instances or available services. */
object CoreAuthorityGraph {
    private val roles = Collections.unmodifiableMap(mapOf(
        CoreSubsystemId.IDENTITY_CONTINUITY_CORE to SubsystemRole.AUTHORITY,
        CoreSubsystemId.PERSONALITY_CAPSULE to SubsystemRole.AUTHORITY,
        CoreSubsystemId.YUKI_STATE to SubsystemRole.AUTHORITY,
        CoreSubsystemId.TEMPORAL_GROUNDING to SubsystemRole.AUTHORITY,
        CoreSubsystemId.MEMORY_ENGINE to SubsystemRole.AUTHORITY,
        CoreSubsystemId.SEMANTIC_RECALL to SubsystemRole.ADVISORY,
        CoreSubsystemId.AFFECTIVE_SYSTEM to SubsystemRole.AUTHORITY,
        CoreSubsystemId.COGNITIVE_WORKSPACE to SubsystemRole.COORDINATOR,
        CoreSubsystemId.REALITY_VERIFICATION to SubsystemRole.ADVISORY,
        CoreSubsystemId.EMBODIED_CAPABILITY_REGISTRY to SubsystemRole.AUTHORITY,
        CoreSubsystemId.EXECUTIVE_ACTION_CONTROL to SubsystemRole.CONTROL,
        CoreSubsystemId.RESOURCE_GOVERNOR to SubsystemRole.AUTHORITY,
        CoreSubsystemId.SCHEDULER_PROACTIVE to SubsystemRole.AUTHORITY,
        CoreSubsystemId.SLEEP_RECOVERY to SubsystemRole.CONTROL,
        CoreSubsystemId.PERCEPTION to SubsystemRole.GROUNDING,
        CoreSubsystemId.SYSTEM_ONE_ENGINE to SubsystemRole.ADVISORY,
        CoreSubsystemId.SYSTEM_TWO_ENGINE to SubsystemRole.ADVISORY,
        CoreSubsystemId.LANGUAGE_EXPRESSION_ENGINE to SubsystemRole.ADVISORY,
        CoreSubsystemId.SPECIALIST_ENGINE to SubsystemRole.ADVISORY,
        CoreSubsystemId.VAULT_EXPORT to SubsystemRole.AUTHORITY
    ))

    val declarations: List<AuthorityDeclaration> = Collections.unmodifiableList(listOf(
        AuthorityDeclaration(AuthorityDomain.IDENTITY_CONTINUITY, app(CoreSubsystemId.IDENTITY_CONTINUITY_CORE)),
        AuthorityDeclaration(AuthorityDomain.PERSONALITY_CAPSULE, app(CoreSubsystemId.PERSONALITY_CAPSULE)),
        AuthorityDeclaration(AuthorityDomain.YUKI_STATE, app(CoreSubsystemId.YUKI_STATE)),
        AuthorityDeclaration(AuthorityDomain.TEMPORAL_GROUNDING, app(CoreSubsystemId.TEMPORAL_GROUNDING)),
        AuthorityDeclaration(AuthorityDomain.MEMORY_HISTORY, app(CoreSubsystemId.MEMORY_ENGINE)),
        AuthorityDeclaration(AuthorityDomain.EVIDENCE_PROVENANCE, app(CoreSubsystemId.MEMORY_ENGINE)),
        AuthorityDeclaration(AuthorityDomain.AFFECTIVE_STATE, app(CoreSubsystemId.AFFECTIVE_SYSTEM)),
        AuthorityDeclaration(AuthorityDomain.CAPABILITY_AVAILABILITY, app(CoreSubsystemId.EMBODIED_CAPABILITY_REGISTRY)),
        AuthorityDeclaration(AuthorityDomain.EXECUTIVE_ACTION_CONTROL, app(CoreSubsystemId.EXECUTIVE_ACTION_CONTROL)),
        AuthorityDeclaration(AuthorityDomain.RESOURCE_LIMITS, app(CoreSubsystemId.RESOURCE_GOVERNOR)),
        AuthorityDeclaration(AuthorityDomain.SCHEDULES, app(CoreSubsystemId.SCHEDULER_PROACTIVE)),
        AuthorityDeclaration(AuthorityDomain.VAULT_CONTINUITY, app(CoreSubsystemId.VAULT_EXPORT)),
        AuthorityDeclaration(AuthorityDomain.PLATFORM_PERMISSION_STATE, AuthorityOwner.ExternalPlatform)
    ))

    private val owners = declarations.associate { it.domain to it.owner }

    init {
        check(roles.keys == CoreSubsystemId.entries.toSet())
        check(declarations.size == AuthorityDomain.entries.size && owners.keys == AuthorityDomain.entries.toSet())
        check(declarations.all {
            val owner = it.owner
            owner !is AuthorityOwner.AppSubsystem || roleOf(owner.id) in setOf(SubsystemRole.AUTHORITY, SubsystemRole.CONTROL)
        })
    }

    fun roleOf(id: CoreSubsystemId): SubsystemRole = roles.getValue(id)
    fun ownerOf(domain: AuthorityDomain): AuthorityOwner = owners.getValue(domain)

    /** A caller may be issued a domain mutator only when this declaration names it as owner. */
    fun mayMutate(id: CoreSubsystemId, domain: AuthorityDomain): Boolean =
        ownerOf(domain) == AuthorityOwner.AppSubsystem(id)

    private fun app(id: CoreSubsystemId) = AuthorityOwner.AppSubsystem(id)
}
