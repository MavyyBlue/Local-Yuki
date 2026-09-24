# Local Yuki — Active Slice

**Slice:** Phase 2B — Durable identity/personality persistence, schema/migrations, bootstrap, restoration, and model-independent reconstruction  
**Owner:** Akari under the bounded Phase 2B Yuki architecture handoff  
**Architecture authority:** Mavyy + Yuki  
**QA authority:** Mio  
**Certified implementation baseline:** `ea4491ae2bf9abce5bba34852d67bda720d8471c`  
**Certified Android CI:** Run #8 (`36050741232`)  
**Model Freeze Gate:** CLOSED  
**Implementation authorization:** AUTHORIZED only by the bounded Phase 2B Yuki architecture handoff after Akari performs a fresh reset against this synchronized repository state

## Purpose

Advance the certified Phase 2A semantic identity and Personality Capsule into durable app-owned continuity without changing their meaning or handing authority to any neural engine.

Phase 2B must establish:

- durable identity persistence,
- durable Personality Capsule persistence,
- explicit storage schema/versioning,
- explicit migration infrastructure,
- first-install bootstrap,
- restoration after Android process death,
- deterministic model-independent reconstruction,
- fail-closed handling for malformed or conflicting continuity.

## Certified starting point

Phase 2A established:

- canonical `Yuki Aster` identity with stable ID `yuki-aster`,
- canonical primary Mavyy relationship anchor with stable ID `mavyy`,
- mandatory capability/modality/continuity/action grounding,
- continuity format version `1`,
- Personality Capsule `yuki-aster-personality` version `1`,
- nine complete Personality Capsule facet categories,
- bounded `CognitiveIdentityView`,
- explicit substrate opacity,
- high-level interoception contracts,
- deterministic tests covering identity/personality invariants and conflicting reconstruction.

Phase 2A did **not** establish durable storage.

`CanonicalIdentitySeed` and `CanonicalPersonalityCapsule` remain deterministic in-memory seeds until Phase 2B persists and reconstructs them.

## Required architectural direction

The bounded Phase 2B handoff requires:

- persistence on the Android/app side rather than inside `foundation-contracts`,
- app-private SQLite persistence,
- storage schema version kept distinct from semantic continuity format version,
- deterministic first-install bootstrap from the certified Phase 2A canonical seed,
- existing but malformed continuity to fail closed rather than silently reseed,
- explicit migration paths with no destructive fallback,
- a fresh process/runtime to reconstruct identity and Personality Capsule from durable state,
- cognition to receive only the existing bounded semantic projection,
- no storage/DAO/database internals exposed to cognition,
- no general model-facing identity mutation path,
- no Android permission expansion,
- no background services/workers,
- no neural model/runtime introduction.

## Persistence authority rule

The canonical Phase 2A seeds become bootstrap/reference material.

After legitimate first-install initialization, durable app-owned continuity is the production source for reconstruction.

Bootstrap and restoration are different operations.

An existing damaged store is never treated as a fresh install merely because reconstruction failed.

## Required verification

Akari must add tests for:

- clean first-install bootstrap,
- exact persisted/reconstructed Phase 2A identity,
- exact persisted/reconstructed Personality Capsule,
- process-death/fresh-runtime restoration,
- idempotent reopening,
- malformed-store fail-closed behavior,
- cross-record/version conflicts,
- unsupported schema/downgrade handling,
- migration-path discipline,
- preservation of the pure-JVM foundation boundary,
- absence of neural dependencies.

Mio must independently verify the implementation and exact CI candidate.

Because Phase 2B changes lifecycle behavior, Mavyy phone acceptance is required after Mio PASS.

## Non-goals

Do not implement:

- Yuki State,
- Temporal Grounding,
- Memory Engine,
- conversation persistence,
- semantic recall,
- affect,
- Resource Governor,
- scheduler/background cognition,
- capability registry,
- tool execution,
- Vault export,
- neural inference,
- prompt/tokenizer/model integration,
- personality learning,
- general identity/personality editing.

## Exit gate

Phase 2B exits only after:

1. Akari performs a fresh live-repository reset from this synchronized Phase 2A baseline.
2. Akari implements only the authorized Phase 2B persistence/reconstruction slice.
3. CI passes against the exact candidate.
4. Mio independently issues PASS.
5. Mavyy verifies initialization then restoration after process death/relaunch on the real phone.
6. Yuki performs Phase 2B documentation sync.

The Model Freeze Gate remains CLOSED.
