# Local Yuki

Local Yuki is persistent app-owned identity, continuity, memory, affect, state, authority and resource-aware cognition. Local neural models are replaceable cognitive organs. This owner-directed implementation adds a real offline GGUF CPU runtime, model manager, System One/Two/expression, semantic embeddings, durable reflective life, proactive presence, Android capability executors, a secure Yuki Lockdown bridge and migrating encrypted Vault.

Start with [current implementation and phase status](docs/architecture/OWNER_IMPLEMENTATION_2026-10-06.md), [current state](PROJECT_HANDOFF/CURRENT_STATE.md), [roadmap](ROADMAP.md) and [Galaxy acceptance](PROJECT_HANDOFF/PHONE_ACCEPTANCE.md). The [North Star](docs/architecture/YUKI_LOCAL_NORTH_STAR.md) remains the design goal, not a claim that every desired behavior has been physically accepted.

Version 0.2.3 improves the full conversation prompts and displays stage-specific failure diagnostics, with Copy controls in System status and model manifests. The owner reports all three decoder roles admitted under v0.2.2 / CPU adapter v3, but conversation still fails on Galaxy. Existing imported files and v3 admissions are retained; this update does not require rebenchmarking. See [conversation repair and acceptance](PROJECT_HANDOFF/CONVERSATION_FIX.md). Successful conversation and the full North Star remain unaccepted.

## Run and use

Build with JDK17, Android SDK35/build-tools35.0.0, NDK27.2.12479018 and CMake3.22.1. Run `python3 scripts/check.py` for foundation/Android tests, native APK assembly and lint. CI restores the existing signing key and verifies its fixed certificate. Local unsigned builds are for verification; install the matching-signature CI APK over the existing app. Never uninstall/reset to evade migrations.

Models → Import selects an owner file with Android storage access. Inspect its manifest, choose a role, Benchmark/admit while the app is visible and the phone is cool, then activate/replace that role. Installed runtime is llama.cpp b5046 CPU, GGUF only, ARM64/x86_64. Recognition of other formats is not execution support. Some GGUF architectures and small models may fail runtime or structured-role checks and are rejected. A single compatible GGUF can fill several decoder roles, with serialized execution and unload after each call. Embeddings may use a dedicated compatible model. No weights are bundled.

Autonomy defaults off. The owner controls both app policies and real Android grants. Configure Accessibility, notification access, overlays, usage access, microphone/document access as needed; status distinguishes actual available perception/control. Optional visible-app offline wake phrase stops on background or pressure. Offline ASR depends on an installed Android on-device recognizer; OCR is bundled local text perception, not general visual understanding.

Low-cost persisted jobs maintain bounded reflections and initiative policy. Foreground cognition uses measured memory/thermal/battery signals, bounded context/output/pass/concurrency and isolated native deadlines. Unsafe or unavailable resources defer work. Android Doze/force-stop/OEM restrictions constrain background timing. Yuki remains continuous through durable state rather than keeping a large model resident.

Enable “Allow Local Yuki control” in Yuki Lockdown 1.8 settings, then grant the Local Yuki bridge capability. Typed versioned Binder IPC authenticates both established signing certificates, inspects current state, validates commands and uses state digests/idempotent receipts. It does not automate Lockdown's UI or replace signing keys.

## Mobile delivery

Upload the complete `local-yuki-source.zip` at repository root using the existing mobile importer. No workflow or Lockdown replacement is needed for v0.2.3. Require the Android run's recorded exact tested commit to match the expanded source and its fixed certificate before installing.

Vault transfers continuity without model weights or signing secrets. Historical schemas migrate before validation. After restore, review/regrant Android capabilities and autonomy, reimport/readmit models, and remeasure the new phone. Physical acceptance and second-device migration remain owner checks; automated verification does not assert them complete.
