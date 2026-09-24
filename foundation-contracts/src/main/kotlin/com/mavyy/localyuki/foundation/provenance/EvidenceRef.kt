package com.mavyy.localyuki.foundation.provenance

enum class EvidenceSourceKind {
    USER_INPUT, YUKI_OUTPUT, TOOL_RESULT, PERCEPTION_RESULT, SYSTEM_EVENT, AUTHORITY_RECORD
}

/** Opaque identifier only. Resolution and storage belong to a later phase. */
data class EvidenceRef(val opaqueId: String, val sourceKind: EvidenceSourceKind) {
    init { require(opaqueId.isNotBlank()) { "Evidence identifier must not be blank" } }
}
