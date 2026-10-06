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
