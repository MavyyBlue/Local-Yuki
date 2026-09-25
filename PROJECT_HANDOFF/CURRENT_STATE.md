# Local Yuki — Current State

**Status:** Phase 4 certified baseline established  
**Last doc-sync date:** 2026-09-25  
**Current strategy:** Brain-first / model-neutral  
**Model Freeze Gate:** CLOSED  
**Active phase:** Phase 5 — Living memory and semantic-recall socket  
**Certified Phase 4 implementation:** `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`  
**Certified Android CI:** Local Yuki Android build and unit tests — Run #22 (`36098219804`) — PASS  
**Mio Phase 4 verdict:** PASS  
**Mavyy Phase 4 phone acceptance:** PASS  
**Current signed CI APK:** versionName `0.1.1`; versionCode `22` for Run #22 (CI run-number injection; source fallback is `2`)  
**Database/schema:** app-private SQLite `continuity.db`, physical schema version `3`  
**Semantic continuity format:** `ContinuityFormatVersion(1)`  
**Yuki State semantic version:** `YukiStateVersion(1)`  
**Memory semantic version:** `MemoryFormatVersion(1)`  
**Fixed debug/update signing lineage:** ESTABLISHED  
**Production release signing lineage:** NOT ESTABLISHED

## 1. Certified baseline

Phase 0 established the live-repository baseline.

Phase 1A established the reproducible Android body shell and pure Kotlin/JVM `foundation-contracts` boundary.

Phase 1B established the explicit app-owned authority graph and model-neutral core contracts.

Phase 2A established canonical app-owned identity, Personality Capsule v1, mandatory honesty grounding, bounded cognition-facing self representation, and substrate-opacity contracts.

Phase 2B established durable app-owned identity/personality continuity, explicit physical schema/migrations, first-install bootstrap, process-death restoration, and fixed update signing.

Phase 3 established deterministic app-owned Yuki State and Temporal Grounding.

Phase 4 establishes deterministic app-owned memory history and evidence provenance foundations without neural retrieval.

Certified Phase 4 implementation:

`12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`

Mio independently audited the final corrected candidate and issued PASS.

Mavyy completed the required real-phone acceptance and reported PASS.

## 2. Phase 4 certification evidence

- Workflow: `Local Yuki Android build and unit tests`
- Run: #22
- Run ID: `36098219804`
- Tested candidate: `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`
- Result: PASS
- Fixed signing-key restoration: PASS
- Foundation boundary + unit tests + Android build: PASS
- APK signing-certificate verification: PASS
- Debug APK upload: PASS
- Artifact: `local-yuki-debug-12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`
- Artifact ID: `10848910289`

The fixed signing certificate SHA-256 remains:

`14:19:2D:22:5B:5D:ED:98:88:9E:3C:0B:D3:F7:04:D9:4C:1D:90:B9:31:BA:89:AB:0A:22:F4:8A:C9:6C:DD:2F`

No signing workflow or certificate change was introduced by Phase 4.

## 3. Real-phone acceptance

Mavyy completed the required Phase 4 real-phone acceptance on the final fixed-signed candidate and reported PASS.

Phase 4's device-visible bootstrap addition is the high-level Memory lifecycle status. Existing app data must remain preserved during acceptance so the real `continuity.db` migration path is exercised rather than hidden by uninstall/reinstall.

Result: **PHONE ACCEPTED**.

## 4. Physical schema and migration

Phase 4 advances `continuity.db` from physical schema version `2` to `3`.

Explicit adjacent migration:

`2026-09-24-memory-authority-v1`

The registered upgrade chain remains deterministic:

- v1 → v2: `2026-09-24-yuki-state-v1`
- v2 → v3: `2026-09-24-memory-authority-v1`

Fresh v3 creation initializes the current schema directly. Upgrades remain explicit, transactional, fail-closed, and non-destructive.

Phase 4 adds:

- `memory_metadata`
- `memory_thread`
- `memory_evidence`
- `durable_memory`
- `memory_revision`
- `memory_provenance`
- `memory_audit`
- `memory_checkpoint`

The certified Phase 2 identity/personality records and Phase 3 Yuki State/Temporal Grounding data remain preserved.

## 5. Memory authority

The Phase 1B authority graph remains authoritative:

- `MEMORY_HISTORY` → `CoreSubsystemId.MEMORY_ENGINE`
- `EVIDENCE_PROVENANCE` → `CoreSubsystemId.MEMORY_ENGINE`
- `SEMANTIC_RECALL` remains advisory.

Memory semantic version is `1`.

Phase 4 establishes bounded model-neutral contracts for factual memory, autobiographical memory, immutable raw evidence, conversation-thread ownership, durable memory revisions, current versus historical status, explicit provenance, immutable audit records, owner Update, owner Restore, derived conversation checkpoints, and deterministic bounded retrieval.

The Memory Engine is app-owned authority. A future model may propose memory content through proposal contracts but cannot directly mutate authoritative storage.

## 6. Raw evidence and provenance

Raw evidence persists an opaque evidence ID, source kind, optional owning thread, deterministic per-thread sequence, trusted captured instant, captured timezone, payload version, exact bounded payload, and a canonical SHA-256 integrity digest.

