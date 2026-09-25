# Local Yuki — Active Slice

**Slice:** Phase 4 — Memory authority and provenance completion  
**Architecture authority:** Mavyy + Yuki  
**Implementation owner:** Akari after bounded Yuki authorization  
**Independent QA authority:** Mio  
**Certified Phase 3 implementation:** `9d3cd57fec9e3c128b449152167ccdadca9f0e9d`  
**Certified Android CI:** Run #16 (`36087120725`) — PASS  
**Phase 3 Mio verdict:** PASS  
**Phase 3 phone acceptance:** PASS  
**Physical continuity schema:** `2`  
**Yuki State semantic version:** `1`  
**Model Freeze Gate:** CLOSED  
**Implementation authorization:** NOT YET AUTHORIZED — await a bounded Phase 4 Yuki architecture handoff after a fresh live-repository reset

## Purpose

Establish Local Yuki's deterministic autobiographical/factual memory authority and evidence provenance independently of inference.

Phase 4 is responsible for completing the app-owned truth layer that later semantic recall may query but never replace.

Per the implementation strategy, Phase 4 is expected to address:

- raw conversation/evidence records,
- source/provenance relationships,
- current versus historical memory state,
- supersession/replacement chains,
- audit history,
- owner Update/Restore behavior,
- thread ownership,
- rolling summary/checkpoint contracts,
- durable exact/indexed memory queries.

## Certified starting point

Phase 3 now provides:

- durable app-owned identity/personality continuity,
- physical SQLite schema version `2`,
- bounded Yuki State v1,
- current project/focus,
- pending intentions,
- unresolved topics,
- trusted interaction markers,
- optimistic revision conflicts,
- explicit expiry behavior,
- deterministic device clock/timezone grounding,
- DST-safe relative calendar windows,
- process-death restoration,
- fixed signing lineage,
- no Android permission expansion,
- no neural runtime/model.

Current project is non-expiring in Phase 3 and remains until explicitly changed/cleared. Focus and individual intentions/topics may expire independently.

Phase 4 must build additively on this baseline.

## Required architectural direction

The next Yuki architecture handoff must define:

- exact Memory Engine ownership,
- evidence/provenance DTOs,
- raw-event versus derived-memory boundaries,
- thread/conversation ownership,
- current/historical/superseded state,
- immutable evidence versus mutable interpretation,
- persistence/schema migration,
- exact/indexed deterministic retrieval,
- Update/Restore semantics,
- conflict/failure behavior,
- restart reconstruction,
- cognition-facing read/proposal boundaries,
- required tests and phone acceptance.

Factual memory and provenance remain app-owned authority.

Semantic recall is later and advisory.

A neural model is not needed and is not authorized.

## Non-goals until the bounded handoff says otherwise

This doc sync does not authorize:

- semantic embeddings,
- neural recall,
- affect/reward,
- capability execution,
- Resource Governor,
- scheduler/proactive cognition,
- neural models,
- tokenizer/prompt/runtime integration,
- Phase 5 living-memory behavior.

## Exit gate

Phase 4 exits only after:

1. Yuki performs a fresh live-repository reset and issues the bounded Phase 4 architecture handoff.
2. Akari implements only the authorized Phase 4 slice.
3. exact-candidate CI is green.
4. Mio independently issues PASS.
5. Mavyy completes required phone acceptance if the slice exposes device-visible/lifecycle behavior.
6. Yuki performs Phase 4 documentation sync.

The Model Freeze Gate remains CLOSED.
