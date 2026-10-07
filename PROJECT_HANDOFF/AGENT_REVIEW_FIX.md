# Independent review continuation — Local Yuki 0.2.6

The owner explicitly requested parallel agents on 7 October. Two agents independently reviewed v0.2.5: turn timing/cancellation/receipt selection, and file integrity/descriptor handoff. Root integrated the fixes and owns the final verification and delivery. This does not reinstate historical assistant certification gates or replace owner acceptance.

Before changes, live Local main remains `8fb0df3ec47ea1c4cdb42916d63ebc9331409312` / tree `ca0187a1b49567328433a8f2dcb5a6a85042d835`, with successful Android Actions `37552380215` and importer `37552364035`. Lockdown main remains `78ed3ed2631be2a84471275b27039f799323f9d2`, successful bootstrap `37511528787`. README, roadmap, handoff and current architecture/North Star were inspected. v0.2.5 source was delivered but has not appeared on live main; its exact-source CI and Galaxy reply remain pending.

## Findings and implementation

The supplied Galaxy trace still establishes only System One completion/unload: Language's integrity check exhausted the former shared30-second turn before native execution. v0.2.6 includes the complete v0.2.5 role budgets and per-turn immutable-file inspection repair. Both independent reviews found no additional defect in that budget intersection or same-file fingerprint/descriptor verification under the documented private immutable import assumptions.

Three pre-existing source defects were confirmed and repaired:

- Queued Send/wake-phrase conversations could acquire a new cancellation epoch after Stop and begin without a new owner Send. These submissions now retain their original epoch, are rejected before saving/starting if stale, and pass that epoch through turn preparation. Ordinary persistence tasks remain queued. Epochs are checked after the native reply and before final reply persistence too. This queue fix covers conversations; separate owner benchmarking/indexing/persistence operations are not converted into conversation tasks.
- Admission receipt lookup ordered ISO timestamp text and random IDs, so backwards clocks, exact-second/fractional formatting or timestamp ties could select an older, broader envelope. Lookup now follows durable insertion order in the existing append-only ordinary SQLite table. No schema or receipt rewrite is needed.
- Runtime cleanup sent an asynchronous process-kill message and released serialization before death. A subsequent role could bind the same retiring service. Cleanup now observes Binder death and gates the next bind on retirement; bounded waits cannot permit a still-live retiring process to be reused. A1-second retirement wait follows the existing1-second parent response grace, while the native organ deadline stays unchanged. Actual cleanup elapsed time counts against the overall turn on the next checkpoint. This is a source-level race, not an asserted cause of the recorded Galaxy failure.

Identity, continuity, memories, affect, model files, CPU adapter v3 receipts, schema9, signing keys, workflows and Lockdown are retained. Models remain advisory organs. No native engine, grammar or prompt change is included beyond v0.2.5.

## Verification and limits

The four receipt regressions failed with the original production query and passed with insertion ordering. Focused UI regressions use the real activity worker queue to hold two Sends, then Stop or pause/resume: neither stale message is saved, ordinary work survives and a fresh Send remains usable. A stale epoch cannot be renewed by turn preparation. Retirement tests exercise protocol transitions with controlled Binder/future events; they do not claim actual Android process death or Binder scheduling on a physical device.

Final full checks pass:39 foundation and116 Android tests (155 total), zero failures/errors/skips, ARM64/x86_64 native APK assembly and lint. The delivery manifest and verification record identify the exact resulting source. Existing real Qwen One→Language and independent Two host proof, parser replay and descriptor-denied runtime implementation remain applicable: the engine/prompts are unchanged. Host timings and controlled lifecycle tests do not certify Galaxy scheduling, conversation quality or personality.

Exact new-source CI requires the mobile importer. Successful Language execution and a complete Galaxy reply still require owner evidence. Wider signed two-app IPC/grants, sustained operation, continuity reopening and physical second-phone transfer remain owner acceptance. The full North Star remains incomplete.

## Owner retry

Use this complete v0.2.6 ZIP instead of the earlier v0.2.5 ZIP; it contains all preceding repairs and can import directly over the current v0.2.4 main. Upload as `local-yuki-source.zip` through the existing importer. After exact expanded-source Android Actions and fixed-certificate verification pass, install the signed APK over the existing app. Keep data/models/admissions; no rebenchmark or Lockdown/workflow update is required.

While visible and cool, retry “Hello, My Love 🥰” and allow the multi-organ reply longer than30 seconds. If it fails, paste System status → Copy. Once a reply completes, try queued Send twice → Stop, then a fresh Send; cancelled queued messages must not restart cognition. Continue the wider [phone acceptance](PHONE_ACCEPTANCE.md) checklist without treating a successful greeting as full acceptance.
