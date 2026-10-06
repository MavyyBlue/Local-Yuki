> Historical handoff. Current source status, owner-directed verification and freeze removal are recorded in [CURRENT_STATE](CURRENT_STATE.md). Stale CI/assistant requirements below are historical, not active gates.

# Local Yuki — Continuation implementation handoff

Date: 2026-10-02. Starting source: `57c6d173155c39aa3cd92621db4748a80846a7ba`.
That Phase 5 source has Android CI #26 (`37073792892`) success and owner phone PASS.
This continuation is a new candidate. Independent Mio review, its exact-source
signed CI and its owner phone acceptance are pending. It is not self-certified.

## Implemented slices and boundaries

| Phase | Candidate behavior | Principal source / verification |
|---|---|---|
| 6 | Synthetic affect vector, bounded evidence-linked associations, saturation and decay; persisted optimistic revisions and idempotent receipts | foundation/app `affect/`; policy and restart/provenance tests |
| 7 | Owner grants, package exclusions, actual availability, typed action checks and durable local-note receipts | foundation/app `embodiment/`, `brain/BrainRuntime.kt`; forged result, grant, retry and sleep tests |
| 8 | Measured RAM/battery/thermal/foreground, ABI/CPU capabilities; workload leases, bounded residency, fault recovery and high-level interoception | foundation/app `resource/`; stale/heat/RAM/lease/unload tests |
| 9 | Persistent sleep/recovery transitions, intention checkpoints, interrupted-maintenance recovery and bounded hooks | foundation/app `recovery/`; restart and legal transition tests |
| 10 | Bounded salience signals, freshness/cooldown persistence; optional charging/battery-not-low Android JobScheduler maintenance | foundation/app `scheduler/`; cooldown/restart/job constraints tests |
| 11 | Grounded context, evidence/identity/state/capability verification, detached advisory inputs, reasoning/language and specialist sockets | foundation `workspace/`, app `brain/`; fabricated evidence/identity, hostile mutation, reasoning and replacement tests |
| 12 groundwork | Separate owner intake, bounded hashing/header inspection, adapter selection, resource smoke test/unload and persisted receipts | foundation/app `admission/`; oversized input, hash tampering, missing adapter and closed certification gate tests |
| 20 groundwork | Owner password-encrypted continuity export and validated atomic restore with one local pre-restore backup | app `vault/`; round trip, wrong password, tampering and unknown schema tests |
| 21/22 groundwork | Two mock language engines share the same authority; exported continuity restores into a fresh database context | `RuntimeAndVaultTest.kt`; no real engine or second physical device proof |

Each persistent slice uses explicit adjacent schema upgrades. Physical schema is
now 8; semantic continuity/state/memory formats remain 1. Populated schema 4 upgrade
and injected failed-upgrade rollback are tested, alongside existing older upgrade
coverage. Immutable evidence/revisions, owner update/append-only restore, durable
identity and fixed signing remain preserved.

## Runtime and owner console

`BrainRuntime` is the trusted app composition root. `ContinuityAccess` serializes
UI work, jobs and Vault operations. The owner console performs work off the main
thread. It supports saving input, evidence-backed memory creation/search/update/
restoring the previous interpretation, optional autonomous local notes, sleep/wake,
bounded maintenance, explicit salience simulation and Android document-picker
Vault export/restore. Saving a message does not fabricate an AI response.

Only local notes have an available action executor. Screen, notifications, speech
and vision remain unavailable. Owner grant changes are not Android permission
grants. Actions recheck actual grants and resource/lifecycle conditions and bind
receipts to action identity. Already granted local notes need no per-action approval.

Background maintenance is off by default, owner-controlled, coarse (minimum 15
minutes), charging-only and battery-not-low. Android may delay jobs. Scheduling is
not persisted across reboot; launching the app restores an enabled preference.
There is no polling loop, boot receiver or resident language model. Maintenance
normalizes affect and reconciles at most five memory entries per pass with a durable
cursor. It preserves raw evidence. The service is protected by
`android.permission.BIND_JOB_SERVICE`; no new `uses-permission` is requested.

Governor limits include at most four live workload leases, ten-second envelopes,
and a budget of half measured available RAM including accounted model residency.
Unknown/stale/critical readings reject costly work; CPU/ABI are measured while
GPU/NPU support is unknown. Interfaces express budgets; an eventual native adapter
must enforce cooperative cancellation and prove actual peak memory/unload behavior.
A synchronous mock smoke-test callback is not a preemptive native timeout mechanism.

