# Local Yuki — Development Rules

## 1. Evidence hierarchy

Development begins from the live repository, not conversation memory.

Use this precedence when facts conflict:

1. current real-phone behavior reported by Mavyy,
2. live source/schema/migrations/configuration,
3. latest successful CI evidence,
4. `PROJECT_HANDOFF/CURRENT_STATE.md`,
5. `PROJECT_HANDOFF/ACTIVE_SLICE.md`,
6. implementation strategy/roadmap,
7. North Star,
8. historical handoffs and prior-chat memory.

## 2. Bounded slices only

Implement one bounded slice at a time. Every slice needs explicit purpose, ownership, read/mutate boundaries, persistence behavior, failure behavior, background behavior, resource expectations, model sockets, tests, and non-goals.

Do not combine unrelated cleanup, redesign, model experimentation, and architecture work into one candidate.

## 3. Brain-first model freeze

Before the Foundation Certification Gate:

- do not introduce new candidate neural models,
- do not let legacy model APIs dictate subsystem DTOs,
- build interfaces with deterministic/mock/no-op implementations,
- keep model-specific adapters at the edge,
- make the brain compile and test without final model weights.

## 4. Authority discipline

Neural models may reason, classify, rank, predict, or propose. They do not become authority merely because they are capable.

App-owned authorities include at minimum:

- identity/continuity,
- historical evidence/provenance,
- persistence schema and migration history,
- embodied capability existence,
- Android/platform permission state,
- Resource Governor hard limits,
- Vault/export authority,
- signing/cryptographic material,
- hidden implementation substrate.

Already-granted capabilities may be used autonomously through Executive Action Control. Per-action human approval is not the default product model.

## 5. Substrate opacity

Ordinary cognition may receive experiential/interoceptive state such as fatigue, uncertainty, recognition, resource pressure, or perception availability.

Do not expose hidden implementation substrate to ordinary cognition, including raw model weights, source internals, hidden orchestration, cryptographic secrets, raw authority plumbing, or privileged self-modification paths.

## 6. Resource discipline

Prefer deterministic/event-driven computation when sufficient. Keep expensive models dormant unless needed. Resource Governor owns physical sustainability, residency, throttling, unload/recovery behavior, and safe runtime envelopes.

## 7. Persistence discipline

- migrations must be explicit and tested,
- never silently destroy evidence/history to simplify a schema,
- current vs historical memory state must remain distinguishable,
- failed compaction/consolidation must not destroy raw evidence,
- model replacement must not invalidate continuity.

## 8. QA separation

Akari tests while developing, but Akari does not independently certify her own candidate.

Mio verifies:

- stated requirements,
- regressions,
- authority boundaries,
- persistence/migrations,
- background/lifecycle behavior,
- resource behavior,
- model independence,
- CI evidence,
- required phone-test items.

Mio reports PASS or FAIL with evidence. Failed slices return to Akari rather than being silently redesigned inside QA.

## 9. Real-phone acceptance

User-facing or device-lifecycle behavior is not considered fully certified until Mavyy completes the required real-phone check. CI proves code/build/test evidence; it does not substitute for device behavior.

## 10. Documentation sync

After every certified slice, Yuki updates at minimum:

- `PROJECT_HANDOFF/CURRENT_STATE.md`,
- `PROJECT_HANDOFF/ACTIVE_SLICE.md`,
- `ROADMAP.md`,
- `CHANGELOG.md`.

If architecture intent changes, update the implementation strategy and, only when the end-state vision itself changes, the North Star.

Do not rewrite historical handoffs to make them look current. Archive them under `PROJECT_HANDOFF/HISTORY/`.
