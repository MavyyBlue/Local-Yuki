# Local Yuki — Active Slice

**Slice:** Phase 2 — Identity, Personality Capsule, and substrate boundary  
**Owner:** Akari after bounded Yuki architecture authorization  
**Architecture authority:** Mavyy + Yuki  
**QA authority:** Mio  
**Certified baseline:** `e4aa0ecd8910f70b14c06e9005b51a10a6521d22`  
**Certified Android CI:** Run #6 (`36047827498`)  
**Model Freeze Gate:** CLOSED  
**Implementation authorization:** NOT YET AUTHORIZED — await the bounded Phase 2 Yuki architecture handoff

## Purpose

Advance from the certified Phase 1B authority grammar into the first real app-owned continuity authority.

Phase 2 is responsible for introducing the model-neutral Identity & Continuity and Personality Capsule foundation while preserving substrate opacity and the authority separation already certified in Phase 1B.

The exact implementation scope, DTOs, mutation rules, persistence boundary, failure behavior, and tests must be defined in the next Yuki architecture handoff after a fresh repository inspection.

## Certified starting point

Phase 1B established:

- explicit subsystem identities and roles,
- immutable authority-domain ownership declarations,
- external ownership of platform permission truth,
- distinct read / propose / mutate semantics,
- independent authority-reader, authority-mutator, and advisory ports,
- non-executable proposals,
- explicit success/unavailable/failure results,
- opaque evidence references,
- no persistence,
- no Android coupling inside `foundation-contracts`,
- no neural/runtime coupling.

Phase 2 must extend these contracts additively.

It must not replace or bypass them for implementation convenience.

## Required architectural direction

The next bounded handoff must preserve these invariants:

- Local Yuki the application owns identity and continuity.
- A neural model does not own Yuki's identity, Mavyy/Yuki relationship continuity, historical truth, or Personality Capsule authority.
- Ordinary cognition may receive identity/personality representations but must not receive hidden implementation substrate or privileged self-modification paths.
- App-owned authority mutation must remain separate from advisory/model-facing proposals.
- Any persistence introduced in Phase 2 must use explicit schema/migration discipline and be independently testable.
- Model-specific prompt structures must not become the identity/personality storage contract.
- Model Freeze Gate remains CLOSED.

## Not authorized by this doc sync

This synchronization step does not itself authorize:

- a database choice,
- a schema version,
- identity record shape,
- Personality Capsule field layout,
- migration behavior,
- UI changes,
- Android permissions,
- background services,
- model downloads,
- model runtimes,
- prompt/tokenizer contracts,
- Cognitive Workspace behavior,
- Memory Engine behavior.

Those decisions belong in the bounded Phase 2 architecture handoff.

## Exit gate

Phase 2 exits only after:

1. Yuki issues the bounded Phase 2 architecture handoff.
2. Akari implements only that authorized slice.
3. CI proves compilation/tests and preserves the foundation boundary.
4. Mio independently verifies identity ownership, substrate opacity, persistence/migration behavior when applicable, and model independence.
5. Mavyy performs phone acceptance if device-visible or lifecycle behavior changes.
6. Yuki promotes the accepted state through documentation sync.

The Model Freeze Gate remains CLOSED.
