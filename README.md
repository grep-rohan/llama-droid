# llama.droid

**Goal:** a minimal, fully open-source Android APK that packages and runs the
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
| T3 | Minimal Views APK | Platform Views scaffold compiled; no native launch UI |
| T4 | Verified native payload staging | 50 tests + real fourteen-file stage verification pass |
| T5 | Package exact native payload | Host packaging verified; installed extraction pending T9 |
| T6 | Internal direct-process launcher | Implemented/compiled/APK-integrated; 19 JVM tests pass |
| T7 | Bounded app-context smoke harness | 24 shared JVM cases pass; instrumentation compiles; ART/native run blocked |
| T8 | Clean-build APK audit | Fresh committed-source build, both APKs, audit/SDK checks verified |
| T9 | Physical S25 verification | Blocked: no connected/authorized device |

**Evidence on the isolated branch (blockers 1–5 host work):**
- **Host-tested:** 94 Python tests, 43 JVM tests (19 launcher + 24 probe)
  and 12 opt-in real Gradle/APK integration checks pass from a clean
  detached checkout at `955bfe3`, using a fresh pinned archive download.
  Staging tests cover real duplicate tar entries, ELF/receipt validation,
  copy/publication/rollback failures, concurrency and hard process exit.
- **Stage-inspected:** real pinned archive stages exactly fourteen unchanged
  payloads + receipt; read-only verification passes. NDK inspection confirms
  dependency/ELF evidence; full source commit was checked against upstream.
- **Compiled:** clean debug **and instrumentation** APK builds pass; lint
  has four existing warnings, zero errors. SDK inventory has a recorded
  non-blocking XML-version warning; no tooling was silently upgraded.
  Platform Views Activity has no production dependencies/listener.
- **APK-inspected:** the standalone ZIP/payload auditor passes for the real
  APK: fourteen pinned ARM64 native files, source-identical attribution,
  no detected extra natives/RPC/GGUF/staging content or duplicate ZIP names.
  SDK `apkanalyzer` confirms SDK 26/35, extraction=true, no permissions or
  exported Activity/services/receivers/providers. Compression/extraction
  configuration is not proof of installed permissions/linking/execution.
- **Device-tested:** none. Physical S25 proof remains blocked.
- **Pending:** authorize the physical S25 and execute the instrumentation
  suite, including real `--version` and cleanup/device evidence. T5 installed
  extraction and T7/T9 ART/native checks remain unchecked. Host success does
  not complete this module or authorize model-import/lifecycle/chat work.

The production APK excludes the probe/tests/provenance asset. The separate
`app-debug-androidTest.apk` is developer instrumentation only; AndroidX adds
test permissions/activities there, **not in the user APK**. Both artifact
hashes and reproduction details are in [build verification](docs/verification/native-build.md).

The auditor never decodes binary XML or verifies APK signing/device behavior.
Its success message explicitly scopes these out; resource/format limits and
content-detection limitations are documented in [BUILDING](docs/BUILDING.md#apk-audit-t8).

The earlier 34-Python-test count, disabled stage gate, binary-manifest audit
crash and merge proposal describe the **original dirty checkout**, not this
isolated branch. The unreviewed harness/auditor/merge were not copied here.

See [ordered blockers and task acceptance](tasks/todo.md) and
[current build evidence](docs/verification/native-build.md). A blocked
module is incomplete, not complete with a waiver.

### Verification commands

The documented host build/audit workflow below passed from clean committed
source. **The device command remains blocked** until the S25 is connected
and authorized; see [remaining blockers](tasks/todo.md#blockers-in-precedence-order).
Preserve the original checkout/stage; its unvalidated NDK merge proposal is
not part of this branch.

```powershell
# Staging
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz

# Build
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Instrumentation APK compilation (verified; does not execute device tests)
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
