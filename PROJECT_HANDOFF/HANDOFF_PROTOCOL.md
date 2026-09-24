# Local Yuki — Handoff and Doc-Sync Protocol

## Purpose

Keep Mavyy, Yuki, Akari, and Mio synchronized without allowing stale conversation context to become architecture truth.

## Standard cycle

1. **Context reset** — actor reads the synchronization spine and inspects live evidence relevant to the task.
2. **Yuki architecture handoff** — defines the bounded slice and authority contract.
3. **Akari implementation handoff** — records exactly what changed and what remains uncertain.
4. **Mio QA handoff** — independently verifies candidate behavior and issues PASS/FAIL.
5. **Mavyy phone acceptance** — validates required behavior on the target device.
6. **Yuki doc sync** — promotes the accepted state into current authoritative documents.
7. **Archive** — slice-specific handoffs move to history; current docs point forward.

## Required architecture-handoff fields

- PURPOSE
- BASELINE
- OWNS
- READS
- MAY PROPOSE
- MAY MUTATE
- MUST NOT OWN
- PERSISTENCE
- INPUT CONTRACT
- OUTPUT CONTRACT
- FAILURE BEHAVIOR
- BACKGROUND BEHAVIOR
- RESOURCE COST
- MODEL SOCKETS
- TEST CONTRACT
- NON-GOALS
- ACCEPTANCE GATE

## Required Akari implementation-handoff fields

- BASELINE IMPLEMENTED AGAINST
- FILES CHANGED
- MIGRATIONS
- NEW/CHANGED INTERFACES
- BEHAVIOR IMPLEMENTED
- AUTHORITY BOUNDARIES PRESERVED
- TESTS ADDED/CHANGED
- TEST RESULTS
- RESOURCE/PROFILE NOTES
- MODEL COUPLING INTRODUCED OR REMOVED
- KNOWN LIMITATIONS
- CI EXPECTATION / CI RESULT WHEN AVAILABLE
- PHONE-TEST ITEMS

## Required Mio QA-handoff fields

- BASELINE TESTED
- CANDIDATE IDENTIFIER
- REQUIREMENTS VERIFIED
- REGRESSIONS CHECKED
- AUTHORITY-BOUNDARY RESULT
- PERSISTENCE/MIGRATION RESULT
- BACKGROUND/LIFECYCLE RESULT
- RESOURCE RESULT
- MODEL-INDEPENDENCE RESULT
- CI RESULT
- PHONE-TEST ITEMS
- FINDINGS
- VERDICT: PASS / FAIL

## Naming convention

Use date + phase/slice + role, for example:

- `2026-09-24_PHASE_0_YUKI_ARCHITECTURE_HANDOFF.md`
- `2026-09-24_PHASE_0_AKARI_RECON_HANDOFF.md`
- `2026-09-24_PHASE_1_AKARI_IMPLEMENTATION_HANDOFF.md`
- `2026-09-24_PHASE_1_MIO_QA_HANDOFF.md`

Store completed handoffs in `PROJECT_HANDOFF/HISTORY/` after the doc sync is complete.

## Promotion rule

A handoff never outranks `CURRENT_STATE.md` merely because it is newer. Candidate claims are promoted only after the relevant verification/acceptance gate.
