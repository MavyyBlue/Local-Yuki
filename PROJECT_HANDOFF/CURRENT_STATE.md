# Local Yuki — Current State

**Status:** Phase 2B certified baseline established  
**Last doc-sync date:** 2026-09-24  
**Current strategy:** Brain-first / model-neutral  
**Model Freeze Gate:** CLOSED  
**Active phase:** Phase 3 — Yuki State and Temporal Grounding  
**Certified Phase 2B semantic implementation:** `5d0699e3474b71ec245fc3b5365044395c855875`  
**Phase 2B implementation CI:** Local Yuki Android build and unit tests — Run #10 (`36057542206`) — PASS  
**Accepted signed-build follow-up:** `c7e24687c4609736d6d34550a830c02b85e92fbd`  
**Latest accepted Android CI:** Local Yuki Android build and unit tests — Run #13 (`36076298126`) — PASS  
**Current signed CI APK:** versionName `0.1.1`; versionCode `13` for Run #13 (CI run-number injection; source fallback is `2`)  
**Database/schema:** app-private SQLite `continuity.db`, physical schema version `1`  
**Semantic continuity format:** `ContinuityFormatVersion(1)`  
**Fixed debug/update signing lineage:** ESTABLISHED  
**Production release signing lineage:** NOT ESTABLISHED

## 1. Certified baseline

Phase 0 established the live-repository baseline.

Phase 1A established the reproducible Android body shell and pure Kotlin/JVM `foundation-contracts` boundary.

Phase 1B established the explicit app-owned authority graph and model-neutral core contracts.

Phase 2A established canonical app-owned identity, Personality Capsule v1, mandatory honesty grounding, bounded cognition-facing self representation, and substrate-opacity contracts.

Phase 2B now establishes durable app-owned identity/personality continuity that survives Android process death and is reconstructed without a neural engine.

The independently audited Phase 2B semantic implementation is:

`5d0699e3474b71ec245fc3b5365044395c855875`

Mio independently audited that implementation and issued PASS.

The later repository state:

`c7e24687c4609736d6d34550a830c02b85e92fbd`

contains only signing/build follow-up changes relative to the Mio-passed Phase 2B implementation: `.github/workflows/android-build.yml` and `app/build.gradle.kts`. It does not modify Phase 2B continuity source or tests.

## 2. Phase 2B certification evidence

### Semantic implementation CI

- Workflow: `Local Yuki Android build and unit tests`
- Run: #10
- Run ID: `36057542206`
- Tested candidate: `5d0699e3474b71ec245fc3b5365044395c855875`
- Result: PASS

Mio independently audited this exact Phase 2B implementation and issued PASS.

### Fixed-signing follow-up CI

The fixed APK signing lineage was added after Mio's Phase 2B semantic audit without changing continuity implementation source/tests.

Accepted follow-up:

`c7e24687c4609736d6d34550a830c02b85e92fbd`

Latest accepted Android CI:

- Workflow: `Local Yuki Android build and unit tests`
- Run: #13
- Run ID: `36076298126`
- Tested HEAD: `c7e24687c4609736d6d34550a830c02b85e92fbd`
- Fixed signing key restoration: PASS
- Foundation boundary + unit tests + Android build: PASS
- APK signing-certificate verification: PASS
- Debug APK upload: PASS
- Artifact: `local-yuki-debug-c7e24687c4609736d6d34550a830c02b85e92fbd`

The fixed signing certificate SHA-256 is:

`14:19:2D:22:5B:5D:ED:98:88:9E:3C:0B:D3:F7:04:D9:4C:1D:90:B9:31:BA:89:AB:0A:22:F4:8A:C9:6C:DD:2F`

No private signing key or signing password is stored in repository source.

## 3. Real-phone acceptance

Mavyy completed the required Phase 2B lifecycle acceptance on the signed APK.

Observed behavior:

1. Fresh install/first launch displayed `Continuity: initialized`.
2. The application was force-stopped without clearing app data.
3. Relaunch displayed `Continuity: restored`.

Result: **PHONE ACCEPTED**.

