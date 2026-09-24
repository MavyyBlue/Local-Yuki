package com.mavyy.localyuki.foundation.contracts

import com.mavyy.localyuki.foundation.provenance.EvidenceRef
import com.mavyy.localyuki.foundation.subsystem.CoreSubsystemId
import java.util.Collections

/** Data for an authority to consider; has no executor, authority handle, or apply behavior. */
class Proposal<out T : Any>(
    val value: T,
    val proposer: CoreSubsystemId,
    evidence: List<EvidenceRef> = emptyList()
) {
    val evidence: List<EvidenceRef> = Collections.unmodifiableList(evidence.toList())
}
