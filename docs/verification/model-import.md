# Model Import — Verification Evidence

## Current status — MI-02 host-verified

**MI-01/MI-02 are implemented and host-tested:** bounded records/names plus
checked record replacement, uncertainty handling and a compiled Android adapter.
**91 total JVM tests pass**, including **19 new MI-02 tests**. Lint reports
**0 errors / 4 existing warnings**, and both APKs build. Android filesystem
behavior is **unverified** pending MI-15/MI-20; this is not an end-user import
flow or device result. Owner authorized MI-02 with **“start next”** after MI-01.
MI-03 onward remains unauthorized. Detailed evidence follows below.

### Baseline reconciliation

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
no clean-checkout reproduction is claimed. That approval covered neither MI-02
nor phone access; the subsequent MI-02 authorization/results are recorded below.

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

## MI-01 — Bounded records and display names

Owner authorization: **“y”** after the proposal to reconcile MI-00 and implement
MI-01 only with failing tests first. Run date: **2026-11-15 UTC**. Worktree,
JDK/SDK/plugins and exclusions are the reviewed MI-00 environment. Starting HEAD
was `20f478f59991fb4bbc332947a8b7c3515647d0d9`; reconciliation is documentation-only
commit `d858794`. Verified implementation commits are **`dc14cd1`** (names) and
**`4e68a8d`** (records/codec). No new dependencies, Android components, UI wiring,
permissions, native payloads, or toolchain changes.

### Deliverables and contract

Exactly the five MI-01 deliverables were added under the approved prefixes:

- `app/src/main/java/io/github/greprohan/llamadroid/modelimport/ModelRecord.kt`:
  empty/active selection with epoch and optional pending nonce, plus the reset-id
  and intended-new-epoch record type. Values contain no source URI/path fields.
- `app/src/main/java/io/github/greprohan/llamadroid/modelimport/ModelRecordCodec.kt`:
  checked encode/decode and bounded stream read. The caller owns the stream;
  short reads are accepted, no `available()`/seek/reopen is used, and reads stop
  at EOF or 2,049 bytes. Invalid data is rejected, never repaired or promoted.
- `app/src/main/java/io/github/greprohan/llamadroid/modelimport/ModelName.kt`:
  scan at most 256 code points, neutralize control/format/lone-surrogate and
  line/paragraph-separator characters to spaces, trim the bounded prefix and
  use `Unnamed model` for an empty result. Valid names are <=256 code points /
  1,024 UTF-8 bytes; large inputs cannot cause whole-input UTF-8 allocation.
- `app/src/test/java/io/github/greprohan/llamadroid/modelimport/ModelRecordCodecTest.kt`:
  **21 tests**, including independent wire fixtures and adversarial input.
- `app/src/test/java/io/github/greprohan/llamadroid/modelimport/ModelNameTest.kt`:
  **8 tests**, including supplementary characters and bounded-prefix fallback.

V1 wire contract: ASCII `LDMR`, unsigned-byte version `1`, unsigned-byte kind
`0` (selection) or `1` (reset). All ids are exactly 32 lower-case ASCII hex bytes.
Selection fields: epoch, strict 0/1 pending flag and optional nonce, strict 0/1
active flag and optional payload id / signed-64-bit byte count / unsigned-16-bit
UTF-8 byte length / UTF-8 name. Reset fields: reset id and new epoch. Integers
are big-endian. No padding or trailing bytes are accepted. Empty selection
contains no payload fields; active size is at least 24. Names are decoded with
strict UTF-8 error reporting and must already satisfy the safe-name contract.
The maximum actual V1 selection is 1,138 bytes, within the 2,048-byte record cap.
No length/count can request an unbounded allocation.

Decoding a reset record is **not** evidence of committed consent or permission to
delete files. Authoritative-path checking, checked file replacement, store
ownership, and reset execution are MI-02/MI-03/MI-13 work, not implemented here.
Malformed-record errors use the fixed `Invalid model record` message without
retaining parser causes or untrusted metadata. External stream I/O errors
propagate to the caller; no diagnostics/logging boundary is implemented here.

