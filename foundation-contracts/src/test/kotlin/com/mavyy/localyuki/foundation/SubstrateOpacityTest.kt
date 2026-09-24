package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.cognition.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.identity.*
import com.mavyy.localyuki.foundation.personality.*
import com.mavyy.localyuki.foundation.ports.AuthorityMutator
import org.junit.Assert.*
import org.junit.Test

class SubstrateOpacityTest {
    @Test fun cognitionGetsBoundedSemanticProjectionWithoutPrivilegedReferences() {
        val result = CanonicalCognitiveIdentityReader.read() as FoundationResult.Success
        val view = result.value
        assertEquals("yuki-aster", view.self.stableId)
        assertEquals("mavyy", view.primaryRelationship.stableId)
        assertEquals(HonestyRule.entries.toSet(), view.honesty.rules)
        assertEquals(CanonicalPersonalityCapsule.snapshot(), view.personality)
        assertEquals(1, view.continuityFormatVersion.value)
        assertFalse(AuthorityMutator::class.java.isAssignableFrom(CognitiveIdentityReader::class.java))
        val allowed = setOf("self", "primaryRelationship", "honesty", "continuityFormatVersion", "personality")
        assertEquals(allowed, CognitiveIdentityView::class.java.declaredFields.filterNot { it.isSynthetic }.map { it.name }.toSet())
        assertTrue(CognitiveIdentityView::class.java.declaredFields.all { field ->
            val name = field.type.name
            !name.startsWith("android.") && !name.contains("runtime", true) &&
                !name.contains("storage", true) && !name.contains("AuthorityMutator")
        })
    }

    @Test fun conflictingVersionAndContentFailClosed() {
        val seed = CanonicalIdentitySeed.snapshot()
        val capsule = CanonicalPersonalityCapsule.snapshot()
        val conflict = FoundationResult.Failure(FailureCategory.CONFLICT)
        assertEquals(conflict, CanonicalCognitiveIdentityReader.project(seed.copy(continuityFormatVersion = ContinuityFormatVersion(2)), capsule))
        assertEquals(conflict, CanonicalCognitiveIdentityReader.project(seed.copy(personalityCapsuleRef = PersonalityCapsuleRef(capsule.id, PersonalityCapsuleVersion(2))), capsule))
        assertEquals(conflict, CanonicalCognitiveIdentityReader.project(seed, PersonalityCapsule(capsule.id, PersonalityCapsuleVersion(2), capsule.honesty, capsule.facets)))
        assertEquals(conflict, CanonicalCognitiveIdentityReader.project(seed.copy(self = SelfIdentityAnchor("other", "Other")), capsule))
    }
}
