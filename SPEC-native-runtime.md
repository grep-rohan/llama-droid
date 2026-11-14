# Spec: Native Runtime

Module id: `native-runtime`  
Status: Approved specification; plan/task approval and scoped interactive decisions are recorded in [tasks/todo.md](tasks/todo.md). The isolated `fix/native-runtime` branch now has verified host clean-source builds of both APKs, packaging/audit and a compiled test-only harness. Physical S25 packaged-launch/cleanup proof remains blocked; module success criteria are not complete. Original-checkout architecture deviations are preserved separately, not approved or included here.

Parent: [Approved capability map](CAPABILITY-MAP.md)

**Evidence note:** Statements below about an empty/docs-only checkout,
missing tools, and device availability are planning-time observations,
not current status. Preserve this spec as the approved requirements;
use [the task tracker](tasks/todo.md) and
[build verification](docs/verification/native-build.md) for current
results, reopened acceptance items and ordered blockers.

## Objective

Establish the smallest Android build that packages an upstream CPU ARM64 `llama-server` and proves that the installed app can launch it under its own Android UID without Termux, root, runtime binary downloads, or copying executable code into writable app storage.

The consumer is the later `server-lifecycle` module. The owner ultimately needs reliable offline chat on a physical Galaxy S25; this module proves only the build, packaging, linking, and native process-launch foundation.

### Scope

- Create a minimal Android Views project because no Android source or Gradle build files currently exist in this checkout.
- Inspect an actual upstream Android CPU ARM64 artifact before selecting a packaging technique.
- Pin the selected upstream release URL and SHA-256; verify the digest before staging files.
- Establish a reproducible staging procedure that preserves every required runtime library and any required WebUI assets, with upstream licenses and attribution.
- Build and inspect the debug APK, install it on the physical S25, and launch a short-lived upstream informational command from the app's own process context.
- Record exact toolchain, artifact, ELF, APK, device, command, exit, and diagnostic evidence.

### Non-goals

Model import, model loading, persistent inference, foreground-service lifecycle, user-facing Start/Stop controls, readiness polling, WebView chat, and text generation belong to downstream modules. This module must not claim those capabilities work.

## Assumptions and Current Evidence

- Architecture target is `arm64-v8a`, CPU-only. The base Galaxy S25's published hardware is Snapdragon 8 Elite for Galaxy with 12 GB RAM and 128/256/512 GB storage variants. It launched with Android 15 / One UI 7.0. Sources: https://www.sammobile.com/samsung/galaxy-s25/ and https://www.gsmarena.com/samsung_galaxy_s25-13610.php
- Use these published specifications for initial targeting without requiring device access. They do not establish the owner's current installed software, free RAM, selected storage variant, or native runtime compatibility.
- The selected eventual model is `LFM3-1.7B-QAD-Q4_0.gguf` from https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/blob/main/LFM3-1.7B-QAD-Q4_0.gguf. Neither compatibility nor memory fit is established here.
- Upstream `b12020` CPU artifact was downloaded and its SHA-256 matched the GitHub release asset digest: `64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec`. Archive: `llama-b12020-bin-android-arm64.tar.gz`, root `llama-b12020/`. This is not evidence of LFM3 support. A different release requires approval and documented rationale.
- Read-only inspection found a 7,192-byte AArch64 ET_DYN launcher using `/system/bin/linker64`, six non-platform server dependency libraries, seven dynamically selected CPU libraries, and an RPC backend that must not be packaged. No shared-library RPATH/RUNPATH was found. A Python standard-library ELF inspection was used because `readelf` is not on PATH; reproduce using the NDK's `llvm-readelf` during implementation.
- The archive has no separate WebUI directory. `libllama-server-impl.so` contains UI accessor symbols, `index.html` references, and gzip streams; pinned upstream CMake links generated UI assets into the server implementation. This is static evidence of embedded assets, not proof that WebUI hosting works.
- Executable-as-`libllama-server.so` packaging is a hypothesis. Only real artifact inspection and installed-app execution can validate it.
- `adb devices -l` returned no attached devices on 2026-07-19. No S25 hardware/software properties have been read.
- `adb` and Python are available on this host; `java` and `gradle` were not found on PATH, and Android SDK environment variables were unset. This is not proof that those tools are absent elsewhere.

## Tech Stack

Proposed baseline follows the repository guidance:

- Kotlin 2.0.21; Android Views, not Compose.
- JDK 17; Android Gradle Plugin 8.8.2; Gradle 8.10.2.
- Package/application id: `io.github.greprohan.llamadroid`.
- minSdk 26; compileSdk and targetSdk 35; only ARM64 native payloads.
- Python 3 standard library for staging checks and host tests.
- JUnit 4 / AndroidX instrumentation for app-context native launch verification; exact test dependency versions are to be pinned in the reviewed plan.
- Upstream prebuilt Android CPU server and its verified dependencies; no inference reimplementation or third-party runtime environment.

