# Native Build Verification (T8)

## Host verification — blocker 5 complete

**Subsequent device result:** the physical S25 passed 25/25 instrumentation
cases, including real pinned `--version` (exit 0 in 101 ms). However Gradle's
connected-test cleanup removed both packages and may have removed private
app data. Device actions are now paused for owner direction/safer retest;
Checkpoint C is not complete. See [native-s25.md](native-s25.md). Historical
no-device statements below describe the earlier host-verification phase,
not current availability or a reason to waive the observed cleanup issue.

User selected option A approving Checkpoint B and the harness/clean-build
work. Final functional revision: **`955bfe3f58cf515943f3ebfa6b76748dcc11092d`**.
The detached worktree at `D:/workspace/projects/llama-droid-native-runtime-clean`
was initially created at `82ea394`, then advanced while Git-clean to
`955bfe3` after an additional cancellation counterexample. The same fresh
pinned download was rechecked/restaged and the entire verification rerun.
`git status --porcelain=v1` was empty before and after final verification; no
untracked implementation, copied stage, SDK configuration file or build
output from either existing checkout was used. The original dirty checkout
remains untouched. This worktree and its audited outputs are retained for
future device testing; subsequent documentation-only commits do not change
this functional source revision.

### Final clean-source reproduction (commands to recreate)

```bash
git worktree add --detach ../llama-droid-native-runtime-clean 955bfe3
cd ../llama-droid-native-runtime-clean
export JAVA_HOME='D:/softwares/jdk/jdk-17.0.14+7'
export ANDROID_HOME='D:/softwares/android-sdk'
export ANDROID_SDK_ROOT='D:/softwares/android-sdk'
export PATH="/d/softwares/jdk/jdk-17.0.14+7/bin:$PATH"
mkdir -p .cache
curl --fail --location --retry 3 --max-time 180 \
  --output .cache/llama-b12020-bin-android-arm64.tar.gz \
  https://github.com/ggml-org/llama.cpp/releases/download/b12020/llama-b12020-bin-android-arm64.tar.gz
printf '%s  %s\n' \
  64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec \
  .cache/llama-b12020-bin-android-arm64.tar.gz | sha256sum --check
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
python scripts/tests/check_native_packaging.py -v
```

- Fresh release download succeeded; size **78,982,432 bytes**, pinned
  archive SHA-256 matched. Fourteen original payloads staged/verified, no
  relinking, source-building, fake ELF execution or model download.
- **94 Python tests pass** (50 importer + 44 auditor), no skips.
- **43 JVM tests pass** (19 launcher + 24 probe), zero failures/errors/skips.
- Final clean build: **BUILD SUCCESSFUL in 39s**, all 81 tasks executed.
  Both debug and instrumentation
  APKs compiled. Lint: **0 errors, 4 unchanged warnings** (`RedundantLabel`,
  two `GradleDependency`, `ChromeOsAbiSupport`).
- **12 opt-in real Gradle/APK checks pass in 156.839s**, including the shipped
  auditor CLI, using disposable copies created from this checkout.
- Production ZIP/payload audit passes. SDK `apkanalyzer manifest print`
  inspected both actual APKs. Source before/after status remained empty.
- Existing global Gradle dependency/wrapper caches, installed SDK/JDK and
  local debug signing key were reused. This is clean-source reproduction
  on this Windows host, not a hermetic/cross-host/release-signing promise.

### Actual tool versions

| Tool/component | Observed |
|---|---|
| Java + javac | Temurin **17.0.14+7**, javac **17.0.14** |
| Gradle wrapper | **8.10.2**, revision `415adb9e06a516c44b391edff552fd42139443f7` |
| Project plugins | AGP **8.8.2**, Kotlin **2.0.21** unchanged; wrapper embedded Kotlin is **1.9.24** |
| Host/Python | Windows 11 amd64; Python **3.14.7** |
| SDK command-line tools at `latest/` | Actual `source.properties`: **12.0** (`cmdline-tools;12.0`) |
| SDK platform/build-tools | `platforms;android-35` revision **2**; build-tools **35.0.0** |
| SDK platform-tools inventory | **37.0.1** (the earlier ADB no-device observation was separate) |
| Pinned NDK inspection tools | **27.2.12479018**, LLVM **18.0.3** |

`sdkmanager --list_installed` exited 0 but warned that command-line tools
understand SDK XML through version 3 while version-4 metadata was seen.
Recorded as a non-blocking inventory-tool warning, **not silently fixed by
installing/upgrading tools**. CMake 3.22.1 and NDK 27.0 are also installed
but not used by this no-JNI/no-source-build module.

### Reproduced APK identities and manifest/contents checks

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,208,764 | `19c39bfbb8315d8deb0de5efab9b7f1ffe2b9c68f5306907e6c6b2086afba2b1` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

