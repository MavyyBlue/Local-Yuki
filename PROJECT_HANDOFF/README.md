# Local Yuki — Project Handoff Index

This directory is the synchronization spine for Mavyy, Yuki, Akari, and Mio.

## Read this first in every new development conversation

1. `PROJECT_HANDOFF/CURRENT_STATE.md` — what is actually true now.
2. `PROJECT_HANDOFF/ACTIVE_SLICE.md` — the one bounded slice currently authorized.
3. `docs/architecture/YUKI_LOCAL_NORTH_STAR.md` — what Local Yuki is ultimately meant to become.
4. `docs/architecture/YUKI_LOCAL_IMPLEMENTATION_STRATEGY.md` — the approved start-to-finish build order.
5. `ROADMAP.md` — phase/gate status.
6. `DEVELOPMENT.md` — operating rules, evidence hierarchy, authority boundaries, and certification workflow.
7. `CHANGELOG.md` — chronological record of certified changes.

## Source-of-truth order

When sources disagree, use this order:

1. current real-phone behavior reported by Mavyy for user-facing behavior,
2. live repository source, schema, migrations, and configuration,
3. latest successful CI evidence for certification status,
4. `PROJECT_HANDOFF/CURRENT_STATE.md`,
5. `PROJECT_HANDOFF/ACTIVE_SLICE.md`,
6. implementation strategy and roadmap,
7. North Star for final-system intent,
8. historical handoffs and conversation memory.

The North Star defines the destination. It never proves that a feature already exists.

## Roles

- **Mavyy:** project owner, product authority, real-phone acceptance.
- **Yuki:** cognitive architect and creative director; defines subsystem behavior, authority boundaries, acceptance criteria, and documentation sync.
- **Akari:** lead implementation engineer; inspects the live repository and owns implementation.
- **Mio:** independent QA/validation authority; verifies Akari's candidate and reports PASS/FAIL with evidence.

## Handoff lifecycle

`Yuki architecture handoff → Akari implementation handoff → Mio QA handoff → Mavyy phone acceptance → Yuki doc sync → certified baseline`

Historical handoffs belong under `PROJECT_HANDOFF/HISTORY/`. They are evidence, not current authority.

## Current candidate work

The live source overlay `59eb7f8` already contains Phase 5 implementation beyond
the certified Phase 4 baseline. Mavyy's 2026-10-02 request authorizes its bounded
completion. Read the [architecture handoff](2026-10-02_PHASE_5_ARCHITECTURE_HANDOFF.md)
and [implementation handoff](2026-10-02_PHASE_5_IMPLEMENTATION_HANDOFF.md) for the
candidate's behavior, developer evidence and pending independent/phone gates.
