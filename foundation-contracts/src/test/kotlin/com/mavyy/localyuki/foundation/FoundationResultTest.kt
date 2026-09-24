package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.provenance.*
import org.junit.Assert.*
import org.junit.Test

class FoundationResultTest {
    @Test fun outcomeVariantsAreExplicitAndEvidenceNeedsNoResolver() {
        val evidence = EvidenceRef("record-alpha", EvidenceSourceKind.AUTHORITY_RECORD)
        val outcomes: List<FoundationResult<EvidenceRef>> = listOf(
            FoundationResult.Success(evidence),
            FoundationResult.Unavailable(UnavailableReason.NOT_IMPLEMENTED),
            FoundationResult.Failure(FailureCategory.REJECTED)
        )
        assertEquals(3, outcomes.map { it::class }.toSet().size)
        assertEquals(evidence, (outcomes[0] as FoundationResult.Success).value)
        assertEquals(UnavailableReason.NOT_IMPLEMENTED, (outcomes[1] as FoundationResult.Unavailable).reason)
        assertEquals(FailureCategory.REJECTED, (outcomes[2] as FoundationResult.Failure).category)
        assertNotEquals(outcomes[0], outcomes[1])
    }
}