Any required toolchain or artifact-version change must be reviewed rather than silently substituted. A minimum SDK declaration is not a broad device-compatibility claim.

## Commands

These are the required command interfaces after implementation, not commands that currently work in this docs-only checkout. Run from the repository root in a Bash-compatible shell; Windows may use `gradlew.bat` instead of `./gradlew`.

```bash
# Host staging tests; must discover and execute actual tests, not pass with zero tests.
python -m unittest discover -s scripts/tests -p 'test_*.py' -v

# Build from clean outputs, run local Android tests, and run lint.
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Inspect packaged payloads.
unzip -l app/build/outputs/apk/debug/app-debug.apk

# Establish device availability and inspect relevant configuration.
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.security_patch
adb shell getprop ro.build.version.oneui
adb shell getprop ro.build.fingerprint
adb shell grep MemTotal /proc/meminfo

# Install and run app-context native smoke tests with only the target S25 connected.
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew --no-daemon :app:connectedDebugAndroidTest
adb logcat -d -s llama.droid
```

An empty One UI property must be recorded as unavailable, not converted into a guessed version. Report `/proc/meminfo` as OS-visible memory, not definitive marketed RAM capacity. Use the published 12 GB specification for initial targeting; no owner confirmation of marketed RAM is required.

The reviewed plan must supply exact download, checksum, staging, and `readelf -h -l -d` commands using the inspected archive's real paths. Do not invent an archive URL/layout in this specification. There is no web dev server or desktop `dev` command; the development loop is build → install → instrumentation.

## Project Structure

Proposed locations, not existing implementation:

```text
app/src/main/AndroidManifest.xml                  Android declarations
app/src/main/java/io/github/greprohan/llamadroid/ Minimal Views entry point and native launch boundary
app/src/main/jniLibs/arm64-v8a/                  Generated native payloads; never committed
app/src/main/assets/                           Required runtime assets/licenses, if inspection requires them
app/src/test/                                  JVM tests for pure launch configuration logic
app/src/androidTest/                           Installed-app native launch smoke tests
scripts/                                       Artifact acquisition/staging support
scripts/tests/                                 Host staging tests and synthetic fixtures
.cache/                                        Ignored downloaded artifacts and inspection outputs
third_party/                                   Committed provenance, checksums, licenses, notices
docs/verification/                              Human-readable build/device evidence
```

Root Gradle files and the pinned Gradle wrapper establish the build. The wrapper JAR is build tooling, not an upstream inference binary; verify its provenance. No existing source example is available in this checkout.

## Code Style

Use idiomatic Kotlin, four-space indentation, `PascalCase` types and `lowerCamelCase` members. Keep native launch and artifact handling separate from Activity UI logic. Prefer explicit argument arrays and bounded resource use.

Illustrative style, not a mandated final API or implemented code:

```kotlin
internal fun versionCommand(executable: File): List<String> {
    require(executable.isFile) { "Bundled llama-server is missing" }
    require(executable.canExecute()) { "Bundled llama-server is not executable" }
    return listOf(executable.absolutePath, "--version")
}
```

Never interpolate a shell command. Execute only the bundled server directly via `ProcessBuilder`; no `sh -c`, downloaded executables, or arbitrary user-selected executable paths. Resolve packaged paths from Android APIs rather than hard-coding installation directories.

Good diagnostic output distinguishes `artifact checksum mismatch`, `missing native dependency`, `native executable not executable`, and `native smoke test timed out`. Preserve useful exit status and bounded native output; do not log model contents or unrelated personal device data.

## Module Boundary / Consumer Contract

The later lifecycle module must receive a verified native launch boundary that:

- Resolves the bundled executable and required library/asset locations from the installed app.
- Starts the executable directly with an explicit argument list and controlled working directory/environment.
- Exposes the process handle, stdout/stderr, and exit status so callers can drain output and manage termination.
- Surfaces missing files, execution failures, and linking failures as actionable diagnostics rather than success.
- Does not select models, manage service states, implement readiness, or open a WebView.

The exact Kotlin API belongs in the reviewed technical plan. The launch smoke test must drain both output streams without hanging and always clean up its child process, including on timeout or assertion failure. General Stop/restart orchestration remains downstream scope.

The launch boundary is internal, not an exported Android command/intent endpoint. No externally supplied raw server arguments are accepted.

## Testing Strategy

### Host tests

