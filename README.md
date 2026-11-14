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
under the app UID is **not yet proven**. Work continues on the isolated
`fix/native-runtime` branch/worktree selected by the user. The original
checkout's JNI/Ktor/inference experiments are preserved separately, not
included here and not evidence of server or WebUI compatibility.

### Verification Status

| Task | Description | Status |
|---|---|---|
| T1 | Artifact provenance boundary | Archive/ELF rechecked; source commit transcription corrected |
| T2 | Pinned Gradle wrapper & toolchain | Historical completion retained |
| T3 | Minimal Views APK | Clean build/lint pass; no native payload |
| T4 | Verified native payload staging | 50 tests + real fourteen-file stage verification pass |
| T5 | Package exact native payload | Pending integration on isolated baseline |
| T6 | Internal direct-process launcher | Implemented/compiled; 19 JVM tests pass; awaits T5 |
| T7 | Bounded app-context smoke harness | Pending reviewed implementation |
| T8 | Clean-build APK audit | Pending native build/auditor |
| T9 | Physical S25 verification | Blocked: host prerequisites and no attached device |

**Evidence on the isolated branch (blockers 1–2):**
- **Host-tested:** 19 launcher JVM tests and 50 importer tests pass.
  Staging tests cover real duplicate tar entries, ELF/receipt validation,
  copy/publication/rollback failures, concurrency and hard process exit.
- **Stage-inspected:** real pinned archive stages exactly fourteen unchanged
  payloads + receipt; read-only verification passes. NDK inspection confirms
  dependency/ELF evidence; full source commit was checked against upstream.
- **Compiled:** clean debug APK build and lint pass; lint has four warnings,
  zero errors. Platform Views Activity has no production dependencies/listener.
- **APK-inspected:** this minimal APK intentionally contains no native
  payload. Its merged manifest has SDK 26/35, no permissions or exported
  activities/services/receivers/providers. This is not native launch proof.
- **Device-tested:** none. Physical S25 proof remains blocked.
- **Pending:** fail-closed native packaging (next decision), instrumentation,
  native APK audit and clean-checkout/device evidence. The existing APK was
  not rebuilt with native payloads during blocker 2.

The earlier 34-Python-test count, disabled stage gate, binary-manifest audit
crash and merge proposal describe the **original dirty checkout**, not this
isolated branch. The unreviewed harness/auditor/merge were not copied here.

See [ordered blockers and task acceptance](tasks/todo.md) and
[current build evidence](docs/verification/native-build.md). A blocked
module is incomplete, not complete with a waiver.

### Verification commands

These are the target workflow, **not a currently passing recipe**.
Resolve the [ordered blockers](tasks/todo.md#blockers-in-precedence-order)
first. Preserve the original checkout/stage; its NDK merge proposal is
unvalidated and is not part of this branch.

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
