# Implementation Plan: Native Runtime

Module: `native-runtime`  
Spec: [SPEC-native-runtime.md](../SPEC-native-runtime.md), approved by the user.  
Plan status: **Approved baseline; original implementation hold is historical.** Implementation files now exist, with deviations requiring review. This document preserves the approved design, not current execution evidence or approval of those deviations.

Task target: [tasks/todo.md](todo.md), the current status and ordered-blocker tracker. The user subsequently selected isolated worktree option A for blocker 1: minimal Activity, direct-process launcher and JVM tests. That scoped implementation is verified on `fix/native-runtime`; later blockers remain interactive decisions.

## Overview

Prove one thin path: clean checkout → verified upstream CPU artifact → packaged debug APK → app-UID `llama-server --version` on the physical Galaxy S25. No GGUF, model picker, service, listener, or WebView is needed for this proof. Preserve explicit evidence boundaries: downloading and inspecting ELF files does not establish Android execution.

## Research Completed During Planning

This was read-only artifact/source research, not implementation or Android execution.

- GitHub release metadata: https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/b12020
- Source commit reported by the release: `0ec4027a2a0183de87c007069adddc06e665f7de`.
- CPU archive: https://github.com/ggml-org/llama.cpp/releases/download/b12020/llama-b12020-bin-android-arm64.tar.gz
- Size: 78,982,432 bytes. Downloaded SHA-256 matches the release API digest: `64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec`.
- Archive has 44 entries including its `llama-b12020/` root, with no symlinks. A first download timed out; resuming completed it and the full digest matched. The research archive is outside the repository, not a committed/build input.
- `llama-b12020/llama-server` is a 7,192-byte ELF64 little-endian AArch64 ET_DYN executable, mode 0755, with interpreter `/system/bin/linker64`. It is a launcher, not a self-contained server.
- The six required non-platform libraries are `libllama-server-impl.so`, `libllama-common.so`, `libmtmd.so`, `libllama.so`, `libggml.so`, and `libggml-base.so`. Their transitive `DT_NEEDED` entries close within this set plus Android `libc.so`, `libm.so`, and `libdl.so`. No separate `libc++_shared.so` dependency was observed.
- Seven CPU backend libraries are dynamically discoverable and must be retained despite not appearing in `DT_NEEDED`:
  - `libggml-cpu-android_armv8.0_1.so`
  - `libggml-cpu-android_armv8.2_1.so`
  - `libggml-cpu-android_armv8.2_2.so`
  - `libggml-cpu-android_armv8.6_1.so`
  - `libggml-cpu-android_armv9.0_1.so`
  - `libggml-cpu-android_armv9.2_1.so`
  - `libggml-cpu-android_armv9.2_2.so`
- Shared-library inspection found no RPATH/RUNPATH. The archive also contains `libggml-rpc.so`, RPC/CLI executables, and unrelated implementation libraries: these are excluded from the APK.
- `libllama-server-impl.so` contains UI accessor symbols, `index.html` references, and gzip streams. Pinned source links the generated UI library into the server implementation. No separate WebUI asset files occur in this archive. Hosting remains untested.
- Sampled launcher, server implementation, and baseline CPU library have 0x4000 (16 KiB) `PT_LOAD` alignment. Check every packaged ELF and the actual device page size during implementation; sampled alignment alone does not prove device compatibility.
- ELF observations came from a one-off Python standard-library inspection, not `readelf`; reproduce with the NDK tool before accepting packaging.

Pinned source evidence:

