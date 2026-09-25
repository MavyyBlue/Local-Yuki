# Local Yuki — Roadmap

This roadmap tracks implementation status. The detailed requirements live in `docs/architecture/YUKI_LOCAL_IMPLEMENTATION_STRATEGY.md`.

Status legend: `NOT STARTED` / `ACTIVE` / `CANDIDATE` / `MIO PASS` / `PHONE ACCEPTED` / `CERTIFIED` / `BLOCKED`.

## Foundation — Model Freeze Gate CLOSED

| Phase | Scope | Status |
|---|---|---|
| 0 | Live repo reset / baseline lock | CERTIFIED |
| 1 | Core authority graph + subsystem interfaces | CERTIFIED |
| 2 | Identity + Personality Capsule + substrate boundary | CERTIFIED |
| 3 | Yuki State + Temporal Grounding | CERTIFIED |
| 4 | Memory authority + provenance completion | CERTIFIED |
| 5 | Living memory + semantic-recall socket | ACTIVE |
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

Phase 1A certified implementation: `ab89ee27d8d005f2febef30cc6d05148a72dea55`  
Phase 1A certified Android CI: Run #4 (`35945088085`)

Phase 1B certified implementation: `e4aa0ecd8910f70b14c06e9005b51a10a6521d22`  
Phase 1B certified Android CI: Run #6 (`36047827498`)

### Phase 2 bounded slices

| Slice | Scope | Status |
|---|---|---|
| 2A | Canonical identity, Personality Capsule, honesty, and substrate-safe self representation | CERTIFIED |
| 2B | Durable identity/personality persistence, schema/migrations, first-install bootstrap, process-death restoration, model-independent reconstruction | CERTIFIED |

Phase 2A certified implementation: `ea4491ae2bf9abce5bba34852d67bda720d8471c`  
Phase 2A certified Android CI: Run #8 (`36050741232`)

Phase 2B independently audited semantic implementation: `5d0699e3474b71ec245fc3b5365044395c855875`  
Phase 2B implementation CI: Run #10 (`36057542206`)  
Mio Phase 2B verdict: **PASS**

Accepted fixed-signing/build follow-up: `c7e24687c4609736d6d34550a830c02b85e92fbd`  
Accepted signed Android CI: Run #13 (`36076298126`) — PASS

Mavyy Phase 2B phone acceptance: **PASS**.

### Phase 3 certification

Certified implementation: `9d3cd57fec9e3c128b449152167ccdadca9f0e9d`  
Certified Android CI: Run #16 (`36087120725`) — PASS  
Mio Phase 3 verdict: **PASS**  
Mavyy Phase 3 phone acceptance: **PASS**

Phase 3 established physical schema `2`, Yuki State semantic version `1`, bounded current state, trusted interaction timestamps, deterministic expiry/conflicts, Temporal Grounding, DST-safe calendar windows, restart restoration, fixed signing, and no model/runtime coupling.

### Phase 4 certification

Certified implementation: `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`  
Certified Android CI: Run #22 (`36098219804`) — PASS  
Mio Phase 4 verdict: **PASS**  
Mavyy Phase 4 phone acceptance: **PASS**

Phase 4 establishes:

- physical continuity schema `3` via explicit migration `2026-09-24-memory-authority-v1`,
- Memory semantic version `1`,
- immutable thread ownership anchored to durable identity,
- immutable raw evidence with deterministic per-thread sequence,
- trusted Temporal Grounding timestamps and captured timezone,
- versioned SHA-256 evidence-integrity digests,
- factual and autobiographical durable memory,
- immutable revision chains with explicit current/historical state,
- evidence provenance for committed revisions,
- owner Update and append-only Restore semantics,
- immutable command/audit history,
- bounded derived conversation checkpoints,
- deterministic bounded exact/indexed retrieval,
- corrected keyset pagination for checkpoint history using `idx_checkpoint_thread_id`,
- restart restoration and fail-closed corruption isolation,
- read-only cognition-facing memory access and non-executable proposal boundaries,
- preservation of fixed signing and permission-free Android state.

The earlier Phase 4 candidate `68fdb66aa90462739b208e6c3eae4733d0347471` was not certified because Mio identified a checkpoint pagination/index mismatch. The final candidate `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21` added the matching `(thread_id, checkpoint_id)` index plus exact query-plan and no-skip/no-duplicate pagination coverage.

### Foundation certification gate

The Model Freeze Gate may open only after Phases 0–11 are implemented to the required maturity and certified.

Phase 5 is now active, but implementation requires a fresh Yuki architecture handoff.

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

Perform a fresh live-repository reset and design the bounded Phase 5 — Living memory and semantic-recall socket — architecture slice.

No candidate neural-model download or integration is authorized.