Use Python `unittest` with small synthetic archives, not committed upstream binaries. Verify checksum enforcement, rejection of unsafe archive paths, missing/duplicate payload handling, dependency staging, and cleanup after failure. Handle archive symlinks according to the actual release layout without writing outside the staging directory. Staging failures must not leave a partially publishable payload.

### Static artifact and APK checks

Record the real archive file inventory, checksum, ELF architecture/type/interpreter, `DT_NEEDED` entries, available evidence of CPU feature requirements, WebUI asset arrangement, and licenses. Do not guess CPU compatibility from the label `arm64` alone.

For every non-platform dependency, show how the APK supplies it and how Android resolves it. Inspect the final APK to confirm required payloads/assets are present, no GGUF is bundled, and stripping/packaging has not silently altered the executable into an unusable file. If CPU requirements remain uncertain, record that uncertainty and require physical-device execution evidence.

### Installed-app smoke test

Use instrumentation on the owner's physical ARM64 S25, with no root or alternate runtime. Launch upstream `--version` from the app context and require successful exit with recognizable upstream version output within 30 seconds; capture and drain stdout/stderr and record exit status. Verify that no child survives test completion or timeout.

A successful `adb shell` launch from a temporary directory is not a substitute: the executable must run from the installed APK's supported native packaging path under the app UID. Record extracted file permissions and loader errors, if any. An emulator result may supplement but cannot replace the S25 result.

No numeric coverage target is imposed. Every critical staging failure above needs an automated test, and packaged launch requires physical-device evidence. Report host-tested, compiled, APK-inspected, and device-tested independently.

## Boundaries

- **Always:** Inspect the actual artifact before packaging decisions; verify pinned hashes before staging; preserve licenses; use explicit arguments; keep native binaries/weights out of Git; run applicable tests before commits; distinguish evidence from assumptions; mark unavailable device checks blocked.
- **Ask first:** Change upstream release or toolchain versions; switch to source-built native binaries; add production dependencies; change CI configuration; change package/SDK policy; weaken Android security settings to make launch work. Minimal test dependencies are subject to plan approval.
- **Never:** Claim model compatibility or WebUI hosting from a version-command test; use Termux/root; execute code copied into writable app storage; add broad storage permissions, runtime download requirements, telemetry, tools/shell/MCP features, LAN listening, or GPU/NPU backends; commit models, upstream native binaries, secrets, or personal device identifiers; remove failing tests merely to pass.

No CI configuration is required by this module; local clean-checkout reproducibility is required. Subsequent inference must bind only to `127.0.0.1:8080`; the informational smoke test must not start a listener.

## Success Criteria

1. A clean checkout can obtain a checksum-verified, pinned upstream Android CPU ARM64 payload and produce a debug APK with the documented JDK/SDK/toolchain; actual command output and versions are recorded.
2. Inspection establishes the real archive layout, ELF interpreter/type, dependency closure, CPU-feature evidence/limitations, and WebUI asset arrangement. No dependency is justified solely by filename guessing.
3. Host tests prove checksum rejection, unsafe-archive rejection, complete dependency staging, and failure cleanup. JVM tests and lint pass.
4. APK inspection confirms all required native/runtime files, correct attribution, and no model weights or unintended native backends.
5. On the physical S25, the installed app launches its packaged server under its own UID; `--version` exits successfully within 30 seconds, with version/output/exit evidence and no surviving child.
6. Device evidence records model, ABI, Android/API/security patch, build, One UI availability, and OS-visible memory. Unknown properties remain explicitly unknown; do not publish unique serial numbers.
7. The consumer launch contract is available internally without implementing downstream import, service, or chat functionality.

If device access is unavailable, criteria 5–6 remain **blocked**, and this module is not complete. Compilation or an emulator run does not waive them. Successful completion still does not establish that the selected GGUF loads or generates text.

## Open Questions / Approval Points

- Can the owner connect and authorize the S25 for ADB? Current result: no attached devices. Physical validation is blocked.
- Will the inspected `b12020` payload launch from Android's installed native-library directory with this target SDK? Artifact availability, checksum, and static embedded-UI evidence are established; extraction, permissions, linking, and process launch remain unverified. If unsuitable, seek approval for a replacement or architectural change.
- What Android/One UI build is currently installed, and how much memory is available during testing? Published hardware RAM is 12 GB; launch software was Android 15 / One UI 7.0. Query current device state when accessible, but do not block specification or planning on it.
- Does the selected upstream runtime support the exact LFM3 QAD Q4_0 model? Native launch alone cannot answer this; later load/generation verification must establish it before claiming the baseline works.

## Approval Gate

The user approved this specification; subsequent plan/task approval is
recorded in the task tracker. That approval does not waive success criteria
or authorize unreviewed architecture, artifact or production-dependency
changes. Current completion and blockers are tracked in `tasks/todo.md`.
