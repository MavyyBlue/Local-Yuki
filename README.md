# Local Yuki

Local Yuki is an Android foundation for persistent, model-independent identity,
state and evidence-backed memory. The launcher provides an owner console for
memories, local notes, sleep/maintenance and encrypted continuity export/restore.
A conversational engine has not been integrated yet.

Start with [Project Handoff](PROJECT_HANDOFF/README.md), especially
[Current State](PROJECT_HANDOFF/CURRENT_STATE.md) and
[Active Slice](PROJECT_HANDOFF/ACTIVE_SLICE.md). The certified baseline is Phase 4;
Phase 5 has signed CI and owner phone acceptance, with independent review pending.
The current continuation implements deterministic Phases 6–11 and later admission,
Vault and mock replacement groundwork. Candidate work does not open the model freeze gate.

## Development checks

Install JDK 17, Android SDK platform 35 and build tools 35.0.0. Set `JAVA_HOME` and
`ANDROID_HOME` (or use the usual `local.properties` SDK configuration), then run:

```sh
python3 scripts/check.py
```

This runs foundation tests and boundary verification, Android/Robolectric unit
tests, and debug APK assembly. It forwards `HTTPS_PROXY` to forked Java test
processes when needed. The proxy's CA must already be trusted by your JDK.

Without fixed signing credentials, assembly produces
`app/build/outputs/apk/debug/app-debug-unsigned.apk`. Use the fixed-signed GitHub
Actions artifact for phone upgrades. Do not generate a replacement signing key or
uninstall the existing app to work around an update failure.

Additional checks can be selected explicitly:

```sh
python3 scripts/check.py :app:lintDebug
```

The Android build workflow runs on `main` and the configured Phase 1A branches, restores the
existing fixed signing key, verifies its certificate and uploads an APK named
with the exact tested commit. See the
[continuation implementation handoff](PROJECT_HANDOFF/2026-10-02_CONTINUATION_IMPLEMENTATION_HANDOFF.md)
for current evidence and the remaining acceptance checklist.
