package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.authority.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.ports.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import org.junit.Assert.*
import org.junit.Test

class ProposalBoundaryTest {
    private data class Command(val domain: AuthorityDomain, val value: Int)

    @Test fun advisorHasNoMutationPortAndOnlyExplicitOwnedMutationChangesState() {
        assertFalse(AuthorityMutator::class.java.isAssignableFrom(AdvisoryPort::class.java))
        assertFalse(AuthorityMutator::class.java.isAssignableFrom(Proposal::class.java))
        val evidence = EvidenceRef("opaque-7", EvidenceSourceKind.USER_INPUT)
        var state = 0
        val advisor: AdvisoryPort<Int, Command> = AdvisoryPort { input ->
            FoundationResult.Success(Proposal(Command(AuthorityDomain.MEMORY_HISTORY, input), CoreSubsystemId.SYSTEM_ONE_ENGINE, listOf(evidence)))
        }
        val authority: AuthorityMutator<Command, Int> = AuthorityMutator { command ->
            if (!CoreAuthorityGraph.mayMutate(CoreSubsystemId.MEMORY_ENGINE, command.domain))
                FoundationResult.Failure(FailureCategory.REJECTED)
            else {
                state = command.value
                FoundationResult.Success(state)
            }
        }
        val proposed = advisor.propose(42) as FoundationResult.Success
        assertEquals(0, state)
        assertEquals(evidence, proposed.value.evidence.single())
        assertEquals(CoreSubsystemId.SYSTEM_ONE_ENGINE, proposed.value.proposer)
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED), authority.mutate(Command(AuthorityDomain.YUKI_STATE, 9)))
        assertEquals(0, state)
        assertEquals(FoundationResult.Success(42), authority.mutate(proposed.value.value))
        assertEquals(42, state)
        try {
            (proposed.value.evidence as MutableList<EvidenceRef>).clear()
            fail("Proposal evidence must be immutable")
        } catch (_: UnsupportedOperationException) { }
    }
}
