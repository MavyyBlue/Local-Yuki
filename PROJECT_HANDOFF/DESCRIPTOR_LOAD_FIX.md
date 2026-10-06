# Galaxy model-admission repair — 0.2.1

The owner installed/opened both updates and reported `Admission rejected: runtime rejected weights/architecture` before model admission. The screenshot alone does not establish a corrupt model or unsupported architecture. The delivered Qwen weights executed on host, but that earlier proof did not impose the isolated Android access constraint.

## Diagnosis and change

The old JNI loader called `llama_model_load_from_file("/proc/self/fd/<received descriptor>")`. An isolated Android UID can receive permission to read a Binder-transferred descriptor without permission to reopen the app-private backing file by pathname. The code incorrectly converted descriptor authority into a new pathname open. Galaxy SELinux/logcat was not captured, so this remains the likely device-specific cause rather than a claimed observed denial.

The vendored b5046 extension `llama_model_load_from_fd` borrows, checks and duplicates a read-only regular-file descriptor. GGUF metadata uses the open FILE stream, and tensor mmap uses that same duplicate. No pathname resolution or model-data copy is needed; split models reject before opening any secondary path. Failure closes internal duplicates; unload releases mappings; the caller retains its descriptor. CPU adapter version is now 2. Bounded printable native load errors are surfaced where the runtime supplies them.

No model/resource cap, isolated UID, Android permission, signing certificate, database schema or continuity record is weakened or reset. Imported model files remain usable. Any prior v1 role activation needs benchmarking/readmission under v2.

## Verification

`NativeDescriptorRegression` invokes the shipping JNI and real external Qwen2.5 1.5B Q4_K_M/BGE-small Q8_0 weights. Its host-only seccomp fixture denies open/openat/openat2: the old `/proc/self/fd` reopen demonstrably returns EACCES. With that restriction still installed, the new loader performs two real decoder generation/load/unload cycles and normalized 384-dimension BGE embeddings. It checks caller-descriptor ownership, descriptor leaks after failed/repeated loads, and invalid, closed, writable, nonregular and corrupt inputs. The test fails if the restriction cannot be installed; the fixture is never linked into the Android APK.

Full Android verification passed: 37 foundation/boundary tests and 88 Android unit/integration tests, zero failures/errors/skips; ARM64/x86_64 native APK assembly and Android lint. The real restricted-access JNI regression passed, including decoder generation and BGE embeddings. Results and exact new-source identity accompany the fix ZIP. The prior published source is independently CI verified: Local main `512e2490ad8b54df4435171bfddbed014c3c7cc7`, tree `f72f121a734630103b03b7367b74f86a2245476f`, Actions #31 / `37511471850`. That run is not evidence for this new fix.

## Mobile acceptance

1. Replace the existing root `local-yuki-source.zip` with the complete fix ZIP. The already-installed mobile importer/build workflow handles it; no workflow replacement or Lockdown update is needed.
2. Require green exact-source CI and install its established-signature APK over Local Yuki. Do not uninstall or clear storage.
3. Open Models, select the existing imported model, then Manage → Benchmark, admit & use / replace role. Benchmark SYSTEM_ONE first; continue with LANGUAGE_EXPRESSION and SYSTEM_TWO only if accepted. Keep the owner surface visible and use the current safe resource envelope.
4. Verify native load no longer returns the old generic error. If another rejection occurs, retain the complete new error and the manifest/file identity; runtime execution and successful admission remain separate checks. No re-download/reimport is needed unless the file actually fails hash/integrity checks.
5. Confirm existing identity, memories and history, then test short conversation and Stop/unload. Real Galaxy acceptance remains pending until these checks pass.
