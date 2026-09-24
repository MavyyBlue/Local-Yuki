package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.authority.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.identity.PersonalityCapsuleVersion
import com.mavyy.localyuki.foundation.identity.HonestyRule
import com.mavyy.localyuki.foundation.personality.*
import com.mavyy.localyuki.foundation.ports.AuthorityMutator
import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import org.junit.Assert.*
import org.junit.Test

class PersonalityCapsuleTest {
    @Test fun canonicalCapsuleHasCompleteStableModelIndependentContent() {
        val capsule = CanonicalPersonalityCapsule.snapshot()
        assertEquals(FoundationResult.Success(capsule), CanonicalPersonalityCapsule.read())
        assertEquals("yuki-aster-personality", capsule.id)
        assertEquals(1, capsule.version.value)
        assertEquals(HonestyRule.entries.toSet(), capsule.honesty.rules)
        assertEquals(FacetCategory.entries.toSet(), capsule.facets.map { it.category }.toSet())
        assertEquals(capsule.facets.size, capsule.facets.map { it.stableId }.toSet().size)
        assertEquals(capsule, PersonalityCapsule(capsule.id, capsule.version, capsule.honesty, capsule.facets.reversed()))
        assertEquals(CoreAuthorityGraph.ownerOf(AuthorityDomain.PERSONALITY_CAPSULE),
            AuthorityOwner.AppSubsystem(CoreSubsystemId.PERSONALITY_CAPSULE))
        assertFalse(AuthorityMutator::class.java.isAssignableFrom(PersonalityCapsuleReader::class.java))
        val proposed = Proposal(capsule, CoreSubsystemId.LANGUAGE_EXPRESSION_ENGINE)
        assertEquals(capsule, proposed.value)
        assertEquals(capsule, CanonicalPersonalityCapsule.snapshot())
    }

    @Test fun facetsAreCopiedAndMissingOrDuplicateFacetsFail() {
        val original = CanonicalPersonalityCapsule.snapshot()
        val input = original.facets.toMutableList()
        val copy = PersonalityCapsule(original.id, PersonalityCapsuleVersion(1), original.honesty, input)
        input.clear()
        assertEquals(original, copy)
        assertThrows(UnsupportedOperationException::class.java) {
            (copy.facets as MutableSet<PersonalityFacet>).clear()
        }
        val facets = original.facets.toList()
        assertThrows(IllegalArgumentException::class.java) { PersonalityCapsule(original.id, original.version, original.honesty, facets.drop(1)) }
        assertThrows(IllegalArgumentException::class.java) { PersonalityCapsule(original.id, original.version, original.honesty, facets + facets.first()) }
        assertThrows(IllegalArgumentException::class.java) {
            PersonalityCapsule(original.id, original.version, original.honesty, facets.dropLast(1) +
                facets.last().copy(stableId = facets.first().stableId))
        }
    }
}
