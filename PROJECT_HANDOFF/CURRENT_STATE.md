# Local Yuki — Current State

**Status:** Phase 3 certified baseline established  
**Last doc-sync date:** 2026-09-24  
**Current strategy:** Brain-first / model-neutral  
**Model Freeze Gate:** CLOSED  
**Active phase:** Phase 4 — Memory authority and provenance completion  
**Certified Phase 3 implementation:** `9d3cd57fec9e3c128b449152167ccdadca9f0e9d`  
**Certified Android CI:** Local Yuki Android build and unit tests — Run #16 (`36087120725`) — PASS  
**Mio Phase 3 verdict:** PASS  
**Mavyy Phase 3 phone acceptance:** PASS  
**Current signed CI APK:** versionName `0.1.1`; versionCode `16` for Run #16 (CI run-number injection; source fallback is `2`)  
**Database/schema:** app-private SQLite `continuity.db`, physical schema version `2`  
**Semantic continuity format:** `ContinuityFormatVersion(1)`  
**Yuki State semantic version:** `YukiStateVersion(1)`  
**Fixed debug/update signing lineage:** ESTABLISHED  
**Production release signing lineage:** NOT ESTABLISHED

## 1. Certified baseline

Phase 0 established the live-repository baseline.

Phase 1A established the reproducible Android body shell and pure Kotlin/JVM `foundation-contracts` boundary.

Phase 1B established the explicit app-owned authority graph and model-neutral core contracts.

Phase 2A established canonical app-owned identity, Personality Capsule v1, mandatory honesty grounding, bounded cognition-facing self representation, and substrate-opacity contracts.

Phase 2B established durable app-owned identity/personality continuity, explicit physical schema/migrations, first-install bootstrap, process-death restoration, and fixed update signing.

Phase 3 now establishes deterministic app-owned Yuki State and Temporal Grounding while preserving all earlier continuity and authority boundaries.

Certified Phase 3 implementation:

`9d3cd57fec9e3c128b449152167ccdadca9f0e9d`

Mio independently audited this exact candidate and issued PASS.

## 2. Phase 3 certification evidence

- Workflow: `Local Yuki Android build and unit tests`
- Run: #16
- Run ID: `36087120725`
- Tested candidate: `9d3cd57fec9e3c128b449152167ccdadca9f0e9d`
- Result: PASS
- Fixed signing-key restoration: PASS
- Foundation boundary + unit tests + Android build: PASS
- APK signing-certificate verification: PASS
- Debug APK upload: PASS
- Artifact: `local-yuki-debug-9d3cd57fec9e3c128b449152167ccdadca9f0e9d`

The fixed signing certificate SHA-256 remains:

`14:19:2D:22:5B:5D:ED:98:88:9E:3C:0B:D3:F7:04:D9:4C:1D:90:B9:31:BA:89:AB:0A:22:F4:8A:C9:6C:DD:2F`

No signing workflow or certificate change was introduced by Phase 3.

## 3. Real-phone acceptance

Mavyy completed Phase 3 acceptance on the fixed-signed update without clearing app data.

Verified on the real phone:

- existing identity continuity restored,
- Yuki State restored after force-stop/relaunch,
- Temporal Grounding reported grounded,
- local date matched the phone,
- timezone matched the phone,
- timezone-change/reopen acceptance passed and the preferred timezone was restored.

Observed post-restart acceptance surface included:

```text
Local Yuki
Foundation setup in progress
Continuity: restored
Yuki State: restored
Temporal: grounded
Local date: 2026-09-24
Timezone: America/Chicago
```

Result: **PHONE ACCEPTED**.

## 4. Physical schema and migration

Phase 3 advances `continuity.db` from physical schema version `1` to `2`.

Explicit adjacent migration:

`2026-09-24-yuki-state-v1`

Schema v2 adds:

- `yuki_state`
- `state_intention`
- `state_topic`
- `state_interaction`

The migration validates existing v1 identity continuity before creating Phase 3 state structures.

Certified Phase 2B identity/personality tables and semantics remain intact.

