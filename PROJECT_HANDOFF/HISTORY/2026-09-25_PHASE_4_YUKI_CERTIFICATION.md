# Local Yuki — Phase 4 Yuki Certification

**Date:** 2026-09-25  
**Phase:** Phase 4 — Memory authority and provenance completion  
**Architecture authority:** Mavyy + Yuki  
**Implementation owner:** Akari  
**Independent QA authority:** Mio  
**Phone acceptance authority:** Mavyy  
**Model Freeze Gate:** CLOSED

## Final certified implementation

`12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`

This is the corrected expanded Phase 4 source commit.

The earlier candidate `68fdb66aa90462739b208e6c3eae4733d0347471` was not certified. Mio identified that `MemoryStore.checkpoints()` paginated using `WHERE thread_id=? AND checkpoint_id>? ORDER BY checkpoint_id LIMIT ?` while the schema only supplied the `(thread_id, created_at, checkpoint_id)` checkpoint index.

The final corrected implementation adds `idx_checkpoint_thread_id(thread_id, checkpoint_id)` and regression coverage that verifies the exact query plan, rejects a temporary B-tree sort, checks no skipped/duplicated checkpoint IDs, and preserves latest-checkpoint behavior.

## CI certification

Workflow: `Local Yuki Android build and unit tests`  
Run: #22  
Run ID: `36098219804`  
Exact tested expanded commit: `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`  
Result: **PASS**

Verified CI stages include expanded-main checkout, exact commit recording, JDK 17, Android SDK/build tools, fixed signing-key restoration, `:foundation-contracts:check`, `:app:testDebugUnitTest`, `:app:assembleDebug`, APK signing-certificate verification, and artifact upload.

Build result: `BUILD SUCCESSFUL`

Artifact: `local-yuki-debug-12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`  
Artifact ID: `10848910289`

Fixed signing certificate SHA-256:

`14:19:2D:22:5B:5D:ED:98:88:9E:3C:0B:D3:F7:04:D9:4C:1D:90:B9:31:BA:89:AB:0A:22:F4:8A:C9:6C:DD:2F`

The signing lineage is unchanged from the certified Phase 2B/3 baseline.

## Independent QA

Mio independently audited the final corrected Phase 4 candidate and issued **PASS**.

Certification therefore applies to `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`, not to the earlier `68fdb66...` candidate.

## Phone acceptance

Mavyy completed the required real-phone acceptance on the final fixed-signed Phase 4 APK and reported **PASS**.

## Certified architecture and behavior

Phase 4 completes the deterministic app-owned Memory Engine and evidence/provenance foundation required before any learned semantic recall.

Certified physical database schema: `continuity.db` version `3`.

Certified Memory semantic version: `MemoryFormatVersion(1)`.

Explicit adjacent migration: `2026-09-24-memory-authority-v1`.

The migration advances physical schema v2 → v3 while preserving prior identity/personality and Yuki State authority.

### Storage and authority

Certified storage includes `memory_metadata`, `memory_thread`, `memory_evidence`, `durable_memory`, `memory_revision`, `memory_provenance`, `memory_audit`, and `memory_checkpoint`.

The existing Phase 1B graph remains intact. The app-owned Memory Engine owns `MEMORY_HISTORY` and `EVIDENCE_PROVENANCE`. Semantic recall remains advisory.

Models do not own autobiographical truth, evidence history, provenance, current-head selection, timestamps, or owner restore semantics.

### Evidence truth

Raw evidence is immutable after commit. Threaded evidence receives deterministic increasing sequence numbers. Trusted evidence time comes from Temporal Grounding. Raw payloads are bounded and retained exactly.

A canonical versioned SHA-256 digest detects stored evidence corruption; the implementation explicitly treats this as corruption detection rather than adversarial protection.

Evidence mutation/deletion is blocked by SQLite triggers. Malformed or digest-conflicting evidence fails closed.

### Memory history and provenance

Durable memories are `FACTUAL` or `AUTOBIOGRAPHICAL`.

Interpretations are immutable revisions. Exactly one explicit current head is exposed per durable memory; older revisions remain historical.

Revisions form a linear supersession chain. Stale expected heads conflict. Database guards restrict current-head advancement to the valid next revision.

Every committed durable-memory revision requires bounded, non-empty evidence references. Provenance rows are immutable.

### Owner Update / Restore

Owner Update requires threaded `USER_INPUT` evidence and the expected current revision.

Owner Restore is append-only: it copies selected historical content into a new current revision, records `restored_from`, supersedes the former current head, retains prior history, and records audit/provenance.

Restore never rewinds the current pointer into the old chain and never deletes later history.

### Audit and checkpoints

Create, Update, Owner Update, and Owner Restore commands produce immutable audit rows.

Command fingerprints support deterministic identical-command replay and reject conflicting command-ID reuse.

Conversation checkpoints are derived projections over a real contiguous thread-evidence range. They are immutable, bounded, are not raw evidence, and cannot be resolved as `EvidenceRef`. Underlying raw evidence remains durable.

### Retrieval

The cognition-facing `MemoryReader` is read-only and bounded.

Certified read paths include thread lookup, exact evidence lookup, ordered thread evidence, exact current memory, paginated current memories, revision history, provenance, audit, latest checkpoint, and checkpoint pages.

Maximum page size is `100`.

Core ordered paths have matching indexes and query-plan regression coverage.

### Restart and corruption behavior

The Memory Engine exposes `INITIALIZED`, `RESTORED`, and `UNAVAILABLE`.

Memory reconstruction comes from durable SQLite state, not a process-static cache.

Memory metadata/evidence corruption fails closed. Memory failure does not rewrite valid identity/personality or Yuki State records.

New trusted-timestamp writes require Temporal Grounding, while existing exact reads do not require the current clock.

## Preserved boundaries

Phase 4 preserves canonical Yuki identity, Personality Capsule and honesty contracts, durable continuity, Yuki State, Temporal Grounding, the app-owned authority graph, substrate opacity, capability truth, fixed signing lineage, explicit migration discipline, permission-free Android state, and the pure Kotlin/JVM foundation boundary.

Phase 4 introduces no neural model, embedding model, vector store, semantic reranker, tokenizer, prompt format, LLM/runtime integration, model download, worker, background service, receiver, or autonomous cognition loop.

## Certification conclusion

Phase 4 — Memory authority and provenance completion is **CERTIFIED**.

The synchronized baseline is `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21` with Android CI Run #22 (`36098219804`), Mio PASS, and Mavyy phone acceptance PASS.

The active foundation phase advances to **Phase 5 — Living memory and semantic-recall socket**.

Phase 5 implementation is not authorized by this certification document alone. Yuki must perform a fresh live-repository reset and issue the next bounded architecture handoff.

The Model Freeze Gate remains **CLOSED**.

Brain first. Evidence first. History does not belong to the model.
