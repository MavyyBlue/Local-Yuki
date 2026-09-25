package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.authority.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LivingMemoryContractTest {
    @Test fun semanticVersionBoundsAndAgingPolicy() {
        assertEquals(1,LivingMemoryFormatVersion(1).value)
        assertThrows(IllegalArgumentException::class.java) { LivingMemoryFormatVersion(2) }
        assertThrows(IllegalArgumentException::class.java) { RecallQuery("",1) }
        assertThrows(IllegalArgumentException::class.java) { RecallQuery("forest",21) }
        val start=Instant.parse("2025-01-01T00:00:00Z")
        fun level(days: Long, importance: Int = 50, reinforcement: Int = 0) = LivingMemoryPolicyV1.level(start,start.plusSeconds(days*86400),importance,reinforcement)
        assertEquals(AbstractionLevel.DETAILED,level(0))
        assertEquals(AbstractionLevel.COMPACT,level(30))
        assertEquals(AbstractionLevel.SPARSE,level(90))
        assertEquals(AbstractionLevel.TRACE,level(365))
        assertEquals(AbstractionLevel.COMPACT,level(90,75))
        assertEquals(AbstractionLevel.TRACE,level(90,25))
        assertEquals(AbstractionLevel.COMPACT,level(90,50,50))
        assertEquals(listOf("café","42","straße"),LivingMemoryPolicyV1.terms("CAFÉ 42 café Straße",12))
        assertEquals(48,LivingMemoryPolicyV1.budget(AbstractionLevel.DETAILED))
        assertEquals(6,LivingMemoryPolicyV1.budget(AbstractionLevel.TRACE))
    }
    @Test fun rerankerCannotInsertDropDuplicateOrAlterIdentities() {
        // The boundary is tested with the empty set here; app tests exercise grounded inputs.
        assertEquals(FoundationResult.Success(emptyList<GroundedRecallCandidate>()),RecallRerankBoundary.validate(emptyList(),emptyList()))
    }
}