These match the final hardened source-worktree build on this host; do not
assume debug signing/packaging produces these hashes elsewhere. The earlier
`82ea394` test APK is superseded; use these `955bfe3` identities.

- Production: exact fourteen pinned ARM64 hashes and source attribution;
  SDK 26/35, debug=true, extraction=true, one unexported Activity; no
  permissions, instrumentation declaration, service/receiver/provider or
  activity alias. No probe, fake-test or smoke-test class names in DEX and
  no `native-provenance.json` asset.
- Instrumentation: package `io.github.greprohan.llamadroid.test`, SDK 26/35,
  runner `androidx.test.runner.AndroidJUnitRunner`, target package
  `io.github.greprohan.llamadroid`. Contains probe/fake/smoke classes and a
  provenance asset byte-identical to `third_party/llama.cpp.json`; no native
  libraries or GGUFs. The SDK does **not** mark this artifact `testOnly`;
  AndroidX adds `REORDER_TASKS`, test-service package queries, runner-library
  use and three exported InstrumentationActivityInvoker activities. These
  are confined to the test APK, **not the user APK**; do not distribute or
  describe the instrumentation APK as the application.

### Bounded probe evidence and limitations

`src/sharedTest/java/` contains one probe and one set of fake-Process cases,
compiled for both JVM and androidTest, never main. Real informational-command
instrumentation lives under `src/androidTest/`. Test provenance is generated
from the pin, not duplicated manually into production assets. No new
production/test dependency versions or manifest hooks were added.

The probe drains both streams concurrently into independent fixed byte
rings (default 16 KiB each), including large single-line excess output.
Execution deadline is 30s, counting recorded launcher latency when start
returns; cleanup has a separate <=2s budget. Process wait/destroy/reap,
stream closing and thread joins are deadline-bounded, including broken API
simulation. Forced closure is not treated as EOF; reader Errors, leaked
readers/operations, failure to reap, interruption and cleanup failures are
explicit failures. Timeout cancellation is non-interrupting: Java/NIO
interruptors can synchronously call a blocking close on the controller.
Workers that cannot stop are reported as cleanup failures, not success.
A 90s whole-instrumentation-test guard also covers
prelaunch file hashing/OS startup, which no host fake proves reliable.

Red-before-green tests caught missing implementation, an uncaught reader
Error falsely appearing successful, and forced pipe closure falsely
appearing drained. A real AbstractInterruptibleChannel counterexample also
caught `cancel(true)` blocking the controller in its close callback; this
was fixed before final source verification. All 24 shared cases now pass
on JVM. That is **not an ART
or native-executable execution result**.

Pinned source inspection established the expected upstream output:
`version: %s (build %d, commit %s)` from
`common/build-info.cpp.in` at commit
`0ec4027a2a0183de87c007069adddc06e665f7de`; `common/arg.cpp` handles
`--version` by printing that header then `exit(0)`. The actual pinned common
library embeds `0ec4027a2` (nine characters); NDK symbol inspection plus
ELF LOAD-address mapping read the actual `LLAMA_BUILD_NUMBER` data as **12020**
(source-worktree log `.cache/blocker5-static-build-number.log`, same pinned
payload hash). Matcher requires build **12020**
and that exact short commit, not a generic executable name or `b12020`
substring. Source URLs:
https://github.com/ggml-org/llama.cpp/blob/0ec4027a2a0183de87c007069adddc06e665f7de/common/build-info.cpp.in
and
https://github.com/ggml-org/llama.cpp/blob/0ec4027a2a0183de87c007069adddc06e665f7de/common/arg.cpp.
Static source/strings are not a claim that the server ran here.

On device, the smoke test will require target UID == current UID, validate
installed native paths/permissions and every payload digest, launch only
`--version`, require exit 0 within 30s, real EOF/cleanup and the pinned
header, then assert no direct child remains alive. T9 still needs physical
S25 properties, exact installed/test APK identities and a post-run process
cross-check; an emulator or another ARM64 model does not complete it.

### Evidence locations and remaining blocker

In the retained clean worktree, ignored `.cache/blocker5-*` outputs contain
download, host/build/audit/packaging logs, full tool inventory, both SDK-decoded
manifests, APK identity JSON and empty pre/post source-status files. Integration
case logs are under `.cache/blocker3/`. JUnit/lint reports remain in
`app/build/`; none are required untracked implementation inputs.

**Device execution is BLOCKED.** `adb devices -l` returned no devices at the
start of this verification. No install, connected instrumentation run,
installed extraction, real native version output, inference or WebUI result
was observed. T5's installed extraction and T7/T9 ART/native cases stay
unchecked; native-runtime and Checkpoint C remain incomplete. Do not start
model-import/lifecycle/chat implementation from these host results.

