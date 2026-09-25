# Local Yuki — Active Slice

**Slice:** Phase 3 — Yuki State and Temporal Grounding  
**Architecture authority:** Mavyy + Yuki  
**Implementation owner:** Akari after bounded Yuki authorization  
**Independent QA authority:** Mio  
**Certified Phase 2B semantic implementation:** `5d0699e3474b71ec245fc3b5365044395c855875`  
**Accepted signing/build follow-up:** `c7e24687c4609736d6d34550a830c02b85e92fbd`  
**Latest accepted Android CI:** Run #13 (`36076298126`) — PASS  
**Phase 2B Mio verdict:** PASS  
**Phase 2B phone acceptance:** PASS  
**Model Freeze Gate:** CLOSED  
**Implementation authorization:** NOT YET AUTHORIZED — await a bounded Phase 3 Yuki architecture handoff after a fresh live-repository reset

## Purpose

Establish bounded current continuity and reliable deterministic time orientation without introducing a language model or handing temporal/state authority to cognition.

Per the implementation strategy, Phase 3 is responsible for:

- device clock/timezone grounding,
- relative-date resolution,
- interaction timestamps,
- bounded Yuki State,
- pending intentions,
- unresolved topics,
- current project/focus,
- expiry rules,
- state persistence and restore.

## Certified starting point

Phase 2B now provides durable app-owned identity and Personality Capsule persistence, physical continuity schema version `1`, explicit future migration infrastructure, deterministic first-install bootstrap, fail-closed malformed-store behavior, validated process-death/restart restoration, model-independent reconstruction, bounded cognition-facing identity projection, fixed debug/update APK signing lineage, no Android permission expansion, and no neural runtime/model.

Phase 3 must build additively on that baseline.

## Required architectural direction

The next Yuki architecture handoff must define:

- exact Yuki State ownership and bounded DTOs,
- deterministic time source/timezone boundaries,
- persistence ownership and schema evolution,
- expiry/conflict behavior,
- restart reconstruction,
- cognition-facing read/proposal boundaries,
- phone-test requirements,
- tests for `today`, `yesterday`, time windows, expiry, restart recovery, and state conflicts.

Deterministic time and persisted state remain app-owned authority.

A neural model is not needed and is not authorized.

## Non-goals until the bounded handoff says otherwise

This doc sync does not authorize implementation of Memory Engine, semantic recall, affect, capability execution, Resource Governor, scheduler/proactive cognition, neural models, or prompt/tokenizer/runtime integration.

## Exit gate

Phase 3 exits only after:

1. Yuki performs a fresh repository reset and issues the bounded Phase 3 architecture handoff.
2. Akari implements only the authorized Phase 3 slice.
3. exact-candidate CI is green.
4. Mio independently issues PASS.
5. Mavyy completes any required phone acceptance.
6. Yuki performs Phase 3 documentation sync.

The Model Freeze Gate remains CLOSED.
