# Build Instructions

> **Current workflow is blocked, not verified end to end.** See
> [ordered blockers](../tasks/todo.md#blockers-in-precedence-order) and
> [current evidence](verification/native-build.md). The commands below
> describe the approved target workflow, not a successful current build.
> The existing stage passes its payload digest check, but Gradle's stage
> verifier is disabled and the real APK audit crashes on the binary
> manifest. Launcher classes/JVM tests are absent from the current tree.
> Do not rerun import with NDK discovery enabled against the genuine stage
> until the unvalidated merge proposal is reviewed. No device run is proven.

## Prerequisites

Installation entries below reflect previously documented host setup,
not tool versions reverified during the documentation review.

### Java

| Requirement | Current status |
|---|---|
| JDK 17 (full, with `javac`) | **Installed** — Temurin 17.0.14 at `D:\softwares\jdk\jdk-17.0.14+7` |
| `JAVA_HOME` set to JDK 17 root | **Required** — set before building |

### Android SDK

| Requirement | Current status |
|---|---|
| SDK command-line tools at `D:\softwares\android-sdk\cmdline-tools\latest` | **Present** |
| Platform `android-35` | **Installed** |
| Build tools `35.0.0` | **Installed** |

### NDK (for artifact inspection only)

| Requirement | Current status |
|---|---|
| NDK r27 (27.2.12479018) at `D:\softwares\android-sdk\ndk\27.2.12479018` | **Installed** |
| `llvm-readelf` / `llvm-nm` | **Available** at `D:\softwares\android-sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin\` |

The approved plan uses the NDK for ELF inspection (T1), not native
compilation. Current CMake/JNI and importer merge additions diverge from
that baseline and require review. Importer tool discovery currently
returns no toolchain despite the documented installation; identify and
configure the pinned tools rather than installing another version blindly.
Setting `ANDROID_NDK_HOME` alone does not validate the merge proposal.

### Gradle Wrapper

The project ships an official Gradle 8.10.2 wrapper:

| File | Source | SHA-256 |
|---|---|---|
| `gradlew` | `gradle/gradle@v8.10.2` | — |
| `gradlew.bat` | `gradle/gradle@v8.10.2` | — |
| `gradle/wrapper/gradle-wrapper.jar` | `gradle/gradle@v8.10.2` | `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046` |
| Distribution ZIP | `services.gradle.org` | `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26` (in `gradle-wrapper.properties`) |

The wrapper validates the distribution SHA-256 automatically via `distributionSha256Sum` in `gradle-wrapper.properties`.

## Staging native payloads (T4 → T5)

Before building, stage the verified native payloads from the upstream archive:

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only
```

The approved stage is fourteen byte-preserved files in
`app/src/main/jniLibs/arm64-v8a/` plus `.payload_receipt.json`. The current
importer additionally attempts an unverified merge when it finds an NDK;
its verifier still expects the original fourteen digests. Receipt and ELF
validation also need repair. Preserve the genuine stage during investigation.

The intended Gradle `verifyNativeStage` gate must reject absent, tampered,
or unexpected payloads before packaging. **It is currently disabled** by
`onlyIf { false }`; its invocation also needs the explicit directory option:

```powershell
python scripts/import_llama_release.py --verify-only --staging-dir app/src/main/jniLibs/arm64-v8a
```

Passing this manual digest check is not proof of build-gate enforcement,
receipt validation, APK integrity, extraction, or native execution.

## Building

Create `local.properties` with your SDK path (host-specific, ignored by Git):

```powershell
echo 'sdk.dir=D:/softwares/android-sdk' > local.properties
```

Then build:

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
./gradlew --no-daemon clean :app:assembleDebug
```

After the blockers are resolved, verify that
`app/build/outputs/apk/debug/app-debug.apk` contains the approved payload:

- 14 native `.so` files (launcher + 6 libraries + 7 CPU backends) in `lib/arm64-v8a/`
- `assets/THIRD_PARTY_LICENSE.txt` and `assets/THIRD_PARTY_NOTICES.txt`
- No RPC backend, CLI tools, or unrelated payloads

## Verifying the wrapper

```powershell
# Wrapper version check
./gradlew --version
# Expected: Gradle 8.10.2, Kotlin 1.9.24, Launcher JVM 17

# Java version check
java -version
# Expected: openjdk version "17.0.x"

# SDK components
& "D:\softwares\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat" --list_installed
# Expected: platforms;android-35, build-tools;35.0.0

# NDK tooling
& "D:\softwares\android-sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe" --version
# Expected: LLVM version 18.x
```

## Testing (T6)

The required launcher JVM test sources are currently absent. Restore
meaningful tests before treating this command as verification; require
nonzero test discovery. The intended tests run on the host without
executing the ARM64 ELF:

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*NativeServerLauncherTest*'
```

Required coverage: path resolution, argument separation, environment
construction, and configuration failure paths. No current passing JVM
result is established.

## Instrumentation smoke test (T7)

The intended smoke test uses `NativeProcessProbe` to drain output with
bounded tails, enforce a 30-second deadline and verify child cleanup.
Current instrumentation references missing `NativeRuntimeLayout` and
`NativeServerLauncher` classes; compilation must be re-established first.
Fake-Process instrumentation source/compilation is not a passing device test.

### Compile instrumentation APK

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
./gradlew --no-daemon :app:assembleDebugAndroidTest
```

### Run on device

Connect an authorized ARM64 device (Galaxy S25), then:

```powershell
./gradlew --no-daemon :app:connectedDebugAndroidTest
adb logcat -d -s llama.droid
```

**Blocked by host prerequisites and no connected/authorized device.**
`adb devices -l` listed no devices in the documentation review. If multiple
devices are present later, explicitly select only the S25 for ADB and
Gradle (see the task plan). Compilation alone does not execute these tests.

## APK audit (T8)

The intended ZIP audit checks payload integrity, ABI, attribution and
absence of GGUF weights. **Current real-APK result: `UnicodeDecodeError`**
while decoding binary `AndroidManifest.xml` as UTF-8. The monolithic path
also uses size-only acceptance instead of digest verification. Repair these
before trusting success; inspect the packaged manifest separately with
SDK `apkanalyzer` as described in [the build checklist](verification/native-build.md).

```powershell
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Host tests for the auditor:

```powershell
python -m unittest discover -s scripts/tests -p 'test_verify_apk.py' -v
```

## Unresolved prerequisites

An agreed native-runtime source set, restored launcher/JVM tests,
fail-closed staging/packaging, a trustworthy real-APK audit, and a clean
reproduction are required before device testing. The physical S25 must be
connected and authorized. Tool installations alone do not remove these
blockers; see the [precedence list](../tasks/todo.md#blockers-in-precedence-order).