### Test-first evidence and exact verification

The name slice was tested, linted/built, regression-tested and committed before
the record slice. The codec was expanded through tested selection, reset/read,
and adversarial-schema increments, then fully verified and committed.
Compiling no-op contracts were used for the initial RED runs; failures were
actual assertions, not missing symbols, missing tests, or compilation failures.

| Retained RED result | Discovery / intended failures | GREEN result |
|---|---|---|
| `name-red.log` / `.xml` | 8 tests, 7 failed | 8 name tests pass (`name-green.xml`) |
| `selection-red.log` / `.xml` | 5 codec tests, 5 failed | 5 codec tests pass (`selection-green.xml`) |
| `reset-read-red.log` / `.xml` | 10 codec tests, 4 failed | 10 codec tests pass (`reset-read-green.xml`) |

Every RED run had zero errors/skips and exit 1. Successful runs had exit 0.
Commands from the repository root, with successive tests/implementations between
RED/GREEN repetitions:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelNameTest'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelNameTest' :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelRecordCodecTest' --tests '*ModelNameTest'
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Final focused discovery: **29 tests** (21 codec + 8 names), all pass.
Final unfiltered JVM regression: **72 tests** (29 new + 24 native probe + 19
native launcher), **0 failures/errors/skips**. Full Gradle verification:
**BUILD SUCCESSFUL in 28s**, 80 actionable tasks (13 executed / 67 up-to-date).
Native-stage verification also passes within that build. Lint: **0 errors / 4
existing warnings** (`RedundantLabel`, two `GradleDependency`,
`ChromeOsAbiSupport`); no suppression or dependency upgrade. Kotlin production,
unit-test and Android-test compilation succeed; Android instrumentation is
**compiled, not executed**. The earlier 94 Python passes remain applicable:
the host scripts and test inputs have not changed, so that suite was not rerun.

Coverage explicitly includes unknown magic/version/kind/flags, every identity
field, all empty/active/reset truncations, trailing/oversized records, invalid
lengths/UTF-8, negative/too-small/64-bit sizes, maximum Unicode names, unsafe or
huge input names, missing names, short/zero-progress stream reads, a guarded
large synthetic stream, caller-owned resource lifetime and I/O failure.
These are pure JVM contract tests, not Android filesystem/provider evidence.

### Artifacts, exclusions, and remaining work

| Built artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,482,963 | `54b046ed2a336f91fd1bb3a7e013f7daa57ef59fd3c11cbcc9558f5a4c9ad251` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Local evidence: `.cache/model-import/mi-01-20261115T202408Z/`. It retains command
logs/exit codes, RED and GREEN XML snapshots, all four final JUnit reports,
lint XML, source/artifact/report hashes in `results.json`, preflight identities,
the original tracker snapshot, and the final preservation/bookkeeping check.

All native-stage contents (including the receipt), `.gitignore`, `app/.cxx/`
and unrelated approved planning content are unchanged. Tracker edits are scoped
to the current gate and MI-00/MI-01 results; MI-02 onward and historical native
records remain unchanged. New source/tests/evidence are committed; tracker
bookkeeping remains in the existing uncommitted planning document so its prior
owner changes are not swept into an implementation commit.

**MI-01 is implemented, JVM-tested and compiled; not device-tested.** It is not
wired to file persistence, a controller, or the Activity, so it adds no user
import/reset behavior yet. No ADB calls or phone writes occurred. Next smallest
task at MI-01 closure was **MI-02 — checked record replacement**, requiring its
own batch approval. That approval and its results follow. Checkpoint F1 remains
pending MI-03 and review.

## MI-02 — Checked record replacement

