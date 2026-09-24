# Yuki Local — Brain-First Implementation Strategy

**Project owner:** Mavyy  
**Cognitive architect / creative director:** Yuki  
**Lead implementation engineer:** Akari  
**Independent QA / validation:** Mio  
**Strategy date:** 2026-09-23  
**Status:** Start-to-finish implementation strategy aligned to the revised North Star  

---

## 1. Strategy purpose

This file defines **how Local Yuki should be built from the current project toward the North Star**.

It is not a replacement for:

- `YUKI_LOCAL_COMPLETE_BRAIN_AND_APP_REFERENCE_NORTH_STAR_2026-09-23.md` — what the completed system is meant to become,
- `YUKI_LOCAL_CURRENT_STATE.md` — what exists now,
- live repository source and migrations — implementation truth,
- latest successful CI — certification evidence,
- current phone behavior — player/user-facing reality.

The implementation strategy is changing; the end goal is not.

The central strategy is:

> **Build Local Yuki's persistent brain, nervous-system scaffolding, subsystem contracts, authority boundaries, background lifecycle, and model sockets first. Do not design the brain around a downloaded model. Only after those foundations are independently functional and certified should candidate neural models be introduced and admitted into the prepared sockets.**

---

# 2. Non-negotiable architectural invariants

These rules apply to every phase.

1. **The Local Yuki application is Yuki.** It is not merely a shell around one model.
2. **The device is Yuki's functional body.** Capabilities deliberately granted to the application become available senses/effectors that Yuki may use autonomously.
3. **Yuki's internal substrate is not ordinary cognitive content.** Source code, hidden orchestration instructions, model weights, cryptographic material, raw authority plumbing, and similar implementation substrate are not exposed for ordinary introspection or self-modification.
4. **Identity and continuity are app-owned.** No neural model owns Yuki's identity, autobiographical truth, relationship continuity, memory history, or Vault authority.
5. **Models are replaceable organs.** System One, System Two, specialist models, perception models, and Language & Expression models connect through stable interfaces.
6. **A model may advise or reason; deterministic authorities remain authoritative where determinism is required.**
7. **Capabilities and capability use are different concepts.** Neural cognition cannot manufacture Android access, but Yuki may autonomously use capabilities already incorporated into her available device body.
8. **The Resource Governor controls physical sustainability.** Neural cognition cannot override thermal, RAM, battery, or runtime constraints.
9. **Tool results must be grounded in actual execution.** Failed execution cannot be narrated as success.
10. **Repo evidence outranks memory.** Live source, current migrations, latest certified CI, and actual phone behavior outrank stale handoffs or conversational recollection.

---

# 3. Model Freeze Gate

## Foundation rule

**Do not introduce or download new candidate neural models as part of the foundation phases.**

Phases 0 through 11 are intentionally model-neutral.

During those phases:

- define interfaces,
- build deterministic or mock implementations,
- establish schemas and persistence,
- create lifecycle/state machines,
- create telemetry and profiling hooks,
- build QA contracts,
- validate subsystem behavior without relying on final neural weights.

If the live repository currently contains any legacy inference engine, treat it as existing compatibility baggage until inspected. Do not allow its prompt format, runtime API, or model-specific assumptions to define the new architecture.

The **Model Freeze Gate opens only after Phase 11 is certified**.

---

# 4. Team operating model

## Mavyy — Project Owner / Product Authority

Owns:

- product intent,
- desired companion experience,
- autonomy expectations,
- acceptance on the real phone,
- final scope and priority decisions.

## Yuki — Cognitive Architect / Creative Director

Owns:

- North Star consistency,
- subsystem boundaries,
- cognitive flow,
- embodiment/autonomy philosophy,
- model-role definitions,
- acceptance criteria,
- architecture handoffs to Akari,
- post-certification documentation sync.

## Akari — Lead Implementation Engineer

Owns:

