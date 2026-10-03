# Local Yuki — Current State

Last synchronized: 2026-10-03. Strategy: brain-first, model-neutral.

## Evidence and baseline

The last independently **certified** baseline remains Phase 4, commit
`12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`, Android CI #22
(`36098219804`), Mio PASS and owner phone PASS. Its physical schema is 3.
See the [Phase 4 certification](HISTORY/2026-09-25_PHASE_4_YUKI_CERTIFICATION.md).

The imported Phase 5 source is `57c6d173155c39aa3cd92621db4748a80846a7ba`:
Android CI #26 (`37073792892`) succeeded on that exact commit. Mavyy reported
phone PASS in this conversation. Independent Phase 5 Mio review remains pending;
CI and phone acceptance alone do not constitute certification.

## Current continuation candidate

Mavyy authorized continued sequential implementation through later phases and
workarounds for environment limits. Phases 6–11 now have deterministic foundation
implementations, app persistence and behavioral tests. Early Phase 12 admission,
Phase 20 Vault and Phase 21/22 mock replacement/restore proofs are groundwork,
not completed neural integration or physical device migration.

The launcher is now an owner console: save input, remember/search/update/restore
memories, enable and execute local notes, sleep/wake, bounded maintenance,
optional charging-time maintenance, and password-encrypted export/restore.
It honestly reports that no language model is connected. No network permission,
model download, native inference runtime, notification/screen capture or microphone
integration has been added. The charging-time JobService requires Android's
system-only binding permission; the app requests no new runtime permissions.

Candidate physical SQLite schema: **8**. Existing continuity, state, memory and
living-memory semantic formats remain 1. Adjacent migrations preserve history:

| Upgrade | Migration |
|---|---|
| 1 → 2 | `2026-09-24-yuki-state-v1` |
| 2 → 3 | `2026-09-24-memory-authority-v1` |
| 3 → 4 | `2026-09-25-living-memory-v1` |
| 4 → 5 | `2026-10-02-affect-v1` |
| 5 → 6 | `2026-10-02-embodiment-v1` |
| 6 → 7 | `2026-10-02-recovery-scheduler-v1` |
| 7 → 8 | `2026-10-02-model-admission-v1` |

Developer validation: **32 foundation + 49 Android/Robolectric tests**, no failures
or skips; boundary verification, unsigned debug assembly and lint pass. Lint has
0 errors and 6 warnings (tool/dependency/target updates, application icon and disk
space heuristic). These are local results, not signed CI or independent QA.
Continuation exact-candidate signed CI, Mio review and phone acceptance are pending.

## Owner language clarification and surface refinement — 2026-10-03

The continuation overlay was imported as remote `80a02c9`; its tracked source
matches the delivered continuation. Android CI #27 (`37080120136`)
succeeded on that imported source. The new owner-surface candidate builds on it.
No phone PASS or independent certification is inferred for that imported candidate.

The owner clarified that cognitive subsystems form the brain, the phone is the
body, language expresses prepared meaning, and a later voice model speaks it.
The language socket now takes bounded prepared meaning rather than a full
thinking context; meaning composition precedes wording and actions use their own
executive path. North Star and Phase 17 descriptions have been corrected.

The owner console is simplified to a message area and grouped menus. Adaptive
safe-area handling excludes system bars/cutouts/keyboard, with scrolling and a
maximum readable width. Existing memory, note, rest and Vault controls remain
accessible. See the [language/surface handoff](2026-10-03_LANGUAGE_AND_OWNER_SURFACE_HANDOFF.md).
Developer follow-up checks pass: 32 foundation + 51 Android tests (83 total),
boundary verification, unsigned assembly and lint (zero errors). New exact-source
signed CI, layout phone acceptance and independent review remain
pending. No real language or voice runtime has been added.

## Model and phone direction

Owner device: **Samsung Galaxy S26+**. Android version and RAM are not yet confirmed;
the app probes actual RAM, battery, thermal status, ABI and CPU thread count.
GPU/NPU support stays unknown until an adapter can demonstrate it.

Owner models should be plug-and-play through a separate admission subsystem:
owner file → bounded inspection/hash → matching adapter → resource envelope →
smoke test/unload → receipt. Reject excessive demand, incompatible formats and
failed unloads. The local Laya Decisions Model is also planned. Real language,
voice, decision-engine adapters and model choices require the later in-depth
owner discussion. The current adapter registry is empty and the **Model Freeze
Gate remains CLOSED** pending foundation certification.

## Signing and delivery

Fixed debug/update signing is unchanged; production signing is not established.
Use the fixed-signed CI APK for upgrades, preserving app data. Local unsigned
assembly is developer evidence only. GitHub writes were blocked by HTTP 403 in
this environment; the existing mobile ZIP importer remains the delivery workaround.

Read the [active slice](ACTIVE_SLICE.md),
[continuation architecture](2026-10-02_CONTINUATION_ARCHITECTURE_HANDOFF.md) and
[implementation / phone checklist](2026-10-02_CONTINUATION_IMPLEMENTATION_HANDOFF.md).
