package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.authority.*
import com.mavyy.localyuki.foundation.subsystem.*
import org.junit.Assert.*
import org.junit.Test

class AuthorityGraphTest {
    @Test fun everyDomainHasExactlyOneOwnerAndRolesCoverCatalog() {
        assertEquals(CoreSubsystemId.entries.toSet(), CoreSubsystemId.entries.filter { CoreAuthorityGraph.roleOf(it) in SubsystemRole.entries }.toSet())
        assertEquals(AuthorityDomain.entries.toSet(), CoreAuthorityGraph.declarations.map { it.domain }.toSet())
        assertEquals(AuthorityDomain.entries.size, CoreAuthorityGraph.declarations.size)
        for (declaration in CoreAuthorityGraph.declarations) {
            assertEquals(declaration.owner, CoreAuthorityGraph.ownerOf(declaration.domain))
            if (declaration.owner is AuthorityOwner.AppSubsystem) {
                assertTrue(CoreAuthorityGraph.mayMutate(declaration.owner.id, declaration.domain))
            }
        }
        val expected = mapOf(
            AuthorityDomain.IDENTITY_CONTINUITY to CoreSubsystemId.IDENTITY_CONTINUITY_CORE,
            AuthorityDomain.PERSONALITY_CAPSULE to CoreSubsystemId.PERSONALITY_CAPSULE,
            AuthorityDomain.YUKI_STATE to CoreSubsystemId.YUKI_STATE,
            AuthorityDomain.TEMPORAL_GROUNDING to CoreSubsystemId.TEMPORAL_GROUNDING,
            AuthorityDomain.MEMORY_HISTORY to CoreSubsystemId.MEMORY_ENGINE,
            AuthorityDomain.EVIDENCE_PROVENANCE to CoreSubsystemId.MEMORY_ENGINE,
            AuthorityDomain.AFFECTIVE_STATE to CoreSubsystemId.AFFECTIVE_SYSTEM,
            AuthorityDomain.CAPABILITY_AVAILABILITY to CoreSubsystemId.EMBODIED_CAPABILITY_REGISTRY,
            AuthorityDomain.EXECUTIVE_ACTION_CONTROL to CoreSubsystemId.EXECUTIVE_ACTION_CONTROL,
            AuthorityDomain.RESOURCE_LIMITS to CoreSubsystemId.RESOURCE_GOVERNOR,
            AuthorityDomain.SCHEDULES to CoreSubsystemId.SCHEDULER_PROACTIVE,
            AuthorityDomain.VAULT_CONTINUITY to CoreSubsystemId.VAULT_EXPORT
        )
        assertEquals(AuthorityDomain.entries.toSet() - AuthorityDomain.PLATFORM_PERMISSION_STATE, expected.keys)
        expected.forEach { (domain, id) -> assertEquals(AuthorityOwner.AppSubsystem(id), CoreAuthorityGraph.ownerOf(domain)) }
    }

    @Test fun platformPermissionIsExternalAndOtherOwnersCannotMutateAcrossDomains() {
        assertEquals(AuthorityOwner.ExternalPlatform, CoreAuthorityGraph.ownerOf(AuthorityDomain.PLATFORM_PERMISSION_STATE))
        for (domain in AuthorityDomain.entries) for (subsystem in CoreSubsystemId.entries) {
            assertEquals(CoreAuthorityGraph.ownerOf(domain) == AuthorityOwner.AppSubsystem(subsystem), CoreAuthorityGraph.mayMutate(subsystem, domain))
        }
    }

    @Test fun advisorsCoordinatorsAndGroundingOwnNoDomain() {
        val advisors = setOf(CoreSubsystemId.SYSTEM_ONE_ENGINE, CoreSubsystemId.SYSTEM_TWO_ENGINE,
            CoreSubsystemId.LANGUAGE_EXPRESSION_ENGINE, CoreSubsystemId.SPECIALIST_ENGINE,
            CoreSubsystemId.SEMANTIC_RECALL, CoreSubsystemId.REALITY_VERIFICATION)
        assertTrue(advisors.all { CoreAuthorityGraph.roleOf(it) == SubsystemRole.ADVISORY })
        assertEquals(SubsystemRole.CONTROL, CoreAuthorityGraph.roleOf(CoreSubsystemId.SLEEP_RECOVERY))
        for (id in CoreSubsystemId.entries.filter { CoreAuthorityGraph.roleOf(it) in setOf(SubsystemRole.ADVISORY, SubsystemRole.COORDINATOR, SubsystemRole.GROUNDING) }) {
            assertTrue(AuthorityDomain.entries.none { CoreAuthorityGraph.mayMutate(id, it) })
        }
        assertEquals(setOf(AuthorityDomain.EXECUTIVE_ACTION_CONTROL), AuthorityDomain.entries.filter {
            CoreAuthorityGraph.mayMutate(CoreSubsystemId.EXECUTIVE_ACTION_CONTROL, it)
        }.toSet())
    }

    @Test fun catalogCannotBeEditedAsServiceRegistry() {
        try {
            (CoreAuthorityGraph.declarations as MutableList<AuthorityDeclaration>).clear()
            fail("Authority declarations must be immutable")
        } catch (_: UnsupportedOperationException) { }
    }
}
