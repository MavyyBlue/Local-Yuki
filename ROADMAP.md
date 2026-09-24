# Local Yuki — Roadmap

This roadmap tracks implementation status. The detailed requirements live in `docs/architecture/YUKI_LOCAL_IMPLEMENTATION_STRATEGY.md`.

Status legend: `NOT STARTED` / `ACTIVE` / `CANDIDATE` / `MIO PASS` / `PHONE ACCEPTED` / `CERTIFIED` / `BLOCKED`.

## Foundation — Model Freeze Gate CLOSED

| Phase | Scope | Status |
|---|---|---|
| 0 | Live repo reset / baseline lock | ACTIVE |
| 1 | Core authority graph + subsystem interfaces | NOT STARTED |
| 2 | Identity + Personality Capsule + substrate boundary | NOT STARTED |
| 3 | Yuki State + Temporal Grounding | NOT STARTED |
| 4 | Memory authority + provenance completion | NOT STARTED |
| 5 | Living memory + semantic-recall socket | NOT STARTED |
| 6 | Affective / Reward scaffolding | NOT STARTED |
| 7 | Embodied Capability Registry + Executive Action Control | NOT STARTED |
| 8 | Resource Governor + physical-body model | NOT STARTED |
| 9 | Sleep / Recovery + maintenance lifecycle | NOT STARTED |
| 10 | Low-power background nervous system | NOT STARTED |
| 11 | Cognitive Workspace + Reality Verification + neural sockets | NOT STARTED |

### Foundation certification gate

The Model Freeze Gate may open only after Phases 0–11 are implemented to the required maturity and certified. Existing legacy model/runtime code may be inspected or preserved as compatibility baggage, but it must not define the new architecture.

## Neural integration — only after foundation certification

| Phase | Scope | Status |
|---|---|---|
| 12 | Generic model admission + compatibility framework | LOCKED |
| 13 | System-One candidate integration | LOCKED |
| 14 | Semantic embedding/reranking specialists | LOCKED |
| 15 | Optional affect/intent specialist models | LOCKED |
| 16 | System-Two reasoning engine | LOCKED |
| 17 | Main uncensored/open-weight Language & Expression engine | LOCKED |
| 18 | Vision, speech, wake word, multimodal embodiment | LOCKED |
| 19 | Autonomous proactive cognition tuning | LOCKED |
| 20 | Vault / continuity migration | LOCKED |
| 21 | Cross-engine replacement proof | LOCKED |
| 22 | Device-migration proof / architectural completion | LOCKED |

## Current next milestone

Complete Phase 0 and update `PROJECT_HANDOFF/CURRENT_STATE.md` with verified live-repository facts before selecting Phase 1 work.
