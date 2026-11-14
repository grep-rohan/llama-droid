# Native Build Verification (T8)

## Current isolated-branch update — blocker 2 resolved

User selected option A: retain the fourteen byte-preserved prebuilt payloads
and repair staging. No merge/relink/stripping/toolchain change was made.

Commands run from `D:/workspace/projects/llama-droid-native-runtime`:

```bash
python -m unittest discover -s scripts/tests -p 'test_import_llama_release.py' -v
python scripts/import_llama_release.py ../llama-droid/.cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug --rerun-tasks
```

- Red: 43 new host tests against the old importer failed with 32 failures
  and 56 errors/subtest errors. Failures included unasserted duplicate paths,
  wrong flat layout, missing receipt/ELF checks and unsafe publication.
- Green: **50 host tests pass**, including selected/unselected unsafe tar
  entries, matching-hash wrong-ELF fixtures, strict receipt/provenance,
  actual partial output, rename/rollback/marker failures, concurrent writer
  rejection and child-process hard exit after old-stage rename with retry.
  Tests used structural ELF fixtures, not executable fake native binaries.
  Staged link/junction flags are injected for portable fail-closed branch
  tests; tar link rejection uses real archive metadata. Windows host tested;
  Unix locking implementation and physical power-loss durability untested.
- Regression: `:app:testDebugUnitTest :app:lintDebug --rerun-tasks` passed
  in 21s, all 32 tasks executed; **19 JVM tests, zero failures/errors/skips**
  reconfirmed. Lint: zero errors, the same four existing warnings.
- Test host Python: **3.14.7**; importer uses only the standard library.
- Real archive import: exact **14 payloads + receipt**, all original digests
  unchanged. Verification succeeds without mutation or network access.
- Independent NDK 27.2.12479018 / LLVM 18.0.3 inspection on every staged ELF
  matches architecture/type/alignment/dependencies, with no RPATH/RUNPATH.
  Interpreter only on launcher; embedded UI symbols reconfirmed. See
  [artifact evidence](native-artifact.md) for provenance/API details.
- Full source commit transcription corrected against upstream tag/release;
  URL, release, archive hash and all payload hashes unchanged.
- Native outputs, receipt and transaction controls are ignored. The source
  archive was read from the other checkout; its stage/source remained intact.

Local logs: `.cache/blocker2-{red,green,regression,readelf,ui-symbols}.log`.
They are not committed build inputs. Failed imports persist an intent marker
and fail verification until successful retry; see [safe recovery](../BUILDING.md#failedinterrupted-staging-and-safe-retry).

**Not a native build result:** no `assembleDebug` was run in blocker 2, so
the existing APK remains blocker 1's no-native scaffold/hash below. T5 must
wire the read-only verifier into Gradle and prove packaging/attribution;
instrumentation, native APK audit and physical S25 launch remain pending.

## Isolated branch snapshot — blocker 1 resolved

User selected option A: isolated branch/worktree `fix/native-runtime` at
`D:/workspace/projects/llama-droid-native-runtime`, based on `4641a20`.
Original JNI/Ktor/inference changes and generated native stage remain
untouched in the other checkout. No original untracked harness/auditor or
merge proposal was copied. See [ordered blockers](../../tasks/todo.md#blockers-in-precedence-order).

### Commands and results

Run from the isolated worktree in Git Bash, with session-local variables
(no global PATH changes or tool installation):

```bash
export JAVA_HOME='/d/softwares/jdk/jdk-17.0.14+7'
export ANDROID_HOME='D:/softwares/android-sdk'
export ANDROID_SDK_ROOT='D:/softwares/android-sdk'
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --version
./gradlew --no-daemon :app:testDebugUnitTest --tests '*NativeServerLauncherTest'
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

- Versions: Gradle **8.10.2**, Temurin JDK **17.0.14+7** on Windows 11.
  AGP/Kotlin project pins remain 8.8.2/2.0.21; wrapper's reported embedded
  Kotlin 1.9.24 is not the project's compiler plugin version.
- Red: focused test compilation failed for missing launcher/layout classes.
- Green: **19 JVM tests, zero failures/errors/skips** (JUnit XML checked).
- Final clean build: **BUILD SUCCESSFUL in 29s**, 50 executed tasks.
- Lint: **0 errors, 4 warnings**: `RedundantLabel`, two `GradleDependency`
  suggestions, `ChromeOsAbiSupport`. No version/ABI changes to silence them.
- At blocker 1: **22 importer tests passed with gaps**; superseded by the
  50-test suite in the current blocker-2 results above.
- Checked merged manifest: minSdk 26/targetSdk 35, no permissions, exported
  activities, services, receivers or providers. No new launch/test endpoint.
- Launcher payload-name set matches all fourteen provenance entries.
- ZIP check: **no `lib/` members**, intentionally. No native stage was copied
  or imported; this is a minimal build/configuration check, not a native APK.

APK: `app/build/outputs/apk/debug/app-debug.apk`, **819,819 bytes**.
SHA-256: `58cf53cd14219df8007dbb039aa1059adc4af2741a40cdbd8498577a6c89424c`.

Reports: `app/build/test-results/testDebugUnitTest/`,
`app/build/reports/lint-results-debug.{xml,html}`. Local, ignored build logs:
`.cache/blocker1-{red,green,build,host-tests,toolchain}.log` (not required as
build inputs and not committed). APK hashes can vary on a later rebuild;
record the actual artifacts rather than assuming byte-reproducible signing.

**Not verified:** native extraction/permissions/linking, ARM64 execution,
instrumentation harness compilation/execution, native APK audit or physical
S25 compatibility. Native packaging (T5) still blocks T6 integration. No
model loading, inference, service, listener or WebUI claim is made.

## Original checkout snapshot — historical documentation review

The following results describe the original dirty checkout before branch
isolation; they are **not current isolated-branch results**:

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
command was run in that review. In that original checkout, launcher/JVM
sources were absent, stage verification was disabled, and harness/auditor
sources were untracked. No installed-app native execution or Android 17 linker
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
| NDK `27.2.12479018` | documented local installation | Approved for ELF inspection; original CMake/merge additions are not included here |
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

Native APK/SDK checks are pending. The blocker-1 check above inspected
only the merged manifest of a no-native APK, not extraction behavior.
The original Python ZIP auditor was not a working binary-manifest decoder;
its replacement must not substitute text-fixture checks for SDK inspection.

## Disposal

These commands use a disposable worktree or archive for clean-build
reproduction.  Never run destructive cleanup (e.g. `git clean -fdX`)
on the user's working directory without explicit confirmation.
