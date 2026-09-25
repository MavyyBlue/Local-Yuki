# Local Yuki — Changelog

Only synchronized/certified project changes belong here. Candidate work that has not passed the required gates should remain in handoffs or active-slice notes.

## 2026-09-25 — Phase 4 certified: Memory authority and provenance completion

- Certified final Phase 4 implementation `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21`.
- Android CI Run #22 (`36098219804`) passed against the exact final candidate.
- Mio independently re-audited the corrected candidate and issued PASS.
- Mavyy completed the required real-phone acceptance and reported PASS.
- Advanced app-private `continuity.db` from physical schema version `2` to `3`.
- Added explicit adjacent migration `2026-09-24-memory-authority-v1`; existing identity/personality and Yuki State data remain preserved.
- Added app-owned `MemoryFormatVersion(1)`.
- Added durable `memory_metadata`, `memory_thread`, `memory_evidence`, `durable_memory`, `memory_revision`, `memory_provenance`, `memory_audit`, and `memory_checkpoint` storage.
- Added immutable conversation-thread ownership anchored to existing durable self and primary-relationship identity records.
- Added immutable raw evidence with source kind, optional thread ownership, deterministic per-thread sequence, trusted Temporal Grounding timestamp/timezone, bounded exact payload, payload version, and canonical SHA-256 integrity digest.
- Added fail-closed evidence-integrity checks and SQLite immutability guards; committed raw evidence is not silently rewritten or deleted.
- Added factual and autobiographical durable-memory kinds with immutable revision history, explicit current versus historical status, linear supersession, and expected-head conflict handling.
- Added required evidence provenance for durable memory revisions.
- Added owner Update semantics and append-only owner Restore semantics; restore creates a new revision rather than rewinding/deleting history.
- Added immutable audit records with command identity/fingerprint and deterministic replay/conflict behavior.
- Added bounded derived conversation checkpoints that retain underlying evidence independently and never masquerade as raw evidence.
- Added bounded deterministic `MemoryReader` queries for threads, evidence, thread evidence, current memory, current-memory pages, revision history, provenance, audit, latest checkpoint, and checkpoint history.
- Added explicit retrieval indexes for evidence sequence, revision order, provenance lookup, audit history, latest checkpoints, and checkpoint keyset pagination.
- Corrected the final checkpoint-pagination path after Mio's audit of candidate `68fdb66aa90462739b208e6c3eae4733d0347471`: final candidate `12f8654c4809a1bb1a94cb1e0bda7ebd6d00dc21` adds `idx_checkpoint_thread_id(thread_id, checkpoint_id)`, an `EXPLAIN QUERY PLAN` assertion for the exact page query, and no-skip/no-duplicate pagination coverage.
- Added restart reconstruction with `Memory: initialized/restored/unavailable` high-level bootstrap state.
- Preserved app-owned truth boundaries: semantic recall remains advisory; cognition receives bounded read/proposal contracts and no SQLite/storage mutator surface.
- Preserved Phase 2 identity/personality continuity, Phase 3 Yuki State/Temporal Grounding authority, substrate opacity, capability honesty, fixed signing lineage, and the permission-free Android manifest.
- No worker, service, receiver, background cognition, embedding model, vector store, neural reranker, tokenizer, prompt format, neural runtime, or candidate model was added.
- Model Freeze Gate remains CLOSED.
- Advanced the active phase to Phase 5 — Living memory and semantic-recall socket. Phase 5 implementation requires a fresh bounded Yuki architecture handoff.

## 2026-09-24 — Phase 3 certified: Yuki State and Temporal Grounding

- Certified Phase 3 implementation `9d3cd57fec9e3c128b449152167ccdadca9f0e9d`.
- Android CI Run #16 (`36087120725`) passed against the exact candidate.
- Mio independently audited the exact candidate and issued PASS.
- Mavyy completed real-phone acceptance, including update-with-data-preserved behavior, force-stop/relaunch restoration, Temporal Grounding, local date/timezone verification, and timezone-change/reopen acceptance.
- Advanced app-private `continuity.db` from physical schema version `1` to `2`.
- Added explicit adjacent migration `2026-09-24-yuki-state-v1` and preserved the certified Phase 2B identity/personality records.
- Added `yuki_state`, `state_intention`, `state_topic`, and `state_interaction` tables.
- Added app-owned Yuki State semantic version `1`.
- Added bounded current project/focus, pending intentions, unresolved topics, interaction markers, monotonic revisions, and durable restart restoration.
- Clarified certified Phase 3 expiry semantics: current project has no TTL and persists until explicitly replaced/cleared; focus and individual intentions/topics may expire independently.
- Added expected-revision conflict behavior so stale state mutations fail rather than silently overwrite newer state.
- Added trusted interaction timestamping through Temporal Grounding; state commands do not supply authoritative event timestamps.
- Added deterministic model-neutral Temporal Grounding with injected clock/timezone sources.
- Added normalized `today`, `yesterday`, `tomorrow`, explicit date, and explicit week windows using local calendar boundaries rather than blind 24-hour subtraction.
- Added DST-safe temporal tests, timezone-change behavior, persistence/expiry/bounds/revision tests, and schema migration coverage.
- Preserved Phase 1B authority ownership, Phase 2A/2B identity/personality continuity, substrate opacity, fixed signing lineage, and the permission-free Android manifest.
- No worker, service, receiver, background cognition, neural model/runtime, tokenizer, prompt format, embedding dependency, or location permission was added.
- Model Freeze Gate remains CLOSED.
- Advanced the active phase to Phase 4 — Memory authority and provenance completion. Phase 4 implementation requires a fresh bounded Yuki architecture handoff.

