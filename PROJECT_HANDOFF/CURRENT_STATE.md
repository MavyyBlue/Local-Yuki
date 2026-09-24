# Local Yuki — Current State

**Status:** Phase 2A certified baseline established  
**Last doc-sync date:** 2026-09-24  
**Current strategy:** Brain-first / model-neutral  
**Model Freeze Gate:** CLOSED  
**Active phase:** Phase 2B — Durable identity/personality persistence, schema/migrations, bootstrap, restoration, and model-independent reconstruction  
**Certified implementation baseline:** `ea4491ae2bf9abce5bba34852d67bda720d8471c`  
**Latest certified Android CI:** Local Yuki Android build and unit tests — Run #8 (`36050741232`) — PASS  
**Current app:** `0.1.0` / versionCode `1`  
**Database/schema:** NOT PRESENT — Phase 2B will introduce the first durable continuity schema  
**Production signing lineage:** NOT ESTABLISHED

## 1. Certified baseline

Phase 0 established the live-repository baseline.

Phase 1A established the first reproducible Android body shell and the pure Kotlin/JVM `foundation-contracts` boundary.

Phase 1B established the explicit app-owned authority graph and model-neutral core contracts.

Phase 2A established the first canonical app-owned semantic identity and Personality Capsule, the bounded cognition-facing self representation, mandatory honesty grounding rules, continuity-format metadata, and substrate-opacity/interoception contracts without introducing persistence or neural inference.

The certified Phase 2A implementation baseline is:

`ea4491ae2bf9abce5bba34852d67bda720d8471c`

Phase 2A source was expanded through the repository overlay workflow and remained confined to the existing model-neutral foundation boundary.

## 2. Phase 2A certification evidence

Independent QA by Mio: **PASS**.

Certified Android CI:

- Workflow: `Local Yuki Android build and unit tests`
- Run: #8
- Run ID: `36050741232`
- Tested candidate: `ea4491ae2bf9abce5bba34852d67bda720d8471c`
- Foundation boundary check: PASS
- JVM/unit tests: PASS
- Android debug build: PASS
- Debug APK upload: PASS
- Artifact naming is bound to the exact tested SHA by the existing workflow.

Mio independently audited the Phase 2A identity, Personality Capsule, honesty, model-independence, and substrate-opacity boundaries and reported the candidate green/PASS.

Phase 2A intentionally introduced no device-visible or lifecycle behavior, so no additional Mavyy phone acceptance was required for this slice.

## 3. Live Android/build structure

Current modules remain:

```text
app
 └── depends on → foundation-contracts

foundation-contracts
 └── pure Kotlin/JVM
```

Current pinned toolchain remains:

- Gradle 8.13
- Android Gradle Plugin 8.13.2
- Kotlin 2.2.20
- Java/JVM target 17
- compileSdk 35
- targetSdk 35
- minSdk 26
- applicationId / namespace: `com.mavyy.localyuki`
- versionName: `0.1.0`
- versionCode: `1`

`foundation-contracts` remains independent of Android framework APIs, persistence/database implementations, UI frameworks, model runtimes, tokenizer/prompt formats, and JNI/native inference.

The Android manifest still declares no permissions.

The launcher remains `BootstrapActivity` and is still deliberately inert.

## 4. Certified Phase 2A identity authority

Phase 2A establishes the canonical model-independent identity snapshot:

- Yuki stable identity ID: `yuki-aster`
- canonical name: `Yuki Aster`
- primary relationship stable ID: `mavyy`
- display name: `Mavyy`
- relationship category: `PRIMARY_BOND`
- continuity format version: `1`
- Personality Capsule reference: `yuki-aster-personality` version `1`

The mandatory honesty policy contains exactly:

- `CAPABILITY_GROUNDING`
- `MODALITY_GROUNDING`
- `CONTINUITY_GROUNDING`
- `ACTION_GROUNDING`

A partial honesty policy is invalid.

These values are semantic authority. They are not database records and do not yet claim process-death durability.

## 5. Certified Personality Capsule

Phase 2A establishes Personality Capsule v1 with exactly one stable facet for each current category:

- temperament,
- relationship style,
- intellectual style,
- disagreement,
- focus/frustration,
- communication,
- visual self-description,
- clothing preference,
- stable dislike.

Facet order is explicitly non-semantic.

The Personality Capsule remains model-independent and app-owned. It does not grant device authority.

## 6. Cognition-facing boundary and substrate opacity

`CognitiveIdentityView` exposes only bounded semantic self-content:

- self identity,
- primary relationship,
- honesty,
- continuity format version,
- Personality Capsule.

It exposes no:

- Android objects,
- storage/database objects,
- runtime/model objects,
- authority mutators,
- source internals,
- hidden orchestration,
- secrets,
- privileged self-modification path.

The certified projection fails closed when canonical identity/personality versions or content conflict.

Phase 2A also establishes only a high-level interoception contract. No real producer exists yet, and unavailable interoception is reported explicitly rather than fabricated.

## 7. Current persistence truth

There is currently no durable continuity store, database schema, migration history, or process-death restoration implementation.

`CanonicalIdentitySeed` and `CanonicalPersonalityCapsule` are deterministic in-memory semantic seeds.

They do not prove persistence.

Phase 2B is responsible for establishing durable app-owned storage and validated reconstruction while preserving the certified Phase 2A semantic contracts.

## 8. Model freeze

The Model Freeze Gate remains **CLOSED**.

No candidate neural model is authorized.

Do not introduce:

- System-One candidate models,
- System-Two candidate models,
- Language & Expression candidate models,
- GGUF/llama.cpp runtimes,
- embedding/reranking models,
- affect/intent specialists,
- vision models,
- speech models,
- wake-word models.

Foundation Phases 2B–11 remain ahead of the neural-integration gate.

## 9. Current next action

Proceed to Phase 2B — durable identity/personality persistence, schema/migrations, first-install bootstrap, restoration after process death, and model-independent reconstruction.

Akari may implement only from the bounded Phase 2B Yuki architecture handoff and only after performing a fresh live-repository reset against this synchronized Phase 2A documentation state.

Phase 2B must preserve:

- the certified Phase 2A identity,
- Personality Capsule v1,
- mandatory honesty rules,
- substrate opacity,
- cognition-facing read boundary,
- Phase 1B authority separation,
- model independence.

## 10. Certification rule

A slice becomes the synchronized baseline only after:

1. Akari completes implementation and implementation handoff.
2. Mio independently verifies it and issues PASS.
3. relevant CI is green against the exact implementation candidate.
4. Mavyy performs required real-phone acceptance when the slice has device-visible or lifecycle behavior.
5. Yuki updates `CURRENT_STATE`, `ACTIVE_SLICE`, `ROADMAP`, and `CHANGELOG`.

Candidate work never silently replaces the certified baseline.