No destructive drop-and-recreate fallback was introduced.

## 5. Yuki State authority

The Phase 1B authority graph remains unchanged:

- `YUKI_STATE` → `CoreSubsystemId.YUKI_STATE`
- `TEMPORAL_GROUNDING` → `CoreSubsystemId.TEMPORAL_GROUNDING`

Yuki State semantic version is `1`.

Bounded current state supports:

- one current project,
- one current focus,
- pending intentions,
- unresolved topics,
- latest user-input interaction marker,
- latest Yuki-output interaction marker,
- monotonic revision,
- durable updated timestamp.

Current state is not factual/autobiographical memory.

Current project has **no Phase 3 TTL** and persists until explicitly replaced or cleared.

Focus may have explicit expiry.

Intentions and unresolved topics may have optional explicit expiry.

Focus expiry does not remove/change the project.

Expired focus/items are reconciled deterministically on state access; no background worker is required.

## 6. Bounds and conflicts

Certified finite limits:

- maximum intentions: `32`
- maximum unresolved topics: `32`
- stable ID length: `80`
- item text length: `512`
- focus length: `240`
- project label length: `160`

Invalid input is rejected rather than truncated.

Mutations use expected revisions. Stale revisions fail with `FailureCategory.CONFLICT`; there is no silent last-write-wins behavior.

Successful semantic mutation increments revision. Revision, not wall-clock order, is authoritative for state mutation ordering.

## 7. Interaction timestamp truth

Trusted app interaction plumbing records only `USER_INPUT` and `YUKI_OUTPUT`.

The owning state implementation obtains authoritative event time from Temporal Grounding.

State commands do not carry authoritative clock values.

Interaction markers persist:

- grounded instant,
- observed timezone ID,
- revision.

A future advisory/model proposal cannot manufacture authoritative interaction history merely by supplying a timestamp.

## 8. Temporal Grounding

Temporal Grounding remains pure/model-neutral in `foundation-contracts` with injected clock and timezone sources.

The Android production path samples device time and `ZoneId.systemDefault()` on demand.

Timezone is not permanently cached at process startup.

Normalized calendar queries support:

- today,
- yesterday,
- tomorrow,
- explicit date windows,
- explicit week windows with caller-supplied first day of week.

Windows use local calendar boundaries with start-inclusive/end-exclusive semantics rather than blind 24-hour subtraction, preserving DST-short and DST-long days.

No natural-language temporal parser or GPS/location dependency is part of Phase 3.

## 9. Restart reconstruction

A new `YukiStateStore` reopens app-private durable storage and reconstructs current state.

No process-static state cache is required.

State lifecycle distinguishes:

- `INITIALIZED`
- `RESTORED`
- `UNAVAILABLE`

Phase 2B identity restoration remains independently validated.

## 10. Android/build state

Dependency direction remains:

```text
app
 └── depends on → foundation-contracts

foundation-contracts
 └── pure Kotlin/JVM
```

The Android manifest still declares no permissions.

Phase 3 adds no worker, service, receiver, background cognition, neural model/runtime, embedding model, tokenizer, prompt format, or network dependency.

Fixed update signing remains unchanged.

## 11. Model freeze

The Model Freeze Gate remains **CLOSED**.

No candidate neural model is authorized.

Foundation Phases 4–11 remain ahead of the neural-integration gate.

## 12. Current next action

Proceed to architectural design for:

**Phase 4 — Memory authority and provenance completion**

Phase 4 implementation is **not authorized by this documentation sync alone**.

Before implementation, Yuki must perform a fresh live-repository reset and issue a bounded Phase 4 architecture handoff.

Phase 4 is responsible for deterministic memory/evidence authority and provenance completion without semantic neural retrieval.

## 13. Certification rule

A future slice becomes synchronized only after Akari completes the bounded implementation and handoff, Mio independently issues PASS, exact-candidate CI is green, Mavyy completes required phone acceptance where applicable, and Yuki performs documentation sync.

Candidate work never silently replaces the certified baseline.
