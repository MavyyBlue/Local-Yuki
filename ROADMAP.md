# Local Yuki — Roadmap

This roadmap tracks implementation status. The detailed requirements live in `docs/architecture/YUKI_LOCAL_IMPLEMENTATION_STRATEGY.md`.

Status legend: `NOT STARTED` / `ACTIVE` / `CANDIDATE` / `MIO PASS` / `PHONE ACCEPTED` / `CERTIFIED` / `BLOCKED`.

## Foundation — Model Freeze Gate CLOSED

| Phase | Scope | Status |
|---|---|---|
| 0 | Live repo reset / baseline lock | CERTIFIED |
| 1 | Core authority graph + subsystem interfaces | ACTIVE |
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

### Phase 1 bounded slices

| Slice | Scope | Status |
|---|---|---|
| 1A | Android body shell, foundation boundary, build certification | CERTIFIED |
| 1B | App-owned authority graph + model-neutral core contracts | ACTIVE |

Phase 1A certified baseline:

`ab89ee27d8d005f2febef30cc6d05148a72dea55`

Certified Android CI:

Run #4 (`35945088085`)

The Phase 1A baseline provides a reproducible Android shell, pure Kotlin/JVM `foundation-contracts` module, one-way `app → foundation-contracts` dependency, boundary verification, unit-test/build CI, and a debug APK accepted on the real phone.

Phase 1B begins explicit authority contracts. It must not introduce persistence, Android coupling inside the foundation module, background systems, or real neural runtimes.

### Foundation certification gate

The Model Freeze Gate may open only after Phases 0–11 are implemented to the required maturity and certified.

The existence of the Android shell does not authorize neural integration.

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

Complete and independently certify Phase 1B without weakening the Phase 1A foundation boundary.

No candidate neural-model download or integration is authorized.
