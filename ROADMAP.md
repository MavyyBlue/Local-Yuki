# Local Yuki — Roadmap

This roadmap tracks implementation status. The detailed requirements live in `docs/architecture/YUKI_LOCAL_IMPLEMENTATION_STRATEGY.md`.

Status legend: `NOT STARTED` / `ACTIVE` / `CANDIDATE` / `MIO PASS` / `PHONE ACCEPTED` / `CERTIFIED` / `BLOCKED`.

## Foundation — Model Freeze Gate CLOSED

| Phase | Scope | Status |
|---|---|---|
| 0 | Live repo reset / baseline lock | CERTIFIED |
| 1 | Core authority graph + subsystem interfaces | CERTIFIED |
| 2 | Identity + Personality Capsule + substrate boundary | ACTIVE |
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
| 1B | App-owned authority graph + model-neutral core contracts | CERTIFIED |

Phase 1A certified implementation:

`ab89ee27d8d005f2febef30cc6d05148a72dea55`

Phase 1A certified Android CI:

Run #4 (`35945088085`)

Phase 1B certified implementation:

`e4aa0ecd8910f70b14c06e9005b51a10a6521d22`

Phase 1B certified Android CI:

Run #6 (`36047827498`)

### Phase 2 bounded slices

| Slice | Scope | Status |
|---|---|---|
| 2A | Canonical identity, Personality Capsule, honesty, and substrate-safe self representation | CERTIFIED |
| 2B | Durable identity/personality persistence, schema/migrations, first-install bootstrap, process-death restoration, model-independent reconstruction | ACTIVE |

Phase 2A certified implementation:

`ea4491ae2bf9abce5bba34852d67bda720d8471c`

Phase 2A certified Android CI:

Run #8 (`36050741232`)

Phase 2A established the canonical app-owned Yuki/Mavyy identity anchors, mandatory honesty policy, versioned Personality Capsule, bounded cognition-facing identity projection, substrate-opacity guarantees, and high-level interoception contract while preserving the pure-JVM foundation boundary.

Mio independently audited Phase 2A and issued PASS.

Phase 2A introduced no device-visible or lifecycle behavior, so no additional phone acceptance was required.

Phase 2B is now active. It is responsible for turning the certified Phase 2A semantic identity/personality into durable app-owned continuity that survives process death and can be reconstructed without any neural engine.

### Foundation certification gate

The Model Freeze Gate may open only after Phases 0–11 are implemented to the required maturity and certified.

The existence of the Android shell, authority graph, and canonical identity does not authorize neural integration.

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

Execute the bounded Phase 2B persistence/reconstruction slice without weakening the certified Phase 1B authority graph or certified Phase 2A identity/personality/substrate boundaries.

No candidate neural-model download or integration is authorized.
