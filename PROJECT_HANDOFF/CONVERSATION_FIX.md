# Conversation continuation — v0.2.3

## Live and owner evidence

Inspected Local main `c1217f8615b80f4f3d9e303bd64bee87ef51c3a0`, tree `5b73d0cbe042103f0f9502c5a9c638f4f82e8420`, successful Android Actions `37520823624` and import `37520798057`. Inspected current README, roadmap, handoff and architecture/North Star before changing source. Lockdown remains `78ed3ed2631be2a84471275b27039f799323f9d2`, latest bootstrap successful.

Owner reports v0.2.2 installed and all three decoder roles admitted using the existing Qwen2.5 1.5B Q4_K_M file. Messages are saved, then after about 15–20 seconds conversation shows the generic setup fallback. The owner cannot conveniently copy manifest text. Admission is owner-reported; successful conversation, sustained use and full North Star acceptance are not established.

## Changes

- Replace the shared setup fallback with the failed stage and bounded reason. Retain available per-pass mode, context/output/deadline limits, startup/preparation/generation times, token-limit flag and unload result. Native failures keep their actual diagnostic. Earlier completed passes remain visible if a later pass cannot start.
- Persist only the latest failure status in app-private preferences, clear it on a completed reply, and make it copyable in System status. It is app status, never a saved Yuki utterance, model input or continuity evidence. Manifest details also have a Copy control and selectable text.
- Generate compact JSON: no formatting whitespace outside strings. The same fields, role syntax, evidence restrictions and capability restrictions remain. A real Qwen development run exhausted 64 generated tokens on formatting before completing its JSON; this demonstrates an output-budget failure, not proof of the exact Galaxy cause.
- Supply each inference's actual output allowance. Keep System One's established interpretation contract; shorten reasoning and expression instructions. Project all four actual honesty rules as semantic JSON instead of the old JVM object identifier, preserving all nine canonical personality facets.

CPU adapter remains v3. JSON field semantics, native ABI, role-receipt ceilings, body governor, deadlines, pass/concurrency limits and validation remain. Existing imported models and admissions remain usable. No database migration, signing-key change, workflow replacement or Lockdown update is required.

## Verification

Automated verification: 39 foundation tests and 93 Android tests, zero failures/errors/skips; ARM64 and x86_64 native APK assembly; Android lint. Regression coverage includes failed-stage diagnostics persisted across ordinary reopen, rejected incomplete JSON and omitted meaning points, intact owner evidence, semantic identity/honesty projection, and the separate production interpretation/composition/expression path. The final Android parser regression also consumes the actual host-generated greeting outputs.

Real shipping-JNI Qwen proof uses fresh app-exported canonical context and production grammars, not owner chat data. With a 64-token output allowance, System One and Language complete sequentially inside a 30-second host turn envelope; a separate uncertain-question System Two execution also returns complete JSON. See [recorded outputs and timings](../scripts/engine-proof/VERIFIED_CONVERSATION_RESULTS.json) and its frozen fixtures. This does not prove a complete three-stage reasoning turn, Android binding/admission, Galaxy speed, or semantic/personality quality. Host expression wording and reasoning quality still need behavioral review.

The local verification APK is unsigned and is not the installation deliverable. New-source CI remains pending mobile source import; require its exact source tree and the established certificate before installing the CI APK. Prior v0.2.2 CI cannot certify v0.2.3.

## Next owner checkpoint

Import the complete source ZIP as `local-yuki-source.zip` using the existing main-branch workflow, install the resulting signed update over the existing app, then send “Hello, Yuki.” Keep existing data, weights and admitted roles; do not rebenchmark solely for this update. If a reply still fails, open System status → Copy and paste the text. Models → existing model → Copy supplies measurements if needed. The next device report identifies the actual Galaxy failure or establishes the first successful conversation; it is not full North Star acceptance.

Run host proof with JDK17 and the existing host JNI/classes:

```sh
YUKI_PROOF_DIR=/tmp/yuki-contracts python3 scripts/check.py
python3 scripts/engine-proof/prepare_contracts.py /tmp/yuki-contracts
python3 scripts/engine-proof/verify_conversation.py --java /path/to/java --classes /path/to/classes --library /path/to/host-jni --model /path/to/qwen.gguf --contracts /tmp/yuki-contracts --results /tmp/yuki-conversation
```

Pass `YUKI_CONVERSATION_RESULTS=/tmp/yuki-conversation/results.json` to the Android checks to replay real outputs through production parsers. These optional host inputs contain only fresh test fixtures; no phone export is required.
