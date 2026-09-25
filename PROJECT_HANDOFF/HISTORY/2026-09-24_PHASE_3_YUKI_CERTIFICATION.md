# Local Yuki — Phase 3 Yuki Certification

**Date:** 2026-09-24  
**Slice:** Phase 3 — Yuki State and Temporal Grounding  
**Certified implementation:** `9d3cd57fec9e3c128b449152167ccdadca9f0e9d`  
**Certified Android CI:** Run #16 (`36087120725`) — SUCCESS  
**Mio independent QA:** PASS  
**Mavyy phone acceptance:** PASS  
**Model Freeze Gate:** CLOSED

## Certified result

Phase 3 is accepted as the deterministic current-state and temporal-grounding baseline for Local Yuki.

The exact independently audited implementation is:

`9d3cd57fec9e3c128b449152167ccdadca9f0e9d`

Android CI Run #16 (`36087120725`) passed against that candidate, including fixed signing-key restoration, foundation-boundary verification, unit tests, Android build, APK certificate verification, and artifact upload.

Artifact:

`local-yuki-debug-9d3cd57fec9e3c128b449152167ccdadca9f0e9d`

The fixed certificate remains:

`14:19:2D:22:5B:5D:ED:98:88:9E:3C:0B:D3:F7:04:D9:4C:1D:90:B9:31:BA:89:AB:0A:22:F4:8A:C9:6C:DD:2F`

## Persistence and migration

Phase 3 advances app-private `continuity.db` from physical schema version `1` to `2`.

The explicit adjacent migration is:

`2026-09-24-yuki-state-v1`

Schema v2 adds:

- `yuki_state`
- `state_intention`
- `state_topic`
- `state_interaction`

The migration validates existing Phase 2B identity continuity before adding Phase 3 state structures.

The Phase 2A/2B canonical identity, relationship anchor, honesty rules, Personality Capsule, continuity format, and migration discipline remain preserved.

## Yuki State

Phase 3 establishes Yuki State semantic version `1`.

Certified state includes:

- current project,
- current focus,
- pending intentions,
- unresolved topics,
- user-input interaction marker,
- Yuki-output interaction marker,
- monotonic revision,
- last update time.

Finite bounds are:

- `32` intentions,
- `32` topics,
- `80` character IDs,
- `512` character item text,
- `240` character focus,
- `160` character project label.

Invalid input is rejected rather than truncated.

### Expiry semantics

The certified Phase 3 semantic clarification is:

- current project has no Phase 3 expiry/TTL,
- project persists until explicitly replaced or cleared,
- focus may have optional explicit expiry,
- individual intentions/topics may have optional explicit expiry,
- focus expiry does not remove or change the project.

Expiry is reconciled during state access using Temporal Grounding.

No background worker or scheduler is required.

### Conflict ordering

State mutation uses expected revisions.

Stale expected revisions fail with `FailureCategory.CONFLICT`.

Mutation ordering is therefore not delegated to wall-clock timestamps.

## Temporal Grounding

Phase 3 establishes deterministic model-neutral Temporal Grounding.

The contract uses injected clock and timezone sources.

The Android production implementation samples the device clock and `ZoneId.systemDefault()` on demand.

Supported normalized calendar queries include today, yesterday, tomorrow, explicit date ranges, and explicit week ranges with explicit first-day-of-week input.

Day windows use local calendar boundaries with start-inclusive/end-exclusive semantics.

This preserves correct 23-hour/25-hour DST days rather than assuming every local day is exactly 24 hours.

No natural-language temporal parser is part of Phase 3.

No GPS/location permission is required.

## Interaction timestamp authority

Trusted app interaction plumbing records only genuine `USER_INPUT` and `YUKI_OUTPUT` markers.

The owning state implementation obtains authoritative timestamp and timezone from Temporal Grounding.

State commands do not carry authoritative clock values.

A future neural/advisory system therefore cannot establish interaction truth merely by proposing a timestamp.

## Real-phone acceptance

Mavyy updated the existing fixed-signed installation without clearing app data.

Phone acceptance passed.

Verified post-restart surface included:

```text
Local Yuki
Foundation setup in progress
Continuity: restored
Yuki State: restored
Temporal: grounded
Local date: 2026-09-24
Timezone: America/Chicago
```

Timezone-change/reopen behavior was also verified and the preferred timezone restored.

This confirms Phase 2 continuity survived the update, Yuki State survived process restart, and Temporal Grounding reflected the device timezone/date without requiring an uninstall/data reset.

## Preserved authority and model freeze

Phase 3 preserves:

- Phase 1B authority graph,
- `YUKI_STATE` app ownership,
- `TEMPORAL_GROUNDING` app ownership,
- Proposal versus mutation separation,
- canonical Yuki/Mavyy identity,
- Personality Capsule v1,
- mandatory honesty rules,
- `ContinuityFormatVersion(1)`,
- substrate opacity,
- model-independent reconstruction,
- fixed signing lineage,
- permission-free Android manifest.

No neural model, neural runtime, embedding model, tokenizer, prompt format, background cognitive worker, service, receiver, or new Android permission was introduced.

## Next phase

Advance to:

**Phase 4 — Memory authority and provenance completion**

Phase 4 implementation is not authorized merely by this certification sync.

Yuki must first perform a fresh live-repository reset and issue a bounded Phase 4 architecture handoff.

The Model Freeze Gate remains CLOSED.