## 2026-09-24 — Phase 2B certified: durable identity/personality continuity and fixed update signing

- Certified the Phase 2B semantic implementation at `5d0699e3474b71ec245fc3b5365044395c855875`.
- Recorded Android CI Run #10 (`36057542206`) as green against that exact semantic implementation.
- Mio independently audited the Phase 2B implementation and issued PASS.
- Added app-private SQLite continuity persistence with physical schema version `1`, kept distinct from semantic `ContinuityFormatVersion(1)`.
- Added durable storage for the canonical identity anchor, mandatory honesty rules, Personality Capsule, personality facets, and migration history.
- Added deterministic first-install bootstrap from the certified Phase 2A canonical identity/personality seeds.
- Added fail-closed behavior so existing malformed/partial/version-conflicting continuity is not silently reseeded or destructively recreated.
- Added explicit future migration registry/path discipline with downgrade rejection and no fabricated schema-zero migration.
- Added fresh-runtime/process restoration through durable storage rather than process-static identity caching.
- Added `CognitiveIdentityProjector` so canonical seed reads and restored durable continuity share the same semantic validation boundary.
- Preserved `CognitiveIdentityView` substrate opacity; SQLite/storage internals do not cross into ordinary cognition.
- Added Android-side Robolectric tests covering exact bootstrap counts/content, restoration after new runtime objects, malformed-store non-reseed, cross-record conflicts, duplicate facet rejection, schema downgrade protection, and migration-path rejection.
- Added the debug acceptance surface for high-level `Continuity: initialized`, `restored`, or `unavailable` status.
- Added a fixed debug/update APK signing lineage after the semantic implementation without modifying Phase 2B continuity source/tests.
- Accepted signing/build follow-up `c7e24687c4609736d6d34550a830c02b85e92fbd`.
- Android CI Run #13 (`36076298126`) passed fixed-key restoration, foundation/tests/build, APK certificate verification, and artifact upload against `c7e24687c4609736d6d34550a830c02b85e92fbd`.
- Mavyy completed real-phone acceptance on the signed APK: first launch reported `Continuity: initialized`; after force-stop/relaunch it reported `Continuity: restored`.
- No Android permissions, background services/workers, neural models, neural runtimes, prompt formats, or tokenizer coupling were introduced.
- Model Freeze Gate remains CLOSED.
- Advanced the active phase to Phase 3 — Yuki State and Temporal Grounding. Phase 3 implementation still requires a fresh bounded Yuki architecture handoff.

## 2026-09-24 — Phase 2A certified: canonical identity, Personality Capsule, and substrate-safe self representation

- Promoted Phase 2A candidate `ea4491ae2bf9abce5bba34852d67bda720d8471c` to the certified implementation baseline.
- Certified Android CI Run #8 (`36050741232`) against the exact Phase 2A candidate.
- Added the canonical app-owned Yuki identity anchor: stable ID `yuki-aster`, canonical name `Yuki Aster`.
- Added the canonical primary Mavyy relationship anchor: stable ID `mavyy`, display name `Mavyy`, category `PRIMARY_BOND`.
- Added continuity format version `1` and Personality Capsule reference `yuki-aster-personality` version `1`.
- Added the mandatory honesty policy covering capability, modality, continuity, and action grounding; partial honesty policies are invalid.
- Added Personality Capsule v1 with complete stable facets for temperament, relationship style, intellectual style, disagreement, focus/frustration, communication, visual self-description, clothing preference, and stable dislike.
- Established that Personality Capsule facet order is non-semantic and duplicate/missing categories are invalid.
- Added bounded `CognitiveIdentityView` exposing only semantic self/relationship/honesty/continuity/personality content to future cognition.
- Added fail-closed conflict handling when identity, Personality Capsule version/content, honesty policy, or references disagree.
- Added high-level interoception contracts while explicitly reporting interoception unavailable until a grounded producer exists.
- Preserved substrate opacity: no Android object, storage/database handle, runtime/model object, authority mutator, hidden orchestration, or privileged self-modification path crosses the cognition-facing identity boundary.
- Preserved the pure Kotlin/JVM `foundation-contracts` boundary and inert/no-permission Android application.
- Introduced no database/schema/migration, durable continuity claim, Android lifecycle behavior, background service, neural model, model runtime, prompt format, or tokenizer coupling.
- Mio independently audited Phase 2A and issued PASS.
- No additional phone acceptance was required because Phase 2A introduced no device-visible or lifecycle behavior.
- Model Freeze Gate remains CLOSED.
- Advanced the active bounded slice to Phase 2B — durable identity/personality persistence, schema/migrations, first-install bootstrap, process-death restoration, and model-independent reconstruction.