## Isolated branch snapshot — blocker 4 auditor verified

User authorized the standalone auditor after `a69a90e`. Added
`scripts/verify_apk.py` and adversarial tests; the opt-in packaging suite
also invokes the shipped CLI against real APKs built in disposable copies.
No Android source, Gradle policy, native payload or original checkout changed.

```bash
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
python scripts/tests/check_native_packaging.py -v
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug --rerun-tasks
```

Results on the same documented Windows/Python/JDK/SDK host:

- Red: focused auditor tests failed to import the not-yet-implemented
  module; this was an error, not a zero-test success.
- Green: **94 host tests pass: 50 importer + 44 auditor**, no skips.
  Fixtures exercise exact pinned inventory/hashes, ABI/ELF sanity, source
  attribution, unsafe/duplicate/case/NUL paths, links/special entries,
  directories, utility/staging leaks, renamed ELF/GGUF, CRC/size errors,
  encryption/compression policy, ZIP64/multi-disk/resource preflight and
  read-only/CLI contracts. Native fixtures are structural bytes, never run.
  A Windows fixture was corrected to put actual backslashes in ZIP headers
  rather than testing the writer's already-normalized safe name.
- **12 Gradle/APK integration tests pass in 91.001s**; positive checks now
  invoke the new auditor CLI inside disposable project/stage copies.
- Forced JVM/lint regression: **BUILD SUCCESSFUL in 17s, 35 executed
  tasks**; JUnit XML confirms **19 pass, no failures/errors/skips**.
  Lint: 0 errors, the same 4 existing warnings. No new APK assembly in this
  step; the previously built real APK was not modified.

Real CLI result: **ZIP/payload audit passed**, 79,208,764 bytes,
SHA-256 `19c39bfbb8315d8deb0de5efab9b7f1ffe2b9c68f5306907e6c6b2086afba2b1`
(unchanged). Thirty ZIP entries, 269,605,833 bytes declared uncompressed;
all fourteen native files match provenance; attribution matches source.
No extraction, download, ELF execution or binary-XML decoding was attempted.
The CLI explicitly says SDK manifest/signing/device checks are **not
verified by this tool**. It never switches to size-only monolithic mode.

Separate SDK `apkanalyzer manifest print` was rerun against that same APK
(hash checked before/after): exit 0; correct package, SDK 26/35,
extraction=true, debug=true, one unexported Activity, no permissions,
aliases, services, receivers, providers or native-library declarations.
This separate result does not make the auditor a binary-manifest decoder
or prove installed extraction/executable permissions/linker behavior.

Resource/format policy: standard non-prefixed, single-disk, non-ZIP64 ZIP;
<=512 MiB APK, <=8 MiB central directory, <=10,000 entries, <=256 MiB/member,
<=1 GiB total uncompressed, <=1 MiB per attribution source, <=100 findings.
Metadata preflight precedes ZIP object construction; member reads/hash/CRC
are streamed. Limits fit the actual artifact, not an arbitrary APK promise.
Name/magic checks cover listed ZIP members, not nested archives, arbitrary
obfuscation, signing-block contents or general malware analysis.

Local outputs: `.cache/blocker4-{red,green,audit,packaging,regression}.log`,
`.cache/blocker4-sdk-manifest.xml`, `.cache/blocker4-sdk-stderr.log`;
per-case/CLI integration logs under `.cache/blocker3/`. These are ignored
evidence outputs, not required source inputs.

**Still pending:** Checkpoint B review of current packaging/launcher,
bounded instrumentation, full clean-Git-checkout reproduction and physical
S25 proof. T5 installed extraction stays unchecked; T8 is not complete
merely because the current APK audit passes. No native startup/inference/
WebUI success or device compatibility is claimed.

## Isolated branch snapshot — blocker 3 host packaging verified

The user authorized fail-closed Gradle packaging on `fix/native-runtime`
after `32de970`. Source changes: `app/build.gradle.kts`, plus the opt-in
`scripts/tests/check_native_packaging.py` integration suite. The source
manifest needs no extraction override: AGP generates it from the DSL.
No original-checkout implementation was copied, no new dependencies,
release/toolchain changes, model loading or native transformation.

### Commands and results

On the documented Windows/JDK 17/SDK host (Git Bash; environment variables
from the blocker-1 setup below, Windows-form `JAVA_HOME` for `.bat` calls):

