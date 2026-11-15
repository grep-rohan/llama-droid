# Model Import — Verification Evidence

## Current status — Native-stage blocker resolved

The owner subsequently requested **“address blocker”**, authorizing host-only
re-staging of the verified local archive and rerunning the blocked baseline.
The receipt/stage verification now passes, **43 JVM tests pass**, lint has
**0 errors / 4 existing warnings**, and both APKs build. The earlier **94 passing
Python tests** remain applicable; their inputs are unchanged. See the follow-up
results below for commands and artifact identities.

No production source, dependencies, toolchain, native library bytes, or phone
state changed during baseline verification. The owner subsequently authorized
reconciling MI-00 and implementing **MI-01 only** (“y” after the explicit proposal).
MI-00 is complete: the existing checkout is the reviewed implementation source,
with unchanged tracked app/build inputs and the documented unrelated-work
exclusions. A separate clean checkout is not required to establish this baseline;
no clean-checkout reproduction is claimed. MI-02 onward and phone access remain
unauthorized.

## MI-00 — Initial host preflight (superseded baseline blocker)

Run date: **2026-11-15 UTC**. The owner replied **“auth”** to the explicit
proposal for **MI-00 only**: inspect workspace/tooling, run baseline host
checks, and document evidence. This authorizes neither MI-01 nor device access.

**Result:** 94 Python tests pass. The existing native stage is rejected by the
current verifier; Gradle stops at that same prerequisite before JVM tests,
lint, or either APK build. **MI-00 remains incomplete and MI-01 is blocked.**
No model-import implementation or runtime validation has occurred.

### Source and workspace boundary

- Worktree: `D:/workspace/projects/llama-droid`, branch `main`, baseline HEAD
  **`d903e24d664fb076de781f4877210304d6e623a9`**.
- `git diff HEAD -- app scripts gradle gradlew gradlew.bat build.gradle.kts
  settings.gradle.kts gradle.properties third_party` was empty before checks.
  This is a baseline of unchanged tracked build/source files in a **dirty
  worktree**, not a clean-checkout reproduction or a newly isolated worktree.
- Existing worktrees were inventoried, not modified:
  `llama-droid-integration` (`merge/native-runtime`, `d903e24`),
  `llama-droid-native-runtime` (`fix/native-runtime`, `9036aa9`), and
  `llama-droid-native-runtime-clean` (detached `955bfe3`).
- Exact pre-existing exclusions: modified `.gitignore`, `CAPABILITY-MAP.md`,
  `tasks/plan.md`, `tasks/todo.md`; untracked `SPEC-model-import.md`; and
  untracked `app/.cxx/` (36 files). No stash, reset, cleanup, source copying,
  worktree switching, or changes to those files during baseline commands.
  Only the targeted MI-00 tracker bookkeeping described below follows checks.
- `app/.cxx/` is not referenced by the current Gradle build configuration;
  its existing files were neither compiled nor removed. Before/after SHA-256
  inventories confirm all 36 file contents and paths are unchanged.
- `.gitignore` remains byte-for-byte unchanged. It ignores this new evidence
  file, so this file is added explicitly with `git add -f`, not an ignore edit.
  Existing planning changes are not included in the evidence-only commit.
- Host-generated output is confined to normal ignored caches/build directories
  and `.cache/model-import/mi-00-20261115T200724Z/`. The native stage is unchanged
  (before/after inventory: 14 libraries plus receipt). No native reimport,
  dependency/toolchain changes, or generated binary commits were performed.

### Approved-document provenance

Specification, plan, and detailed tasks identify themselves as owner-approved;
implementation authorization is separately limited to MI-00 by this conversation.
The approved documents are local, uncommitted work, not content at baseline HEAD.
These are SHA-256 identities of the exact inputs **before tracker bookkeeping**:

| Input | SHA-256 |
|---|---|
| `SPEC-model-import.md` | `5c94280b62bdac36c30377ba43f505c35c2e4a76fe5bc52541231b1fa7d48a52` |
| `tasks/plan.md` | `0440b74ada5eb995755e9d87ccfbeffbbc2a14d0d9dea28747a8411f8b4cf42f` |
| `tasks/todo.md` | `fc030791b728900cf81a8cb1b401990445c7b1987e28b8f84a3238d6c674b43c` |
| `CAPABILITY-MAP.md` | `418d7e63e3529fd431b81e08d48fb8bbce31f8042592a07c18c4c8cb64a5a589` |
| `.gitignore` | `e45555239189172c748faeda86a7f4d0069ec75cca3893f294e0a407f7739532` |
| `third_party/llama.cpp.json` | `c944ecfbca57152e998bf19581ed99037f258b3b57afbe2e914961b3cc7331e7` |