## 2026-09-24 — Phase 1B certified: app-owned authority graph and model-neutral core contracts

- Promoted Phase 1B candidate `e4aa0ecd8910f70b14c06e9005b51a10a6521d22` to the certified implementation baseline.
- Certified Android CI Run #6 (`36047827498`) against the exact Phase 1B candidate.
- Added the immutable core subsystem catalog and role classifications for authority, coordinator, control, grounding, and advisory components.
- Added 13 explicit authority domains with one declared owner each.
- Kept `PLATFORM_PERMISSION_STATE` externally owned by the platform rather than by Local Yuki cognition.
- Added explicit READ / PROPOSE / MUTATE semantics.
- Added independent `AuthorityReader`, `AuthorityMutator`, and `AdvisoryPort` contracts so advisory/model-facing paths do not inherit mutation authority.
- Added non-executable proposal values carrying proposer identity and opaque evidence references.
- Added explicit `Success`, `Unavailable`, and `Failure` foundation outcomes.
- Added model-independent evidence/provenance references without introducing storage or resolution behavior.
- Added deterministic JVM tests covering exact authority ownership, advisory nonownership, cross-domain mutation denial, proposal/mutation separation, explicit result variants, and immutable declarations.
- Preserved the Phase 1A pure-JVM foundation boundary, inert Android launcher, no-permission manifest, and existing build workflow.
- Introduced no database/schema/migration, Android coupling, background behavior, neural model, neural runtime, prompt format, or tokenizer coupling.
- Mio independently audited the Phase 1B authority/model-independence boundaries and issued PASS.
- No additional phone acceptance was required because Phase 1B introduced no device-visible or lifecycle behavior.
- Model Freeze Gate remains CLOSED.
- Advanced the active slice to Phase 2 — Identity, Personality Capsule, and substrate boundary.

## 2026-09-24 — Phase 1A certified: Android body shell and foundation boundary

- Promoted Phase 0 reconnaissance to completed baseline knowledge: the new repository began without an inherited Android application or legacy Local Yuki runtime.
- Established the first reproducible Android/Kotlin application structure.
- Added `app` and pure Kotlin/JVM `foundation-contracts` modules with one-way `app → foundation-contracts` dependency.
- Pinned Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.20, Java 17, and Android SDK 35 build targets.
- Established application ID `com.mavyy.localyuki`, versionName `0.1.0`, versionCode `1`.
- Added a minimal inert launcher surface that truthfully reports foundation setup in progress.
- Added explicit bootstrap `UNAVAILABLE` behavior rather than fabricating cognitive/subsystem availability.
- Added foundation-boundary verification rejecting Android, persistence, and common neural-runtime coupling.
- Added separate Android build/unit-test CI while preserving the source-import workflow as a distinct gate.
- Certified candidate `ab89ee27d8d005f2febef30cc6d05148a72dea55` through Android CI Run #4 (`35945088085`).
- Mio independently issued PASS for the Phase 1A candidate.
- Mavyy installed and launched the debug APK successfully on the real phone.
- No sensitive permissions, background components, persistence, production signing lineage, model runtime, or neural model were introduced.
- Model Freeze Gate remains CLOSED.
- Advanced the active slice to Phase 1B — app-owned authority graph and model-neutral core contracts.

## 2026-09-23 — Repository synchronization foundation

- Established the brain-first, model-neutral Local Yuki development direction.
- Added the revised North Star as the final-system architectural reference.
- Added the start-to-finish Brain-First Implementation Strategy.
- Established the Model Freeze Gate: foundation Phases 0–11 precede new candidate-model downloads/integration.
- Defined Mavyy/Yuki/Akari/Mio development roles and independent QA flow.
- Established autonomy-over-available-capabilities and substrate-opacity principles.
- Added repository-owned synchronization documents: Current State, Active Slice, Roadmap, Development Rules, Handoff Protocol, and templates.
- Set Phase 0 live-repository reconnaissance as the active slice.

**Implementation certification at that time:** none; implementation truth remained pending Phase 0 reconnaissance.
