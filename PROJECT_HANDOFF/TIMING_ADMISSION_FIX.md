# Galaxy role-admission timing repair — 0.2.2

## Observed owner evidence

Live Local main `7209583417f078ab91baf212fc8640f01dd81af3`, tree `9a53dba97bbb00573e2b6c5d5ed8a01c1cc50004`, exactly matches the supplied v0.2.1 tree. Android Actions `37516394074` passed tests, ARM64/x86_64 builds, lint and the existing certificate check. Lockdown main remains `78ed3ed2631be2a84471275b27039f799323f9d2`, latest bootstrap `37511528787` successful. Neither workflow nor Lockdown needs replacement for this repair.

Owner reports v0.2.1 installed and SYSTEM_ONE admitted on Galaxy S26+. The imported Qwen2.5 1.5B Q4_K_M file is 1,117,320,736 bytes, SHA-256 `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`. Its v2 receipt reports startup 740ms, inference 14,203ms, 56 tokens, output budget 69, 4096 context, 4 threads, batch 64, peak 1,464,815,616 bytes, normal thermal before/after, unload confirmed. LANGUAGE_EXPRESSION and SYSTEM_TWO each rejected with `Measured generation is too slow for a safe structured response`. Old code did not save their timing measurements; the owner's displayed manifest contains SYSTEM_ONE timings. Do not infer missing role timings or complete conversation acceptance.

This establishes that the old generic load failure was resolved for this model and device through a complete SYSTEM_ONE admission. It does not establish full device or North-Star acceptance.

## Defect and repair

The old throughput calculation divided generated tokens by all inference time, including fixed prompt tokenization/prefill. It then projected that apparent per-token cost across a future response. A short reply was penalized disproportionately for the same prompt preparation. A slow decoder can still correctly fail; the observed failed-role timings were not retained, so this defect is a supported explanation rather than a claimed complete Galaxy diagnosis.

Shipping JNI now counts emitted/decoded tokens directly and measures preparation and generation separately. Preparation includes tokenization/template/truncation/prefill; generation includes grammar/sampler setup, sampling and decode through end-of-generation. Native asynchronous work is synchronized at both timing boundaries so pending prefill cannot be charged as generation. The supervisor conservatively assigns any remaining measured inference overhead to preparation. Admission reserves loading and preparation once, then computes `tokens / generationMs * (deadline - startupMs - preparationMs) * 0.6`, capped by the current profile. It still requires at least 64 output tokens. Deadlines, context/memory limits, thermal/battery checks, isolation, cancellation and unload remain enforced.

Manifests retain `lastBenchmark` and `roleBenchmarks` with role, adapter version, mode, deadline, preparation/generation/wall timings, tokens, output budget, peak and unload. Failed attempts retain their available measurements and rejection reason in their immutable receipts; a failure before measurement records no invented timing. Accepted top-level fields describe the most recently accepted role. Runtime limits and peak checks now use the requested role's immutable accepted receipt, so another role's admission cannot overwrite its measured limits.

CPU adapter version is 3 and app version is 0.2.2. Rebenchmark each role under v3. Imported files and all app-owned identity/continuity stay intact. There is no schema, signing, permission or Lockdown change.

## Verification and acceptance

New regression cases compare short/long samples at equal measured decode speed, retain fixed prompt cost and 40% reserve, reject slow/missing/invalid timing or exhausted deadlines, and preserve independent role envelopes after shared-file admission. Real external Qwen weights execute the shipping JNI with the actual admission samples and grammar; split timings, structured role output and unload are checked. `scripts/engine-proof/VERIFIED_TIMING_RESULTS.json` records host results and their limits. The final synchronized host run produced all three valid roles; calculated budgets differ from the earlier run under build contention. Slow-decode regression cases still reject. No host result establishes Galaxy speed or admission.

39 foundation/boundary tests and 89 Android tests passed, with zero failures/errors/skips. Real Qwen structured role output/timing/unload and path-open-denied Qwen/BGE descriptor regression passed. ARM64/x86_64 native APK assembly and Android lint also passed. The local APK is unsigned and is not the device delivery; matching-signature CI remains required. Committed source/tree and ZIP hash accompany the delivery record. Exact-source CI for v0.2.2 and Galaxy readmission remain pending until mobile import and owner testing.

1. Upload the complete `local-yuki-source.zip` over the existing wrapper using the existing importer. Wait for successful exact-source Android CI and the established-signature artifact. Install over Local Yuki without uninstall/reset.
2. On a cool, visible phone, select the existing imported file. Manage → Benchmark, admit & use / replace role → SYSTEM_ONE. Preserve its v3 result.
3. If accepted, benchmark LANGUAGE_EXPRESSION separately, then SYSTEM_TWO separately. Use `roleBenchmarks` to distinguish each result; report any full new diagnostic and timing snapshot. A genuine throughput rejection remains a rejection, not permission to loosen safety limits.
4. Verify retained identity/history, a short greeting and bounded conversation, then Stop/unload. Other grants/IPC/background/restart/Vault checks remain the separate Galaxy checklist.
