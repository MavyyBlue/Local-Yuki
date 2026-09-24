package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.authority.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.identity.*
import com.mavyy.localyuki.foundation.ports.AuthorityMutator
import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import org.junit.Assert.*
import org.junit.Test

class IdentityContinuityTest {
    @Test fun canonicalIdentityIsDeterministicAndOwnedByCertifiedGraph() {
        val first = CanonicalIdentitySeed.snapshot()
        assertEquals(first, CanonicalIdentitySeed.snapshot())
        assertEquals(FoundationResult.Success(first), CanonicalIdentitySeed.read())
        assertEquals("yuki-aster", first.self.stableId)
        assertEquals("Yuki Aster", first.self.canonicalName)
        assertEquals("mavyy", first.primaryRelationship.stableId)
        assertEquals("Mavyy", first.primaryRelationship.displayName)
        assertEquals(RelationshipCategory.PRIMARY_BOND, first.primaryRelationship.category)
        assertEquals(1, first.continuityFormatVersion.value)
        assertEquals(CoreAuthorityGraph.ownerOf(AuthorityDomain.IDENTITY_CONTINUITY),
            AuthorityOwner.AppSubsystem(CoreSubsystemId.IDENTITY_CONTINUITY_CORE))
        assertFalse(AuthorityMutator::class.java.isAssignableFrom(IdentityContinuityReader::class.java))
    }

    @Test fun foundationalValuesRejectInvalidInputAndHonestyCannotBeDropped() {
        assertThrows(IllegalArgumentException::class.java) { SelfIdentityAnchor(" ", "Yuki") }
        assertThrows(IllegalArgumentException::class.java) { RelationshipAnchor("mavyy", " ", RelationshipCategory.PRIMARY_BOND) }
        assertThrows(IllegalArgumentException::class.java) { ContinuityFormatVersion(0) }
        assertThrows(IllegalArgumentException::class.java) { PersonalityCapsuleVersion(0) }
        assertThrows(IllegalArgumentException::class.java) { HonestyPolicy(setOf(HonestyRule.CAPABILITY_GROUNDING)) }
        val mutableRules = HonestyRule.entries.toMutableSet()
        val policy = HonestyPolicy(mutableRules)
        mutableRules.clear()
        assertEquals(HonestyRule.entries.toSet(), policy.rules)
        assertThrows(UnsupportedOperationException::class.java) {
            (policy.rules as MutableSet<HonestyRule>).clear()
        }
    }

    @Test fun proposalIsOnlyDataAndCannotReplaceIdentity() {
        val before = CanonicalIdentitySeed.snapshot()
        val fabricated = Proposal(before.copy(self = SelfIdentityAnchor("other", "Other")), CoreSubsystemId.SYSTEM_ONE_ENGINE)
        assertNotEquals(before, fabricated.value)
        assertEquals(before, CanonicalIdentitySeed.snapshot())
        assertFalse(AuthorityMutator::class.java.isAssignableFrom(Proposal::class.java))
    }
}