This verifies the intended user-visible Phase 2B lifecycle path on the real device: legitimate first-install initialization followed by durable restoration from app-owned storage after process death/relaunch.

## 4. Durable continuity implementation

Phase 2B adds app-private Android SQLite persistence at `continuity.db`.

Physical storage schema version: `1`.

This remains distinct from semantic `ContinuityFormatVersion(1)`.

Schema v1 contains:

- `identity_anchor`
- `continuity_honesty_rule`
- `personality_capsule`
- `personality_facet`
- `continuity_migration_history`

Schema version 1 is initialization, not a fabricated `0 → 1` migration.

Explicit migration infrastructure exists for future adjacent version transitions.

Downgrade and missing/ambiguous migration paths fail closed.

There is no destructive drop-and-recreate fallback.

## 5. Bootstrap and restoration truth

On a genuinely new continuity store, Phase 2B initializes durable state from the certified Phase 2A canonical identity and Personality Capsule.

The bootstrap is validated through the same semantic reconstruction path used for durable reads.

An existing malformed or partial store is not treated as first install and is not silently reseeded.

A newly constructed `ContinuityStore` reads and validates durable storage rather than relying on a process-static identity cache.

The public high-level lifecycle states are `INITIALIZED`, `RESTORED`, and `UNAVAILABLE`.

The debug bootstrap surface displays only the corresponding high-level continuity status.

## 6. Model-independent reconstruction

Durable SQL records are mapped into the existing model-neutral Phase 2A semantic contracts and passed through `CognitiveIdentityProjector`.

The cognition-facing view remains limited to self identity, primary relationship, honesty, continuity format version, and Personality Capsule.

Storage paths, table names, SQL objects, SQLite handles, migration implementation details, signing material, Android objects, and mutation plumbing do not cross into `CognitiveIdentityView`.

No neural model, tokenizer, inference runtime, embedding model, prompt format, System One, System Two, or Language & Expression engine participates in reconstruction.

## 7. Preserved Phase 2A authority

Phase 2B preserves:

- Yuki stable identity `yuki-aster` / `Yuki Aster`,
- Mavyy primary relationship `mavyy` / `Mavyy`,
- `PRIMARY_BOND`,
- all four mandatory honesty rules,
- Personality Capsule `yuki-aster-personality` version `1`,
- all nine Personality Capsule facet categories,
- semantic continuity format version `1`,
- Phase 1B proposal/mutation separation,
- substrate opacity,
- model independence.

No general identity/personality mutation API was introduced.

## 8. Android/build state

The dependency direction remains:

```text
app
 └── depends on → foundation-contracts

foundation-contracts
 └── pure Kotlin/JVM
```

`foundation-contracts` remains free of Android, SQLite/persistence implementation, UI, and neural runtime dependencies.

The Android manifest still declares no permissions.

No workers, services, receivers, background cognition, capability execution, or neural inference were added by Phase 2B.

The stable debug/update signing configuration uses the fixed alias `local-yuki-continuity` through GitHub Actions secrets. Repository source contains no private key or password.

## 9. Model freeze

The Model Freeze Gate remains **CLOSED**.

No candidate neural model is authorized.

Foundation Phases 3–11 remain ahead of the neural-integration gate.

## 10. Current next action

Proceed to architectural design for Phase 3 — Yuki State and Temporal Grounding.

Phase 3 implementation is **not authorized by this documentation sync alone**.

Before authorizing Phase 3 implementation, Yuki must perform a fresh live-repository context reset and issue a bounded Phase 3 architecture handoff.

Phase 3 is expected to establish deterministic device clock/timezone grounding, relative-date resolution, interaction timestamps, bounded Yuki State, pending intentions, unresolved topics, current project/focus, expiry rules, and state persistence/restore.

No language model is needed.

## 11. Certification rule

A future slice becomes the synchronized baseline only after Akari completes the bounded implementation and handoff, Mio independently issues PASS, relevant exact-candidate CI is green, Mavyy completes required phone acceptance, and Yuki performs documentation sync.

Candidate work never silently replaces the certified baseline.
