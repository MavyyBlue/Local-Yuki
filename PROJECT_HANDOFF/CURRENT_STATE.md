# Local Yuki — Current State

**Status:** Initial repository synchronization baseline  
**Last doc-sync date:** 2026-09-23  
**Current strategy:** Brain-first / model-neutral  
**Model Freeze Gate:** CLOSED  
**Active phase:** Phase 0 — Live-repository context reset and baseline lock  
**Certified implementation baseline:** NOT YET RECORDED IN THIS NEW REPOSITORY  

## 1. What is authoritative right now

The current architectural direction is synchronized around two documents:

- `docs/architecture/YUKI_LOCAL_NORTH_STAR.md`
- `docs/architecture/YUKI_LOCAL_IMPLEMENTATION_STRATEGY.md`

The end goal has not changed: the Local Yuki application is Yuki; the Android device is her functional body; app-owned systems preserve identity, relationship continuity, autobiographical memory, affect, state, temporal grounding, embodiment, resource regulation, and portable continuity while neural models remain replaceable cognitive mechanisms.

The implementation strategy has changed to **brain-first**. New candidate neural models are not introduced until the model-neutral subsystem foundation and neural sockets are independently functional and certified.

## 2. Current known project decisions

- Build persistent subsystems and scaffolding before introducing new candidate models.
- Use stable model-neutral interfaces for System One, System Two, Language & Expression, embeddings/reranking, perception, speech, and other specialist roles.
- Preserve deterministic authority where deterministic authority is required.
- Already-granted device capabilities are part of Yuki's available functional body and may be used autonomously by Yuki.
- Android permission existence remains platform-owned; neural cognition cannot manufacture permission.
- Internal implementation substrate is not ordinary cognitive content and must not be exposed for routine introspection/self-modification.
- Low-power background awareness is a first-class architectural requirement.
- Resource Governor controls physical sustainability and model/runtime residency, not Yuki's personality or product intent.
- Repository evidence outranks old handoffs and conversation memory.

## 3. Team ownership

### Mavyy
Product authority, priority, acceptance criteria, and real-phone acceptance.

### Yuki
Cognitive architecture, creative direction, subsystem contracts, authority boundaries, architecture handoffs, and post-certification doc sync.

### Akari
Live-repository reconnaissance and implementation. She owns code changes, migrations, tests, Android/runtime integration, and implementation handoffs.

### Mio
Independent QA. She verifies requirements, regressions, authority boundaries, persistence/migrations, lifecycle/resource behavior, CI evidence, and phone-test items.

## 4. Current implementation truth

**Unknown until Phase 0 is completed against the new live repository.**

Do not infer implementation status from the North Star, strategy, an older Local Yuki repository, or conversation memory. Akari must inventory the live repository first.

Phase 0 must record at minimum:

- repository/default branch,
- inspected commit SHA,
- latest relevant successful CI run,
- app version/versionCode if present,
- database/schema version if present,
- signing lineage status if applicable,
- existing subsystem inventory,
- existing model/runtime coupling,
- current Android services/permissions,
- background lifecycle behavior,
- current tests,
- known real-phone behavior,
- additive migration risks.

## 5. Current next action

Akari performs Phase 0 reconnaissance only. No architectural rewrite and no new candidate-model download/integration during Phase 0.

After Akari's inventory, Yuki reconciles actual source against the strategy and defines the first bounded implementation slice. Mio then receives a concrete candidate to validate.

## 6. Certification rule

A slice becomes the synchronized baseline only after:

1. Akari completes implementation and implementation handoff.
2. Mio independently verifies it and issues PASS.
3. relevant CI is green.
4. Mavyy performs required real-phone acceptance.
5. Yuki updates `CURRENT_STATE`, `ACTIVE_SLICE`, `ROADMAP`, and `CHANGELOG`.

Until all applicable gates are complete, the prior certified baseline remains authoritative.