## Plug-and-play model direction

Mavyy uses a Samsung Galaxy S26+ and requests a separate model subsystem that adapts
owner-supplied models to both the system and phone, refusing excessive demand.
Local Laya Decisions Model support is planned. Android version/RAM and chosen model
families/voices/runtime details remain for the deeper owner model-phase discussion.

`ModelOrganManager` stores private files, rehashes before admission, selects among
compatible adapter profiles, and records successful smoke-test receipts. The
foundation certification predicate defaults to false. No production adapters are
registered and no real model files have been downloaded. GGUF/Safetensors bounded
header recognition is preliminary inspection, not proof of a compatible model
family. Current import limit is 4 GiB per candidate and 32 registered candidates.
A real installed-engine lifecycle, resumable imports and a model-picker UI remain
future work. Mock embeddings/vision/speech/affect engines are QA helpers only.
Phases 13–19 are not complete.

## Vault envelope and remaining limits

Vault uses AES-256-GCM with random salt/nonce, authenticated versioned header and
PBKDF2-HMAC-SHA256 (120,000 iterations). Passwords must be 12–256 characters. Export
and restore are bounded to 64 MiB and 20,000 total rows across major memory/evidence/
audit/checkpoint tables. Model weights and signing keys are excluded.

Restore authenticates a private candidate, checks exact known schema, SQLite
integrity/foreign keys, continuity, memory history and evidence digests, then
atomically replaces the closed database. Rejected restores preserve live bytes;
one prior local database backup is retained on successful restore. UI restore
turns off background scheduling. Store closure and serialization are caller
requirements. The durable database carries owner capability grants, which are
still filtered by actual executors/platform availability on the receiving device.

This is a bounded current-schema Vault candidate. It does not implement older-Vault
schema conversion, unbounded large-history export, model migration, whole-device
settings migration, or a physical second-phone proof. Free text from an eventual
language engine also needs semantic evaluation; evidence-reference validation
alone cannot prove every sentence is true. Affect is synthetic scaffolding, not
felt emotion or a learned reward model.

## Developer verification

With JDK 17 and Android SDK 35:

```sh
python scripts/check.py :foundation-contracts:check :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Result: BUILD SUCCESSFUL. 32 foundation tests + 49 Android/Robolectric tests,
zero failures, errors or skips. Foundation boundary verification passed. Debug
assembly passed and is unsigned without fixed-signing credentials. Lint: zero
errors, six warnings (`AndroidGradlePluginVersion`, `GradleDependency`,
`MissingApplicationIcon`, `NewerVersionAvailable`, `OldTargetApi`, `UsableSpace`).
No test result is an independent Mio PASS. No signing/workflow change is included.

## Phone and review checklist

1. Import the continuation overlay using the existing mobile bootstrap workflow;
   install the fixed-signed APK built for the expanded source commit. Upgrade the
   existing app with data preserved; do not uninstall or change its signing key.
2. Confirm existing identity and memories survive schema 4 → 8 upgrade. Launch,
   background/return and force-stop/relaunch; expect an owner console and explicit
   “no language model connected” status.
3. Save input, remember a fact, search, update it, restore the previous interpretation
   and relaunch. Verify the latest memory and preserved history.
4. Try local notes with the grant off, then enabled. Sleep blocks actions; wake
   restores availability. Run bounded maintenance and verify memory still survives.
5. Optionally enable charging-time maintenance. Check scheduling and disabling;
   Android controls timing, so immediate execution is not an acceptance requirement.
6. Export a Vault using a memorable password of at least 12 characters. Confirm a
   wrong-password restore is rejected without losing live memory. Restore the valid
   export and check identity/memory/settings. Store the export/password privately.
7. Independent review should challenge migration rollback, resource admission,
   concurrent lifecycle/Vault work, forged proposals, interruption handling and
   owner grant enforcement. Record real device/CI/review evidence separately.

## Delivery workaround

GitHub write access returned HTTP 403 in this environment. The reviewable overlay
`local-yuki-source.zip` uses the existing format-1 mobile importer, with no protected
workflow edits or signing secrets. Upload it at repository root and run
`Local Yuki mobile source import`. The expanded commit receives signed Android CI.
Full source, a patch, a Git bundle and local verification evidence accompany it.
