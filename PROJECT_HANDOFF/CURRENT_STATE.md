# Local Yuki — Current State

**Status:** Phase 1A certified baseline established  
**Last doc-sync date:** 2026-09-24  
**Current strategy:** Brain-first / model-neutral  
**Model Freeze Gate:** CLOSED  
**Active phase:** Phase 1B — App-owned authority graph and model-neutral core contracts  
**Certified implementation baseline:** `ab89ee27d8d005f2febef30cc6d05148a72dea55`  
**Latest certified Android CI:** Local Yuki Android build and unit tests — Run #4 (`35945088085`) — PASS  
**Current app:** `0.1.0` / versionCode `1`  
**Database/schema:** NOT PRESENT  
**Production signing lineage:** NOT ESTABLISHED

## 1. Certified baseline

Phase 0 reconnaissance established that the new repository contained the synchronization/documentation spine and source-import workflow only; no Android application or inherited Local Yuki implementation was present.

Phase 1A then established the first executable Android body shell without introducing cognition.

The certified implementation baseline is:

`ab89ee27d8d005f2febef30cc6d05148a72dea55`

Phase 1A source entered at:

`379cd5c5d3f61a38c53a8f51b0afc9f2da4fd64f`

The later candidate commits added and corrected the separate Android build/test workflow.

## 2. Certification evidence

Independent QA by Mio: **PASS**.

Certified Android CI:

- Workflow: `Local Yuki Android build and unit tests`
- Run: #4
- Run ID: `35945088085`
- Tested candidate: `ab89ee27d8d005f2febef30cc6d05148a72dea55`
- Foundation boundary check: PASS
- JVM/unit tests: PASS
- Android debug build: PASS
- Debug APK upload: PASS
- Artifact: `local-yuki-debug-ab89ee27d8d005f2febef30cc6d05148a72dea55`

Mavyy completed the required real-phone acceptance on 2026-09-24:

- debug APK installed,
- application launched successfully,
- minimal bootstrap surface loaded,
- no immediate crash or unexpected behavior was reported.

Phase 1A is therefore promoted to the synchronized certified baseline.

## 3. Live Android/build structure

Current modules:

```text
app
 └── depends on → foundation-contracts

foundation-contracts
 └── pure Kotlin/JVM
```

Current pinned toolchain:

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

`foundation-contracts` contains no Android framework dependency and no neural/model-runtime dependency. Its verification task rejects Android, persistence, and common neural-runtime coupling.

The current bootstrap contract exposes only explicit `UNAVAILABLE` state. It is not cognitive state.

## 4. Current runtime behavior

The current application is deliberately inert.

The launcher is `BootstrapActivity`. It displays:

```text
Local Yuki
Foundation setup in progress
```

Current Android manifest declares no permissions.

There are currently no:

- Android services,
- foreground services,
- workers,
- receivers,
- alarms,
- notification listeners,
- accessibility services,
- overlay services,
- persistence/database/schema,
- memory implementation,
- identity implementation,
- affect/state implementation,
- model runtime,
- neural engine,
- scheduler,
- perception pipeline,
- Resource Governor,
- Cognitive Workspace.

Production signing has not been established. Current installability uses normal debug signing only.

## 5. Authority truth

Phase 1A establishes structural boundaries, not cognitive authorities.

No live implementation yet owns:

- Yuki identity/continuity,
- Mavyy/Yuki relationship continuity,
- autobiographical memory,
- evidence/provenance,
- Yuki State,
- affect,
- embodied capability availability,
- executive action,
- resource limits,
- schedules,
- perception,
- Vault continuity.

Those authorities remain architectural requirements to be introduced deliberately in later bounded slices.

Android/platform permission truth remains platform-owned. Neural models remain replaceable future cognitive mechanisms and do not own app truth.

## 6. Model freeze

The Model Freeze Gate remains **CLOSED**.

No candidate neural model is authorized yet.

Do not introduce:

- Laya,
- System-Two candidates,
- Language & Expression candidates,
- GGUF/llama.cpp runtimes,
- embedding/reranking models,
- affect/intent specialists,
- vision models,
- speech models,
- wake-word models.

Foundation Phases 1–11 must reach the required certified maturity first.

## 7. Current next action

Proceed with Phase 1B: define the first explicit app-owned authority graph and model-neutral core contracts inside the already-certified `foundation-contracts` boundary.

Phase 1B must remain persistence-free, Android-free inside the foundation module, and model-runtime-free.

Akari implements only from the bounded Phase 1B architecture handoff. Mio independently verifies the candidate before promotion.

## 8. Certification rule

A slice becomes the synchronized baseline only after:

1. Akari completes implementation and implementation handoff.
2. Mio independently verifies it and issues PASS.
3. relevant CI is green.
4. Mavyy performs required real-phone acceptance when the slice has device-visible behavior.
5. Yuki updates `CURRENT_STATE`, `ACTIVE_SLICE`, `ROADMAP`, and `CHANGELOG`.

Candidate work never silently replaces the certified baseline.
