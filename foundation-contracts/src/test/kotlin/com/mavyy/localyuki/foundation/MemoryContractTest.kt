package com.mavyy.localyuki.foundation

import com.mavyy.localyuki.foundation.authority.*
import com.mavyy.localyuki.foundation.contracts.Proposal
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import org.junit.Assert.*
import org.junit.Test

class MemoryContractTest {
    @Test fun semanticVersionBoundsAndKindsAreIndependentOfStorage() {
        assertEquals(1,MemoryFormatVersion(1).value)
        assertEquals(setOf(MemoryKind.FACTUAL,MemoryKind.AUTOBIOGRAPHICAL),MemoryKind.entries.toSet())
        assertFalse(MemoryBounds.id(" bad"))
        assertFalse(MemoryBounds.text("x".repeat(MemoryBounds.MAX_CONTENT_BYTES+1),MemoryBounds.MAX_CONTENT_BYTES))
        assertFalse(MemoryBounds.page(0,0)); assertFalse(MemoryBounds.page(101,0))
        assertTrue(MemoryBounds.evidence(listOf(EvidenceRef("one",EvidenceSourceKind.USER_INPUT))))
        assertFalse(MemoryBounds.evidence(emptyList()))
    }
    @Test fun advisoryProposalCannotExecuteOwnerRestore() {
        val ref=EvidenceRef("owner-word",EvidenceSourceKind.USER_INPUT)
        val advisory=memoryProposal(CreateMemoryProposal("m",MemoryKind.FACTUAL,"text",listOf(ref)),
            CoreSubsystemId.SEMANTIC_RECALL,listOf(ref))
        assertTrue(advisory is Proposal<*>)
        assertEquals(ref,advisory.evidence.single())
        assertFalse((advisory.value as Any) is OwnerRestore)
        assertFalse(MemoryReader::class.java.methods.any { it.name in setOf("create", "update", "ownerRestore", "appendEvidence") })
        assertEquals(AuthorityOwner.AppSubsystem(CoreSubsystemId.MEMORY_ENGINE),CoreAuthorityGraph.ownerOf(AuthorityDomain.MEMORY_HISTORY))
        assertEquals(AuthorityOwner.AppSubsystem(CoreSubsystemId.MEMORY_ENGINE),CoreAuthorityGraph.ownerOf(AuthorityDomain.EVIDENCE_PROVENANCE))
        assertFalse(CoreAuthorityGraph.mayMutate(CoreSubsystemId.SEMANTIC_RECALL,AuthorityDomain.MEMORY_HISTORY))
    }
}