Authorization: owner requested **“start next”** following the MI-01 completion
report and explicit MI-02 next-task proposal. Scope is MI-02 only; no device
consent. Run date: **2026-11-15 UTC**. Starting HEAD:
`fde2a8df283da0193fa7981381c58d6b2bb082c9`; same reviewed source/exclusions and
MI-00 JDK/SDK/toolchain. Implementation commits: **`bb5fb4f`** (protocol/fakes),
**`13407f3`** (Android adapter/additional cases), **`443bdfe`** (sidecar readback
fault matrix). No dependency, native payload, manifest or Activity changes.

### Five deliverables

Under `app/src/main/java/io/github/greprohan/llamadroid/modelimport/`:

- `ModelFileOps.kt`: small no-follow entry/descriptor I/O boundary with
  device/inode/type/length/link identity, reader/writer ownership, exclusive
  creation and checked namespace operations.
- `CheckedModelRecord.kt`: fixed `selection`/`reset` slots; caller supplies the
  expected old record. Creates `.next` exclusively, checks identity, writes,
  explicitly flushes/syncs/closes, bounded-readbacks the temporary record, then
  verifies the old base again before same-directory rename. Confirms the new
  base's entire record and transferred inode, closed descriptors and absent
  sidecar before success. It never truncates/deletes/renames aside the old base.
- `AndroidModelFileOps.kt`: existing canonical private root only, root inode/type
  rechecks, direct-child names, `lstat`/`fstat`, no-follow/exclusive/nonblocking
  opens with mode 0600, unbuffered checked writes, `fsync`, checked `Os.close`,
  public `Os.rename` and checked `Os.remove` on a validated single-link regular
  file. No public `Os.unlink` assumption. The borrowed input stream does not own
  a second descriptor; its enclosing Reader owns/closes the original fd.

Under `app/src/test/java/io/github/greprohan/llamadroid/modelimport/`:
`FakeModelFiles.kt` and `CheckedModelRecordTest.kt` (**19 JVM methods**, with
per-operation fault matrices). No Android API mocks are counted as platform
proof. All mutation is in-memory fake data; no owner model paths are accessed.

### Outcome/resource contract

- `NEW`: expected replacement and candidate inode confirmed at base, `.next`
  absent, no live descriptor. An exception after successful rename can still
  reconcile to this outcome; its original failure is retained for the caller.
- `OLD`: exact old record and inode (or initial absence) confirmed after the
  attempt/cleanup. Only a known owned sidecar may be removed, after all handles
  stop. Sidecar-removal failure is explicit debt, never permission to delete the
  authoritative base or any payload.
- `AMBIGUOUS`: unknown/unreadable/mismatched namespace or unresolved descriptor;
  no rollback or guessed selection. Debt is conservatively true here, not proof
  that a sidecar physically exists. `quiescent=false` blocks subsequent I/O;
  explicit `close()` retries only the captured handle, never a reused descriptor.

Read-only lookup never promotes or deletes `.next`. Stale regular owned
sidecars are checked before removal, and cleanup false returns are detected by
postinspection. Primary write/sync failures retain secondary close/cleanup
failures. Caller must retain one serialized, exclusively leased store owner;
root lease/recovery/payload ownership are **MI-03**, not implemented here.
The protocol does not establish power-loss/directory-fsync durability.

### Test-first and build evidence

| Stage | Actual evidence |
|---|---|
| Initial publication RED | **1 discovered / 1 assertion failure**, exit 1; compiling no-op protocol |
| Publication GREEN | 1/1 pass, exit 0 |
| Reconciliation RED | **4 discovered / 3 assertion failures**, exit 1; pre-commit faults, false normal returns, post-rename exception |
| Reconciliation/resource GREEN | Passing focused tests after implementation, then expanded contract cases |
| Protocol slice | Focused tests + lint/both APKs pass; full JVM regression passes before commit |
| First Android adapter build | JVM/APK steps pass, **lint fails** with `NewApi` for `OsConstants.O_CLOEXEC` (SDK field since 27, minSdk 26) |
| Corrected adapter build | **BUILD SUCCESSFUL in 21s**, lint 0 errors / 4 existing warnings, both APKs built |
| Final expanded readback matrix | **91 JVM tests pass**, 0 failures/errors/skips; **BUILD SUCCESSFUL in 18s**, 80 actionable tasks (6 executed / 74 up-to-date), lint and APK tasks pass |