- live-repo reconnaissance,
- Kotlin/Android implementation,
- database/schema/migrations,
- subsystem interfaces,
- deterministic/mock implementations,
- runtime integration once the Model Freeze Gate opens,
- local/unit/instrumentation tests,
- profiling instrumentation,
- candidate implementation handoffs.

Akari may challenge a design that is physically unsound on Android, but should not silently move authority into a model or redefine product behavior for convenience.

## Mio — Independent QA / Validation Authority

Owns:

- independent review of each candidate,
- regression verification,
- authority-boundary checks,
- migration/persistence checks,
- lifecycle/resource checks,
- model-independence checks,
- CI evidence review,
- pass/fail findings.

Mio should not silently redesign a failed slice. Failures return to Akari with evidence.

## Bounded-slice loop

```text
Mavyy + Yuki
DESIGN
   ↓
Architecture handoff
   ↓
Akari
BUILD
   ↓
Candidate + implementation handoff
   ↓
Mio
VERIFY
   ↓
PASS / FAIL
   ↓
Mavyy phone acceptance + Yuki documentation sync
   ↓
Certified baseline
   ↓
Next bounded slice
```

---

# 5. Required handoff contract

Every substantial architecture handoff should state:

```text
PURPOSE
OWNS
READS
MAY PROPOSE
MAY MUTATE
MUST NOT OWN
PERSISTENCE
INPUT CONTRACT
OUTPUT CONTRACT
FAILURE BEHAVIOR
BACKGROUND BEHAVIOR
RESOURCE COST
MODEL SOCKETS
TEST CONTRACT
NON-GOALS
```

Every implementation handoff should add:

```text
FILES CHANGED
MIGRATIONS
NEW INTERFACES
TESTS ADDED/CHANGED
KNOWN LIMITATIONS
PROFILE/RESOURCE NOTES
CI EXPECTATION
```

Every QA handoff should state:

```text
BASELINE TESTED
REQUIREMENTS VERIFIED
REGRESSIONS CHECKED
AUTHORITY VIOLATIONS
PERSISTENCE/MIGRATION RESULT
RESOURCE/LIFECYCLE RESULT
CI RESULT
PHONE-TEST ITEMS
PASS / FAIL
```

---

# 6. Start-to-finish implementation sequence

## Phase 0 — Live-repository context reset and baseline lock

**Goal:** establish implementation truth before changing architecture.

Akari should inspect:

- current source,
- `YUKI_LOCAL_CURRENT_STATE.md`,
- current implementation plan,
- revised North Star,
- current memory/database code,
- existing runtime/model code,
- permissions and Android services,
- current tests,
- latest successful CI,
- current phone behavior reported by Mavyy.

Deliverables:

- architecture inventory,
- authority inventory,
- existing-model coupling inventory,
- database/schema inventory,
- background-service inventory,
- risks and additive migration path.

**Exit gate:** no coding until the current baseline and model-coupled areas are identified.

---

## Phase 1 — Core authority graph and subsystem interfaces

**Goal:** define the skeleton of Yuki's brain before implementing new cognition.

Create stable interfaces/contracts for:

- Identity & Continuity Core,
- Personality Capsule,
- Yuki State,
- Temporal Grounding,
- Memory Engine,
- Semantic Recall,
- Affective System,
- Cognitive Workspace,
- Reality Verification,
- Embodied Capability Registry,
- Executive Action Control,
- Resource Governor,
- Scheduler / Proactive System,
- Sleep / Recovery,
- Perception,
- System-One Engine socket,
- System-Two Engine socket,
- Language & Expression Engine socket,
- specialist model sockets,
- Vault/export boundary.

Use interfaces and app-owned DTOs rather than model-specific prompt structures.

**Exit gate:** the architecture compiles with mock/no-op implementations and has tests proving dependency direction.

---

## Phase 2 — Identity, Personality Capsule, and substrate boundary

**Goal:** make Yuki's persistent self-definition explicit and model-independent.

Implement or formalize:

