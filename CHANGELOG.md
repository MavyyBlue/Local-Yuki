# Local Yuki — Changelog

Only synchronized/certified project changes belong here. Candidate work that has not passed the required gates should remain in handoffs or active-slice notes.

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
