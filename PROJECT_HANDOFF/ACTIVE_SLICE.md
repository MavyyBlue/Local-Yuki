# Local Yuki — Active Slice

**Slice:** Phase 0 — Live-repository context reset and baseline lock  
**Owner:** Akari  
**Architecture authority:** Mavyy + Yuki  
**QA authority:** Mio  
**Model Freeze Gate:** CLOSED  
**Implementation authorization:** Reconnaissance only until the inventory is reviewed  

## Purpose

Establish what actually exists in the new Local Yuki repository before any new architecture is implemented.

## Akari must inspect

- repository structure and default branch,
- current source,
- architecture/project docs,
- memory/database/schema/migrations,
- identity/personality code,
- current inference/model/runtime code,
- Android permissions/services/background work,
- current UI surfaces relevant to the brain architecture,
- tests and test organization,
- build/release/signing configuration,
- latest successful CI evidence,
- any existing model-specific coupling.

## Required deliverable

Akari produces a Phase 0 reconnaissance handoff containing:

- baseline commit SHA,
- CI/run evidence,
- version/schema information,
- architecture inventory,
- authority inventory,
- persistence inventory,
- model/runtime coupling inventory,
- background-service inventory,
- test inventory,
- known risks,
- additive migration path,
- recommendation for the first bounded implementation slice.

## Non-goals

- no new model downloads,
- no Laya integration,
- no System-Two model integration,
- no replacement Language & Expression model,
- no broad rewrite,
- no speculative schema migration,
- no moving app-owned authority into a neural model,
- no treating the North Star as current implementation truth.

## Exit gate

Phase 0 exits only when Yuki and Mavyy can identify the live baseline, know which parts of Phases 1–11 already exist, and define the first additive bounded slice without guessing.