```bash
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python scripts/tests/check_native_packaging.py -v
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- Red: one real Gradle check removed only a disposable fixture's stage;
  baseline Gradle still **succeeded** and emitted a no-native APK. Test
  failed as intended. Initial implementation then rejected bad input, but
  two integration cases exposed hidden attribution/Python error causes;
  diagnostics were fixed and the entire suite rerun.
- Green: **12 actual Gradle/APK integration tests pass** in 96.841s, no
  skips/failures. They cover missing/tampered/extra stage/receipt, incomplete
  import, missing Python/notice, direct `packageDebug`, unchanged build
  verification, exact native bytes/assets, generated-output tampering repair
  and exclusion of sibling ABI/orphan candidate data. Only disposable
  project/stage copies under `.cache/` were mutated, never genuine inputs.
- Host discovery: **50 tests pass**. Final clean build: **BUILD SUCCESSFUL
  in 21s, 55 executed tasks**. JUnit XML: **19 pass, zero errors/failures/
  skips**. Lint: 0 errors, 4 unchanged warnings (`RedundantLabel`, two
  `GradleDependency`, `ChromeOsAbiSupport`). No manifest-source warning in
  final build; redundant `extractNativeLibs` override was removed.
- Always-running `verifyNativeStage` invokes `--verify-only --staging-dir`
  and fails clearly on errors. Verified native/attribution inputs are
  synced to separate generated roots with task dependencies; no receipt or
  transaction directories are inputs to JNI packaging. This is not a lock
  spanning stage verification/copy/packaging: do not import concurrently.

### Native debug APK and SDK manifest inspection

APK: `app/build/outputs/apk/debug/app-debug.apk`, **79,208,764 bytes**.
SHA-256: `19c39bfbb8315d8deb0de5efab9b7f1ffe2b9c68f5306907e6c6b2086afba2b1`.

Independent ZIP/member inspection found exactly fourteen native entries,
all under `lib/arm64-v8a/`, all DEFLATE-compressed and each SHA-256 identical
to `third_party/llama.cpp.json`. Native entries are preserved despite the
AGP task name `stripDebugDebugSymbols`; the digest comparison, not the task
name, is the evidence. No duplicate ZIP names, other native payloads, RPC,
GGUF, receipt or transaction controls. Both attribution assets equal their
`third_party/` source bytes.

SDK tool invoked against the actual binary-manifest APK:

```powershell
& "D:\softwares\android-sdk\cmdline-tools\latest\bin\apkanalyzer.bat" manifest print app/build/outputs/apk/debug/app-debug.apk
```

Exit 0. Parsed SDK-decoded output confirms package `io.github.greprohan.llamadroid`,
minSdk **26**, targetSdk **35**, `extractNativeLibs=true`, debug=true;
one Activity with exported=false; no permissions, services, receivers,
providers or native-library declarations. No binary XML was decoded as
UTF-8. `useLegacyPackaging=true` controls compression/extraction intent;
`keepDebugSymbols` preserves bytes.

Local evidence outputs: `.cache/blocker3-{red,green,host,build}.log`,
`.cache/blocker3-manifest.xml`, `.cache/blocker3-apkanalyzer-stderr.log`,
`.cache/blocker3-apk-payloads.log`; per-case Gradle logs in
`.cache/blocker3/*.log`. Reports remain under `app/build/reports/` and
`app/build/test-results/`. Outputs are ignored, not committed build inputs.

**Still unverified:** installed package-manager extraction and permissions,
linking/CPU execution under app UID, instrumentation compilation/run,
standalone auditor and complete clean-checkout reproduction. T5's actual
installed extraction clause is explicitly unchecked pending T9. No S25
result, inference/WebUI claim or module-completion waiver.

## Isolated branch snapshot — blocker 2 resolved

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

## Reusable clean-build procedure — host verified

The current verification above executed this workflow from tracked source
in a detached worktree. Reproduce it in another disposable checkout with
the documented prerequisites, recording its own revision, commands, versions
and artifact hashes. Device execution remains a separate, blocked step.
Never use destructive cleanup on the user's working directory.

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

# 2. Obtain the pinned archive using the acquisition commands above, then stage.
# This branch never merges/relinks; preserve other checkout stages.
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only

# 3. Create local.properties (host-specific, ignored by Git)
echo 'sdk.dir=D:/softwares/android-sdk' > local.properties

# 4. Clean build + JVM tests + lint
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# 5. Instrumentation APK (compile only; run still requires physical S25)
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

Both current hashes/sizes are recorded above. Record fresh outputs on each
reproduction rather than assuming identical debug signatures:

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

These SDK fields have now been inspected for the current native debug APK
(see blocker-3 evidence above), not for an instrumentation APK or installed
package. Keep the same checks in the future full clean-checkout reproduction.
The original Python ZIP auditor was not a working binary-manifest decoder;
its replacement must not substitute text-fixture checks for SDK inspection.

## Disposal

These commands use a disposable worktree or archive for clean-build
reproduction.  Never run destructive cleanup (e.g. `git clean -fdX`)
on the user's working directory without explicit confirmation.
