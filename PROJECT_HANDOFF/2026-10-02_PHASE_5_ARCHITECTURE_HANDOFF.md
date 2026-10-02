# Local Yuki — Phase 5 completion architecture handoff

**Date:** 2026-10-02
**Baseline inspected:** `59eb7f8` (live source overlay)
**Certified baseline:** Phase 4 `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`
**Authorization:** Mavyy's current request to complete as much of Local Yuki as possible. This document bounds the first implementation slice; it does not certify the existing overlay.

## PURPOSE / BASELINE

Finish and verify the already-present Phase 5 candidate before expanding the foundation. The live overlay already contains schema 4, living-memory stores, lexical recall and tests. The synchronization docs still describe certified schema 3. Preserve this distinction.

## OWNS

The trusted Memory Engine owns derived surface state, importance, reinforcement, meaningful-consumption events and index coverage. Deep memory remains the factual authority. Semantic Recall only recommends candidates and ordering.

## READS

Validated Phase 4 current memories, revision history and evidence; trusted Temporal Grounding; app-private derived metadata. Cognition receives read-only, model-neutral views.

## MAY PROPOSE

Model-neutral candidate hints and reranker permutations. Compression/checkpoint content remains non-executable proposals through existing proposal contracts.

## MAY MUTATE

Only living-memory tables through trusted application plumbing: bounded reconciliation, optimistic importance changes and idempotent meaningful recall. Search alone never reinforces memory. Surface terms are refreshed atomically with metadata changes.

## MUST NOT OWN

Identity, relationship anchors, evidence truth, owner Update/Restore, permissions, signing or resource authority. Neither retrieval nor abstraction may rewrite raw evidence, durable content, provenance or historical revisions.

## PERSISTENCE

Retain the additive schema 3 → 4 migration `2026-09-25-living-memory-v1`. Preserve old tables and migration history. Derived corruption fails closed without rewriting deep authority. No destructive repair or silent reinitialization.

## INPUT CONTRACT

Reconcile at most 50 memories per call using a keyset cursor. Queries contain at most 512 UTF-8 bytes, 12 normalized terms and a result limit of 1–20. Candidate identities, scores and terms are validated at the trust boundary. Mutation requires current source/state revisions; event IDs bind to one exact command.

## OUTPUT CONTRACT

Surface/episode views distinguish current and historical revisions. Grounded recall content and provenance come from Phase 4, never the hint or ranker. Coverage is partial for bounded/truncated searches and unavailable dependencies remain explicit. Invalid or unavailable advisory engines/rankers fall back to deterministic lexical ordering.

## FAILURE BEHAVIOR

Stale heads are omitted from lexical lookup; corrupted authoritative reads propagate failure. Missing clocks prevent new writes but not persisted reads or exact event replays. Stale optimistic mutations and conflicting event reuse fail atomically. Clock rollback does not move the last-meaningful-recall timestamp backward.

## BACKGROUND BEHAVIOR

No startup scan, worker, receiver, service, polling or resident model. Maintenance remains explicit and page-bounded. Bootstrap displays honest high-level readiness only.

## RESOURCE COST

Indexed lexical lookup bounded to 200 matches per term, bounded grounding, no weights/runtime/network dependency. Report partial coverage whenever the lookup bound hides potential matches.

## MODEL SOCKETS

`SemanticCandidateEngine` and `RecallReranker` remain pure JVM sockets with deterministic/mock adapters. The model freeze remains CLOSED.

## TEST CONTRACT

Run foundation checks, Android unit tests and unsigned debug assembly. Cover upgrade/rollback, restart, immutable evidence/history, age/importance/reinforcement transitions, partial coverage, stale hints, corruption, hostile/throwing adapters, long episode histories and idempotent recall under clock loss. Keep fixed signing configuration unchanged.

## NON-GOALS

Neural downloads/integration, conversational UI, autonomous tools, background cognition, Phase 6 implementation, production signing and certification of our own candidate.

## ACCEPTANCE GATE

Local tests/build provide developer evidence only. Independent Mio review, exact-candidate signed CI and Mavyy's upgrade/launch/restart phone check remain required for Phase 5 certification. Record pending checks plainly; do not replace the certified Phase 4 baseline.