Final suite roster: MI-02 **19**, codec **21**, names **8**, native probe **24**,
native launcher **19**. Required malformed/postcondition/resource cases are not
skipped. Tests exercise create/info/write/flush/sync/close/readback/rename faults,
rename-then-throw, silent write/rename/close/delete failures, debt/retry, unknown
base, wrong inode, unsafe entry kinds/hardlinks, stale sidecars, reset/initial
slots, fixed operation ordering, byte preservation until rename, and no further
I/O while a descriptor remains live. No worker threads or Android processes are
created by these tests. Native-stage verification passes in each Gradle build.
The 94 earlier Python results remain applicable; their inputs are unchanged.

Exact repository-root commands, repeated only after relevant source/test edits
or for expanded regression scope:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*CheckedModelRecordTest'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*CheckedModelRecordTest' :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
javap -classpath D:/softwares/android-sdk/platforms/android-35/android.jar android.system.Os android.system.OsConstants
```

### API-26 flag correction provenance

SDK API metadata confirms public `Os.open`, `Os.rename` and `Os.remove` since 21,
`OsConstants.O_CLOEXEC` since 27 and `Os.fcntlInt` since 30. Neither newer Java
API is used in the final adapter. Atomic close-on-exec open is retained via the
verified ARM64 Linux UAPI value **02000000 octal = 0x80000**, not a lint
suppression or minSdk upgrade. This is specific to the already ARM64-only APK;
any ABI expansion requires reviewing native constants again.

Evidence: Android 8.0/API-26 [generic fcntl header](https://android.googlesource.com/platform/bionic/+/android-8.0.0_r1/libc/kernel/uapi/asm-generic/fcntl.h)
and its [ARM64 include/overrides](https://android.googlesource.com/platform/bionic/+/android-8.0.0_r1/libc/kernel/uapi/asm-arm64/asm/fcntl.h).
The installed, unchanged NDK **27.2.12479018** headers agree:
`sysroot/usr/include/asm-generic/fcntl.h` and
`sysroot/usr/include/aarch64-linux-android/asm/fcntl.h`. Header snapshots and
SDK API metadata results are retained locally. This source/API check is not an
API-26 or S25 runtime test of fd flags/closing/linking.

### Artifacts and remaining gate

| Built artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,482,963 | `d949e7d72878b1d93e065e4ecfc9c999f055bae32a0baf69d0f353e9b7adb9ea` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Local evidence: `.cache/model-import/mi-02-20261115T204241Z/`: preflight/tracker
snapshots, RED/GREEN XML and logs, checked exit codes, initial failing lint log,
final five JUnit reports/lint XML, `android-public-api.txt`, NDK header snapshots,
source/artifact/report identities in `results.json` and final preservation check.
No generated files, APKs, logs or native payloads are committed. Existing owner
planning changes remain uncommitted; tracker bookkeeping does not sweep them
into the implementation commits.

**MI-02 is implemented, fake-I/O/JVM-tested and Android-compiled, not
Android/device-tested.** Filesystem syscall/descriptor/rename behavior must be
validated by MI-15/MI-20; source review, JVM fakes and lint are not substitutes.
No ADB, device installation/launch/stop, process-death, backup or owner-data test
occurred. Native stage, `.gitignore`, `app/.cxx/`, toolchain and unrelated files
are unchanged. Next smallest task is **MI-03 — authoritative selection recovery**,
requiring its own authorization; Checkpoint F1 remains pending that task/review.
