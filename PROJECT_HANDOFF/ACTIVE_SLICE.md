# Local Yuki — Active Slice

**Slice:** Phase 5 — Living memory and semantic-recall socket  
**Architecture authority:** Mavyy + Yuki  
**Implementation owner:** Akari after bounded Yuki authorization  
**Independent QA authority:** Mio  
**Certified Phase 4 implementation:** `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`  
**Certified Android CI:** Run #22 (`36098219804`) — PASS  
**Phase 4 Mio verdict:** PASS  
**Phase 4 phone acceptance:** PASS  
**Physical continuity schema:** `3`  
**Continuity semantic format:** `1`  
**Yuki State semantic version:** `1`  
**Memory semantic version:** `1`  
**Model Freeze Gate:** CLOSED  
**Implementation authorization:** NOT YET AUTHORIZED — await a bounded Phase 5 Yuki architecture handoff after a fresh live-repository reset

## Purpose

Design the next bounded foundation slice for living-memory behavior and the semantic-recall socket while preserving Phase 4's deterministic memory/evidence authority.

Phase 5 may introduce deterministic lifecycle/scaffolding that prepares memory for later semantic access, but learned retrieval must remain advisory and must never become the source of autobiographical truth.

## Certified starting point

Phase 4 now provides:

- physical SQLite schema version `3`,
- explicit v2 → v3 migration `2026-09-24-memory-authority-v1`,
- app-owned `MemoryFormatVersion(1)`,
- immutable conversation threads,
- immutable raw evidence with provenance and integrity digests,
- factual and autobiographical durable memory,
- immutable revision chains,
- explicit current/historical status,
- owner Update and Restore semantics,
- immutable audit history,
- derived rolling-checkpoint contracts,
- bounded deterministic exact/indexed reads,
- corrected checkpoint keyset pagination with matching `(thread_id, checkpoint_id)` index,
- restart reconstruction and corruption isolation,
- trusted timestamp dependency on Temporal Grounding for new writes,
- read-only cognition-facing memory contracts,
- non-executable proposal boundaries,
- fixed signing lineage,
- no Android permission expansion,
- no neural runtime/model.

Phase 5 must build additively on this baseline.

## Required architectural questions for the next Yuki handoff

Before implementation, Yuki must freshly inspect the live repository and define:

- what “living memory” means without allowing automatic truth rewriting,
- what deterministic lifecycle metadata, if any, is authoritative,
- how current versus historical memories participate,
- how recency/importance/use signals are represented without becoming fabricated truth,
- what the semantic-recall socket may read and return,
- how advisory retrieval results retain provenance back to Phase 4 evidence/revisions,
- how unavailable learned retrieval degrades cleanly to deterministic exact reads,
- how memory consolidation/summary proposals remain proposals until accepted by authority,
- persistence and migration requirements,
- resource/lifecycle constraints,
- corruption/conflict behavior,
- restart reconstruction,
- tests and phone acceptance.

## Hard boundaries

Phase 5 must not rewrite/delete Phase 4 raw evidence as a side effect of “living” memory, collapse historical revision chains, let semantic relevance become factual authority, let a model directly execute owner Update/Restore, let checkpoints masquerade as raw evidence, expose SQLite/storage/substrate internals to ordinary cognition, or weaken fixed signing, migration discipline, capability honesty, or prior authorities.

## Neural/model boundary

The Model Freeze Gate remains CLOSED.

No candidate neural model, embedding model, reranker, tokenizer, LLM runtime, downloadable model asset, or model-specific prompt format is authorized by this active-slice transition.

If Phase 5 requires a semantic-recall interface, the interface must be model-neutral and testable with deterministic/mocked implementations.

## Exit gate

Phase 5 exits only after:

1. Yuki performs a fresh live-repository reset and issues the bounded Phase 5 architecture handoff.
2. Akari implements only the authorized Phase 5 slice.
3. Exact-candidate Android CI is green.
4. Mio independently issues PASS.
5. Mavyy completes required phone acceptance if the slice exposes device-visible/lifecycle behavior.
6. Yuki performs Phase 5 documentation sync.

The Model Freeze Gate remains CLOSED.