- [Server link graph](https://github.com/ggml-org/llama.cpp/blob/0ec4027a2a0183de87c007069adddc06e665f7de/tools/server/CMakeLists.txt)
- [Compiled UI assets](https://github.com/ggml-org/llama.cpp/blob/0ec4027a2a0183de87c007069adddc06e665f7de/tools/ui/CMakeLists.txt)
- [Dynamic backend discovery](https://github.com/ggml-org/llama.cpp/blob/0ec4027a2a0183de87c007069adddc06e665f7de/ggml/src/ggml-backend-reg.cpp)

The backend loader scores matching CPU libraries and searches the executable directory and working directory. `GGML_BACKEND_PATH` specifies an individual out-of-tree library, not a backend directory. Do not set it to the native library directory.

## Architecture Decisions Proposed for Approval

### 1. Minimal Views application, no production dependencies

Keep package `io.github.greprohan.llamadroid`, minSdk 26, target/compileSdk 35, Kotlin 2.0.21, AGP 8.8.2, Gradle 8.10.2, and JDK 17. Use a platform Activity with a static explanatory view and a platform theme; no Compose, AndroidX UI, custom chat, or extra controllers.

Pin test-only dependencies: JUnit `4.13.2`, AndroidX Test runner `1.6.2`, and AndroidX Test ext JUnit `1.2.1`. No CI changes. Discover installed JDK/SDK tooling before provisioning missing tools; document exact setup instead of silently replacing versions. Use NDK `27.2.12479018` solely for host `llvm-readelf`/`llvm-nm` inspection, not for rebuilding llama.cpp. If unavailable, pause and review an alternative inspection tool/version.

### 2. Manifest-driven native staging

Commit a small provenance manifest at `third_party/llama.cpp.json`: release, commit, asset URL, archive size/SHA-256, explicit member-to-APK mapping, and per-file digests established from the verified archive. Commit license/notice text, not binaries.

Map `llama-server` to `libllama-server.so`, preserving its bytes. Keep the original names of the six library dependencies and seven CPU variants: fourteen native files in total. Exclude the RPC backend and all unrelated utilities/backends. Do not trim CPU variants based solely on the phone's marketed chipset.

Use Python's standard library to verify the full archive before reading selected members. Do not use blanket extraction: reject absolute/traversing/ambiguous paths, duplicate members, unsupported links/special files, missing expected members, and wrong ELF architecture. Use synthetic fixtures for tests. This real archive contains no links; future link-bearing releases require deliberate manifest/handling review.

Stage to a temporary sibling directory, validate the complete payload, then replace the generated `jniLibs` directory with rollback on failure. An interrupted or failed stage must be rejected by build-time verification, even if an old complete stage remains. A receipt records payload hashes; a verification-only mode checks the actual staged bytes and rejects unexpected files. No network access during Gradle assembly and no runtime downloads.

### 3. Executable-as-native-library remains a testable packaging proposal

Use `app/src/main/jniLibs/arm64-v8a/`, legacy JNI extraction (`useLegacyPackaging = true` with consistent manifest extraction), and `keepDebugSymbols` for the staged native files so the Android package manager can extract them and AGP does not strip/rewrite the executable. Compare every APK native member with its expected source digest.

Resolve the installed executable through `ApplicationInfo.nativeLibraryDir`, never a hard-coded `/data/app` path. If Android will not extract/execute the renamed ELF with this supported configuration, stop: do not lower target SDK, disable security features, copy binaries to writable app storage, or switch to a JNI/source-build architecture without review.

### 4. Internal launch boundary

Proposed Kotlin surface:

```kotlin
internal class NativeServerLauncher(context: Context) {
    fun start(arguments: List<String>): Process
}
```

A separate internal `NativeRuntimeLayout` describes the trusted native executable/library paths and constructs the explicit command/environment for testing. The context-based launcher owns path resolution; callers cannot select another executable or library directory through intents or UI input.

- Command: absolute installed `libllama-server.so` path plus individual argument strings; no shell.
- Working directory: installed native-library directory, which is read-only to the app and supports upstream backend discovery.
- Child `LD_LIBRARY_PATH`: trusted native-library directory; Android platform library resolution remains the linker's responsibility.
- Remove inherited `GGML_BACKEND_PATH` rather than allowing an out-of-tree backend override. Do not append writable or ambient search paths.
- Validate expected payload presence and executable permission before launch; enrich failures with concise paths, cause, and relevant configuration, without masking native stderr or inventing a linker diagnosis.
- Return the `Process` with separate stdout/stderr. The downstream lifecycle module will own asynchronous draining and persistent process management; the instrumentation smoke test owns bounded draining and cleanup for this module.

No exported native-launch service/receiver/test hook in the production manifest. Only instrumentation starts `--version` in this module. The argument boundary is internal and is not permission to expose raw upstream flags later.

### 5. Bounded physical-device smoke test

Run under the target app UID, not from `adb shell` or a copied temporary executable. Concurrently drain stdout and stderr with fixed-size retained tails while continuing to consume excess output. Require version output identifying the pinned build and exit 0 within 30 seconds. In `finally`, terminate an unfinished process, wait with a bound, force termination if required, close streams, and join/cancel readers without unbounded waits. Record any inability to reap the child as failure.

Use a Kotlin test seam around process observation/cleanup to exercise timeout and abnormal-exit handling without introducing fake native binaries. The smoke test must pass independently on the real server. Check for survivors after the run. Do not accept a pre-existing server or any HTTP response as evidence.

## Dependency Graph and Build Order

```text
T1 Artifact/provenance ───────────────┐
                                    ├─ T4 Secure staging ─ T5 Packaged payload ─┐
T2 Pinned wrapper ─ T3 Minimal APK ──┘                                         ├─ T6 Launch boundary
                                                                              └─ T7 Smoke harness
T5 + T6 + T7 ─ T8 Clean-build/APK audit ─ T9 Physical S25 evidence
```

T7 requires T6's interface and T5's packaging. T9 may be blocked by phone access without blocking host build work. No downstream module work is used to bypass a failed native launch.

## Approved Task Index

The approved acceptance/verification details and current scoped authorization are in `tasks/todo.md`. Historical hold statements below are not new authorization for later tasks.

1. T1 — Record and reproduce the inspected upstream payload and licensing.
2. T2 — Establish the pinned wrapper and host setup instructions.
3. T3 — Build the smallest Android Views APK.
4. **Checkpoint A:** reviewed artifact boundary; wrapper/toolchain verified; empty APK builds.
5. T4 — Implement tested, checksum-enforced native staging.
6. T5 — Package and verify the selected native payloads.
7. T6 — Implement the internal direct-process launch contract.
8. **Checkpoint B:** host/JVM checks pass; staged and APK payloads match; launch contract reviewed.
9. T7 — Add the bounded app-context instrumentation smoke harness.
10. T8 — Verify a clean build and audit the complete APK/manifest.
11. T9 — Run the physical S25 smoke test and record evidence.
12. **Checkpoint C:** report compiled/APK-inspected/device-tested independently; module complete only when every spec criterion is met.

Tasks are limited to roughly five touched files. Early tasks may produce a wrapper or empty APK without native launch; these are verifiable increments, not claims that the product works.

## Exact Artifact and Verification Commands

Future implementation commands, not commands claimed to have succeeded in this checkout. The URL, digest, archive root, and ELF path below are based on the inspected artifact.

```bash
# After locating JDK 17 and the Android command-line tools, and accepting SDK licenses:
java -version
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018"
sdkmanager --list_installed

mkdir -p .cache
curl --fail --location --retry 3 --output .cache/llama-b12020-bin-android-arm64.tar.gz \
  https://github.com/ggml-org/llama.cpp/releases/download/b12020/llama-b12020-bin-android-arm64.tar.gz
printf '%s  %s\n' \
  64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec \
  .cache/llama-b12020-bin-android-arm64.tar.gz | sha256sum --check --status

python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only

# Use Android SDK's pinned NDK host tools (Windows Git Bash host path shown).
READELF="$ANDROID_SDK_ROOT/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe"
"$READELF" -h -l -d app/src/main/jniLibs/arm64-v8a/libllama-server.so
for library in app/src/main/jniLibs/arm64-v8a/*.so; do
  "$READELF" -h -l -d "$library" || exit 1
done

./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --no-daemon :app:assembleDebugAndroidTest
unzip -l app/build/outputs/apk/debug/app-debug.apk
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk

# Final device checks; select only the authorized S25 before running instrumentation.
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew --no-daemon :app:connectedDebugAndroidTest
adb logcat -d -s llama.droid
```

The staging CLI loads the committed manifest by default and emits a verified receipt. `--verify-only` must not download, extract, or repair anything. Gradle's pre-build check invokes this mode (host Python command configurable, default `python`). `verify_apk.py` uses ZIP/member hashes plus the committed manifest; inspect the merged/packaged manifest separately with SDK `apkanalyzer` to establish extraction settings, permissions, exported components, and SDK levels.

Host setup must define the actual JDK 17 and `ANDROID_SDK_ROOT` paths in local instructions. Use `gradlew.bat` if Windows Bash cannot invoke the wrapper/JDK correctly. Wrapper JAR/distribution digests are verified against official Gradle checksums; exact digests must be recorded, not guessed in this plan.

## Verification Checkpoints

- **A, after T1–T3:** artifact allowlist/provenance reviewed; pinned tools available; minimal APK compiles. If official artifact evidence contradicts the initial inspection, update the spec/plan before implementing packaging.
- **B, after T4–T6:** adversarial staging tests and JVM tests pass; Gradle rejects missing/tampered stage; APK-native bytes match manifest; direct launch boundary has no shell/external entry point.
- **C, after T7–T9:** smoke harness compiles; clean build/lint/tests and APK audit pass; physical run proves version exit/cleanup and records current device state. No phone means **blocked**, not passed.

Tests must discover real cases, fail for the intended reason before their implementation, and pass after it. Do not count zero-test success, mocked native execution, static HTML markers, or shell-context execution as device evidence. Apply the standing Definition of Done to each task using scope-appropriate evidence; blockers remain unchecked.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Renamed executable not extracted/executable under target SDK 35 | High | Preserve ELF bytes, inspect manifest/APK, then app-UID physical test; stop before security workarounds |
| Missing dynamically discovered CPU libraries | High | Stage all seven inspected variants, preserve names and native-dir lookup; later model tests establish actual backend operation |
| Unsafe archive or stale/tampered stage | High | Full digest first, explicit allowlist, safe member reads, transactional replacement, receipt verification before build |
| RPC or other unintended backend packaged | High | Explicit native allowlist and negative APK assertions; no blanket `.so` copy |
| Native output deadlock or leaked child | High | Concurrent bounded-tail drains, deadline, `finally` cleanup/reap assertions, failure-path tests |
| Embedded UI misinterpreted as proven hosting | Medium | Record source/static evidence only; actual hosting/generation remain downstream acceptance criteria |
| Host lacks JDK/SDK/NDK tools | Medium | Discover first; document pinned prerequisites and setup commands; do not claim build before compiler output |
| S25 not accessible | High for completion | Continue host verification; preserve concrete physical test commands and blocked status |
| Device page size / ELF alignment mismatch | High | Inspect every staged ELF's load alignment and query `adb shell getconf PAGE_SIZE`; confirm on the actual device rather than relying on the sampled 16 KiB alignments |
| Large unstripped runtime/APK | Medium | Retain correctness first; record size and do not trim dependencies or strip launcher without new evidence |
| Download interruption | Medium | Keep download separate from staging, require complete digest, retry/resume only with final verification |

## Parallelization

T1 artifact review and T2 wrapper setup can run in parallel; T3 depends on T2. T4 depends on T1 and can overlap T3 if different files are owned. Packaging and launcher integration must be sequential because their paths/manifest contract are shared. Instrumentation may be prepared after T6 while APK audit tests are authored separately, but shared Gradle-file edits require coordination. Device execution is last and cannot be parallelized away.

## Open Approval Points and Stop Conditions

- Approve the packaging experiment, test-only dependency pins, NDK inspection-tool pin, CLI contracts, and internal launcher API.
- JDK/SDK/tool availability and device access are execution prerequisites, not unanswered hardware-spec questions. The S25's published 12 GB RAM/SoC baseline is already known.
- Replacing `b12020`, source-building native code, adding production dependencies/CI, or changing the executable architecture requires a new review.
- No claim of LFM3 model support, inference, service reliability, or WebUI operation is permitted at this checkpoint.

## Next Gate

The original implementation hold was later superseded for the user's chosen blocker-1 scope (isolated worktree option A). That host boundary is now implemented/tested. Pause for the next interactive decision: retain the approved fourteen-file staging format and repair its safety/receipt tests (recommended), or separately review an alternative artifact/linking strategy. No later task is silently authorized by this status update.