Local preflight JSON preserves the original status/worktree list, exclusion
hashes, wrapper identity, environment, and stage inventory. A local pre-edit
tracker snapshot and pre-existing document diff preserve the bookkeeping boundary.

### Actual host/tool prerequisites

| Component | Observed for this run |
|---|---|
| Host/shell | Windows 11 10.0 amd64, Git Bash; working directory `/d/workspace/projects/llama-droid` |
| Python | `C:/Program Files/Python314/python.exe`, **3.14.7** |
| Java | Temurin **17.0.21+9**, `C:/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot/bin/java` |
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot\` |
| SDK configuration | Existing ignored `local.properties`: `sdk.dir=D:/softwares/android-sdk`; `ANDROID_SDK_ROOT` and `ANDROID_HOME` are unset |
| Gradle | Wrapper **8.10.2**, revision `415adb9e06a516c44b391edff552fd42139443f7`; launcher/daemon JVM 17.0.21 |
| Project plugins | AGP **8.8.2**, Kotlin **2.0.21**; wrapper's embedded Kotlin **1.9.24** is separate |
| SDK inventory (read from `source.properties`) | Command-line tools **12.0** at `D:/softwares/android-sdk/cmdline-tools/latest`; platform `android-35` revision **2**; build-tools **35.0.0**; platform-tools **37.0.1** |

No SDK manager, ADB, installer, emulator, or manifest decoder was run. Tool
inventory is not a platform runtime result. The current JDK differs from the
historical native evidence's 17.0.14; no JDK was installed or selected by this task.

### Exact baseline commands and results

Commands ran once from the worktree root, using the existing environment:

```bash
git status --short
git worktree list
python scripts/import_llama_release.py --verify-only
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
./gradlew --version
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

| Check | Actual result |
|---|---|
| Native stage verification | **Exit 1**: `Invalid stage receipt: Receipt provenance/payload mapping differs from manifest` |
| Python discovery | **Exit 0**, **94 tests in 5.203s**, all pass, no skips (50 importer + 44 APK auditor) |
| Gradle version | **Exit 0**, 8.10.2 |
| Combined JVM/lint/APK baseline | **Exit 1**, `BUILD FAILED in 41s`, `:app:verifyNativeStage FAILED`; 1 actionable task executed |
| JVM discovery/execution | **Blocked** before execution; no current JVM test count or pass claimed |
| Lint / debug APK / instrumentation APK | **Blocked** by stage verification; no fresh lint or compiler/APK success claimed |

The Gradle `checkKotlinGradlePluginConfigurationErrors SKIPPED` line is a
conditional configuration task, not a test result. Existing `app-debug.apk`
output was not produced by this run and is not accepted as current evidence.
The opt-in native-packaging integration checks and final artifact/manifest audit
were not run: those belong to MI-19, not this blocked baseline.

Raw local evidence under `.cache/model-import/mi-00-20261115T200724Z/`:
`preflight.json`, `native-stage.log`, `python-tests.log`, `host-exit-codes.txt`,
`gradle-version.log`, `gradle-baseline.log`, `gradle-exit-codes.txt`,
`read-only-diagnosis.json`, `todo-before-bookkeeping.md`, and
`preexisting-document-changes.patch`. Logs were captured with Bash `pipefail`
and `${PIPESTATUS[0]}` so `tee` cannot conceal a failing check. These ignored
host logs are retained locally, not committed or presented as device evidence.

### Smallest failing step and next action

Read-only inspection of the actual receipt shows a legacy flat mapping of
archive-member names to digests. The current importer requires a versioned
receipt including archive/release/source provenance and the APK payload mapping.
All **14 actual staged library hashes match** the committed manifest, but that
does **not** make the stage valid or permit bypassing the receipt gate.

The existing local archive was independently read/hash-checked:

- `.cache/llama-b12020-bin-android-arm64.tar.gz`: **78,982,432 bytes**.
- SHA-256: `64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec`;
  size and digest match the committed provenance manifest.
- No archive extraction, receipt editing, re-staging, verifier bypass, or stage
  repair was performed. No earlier native test result substitutes for this run.

**Proposed next host-only action, requiring owner confirmation:** re-stage that
verified local archive using the current importer, then rerun the failed native
verification and the blocked Gradle baseline. This changes generated host stage
files, not production source or a phone; never hand-edit a receipt to pass.
The already-passing Python suite need not be repeated unless its inputs change.
A fully clean implementation worktree/source decision remains pending before
advancing beyond MI-00; no approved documents or native stage were copied into
another worktree by this task.