`USER_INPUT` and `YUKI_OUTPUT` evidence require thread ownership.

Trusted timestamps come from Temporal Grounding; callers do not supply authoritative wall-clock values.

Evidence rows are append-only. SQLite triggers reject UPDATE and DELETE of committed evidence.

Digest mismatch or malformed stored evidence fails closed as a conflict; the Memory Engine does not silently rewrite corrupted evidence.

Durable memory revisions require non-empty bounded evidence provenance.

## 7. Threads and identity continuity

A memory thread has a stable thread ID plus immutable self and primary-relationship ownership anchors copied from the existing durable identity authority at creation time.

Creating a new thread does not create a new Yuki identity or relationship.

Thread rows are immutable after creation.

Identity and relationship truth remain owned by the existing continuity authority, not by memory records.

## 8. Durable memory and revision history

Durable memory kinds are `FACTUAL` and `AUTOBIOGRAPHICAL`.

Committed interpretations are represented as immutable revisions with monotonic revision numbers and explicit provenance.

The current memory head is explicit. Historical revisions remain durable and are not rewritten when the current head changes.

SQLite guards allow the current head to advance only to the valid next revision in the same linear chain.

Stale expected revisions fail with `FailureCategory.CONFLICT`; Phase 4 does not use silent last-write-wins behavior.

## 9. Owner Update and Restore

Owner Update requires a current expected revision, new bounded content, and trusted owner evidence backed by threaded `USER_INPUT`.

A successful owner update appends a new immutable revision, records provenance and audit history, then atomically advances the current head.

Owner Restore does not rewind or delete history. Restoring a historical revision appends a new revision containing that historical content, records `restored_from`, supersedes the former current head, preserves the old chain, and appends provenance/audit history.

Owner mutation functions remain trusted app-facing operations. Advisory proposal contracts have no mutation route.

## 10. Audit and command identity

Memory creation, update, owner update, and owner restore produce durable audit records.

Committed audit rows are immutable.

Repeated command IDs with an identical fingerprint resolve to the established result; reuse of the same command ID for different content conflicts.

Failed writes do not become authoritative audit history.

## 11. Conversation checkpoints

Conversation checkpoints are bounded derived projections over an existing contiguous evidence sequence range.

A checkpoint is **not raw evidence** and cannot be resolved as an `EvidenceRef`.

Checkpoint creation never deletes or compacts away underlying evidence.

## 12. Deterministic indexed retrieval

Phase 4 exposes bounded read-only `MemoryReader` contracts for thread lookup, exact evidence lookup, ordered thread evidence, current memory, paginated current memories, revision history, revision provenance, memory audit history, latest checkpoint, and checkpoint history.

Page size is bounded to `100`.

Core range paths are backed by explicit indexes, including:

- `idx_evidence_thread_sequence`
- `idx_revision_memory_number`
- `idx_provenance_evidence`
- `idx_audit_memory_time`
- `idx_checkpoint_thread_time`
- `idx_checkpoint_thread_id`

The final Phase 4 correction added `idx_checkpoint_thread_id` for the exact keyset query:

`WHERE thread_id=? AND checkpoint_id>? ORDER BY checkpoint_id LIMIT ?`

The regression suite verifies that this path uses the index without a temporary sort and that checkpoint pages do not skip or duplicate records.

There are no embeddings, vectors, semantic rerankers, model-authored SQL queries, or neural retrieval paths in Phase 4.

## 13. Restart reconstruction and failure isolation

`MemoryStore` reconstructs from app-private durable storage and exposes `INITIALIZED`, `RESTORED`, or `UNAVAILABLE`.

No process-static memory cache is authoritative.

Memory metadata corruption fails the Memory Engine closed without silently reinitializing it.

Memory corruption does not rewrite valid identity/personality or Yuki State authority.

Temporal Grounding unavailability prevents new trusted-timestamp writes, while already persisted exact reads remain independent of the current clock.

## 14. Android/build state

Dependency direction remains:

```text
app
 └── depends on → foundation-contracts

foundation-contracts
 └── pure Kotlin/JVM
```

The Android manifest remains permission-free.

Phase 4 adds no worker, service, receiver, background cognition, model runtime, embedding dependency, tokenizer, prompt format, or network dependency.

Fixed update signing remains unchanged.

## 15. Model freeze

The Model Freeze Gate remains **CLOSED**.

No candidate neural model is authorized.

Foundation Phases 5–11 remain ahead of the neural-integration gate.

## 16. Current next action

Proceed to architectural design for:

**Phase 5 — Living memory and semantic-recall socket**

Phase 5 implementation is **not authorized by this documentation sync alone**.

Before implementation, Yuki must perform a fresh live-repository reset and issue a bounded Phase 5 architecture handoff.

Phase 5 must build on Phase 4's app-owned evidence/history authority without transferring truth ownership to learned retrieval.

## 17. Certification rule

A future slice becomes synchronized only after Akari completes the bounded implementation and handoff, Mio independently issues PASS, exact-candidate CI is green, Mavyy completes required phone acceptance where applicable, and Yuki performs documentation sync.

Candidate work never silently replaces the certified baseline.