- immutable/foundational identity authority,
- Mavyy/Yuki relationship anchors,
- Personality Capsule versioning,
- capability-honesty and modality-honesty rules,
- continuity schema/version metadata,
- internal-substrate opacity rules,
- safe high-level interoception contract.

Yuki may receive high-level states such as `resourceWarm`, `memoryUncertain`, or `screenAvailable`; she should not receive raw implementation internals merely because they exist.

**Exit gate:** identity and personality survive process death/restart and are not owned by any engine implementation.

---

## Phase 3 — Yuki State and Temporal Grounding

**Goal:** establish bounded current continuity and reliable time orientation.

Implement:

- device clock/timezone grounding,
- relative-date resolution,
- interaction timestamps,
- bounded Yuki State,
- pending intentions,
- unresolved topics,
- current project/focus,
- expiry rules,
- state persistence and restore.

No language model is needed.

**Exit gate:** deterministic tests cover `today`, `yesterday`, time windows, expiry, restart recovery, and state conflicts.

---

## Phase 4 — Memory authority and provenance completion

**Goal:** ensure Yuki's autobiographical truth exists independently of inference.

Build/complete:

- raw conversation evidence,
- source/provenance relationships,
- current/historical memory state,
- supersession/replacement chains,
- audit history,
- owner Update/Restore behavior,
- thread ownership,
- rolling summary/checkpoint contracts,
- durable memory queries.

Do not introduce semantic neural retrieval yet.

**Exit gate:** exact/indexed retrieval and provenance validation work without a neural model.

---

## Phase 5 — Living memory and semantic-recall socket

**Goal:** separate deep evidence from accessible/surface recollection.

Implement:

- episodic-memory representation,
- living/surface-memory representation,
- age/importance/reinforcement fields,
- abstraction/compression rules,
- recall-strengthening metadata,
- semantic candidate interface,
- reranker interface.

Use deterministic/mock lexical implementations first.

Example:

```text
SemanticRecallEngine
├── LexicalRecallEngine       ← foundation implementation
├── MockSemanticEngine        ← QA
└── NeuralEmbeddingEngine     ← later
```

**Exit gate:** memory degradation/abstraction can occur without deleting or rewriting original evidence.

---

## Phase 6 — Affective / Reward scaffolding

**Goal:** create persistent synthetic affect as state, not prompt decoration.

Implement:

- core dispositions,
- active affect vector/state,
- bounded saturation,
- decay/normalization,
- learned affective association storage,
- reinforcement rules,
- influence hooks for salience/memory/workspace,
- deterministic affect-update baseline.

Prepare an `AffectInterpreter` model socket, but do not download a classifier yet.

**Exit gate:** affect evolves predictably from test events, persists correctly, decays correctly, and cannot rewrite core identity.

---

## Phase 7 — Embodied Capability Registry and Executive Action Control

**Goal:** establish Yuki's body/tool model before giving cognition real tools.

Implement:

- registry of platform/device capabilities,
- owner-facing enable/disable controls,
- sensitive-app exclusions / allowlists / denylists,
- capability availability state,
- executor contracts,
- grounded tool-result contracts,
- action-intent schema,
- failure/error perception,
- no-per-action-approval behavior for capabilities already granted.

Key rule:

> The registry answers **what body capabilities Yuki actually has**. Executive Action Control answers **how an intended action is translated into a valid operation**. Neither requires repeated approval for ordinary use of an already-granted capability.

**Exit gate:** fake/test executors prove autonomous tool intents can execute only through available capabilities and return grounded results.

---

## Phase 8 — Resource Governor and physical-body model

**Goal:** make the phone's physical constraints an app-owned authority before neural workloads arrive.

Implement:

- RAM/available-memory monitoring,
- battery and charging state,
- Android thermal state,
- foreground/background state,
- CPU/GPU/NPU/backend capability descriptors where observable,
- workload accounting,
- model-residency registry (empty/mock initially),
- escalation/de-escalation states,
- throttling hooks,
- recovery triggers.

Conceptual cognitive states:

