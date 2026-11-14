# Native Build Verification (T8)

## Current evidence — incomplete

This file is a reproduction checklist, **not a successful clean-build
report**. Earlier completion claims are not established for the current
working tree. See [ordered blockers](../../tasks/todo.md#blockers-in-precedence-order).

Checks executed during documentation reconciliation:

| Command/check | Observed result |
|---|---|
| `python -m unittest discover -s scripts/tests -p 'test_*.py' -v` | 34 pass: 22 importer + 12 APK audit |
| `python scripts/import_llama_release.py --verify-only` | Existing fourteen-file stage payload digests pass |
| `python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk` | Existing APK audit crashes with `UnicodeDecodeError` at binary manifest UTF-8 decoding |
| `adb devices -l` | No devices attached |
| Importer `_find_ndk_toolchain()` | `None`; documented NDK inspection executable exists, so discovery/configuration is unresolved |

Limits: duplicate-member rejection is not asserted by its named test;
receipt/ELF validation is incomplete; monolithic tests use synthetic ZIPs
and size checks, not a real merge. No Gradle/JVM/lint/instrumentation
command was run in this review. Required launcher classes and JVM tests
are absent, stage verification is disabled, and harness/auditor sources
are untracked. No installed-app native execution or Android 17 linker
restriction is established. The existing APK is not tied to a reproduced
source revision here; do not install it as a verified artifact.

## Target clean-build procedure — blocked

After resolving scope, staging, packaging and auditor blockers, reproduce
the following from a disposable clean checkout/worktree with the selected
implementation source tracked and only documented prerequisites. Record
the revision, tool versions, full command results and both APK hashes/sizes.
Do not claim clean reproducibility from this dirty working tree.

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK 17 (full, with `javac`) | Temurin 17.0.14 | `JAVA_HOME` must point to JDK root |
| Android SDK command-line tools | latest | `ANDROID_SDK_ROOT` or `local.properties` |
| Platform `android-35` | installed | Required for compilation |
| Build tools `35.0.0` | installed | Required for packaging |
| NDK `27.2.12479018` | documented local installation | Approved for ELF inspection; current CMake/merge additions require scope review |
| Python 3 | standard library | Staging and APK audit |
| Gradle 8.10.2 | wrapper (pinned) | `gradle/wrapper/gradle-wrapper.jar` |

### Step-by-step

```powershell
# 1. Ensure environment
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'

# 2. Stage native payloads ONLY after the importer/merge blocker is resolved.
# Do not overwrite the genuine stage to experiment with the current merge.
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only

# 3. Create local.properties (host-specific, ignored by Git)
echo 'sdk.dir=D:/softwares/android-sdk' > local.properties

# 4. Clean build + JVM tests + lint
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# 5. Instrumentation APK (compiles; device run requires physical S25)
./gradlew --no-daemon :app:assembleDebugAndroidTest

# 6. APK inspection
unzip -l app/build/outputs/apk/debug/app-debug.apk

# 7. Automated APK audit
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk

# 8. Host staging tests
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

## Expected outputs under the approved fourteen-file plan

These are target outputs, not assertions about the existing APK. A
changed native format requires review and updated provenance/audit rules.

| Artifact | Path | Contents |
|---|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 14 native `.so` files, attribution assets, no RPC/GGUF |
| Instrumentation APK | `app/build/outputs/apk/androidTest/debug/...` | Test harness for T7 smoke test |
| JVM test report | `app/build/reports/tests/testDebugUnitTest/` | `NativeServerLauncherTest` results |
| Lint report | `app/build/reports/lint-results-debug.html` | Lint findings |
| APK audit | stdout/stderr | Passed or detailed error list |

## Tool versions

Record actual versions in the build log:

```powershell
java -version
./gradlew --version
& "D:\softwares\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat" --list_installed
& "D:\softwares\android-sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe" --version
python --version
```

## APK hash and size

Pending: record both produced APKs' hashes and sizes for traceability
(the instrumentation filename may vary; use the actual build output):

```powershell
Get-FileHash app/build/outputs/apk/debug/app-debug.apk
(Get-Item app/build/outputs/apk/debug/app-debug.apk).Length
Get-ChildItem app/build/outputs/apk/androidTest/debug/*.apk | ForEach-Object {
    Get-FileHash $_.FullName
    $_.Length
}
```

## APK manifest inspection

Use SDK `apkanalyzer` to inspect the merged/packaged manifest:

```powershell
& "D:\softwares\android-sdk\cmdline-tools\latest\bin\apkanalyzer.bat" manifest print app/build/outputs/apk/debug/app-debug.apk
```

Verify:
- `minSdkVersion="26"` and `targetSdkVersion="35"`
- No unintended exported activities, services, or receivers
- Effective native extraction settings, including `extractNativeLibs`
- Permissions and `<uses-native-library>` declarations (if any)
- ABI contents: `arm64-v8a` only (inspect ZIP members separately)

These checks are pending. The Python ZIP auditor is not currently a
working binary-manifest decoder and must not substitute for SDK inspection.

## Disposal

These commands use a disposable worktree or archive for clean-build
reproduction.  Never run destructive cleanup (e.g. `git clean -fdX`)
on the user's working directory without explicit confirmation.
