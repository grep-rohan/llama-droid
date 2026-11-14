# llama.droid

Minimal, fully open-source Android APK that packages and runs the
upstream **llama.cpp `llama-server`** locally and opens its built-in
WebUI in an Android WebView.

**Intended user experience (not yet validated):** Install APK → import a
GGUF → adjust basic arguments → Start server → Chat. End users should
not need Termux, root, ADB, a separate inference service, or a cloud API.

## Current Status

| Milestone | Status |
|---|---|
| M0 — Make the APK actually work | **In progress** |

## Native Runtime Module

The `native-runtime` module aims to establish the build, packaging,
linking, and direct-process launch foundation. Installed-app execution
under the app UID is **not yet proven**. Current JNI/Ktor/inference
changes diverge from the approved module boundary and need review;
they are not evidence of upstream server or WebUI compatibility.

### Verification Status

| Task | Description | Status |
|---|---|---|
| T1 | Artifact provenance boundary | Historical completion retained |
| T2 | Pinned Gradle wrapper & toolchain | Historical completion retained |
| T3 | Minimal Views APK | Reopened: current Activity starts a server |
| T4 | Verified native payload staging | Reopened: safety/receipt gaps; merge unverified |
| T5 | Package exact native payload | Reopened: Gradle stage verification disabled |
| T6 | Internal direct-process launcher | Reopened: launcher classes/JVM tests absent |
| T7 | Bounded app-context smoke harness | Current compilation/execution unverified |
| T8 | Clean-build APK audit | Blocked: real APK auditor crashes; clean build pending |
| T9 | Physical S25 verification | Blocked: host prerequisites and no attached device |

**Evidence from the documentation review:**
- **Host-tested:** 34 Python tests pass (22 importer + 12 APK audit).
  Coverage gaps remain; duplicate rejection is not actually asserted and
  monolithic tests check synthetic ZIPs, not successful native linking.
- **Stage-inspected:** existing fourteen-file payload digest check passes;
  receipt and ELF architecture checks remain incomplete.
- **Compiled/JVM/lint:** not rerun for current sources; earlier reports do
  not establish current success. Instrumentation references missing classes.
- **APK-inspected:** auditor crashes decoding binary `AndroidManifest.xml`;
  packaged manifest checks and clean-build hash evidence remain pending.
- **Device-tested:** not established; `adb devices -l` lists no devices.
- **Linking proposal:** unvalidated; do not treat the merge or the claimed
  Android 17 restriction as a verified diagnosis/remedy.

See [ordered blockers and task acceptance](tasks/todo.md) and
[current build evidence](docs/verification/native-build.md). A blocked
module is incomplete, not complete with a waiver.

### Verification commands

These are the target workflow, **not a currently passing recipe**.
Resolve the [ordered blockers](tasks/todo.md#blockers-in-precedence-order)
first. In particular, do not rerun import with NDK discovery enabled
against the genuine stage until the merge proposal is reviewed.

```powershell
# Staging
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz

# Build
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Instrumentation (compiles; device run requires phone)
./gradlew --no-daemon :app:assembleDebugAndroidTest

# APK audit
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk

# On device (requires authorized S25)
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew --no-daemon :app:connectedDebugAndroidTest
adb logcat -d -s llama.droid
```

## Intended architecture (not current runtime evidence)

```
MainActivity (model, CPU arguments, start/stop)
       |
       v
LlamaServerService (foreground service + ProcessBuilder)
       |
       v
Bundled upstream ARM64 llama-server + required shared libraries
       |
       v
127.0.0.1:8080  <---->  ChatActivity (upstream WebUI in WebView)
```

## Non-goals (native-runtime module)

Model import, model loading, persistent inference, foreground-service
lifecycle, user-facing Start/Stop controls, readiness polling, WebView
chat, and text generation belong to **downstream modules**.  This
module must not claim those capabilities work.

## Requirements

- JDK 17 (full, with `javac`)
- Android SDK with platform 35, build-tools 35.0.0
- NDK 27.2.12479018 (approved for ELF inspection; merge proposal unverified)
- Python 3 (for staging and verification)
- Physical ARM64 Android device (for smoke test — currently blocked)

## License

This project is open-source.  Upstream llama.cpp binaries and model
weights are **not** committed to Git.  Provenance, checksums, and
attribution text are maintained in `third_party/`.
