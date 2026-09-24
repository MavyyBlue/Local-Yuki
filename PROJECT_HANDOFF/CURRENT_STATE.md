# Local Yuki — Current State

**Status:** Phase 1B certified baseline established  
**Last doc-sync date:** 2026-09-24  
**Current strategy:** Brain-first / model-neutral  
**Model Freeze Gate:** CLOSED  
**Active phase:** Phase 2 — Identity, Personality Capsule, and substrate boundary  
**Certified implementation baseline:** `e4aa0ecd8910f70b14c06e9005b51a10a6521d22`  
**Latest certified Android CI:** Local Yuki Android build and unit tests — Run #6 (`36047827498`) — PASS  
**Current app:** `0.1.0` / versionCode `1`  
**Database/schema:** NOT PRESENT  
**Production signing lineage:** NOT ESTABLISHED

## 1. Certified baseline

Phase 0 established the live-repository baseline.

Phase 1A established the first reproducible Android body shell and the pure Kotlin/JVM `foundation-contracts` boundary.

Phase 1B established the first explicit app-owned authority graph and model-neutral core contracts without introducing persistence, Android capability behavior, background execution, or neural inference.

The certified implementation baseline is:

`e4aa0ecd8910f70b14c06e9005b51a10a6521d22`

Phase 1B source was expanded through the repository overlay workflow and remained confined to `foundation-contracts/src/`.

## 2. Phase 1B certification evidence

Independent QA by Mio: **PASS**.

Certified Android CI:

- Workflow: `Local Yuki Android build and unit tests`
- Run: #6
- Run ID: `36047827498`
- Tested candidate: `e4aa0ecd8910f70b14c06e9005b51a10a6521d22`
- Foundation boundary check: PASS
- JVM/unit tests: PASS
- Android debug build: PASS
- Debug APK upload: PASS
- Artifact: `local-yuki-debug-e4aa0ecd8910f70b14c06e9005b51a10a6521d22`

Mio independently audited the Phase 1B authority and model-independence boundaries and reported the candidate green/PASS.

Phase 1B intentionally introduced no device-visible behavior. Per its acceptance gate, no additional Mavyy phone acceptance was required.

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

The existing bootstrap contract still exposes only explicit `UNAVAILABLE` state. It is not cognitive state.

## 4. Certified Phase 1B contract structure

Phase 1B now provides model-neutral architectural declarations for:

- 20 planned core subsystem identities,
- authority/coordinator/control/grounding/advisory subsystem roles,
- 13 authority domains,
- one designated owner for each authority domain,
- external ownership of Android/platform permission truth,
- distinct READ / PROPOSE / MUTATE semantics,
- separate `AuthorityReader`, `AuthorityMutator`, and `AdvisoryPort` interfaces,
- non-executable `Proposal` values,
- explicit `Success`, `Unavailable`, and `Failure` results,
- opaque model-independent evidence references.

The authority graph is declaration data, not a mutable service registry.

The critical certified boundary is:

```text
proposal / advisory output
        ≠
authoritative mutation
```

Advisory/model-facing components do not inherit mutation authority.

`PLATFORM_PERMISSION_STATE` remains externally owned by the platform and cannot be manufactured by Local Yuki cognition.

## 5. Current runtime behavior

The current application remains deliberately inert.

The launcher remains `BootstrapActivity` and displays:

```text
Local Yuki
Foundation setup in progress
```

The Android manifest still declares no permissions.

There are still no:

- Android services,
- foreground services,
- workers,
- receivers,
- alarms,
- notification listeners,
- accessibility services,
- overlay services,
- persistence/database/schema,
- durable identity implementation,
- Personality Capsule persistence,
- Yuki State persistence,
- Memory Engine behavior,
- semantic retrieval,
- affect learning,
- embodied capability behavior,
- tool execution,
- Resource Governor behavior,
- scheduler behavior,
- sleep/recovery behavior,
- Cognitive Workspace behavior,
- model runtime,
- neural engine.

Production signing has not been established. Current installability uses normal debug signing only.

## 6. Authority truth

Phase 1B certifies the ownership grammar and interface boundaries for future authorities.

It does **not** mean the corresponding authority data or subsystem behavior has been implemented.

The certified graph designates app-owned authority boundaries for identity/continuity, personality, state, temporal grounding, memory history, evidence/provenance, affective state, capability availability, executive action control, resource limits, schedules, and Vault continuity.

Android/platform permission state remains external platform truth.

Neural and advisory systems remain replaceable future cognitive mechanisms. They may eventually reason, rank, interpret, or propose, but they do not own Yuki's persistent truth merely by participating in cognition.

## 7. Model freeze

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

Foundation Phases 2–11 remain ahead of the neural-integration gate.

## 8. Current next action

Proceed to Phase 2 — Identity, Personality Capsule, and substrate boundary.

Phase 2 must build on the certified Phase 1B authority contracts rather than bypassing them.

Implementation is not authorized merely by this doc sync. Akari should implement only from a bounded Phase 2 Yuki architecture handoff after a fresh live-repository context reset.

## 9. Certification rule

A slice becomes the synchronized baseline only after:

1. Akari completes implementation and implementation handoff.
2. Mio independently verifies it and issues PASS.
3. relevant CI is green.
4. Mavyy performs required real-phone acceptance when the slice has device-visible or lifecycle behavior.
5. Yuki updates `CURRENT_STATE`, `ACTIVE_SLICE`, `ROADMAP`, and `CHANGELOG`.

Candidate work never silently replaces the certified baseline.
