# Local Yuki — Active Slice

**Slice:** Phase 1B — App-owned authority graph and model-neutral core contracts  
**Owner:** Akari  
**Architecture authority:** Mavyy + Yuki  
**QA authority:** Mio  
**Certified baseline:** `ab89ee27d8d005f2febef30cc6d05148a72dea55`  
**Certified Android CI:** Run #4 (`35945088085`)  
**Model Freeze Gate:** CLOSED  
**Implementation authorization:** Only from the bounded Phase 1B Yuki architecture handoff

## Purpose

Use the certified pure-Kotlin `foundation-contracts` boundary to define the first explicit authority graph for Local Yuki without implementing persistence, Android capability access, background behavior, or neural inference.

Phase 1B begins the model-neutral brain skeleton. It establishes who may own truth, who may read it, who may propose changes, and how unavailable/not-yet-implemented behavior is represented without fabrication.

## Required direction

The already-certified dependency direction remains:

```text
app
 ↓
foundation-contracts
```

`foundation-contracts` must remain independent of:

- Android framework APIs,
- persistence/database implementations,
- model runtimes,
- tokenizer/prompt formats,
- JNI/native inference,
- UI frameworks.

## Intended Phase 1B scope

The architecture handoff may authorize model-neutral contracts for:

- shared authority/read/propose/mutate semantics,
- model-independent evidence/provenance references,
- explicit availability/unavailability/failure results,
- foundational subsystem identity/role declarations,
- the first app-owned authority interfaces needed to express dependency direction,
- deterministic/mock/no-op implementations required to compile and test the graph.

This slice should define contracts, not full subsystem behavior.

## Must not implement yet

- database/schema/migrations,
- durable identity storage,
- Personality Capsule persistence,
- Yuki State persistence,
- Memory Engine behavior,
- semantic retrieval,
- affect learning,
- Android capabilities or permissions,
- tool execution,
- Resource Governor,
- sleep/recovery,
- background nervous system,
- Cognitive Workspace behavior,
- real neural engines or model downloads.

## Test expectations

Phase 1B must prove:

- authority ownership is explicit,
- proposal is distinguishable from mutation,
- model-facing/advisory contracts cannot become deterministic authority by interface design,
- contracts remain deterministic and platform/model neutral,
- dependency direction remains clean,
- unavailable/failure states cannot be mistaken for successful execution.

## Exit gate

Phase 1B exits only after:

1. Akari implements the bounded contract slice.
2. CI proves compilation/tests and foundation-boundary purity.
3. Mio independently verifies authority direction and model independence.
4. Mavyy performs phone acceptance only if device-visible behavior changes.
5. Yuki promotes the accepted state through documentation sync.

The Model Freeze Gate remains CLOSED.