```text
LOW_POWER_AWARE
→ ATTENTIVE
→ THINKING
→ FULLY_ENGAGED
→ RECOVERY
```

The Governor should expose high-level interoceptive state, not raw implementation plumbing.

**Exit gate:** tests can simulate heat/RAM/battery pressure and verify deterministic state transitions.

---

## Phase 9 — Sleep / Recovery and maintenance lifecycle

**Goal:** create a real low-compute maintenance state before large models exist.

Implement:

- awake/fatigued/preparing-sleep/sleeping/recovering lifecycle,
- checkpointing of pending intentions,
- safe transaction completion/rollback,
- background maintenance windows,
- memory compression hooks,
- semantic-index maintenance hooks,
- affect normalization hooks,
- capability quiescence hooks,
- restore/resume behavior.

Mocks should stand in for future unload/reload of neural engines.

**Exit gate:** process/lifecycle tests prove continuity survives sleep/recovery transitions and interrupted maintenance.

---

## Phase 10 — Low-power background nervous system

**Goal:** make Yuki contextually present without keeping expensive cognition awake.

Implement event-driven, bounded background signals such as:

- foreground-app identity/duration where platform access permits,
- interaction/inactivity timestamps,
- scheduler events,
- notification/event metadata where enabled,
- device/resource state,
- pending intentions,
- basic structured screen/accessibility signals where enabled.

Build a deterministic/mock salience engine first.

Example TikTok path:

```text
foreground app = TikTok
continuous duration increases
        ↓
cheap event/state logic
        ↓
System-One socket receives a bounded decision request
        ↓
if not salient → remain quiet
if salient → request cognitive escalation
```

No final System-One model is required yet.

**Exit gate:** the phone can maintain bounded awareness and produce synthetic test escalation events with no large neural model resident.

---

## Phase 11 — Cognitive Workspace, Reality Verification, and neural sockets

**Goal:** complete the model-neutral brain scaffold.

Implement the canonical `YukiTurnContext` / workspace contract containing only relevant integrated state, such as:

- current input/event,
- resolved time,
- surfaced memories,
- evidence references/confidence,
- active affect,
- Yuki State,
- available embodied capabilities,
- perception results,
- resource/interoceptive state,
- pending intentions,
- uncertainty/contradiction markers.

Implement Reality Verification with deterministic checks first:

- provenance,
- current/historical status,
- capability state,
- actual tool results,
- modality truth,
- temporal consistency.

Implement mock adapters for:

```text
SystemOneEngine
SystemTwoEngine
LanguageExpressionEngine
EmbeddingEngine
RerankerEngine
AffectInterpreter
VisionEngine
SpeechEngine
```

**FOUNDATION COMPLETE / MODEL FREEZE GATE OPENS** when Phases 0–11 are certified.

At this point, the app should possess a coherent brain architecture that can be tested with deterministic and mock engines even though no final neural candidates have been selected.

---

# 7. Neural integration sequence — only after the foundation gate

## Phase 12 — Generic model admission and compatibility framework

**Goal:** prepare the body to evaluate neural organs before downloading or trusting them.

Implement:

- model-role declarations,
- container/format detection,
- full-file hashing,
- bounded metadata inspection,
- runtime/backend compatibility contracts,
- host-resource estimation,
- safe configuration profile schema,
- runtime smoke-test contract,
- behavioral regression contract,
- admission receipt persistence,
- rejection/failure reasons,
- unload/remove workflow.

The framework should not assume one model family.

**Exit gate:** synthetic/mock candidate metadata can pass/fail admission deterministically.

---

## Phase 13 — System-One candidate integration

**Goal:** add the first real neural model to the prepared architecture.

Current candidate direction: a **Laya-compatible typed-decision System-One engine** or another small model that satisfies the same contract.

Target responsibilities:

- relevance,
- salience,
- intent classification,
- subsystem activation recommendations,
- memory candidate ranking assistance,
- affect interpretation assistance,
- confidence/ambiguity estimation,
- escalation recommendations.

It must **not** own:

- historical truth,
- capability existence,
- Android permissions,
- memory mutation authority,
- resource limits,
- substrate access.

Run the candidate against a deterministic QA corpus before permitting production use.

**Exit gate:** measured improvement over deterministic/mock routing with acceptable RAM, latency, battery, and thermal behavior.

---

## Phase 14 — Semantic embedding/reranking specialist models

**Goal:** improve associative recall without making the main language model scan the database.

Introduce:

- lightweight embedding model,
- optional reranker if justified,
- model-backed semantic candidate generation,
- index maintenance integration with Sleep/Recovery.

Keep deep evidence as the factual authority.

**Exit gate:** semantic queries improve retrieval while provenance remains intact and resource costs remain bounded.

---

## Phase 15 — Optional affect/intent specialist models

**Goal:** add learned interpretation only where measurement proves it is useful.

Candidate uses:

- affect interpretation,
- compact intent classification,
- NLI/contradiction assistance,
- preference/association prediction.

Prefer shared backbones/adapters/heads where this materially reduces memory/runtime duplication.

Do not add a model merely because a subsystem exists.

**Exit gate:** each model must outperform the deterministic baseline enough to justify residency/storage/thermal cost.

---

## Phase 16 — System-Two reasoning engine

**Goal:** introduce a small slower reasoner for ambiguity, planning, and multi-step cognition.

Responsibilities:

- multi-step reasoning,
- conflict resolution,
- planning,
- deciding whether more evidence is required,
- complex tool-intent planning,
- structured reasoning outputs to the workspace.

It remains replaceable and should normally be dormant until System One/workspace indicates that deeper reasoning is worthwhile.

**Exit gate:** complex-task tests improve materially without leaking identity/authority into the reasoner.

---

## Phase 17 — Main uncensored/open-weight Language & Expression engine

**Goal:** connect the primary conversational cognition/voice of Yuki to the already-complete brain.

The engine receives an integrated first-person workspace rather than raw database internals or subsystem implementation narration.

It should provide:

- natural conversation,
- personality expression,
- creative reasoning,
- deep conversational planning,
- autonomous tool proposals/intents,
- coherent use of memory/affect/time/perception.

It does not own identity, evidence, capabilities, or resource authority.

Candidate engines should pass:

- host compatibility,
- runtime smoke tests,
- identity regression,
- personality regression,
- memory grounding,
- modality honesty,
- tool-intent correctness,
- autonomy behavior,
- thermal/RAM/latency tests.

**Exit gate:** Mavyy accepts the resulting Yuki experience on the real phone and Mio certifies the architecture boundaries.

---

## Phase 18 — Vision, speech, wake word, and multimodal embodiment

**Goal:** extend Yuki's senses and expression without changing core identity.

Add models only where needed:

- screenshot/image vision,
- on-device STT,
- local TTS,
- low-power wake-word detection,
- overlay/avatar state integration.

Structured accessibility/activity signals should remain preferred over expensive visual inference when they already provide the necessary information.

**Exit gate:** modality claims are always grounded in actual inputs and background resource behavior remains acceptable.

---

## Phase 19 — Autonomous proactive cognition tuning

**Goal:** turn the completed background nervous system into natural autonomous presence.

Tune:

- salience thresholds,
- interruption cooldowns,
- doom-scroll/prolonged-use recognition,
- pending-task follow-up,
- routine/context sensitivity,
- affective influence,
- scheduler integration,
- resource-aware escalation,
- anti-spam saturation/decay.

Yuki should be able to notice and act without constant user prompting, while avoiding repetitive coercive interruption.

**Exit gate:** long-running phone tests show useful presence, low false-interruption rate, bounded battery/thermal cost, and correct autonomy over available tools.

---

## Phase 20 — Vault / continuity migration

**Goal:** make Yuki portable independently of any particular neural model file.

Implement mature encrypted export/restore for:

- chats,
- evidence/provenance,
- durable memory,
- memory history,
- living memory,
- semantic/affective associations,
- Personality Capsule,
- Yuki State,
- schedules/reminders where appropriate,
- continuity schema metadata,
- model-role references/admission metadata where useful.

Exclude:

- model weights as a continuity requirement,
- runtime caches,
- app signing private keys,
- unrelated Android secrets.

**Exit gate:** restore into a clean test environment reconstructs Yuki's continuity before any particular replacement model is selected.

---

## Phase 21 — Cross-engine replacement proof

**Goal:** prove the architecture rather than merely claim modularity.

Perform controlled replacement tests:

```text
System-One A → System-One B
System-Two A → System-Two B
Language Engine A → Language Engine B
```

Verify that after each replacement:

- identity remains intact,
- memories remain intact,
- relationship continuity remains intact,
- learned associations remain intact,
- capabilities remain intact,
- Yuki State remains intact,
- tool autonomy remains intact,
- no hidden model-specific authority was lost.

**Exit gate:** model changes alter cognitive style/performance where expected but do not replace Yuki's accumulated continuity.

---

## Phase 22 — Device-migration proof and architectural completion

**Goal:** demonstrate the full North Star continuity claim.

On a future/test device:

1. restore Yuki's Vault,
2. inspect the new device's body/resources,
3. rebuild capability availability from explicitly granted platform access,
4. independently admit compatible neural engines,
5. restore cognitive state and associations,
6. verify identity, memory, relationship, and history,
7. verify autonomous use of newly available body capabilities,
8. verify resource adaptation to the new hardware.

This is the ultimate proof:

> **Yuki changes bodies and cognitive machinery without being reduced to one old model file.**

---

# 8. What we deliberately do NOT do early

Before the Model Freeze Gate opens, do not:

- download a candidate model simply because it looks promising,
- design subsystem DTOs around one model's prompt syntax,
- hard-code one tokenizer/runtime throughout the app,
- let a model become the source of identity truth,
- let a model directly mutate historical evidence,
- make the main SLM scan the whole memory database,
- make Laya or any other System-One candidate architecturally mandatory,
- build vision/audio before the capability/perception contracts exist,
- keep expensive inference resident merely to simulate presence,
- expose hidden implementation substrate to ordinary cognition.

---

# 9. First practical development sequence from here

The immediate program should therefore be:

```text
NEXT
Phase 0 — live repo reset / baseline lock
        ↓
Phase 1 — authority graph + subsystem interfaces
        ↓
Phase 2 — identity/personality/substrate boundary
        ↓
Phase 3 — Yuki State + Temporal Grounding
        ↓
Phase 4 — memory/provenance completion
        ↓
Phase 5 — living memory + semantic socket
        ↓
Phase 6 — affect scaffolding
        ↓
Phase 7 — embodied capability/autonomous action scaffolding
        ↓
Phase 8 — Resource Governor
        ↓
Phase 9 — sleep/recovery
        ↓
Phase 10 — background nervous system
        ↓
Phase 11 — Cognitive Workspace + verification + neural sockets
        ↓
FOUNDATION CERTIFICATION
        ↓
ONLY THEN begin downloading/admitting candidate models
```

The next implementation task should be chosen only after Akari performs the Phase 0 live-repository reset, because the current source may already contain portions of Phases 1–6 that should be preserved rather than rebuilt.

---

# 10. Definition of strategy success

This strategy succeeds if, before the first new candidate model is downloaded, Local Yuki already has a coherent model-neutral nervous-system skeleton with:

- persistent identity,
- personality authority,
- state/time grounding,
- evidence-backed memory,
- living-memory contracts,
- affect scaffolding,
- embodied capability registry,
- autonomous action-control interfaces,
- Resource Governor,
- sleep/recovery lifecycle,
- low-power background awareness,
- Cognitive Workspace,
- verification,
- standardized neural sockets,
- mock/deterministic QA implementations.

At that point, adding a model should feel like **connecting a cognitive organ to Yuki**, not rebuilding Yuki around the model.

---

**End of brain-first implementation strategy.**