### Safety and completion boundary

- **No phone access or writes:** no ADB calls (including inventory), installs,
  instrumentation, launches, stops, package changes, or owner-model operations.
- **No model-import code was implemented, compiled, or device-tested.**
  Compilation was attempted only for the existing baseline and was blocked.
- All model-import runtime, provider, cancellation, process-death, >2 GiB,
  backup/transfer, and physical-device criteria remain unverified.
- Initial tracker bookkeeping recorded MI-00 authorization, evidence, and
  blockers; completion/build acceptance remained unchecked at that time.
  MI-01–MI-23 and every checkpoint/device gate remained pending. Existing native
  history is untouched.

## MI-00 follow-up — Authorized stage repair and passing host baseline

Run date: **2026-11-15 UTC**; re-stage began at **20:18:02 UTC**. Authorization:
owner requested **“address blocker”** after the proposed local re-stage and
baseline retry were described. This extends MI-00 to that generated-host-stage
repair only; it does not authorize model-import implementation or phone access.

Source/worktree and environment are unchanged from the initial preflight.
Follow-up HEAD: **`8e2fc47f665f64451704d7851ec1cd3b16c01346`**, an evidence-only
commit over the original baseline. Tracked app/scripts/build/native-provenance
inputs still match HEAD with no source changes. Existing dirty planning files
and `app/.cxx/` remain excluded. Their contents were checked unchanged before
follow-up bookkeeping; no stash, reset, or copying into another source tree.

### Commands and actual results

The archive size and SHA-256 were checked again against the committed manifest
before the importer was allowed to mutate the stage. The existing current
importer, not a hand-edited receipt, performed the complete transactional import:

```bash
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

| Check | Actual result |
|---|---|
| Native import | **Exit 0**; 14 byte-preserved payloads staged from the pinned local archive; no download |
| Native verification | **Exit 0**; exact payload set, ELF headers, versioned receipt, and transaction state pass |
| Gradle baseline | **Exit 0**, **BUILD SUCCESSFUL in 24s**; 80 actionable tasks, 68 executed and 12 up-to-date |
| JVM discovery/execution | **43 tests pass**, 0 failures/errors/skips: `NativeProcessProbeTest` **24**, `NativeServerLauncherTest` **19** |
| Lint | **0 errors / 4 warnings**: `RedundantLabel`, two `GradleDependency`, `ChromeOsAbiSupport`; same categories as historical native evidence; not fixed or suppressed |
| Production debug APK | `:app:assembleDebug` succeeds |
| Android test APK | `:app:assembleDebugAndroidTest` succeeds; **compiled only**, not executed |

These are existing native-runtime JVM tests, not tests of an implemented
model-import flow. The 94 Python tests were **not rerun**: importer/auditor/test
source and their fixture contracts are unchanged since their passing initial
run. Changing the generated real stage does not change those disposable-fixture
tests. No final clean-output build, APK content/manifest audit, device install,
or instrumentation execution is claimed by this follow-up.

### Generated-state and artifact identities

The importer regenerated `.payload_receipt.json` with SHA-256
`fe2b7459b888908415f8b52336f2f541a9c6faf80e06df4ea8198ff536af8d73`.
All **14 staged library SHA-256 values are unchanged** from the initial stage
and match the pinned manifest; archive bytes and provenance are unchanged.
Successful verification confirms no unresolved import marker or backup remains.
The importer's persistent host lock file is generated state, not app payload.

| Built artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,482,905 | `990419bb5d26ed00a19d08ff30bb14e93e23605f5afdc9ddd434697edbe79a99` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Retained follow-up evidence:
`.cache/model-import/mi-00-restage-20261115T201802Z/`, containing preflight
identities, original receipt/tracker snapshots, `native-import.log`,
`native-verify.log`, `gradle-baseline.log`, checked exit codes,
`verification-results.json`, copies of both JUnit XML reports and the lint XML
report, plus the final preservation/bookkeeping check. Reports record nonzero
test discovery and exact artifact/report hashes. Generated stage files, APKs,
and local evidence logs are **not committed**.

### Remaining boundary

The native-stage blocker and blocked host checks are resolved. At the end of
this repair, the tracker still held MI-00 open for a separate clean-worktree
decision. That was unnecessarily restrictive: its reviewed tracked source was
unchanged and unrelated work was explicitly excluded. The subsequent owner
approval of reconciliation and MI-01 selects this existing checkout without
stashing, resetting, or deleting unrelated work. MI-00 is therefore complete;
this is not a claim of a fresh clean-checkout build. No model-import code was
added during stage repair, and no ADB or other phone operation was performed.
