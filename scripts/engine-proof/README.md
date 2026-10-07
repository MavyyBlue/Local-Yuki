# Real engine proof

VERIFIED_HOST_RESULTS.json records external model hashes/revisions, real shipping-JNI outputs, normalized semantic separation and unload evidence. Two Qwen2.5 Q4_K_M decoder engines and BGE/Qwen embeddings execute; host replacement does not claim end-to-end Android continuity replacement or Galaxy speed. Model weights are deliberately external. The Linux/JVM peak includes the JVM and earlier runs, unlike isolated Android receipts.

With JDK17, CMake3.22+, Ninja and external compatible weights:

```sh
cmake -S app/src/main/cpp -B /tmp/yuki-native -G Ninja -DYUKI_HOST_PROOF=ON -DCMAKE_BUILD_TYPE=Release
cmake --build /tmp/yuki-native --target yuki-organ
javac -d /tmp/yuki-proof scripts/engine-proof/com/mavyy/localyuki/inference/NativeOrgan.java
python3 scripts/engine-proof/prepare_contracts.py /tmp/yuki-contracts
java --add-opens java.base/java.io=ALL-UNNAMED -Djava.library.path=/tmp/yuki-native -Dyuki.role=one -Dyuki.system=/tmp/yuki-contracts/one-system.txt -Dyuki.user=/tmp/yuki-contracts/one-user.json -Dyuki.grammar=/tmp/yuki-contracts/one.gbnf -cp /tmp/yuki-proof com.mavyy.localyuki.inference.NativeOrgan /path/first.gguf /path/second.gguf
```

Use roles `two` and `language` with their matching extracted contracts. Role `embedding` skips generation and tests normalized related/unrelated vectors. The harness bounds real inference and always unloads. Representative JSON checks do not certify broad reasoning, calibration or personality quality. Small SmolLM models tested earlier could generate language/vectors but failed typed decision output; format/size alone therefore cannot admit a model. A pathological bounded-string grammar was corrected after timeout evidence; recursive character grammar plus runtime token/byte bounds avoids that cost.

## Isolated-descriptor regression

On Linux, build with `-DYUKI_HOST_PROOF=ON -DYUKI_DESCRIPTOR_TESTS=ON` and target `yuki-organ yuki-descriptor-test`. Compile both Java classes from `com/mavyy/localyuki/inference/`, then run:

```sh
java --add-opens java.base/java.io=ALL-UNNAMED -Djava.library.path=/tmp/yuki-native -cp /tmp/yuki-proof com.mavyy.localyuki.inference.NativeDescriptorRegression /path/qwen2.5-1.5b-instruct-q4_k_m.gguf /path/bge-small-en-v1.5-q8_0.gguf
```

This requires seccomp support; failure to install restrictions is a test failure, never a skipped pass. It proves `/proc/self/fd` reopening returns EACCES, then executes actual decoder generation twice and normalized BGE embeddings through shipping JNI with all pathname opens forbidden. It checks rejected malformed/unsafe descriptors and descriptor cleanup/borrowed ownership. The host-only restriction fixture is never linked into the Android APK. This simulates the access constraint; it does not assert Galaxy SELinux logs or physical acceptance.

## Role timing regression — v0.2.2

The host harness now asserts positive generated-token counts and separate preparation/generation timing whose sum is within rounding of the measured wall time. `prepare_contracts.py` also extracts the actual admission samples. Use `admission-system.txt` and `<role>-admission.txt` with the matching grammar to test short admission outputs. `VERIFIED_TIMING_RESULTS.json` records real Qwen1.5B samples and old/new 30-second calculation comparisons, not Galaxy admission. The host harness itself uses a 60-second bound. Short language/reasoning samples demonstrate the fixed-prompt penalty. The final synchronized host run completes all roles; separate slow-decode/deadline regression cases still reject, and no host throughput establishes Galaxy acceptance.

## Conversation preparation regression — v0.2.4

Export exact app context/candidate grammars with `YUKI_PROOF_DIR` during `scripts/check.py`, then run `prepare_contracts.py` and `verify_conversation.py` with the exported directory, Java classes/native library, real owner-compatible Qwen file and an empty results directory. The `crowded/` export includes accumulated synthetic history; extract prompts into that directory too. Whole candidate selection uses the actual model tokenizer. Proof runs One→Language under a30-second host deadline and separately executes Two; outputs replay through app production parsers using `YUKI_CONVERSATION_RESULTS`. `VERIFIED_PREFILL_RESULTS.json` and `prefill-fixtures/` retain the fresh greeting; crowded-context results are recorded separately.

`NativePreparationRegression` takes model, One system prompt and compact One grammar paths and verifies real candidate selection, explicit oversized-input refusal before decode, preparation-deadline progress and unload. Full path-denied descriptor regression is rerun for the changed JNI. Host timing, parser success and structural point coverage do not certify Galaxy behavior or semantic/personality quality.
