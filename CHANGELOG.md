# Local Yuki — Changelog

Only synchronized/certified project changes belong here. Candidate work that has not passed the required gates should remain in handoffs or active-slice notes.

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
