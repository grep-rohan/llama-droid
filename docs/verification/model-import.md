# Model Import — Verification Evidence

## Current status — MI-12 / F3 technically host-verified; Android runtime unverified

**MI-00–MI-03 are complete in their host-only scope.** Authoritative selection
recovery, root ownership, validation and safe cleanup are implemented.
**223 JVM tests pass**. Nine route, four screen and seven adapter Android methods
are **compiled but not executed**, including the gated unknown-size provider case.
MI-12 and the F3 technical review are complete in host scope. Lint reports
**0 errors / 4 existing warnings**, and both APK targets pass. MI-04 adds the
fixed-header/sequential-copy path with closed-resource handoff, not selection
publication. MI-05 now adds the size/space/failure-cleanup policy. F1 source/evidence review
found no host-contract blocker. The owner then
directed **“don't stop until phoen required”**: continue host-only implementation
and actual technical checkpoint reviews through MI-19/H2, including MI-17A,
without per-task approval pauses. **MI-06 and F2 technical review are complete;
MI-09 and checkpoint A technical review are complete; F3 reviewed the integrated
screen/routes. MI-13 reset storage is next. Phone work is not authorized.**
Reviews are assistant technical reviews under this standing instruction, not
invented human sign-offs. Distinct human/device results remain explicitly pending
where required. No Android filesystem, process-death, backup, end-user import or
phone result is inferred from these JVM tests/compilation.

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
are unchanged. At MI-02 closure, the next task was MI-03 and required its own
authorization. That authorization/results follow; F1 owner review remains pending.

## MI-03 — Authoritative selection recovery and root ownership

Owner authorization: **“start next”** after the explicit MI-02 completion/MI-03
next-task report. Scope is MI-03 only, followed by F1 evidence preparation, not
MI-04 or device writes. Run date: **2026-11-16 UTC**, starting HEAD
`d1ad976db22fd083ae9512235d23f1c751cf804f`. Same reviewed worktree/source exclusions.
Implemented/tested commits: **`4aba8e4`** (lease), **`6fcbea3`** (store/root/recovery),
**`b1cb474`** (adversarial/resource coverage), **`fcdb9ef`** (committed-empty/root
replacement boundaries). No SDK/dependency/toolchain/native/manifest/UI changes.

### Five approved deliverables

Under `app/src/main/java/io/github/greprohan/llamadroid/modelimport/`:

- `ModelOwnership.kt`: synchronized root-keyed in-process lease; idempotent close
  cannot release a newer owner's lease. Also holds the small root/inventory
  boundary and Android implementation, keeping the existing record-I/O API
  unchanged. The production factory canonicalizes the supplied no-backup parent,
  uses fixed child `models`, checks creation, rejects a linked/non-directory root,
  remembers/rechecks root device/inode, and lazily enumerates direct children
  through a closed `DirectoryStream`. It never materializes the directory list.
- `ModelStore.kt`: one leased, caller-serialized recovery/cleanup owner. It reads
  authoritative reset state first, explicitly initializes empty selection only
  for an empty root or lone owned `selection.next`, validates authoritative
  selection/owned entries, then resolves active payload kind, links, exact
  signed-64-bit length, descriptor identity and readability before exposing its
  generated private path. It validates again after cleanup. Display names never
  become path components; `.next` is never promoted by inspection.

Under `app/src/test/java/io/github/greprohan/llamadroid/modelimport/`:
`ModelStoreTest.kt` (**17 methods**), `ModelRecoveryTest.kt` (**15 methods**), and
the extended `FakeModelFiles.kt` (fake root/inventory, named read/close faults,
and optional long stat length for synthetic tests). Exactly the five planned
files were added/modified. Inventory snapshots in the bounded fake fixtures are
not the production enumeration implementation.

`ModelStore.open(noBackupParent)` expects `Context.noBackupFilesDir` from its
future application-context owner; no Activity/singleton wiring is added here.
The lease excludes a second store instance, not concurrent operations on the
same instance: callers must serialize store methods, as the approved controller
will do. This is not cross-process locking.

### Authority, cleanup and resource outcomes

- Valid selection is the sole model authority. Old/new/explicit-empty file
  images retain exactly their authoritative epoch, pending nonce and active
  id/name/size. Uncommitted records cannot select a promoted payload or resume a
  dead copy. Missing/corrupt metadata with payloads, candidates, reset state or
  unknown entries blocks mutation; it never invents an empty state.
- Committed reset is reported as `RESET_PENDING` **before selection lookup**.
  It is not executed here (MI-13 owns reset recovery). Corrupt reset or any
  uncommitted `reset.next` blocks ordinary recovery without authorizing deletion.
- A complete preflight validates every recognized owned entry before any
  sweeping. Cleanup checks current authoritative selection, root identity,
  no-follow regular type, one link and unchanged entry identity before unlink;
  it confirms absence afterward. Only strict generated candidates, unreferenced
  payloads and owned `selection.next` are eligible. Sources, unknown names,
  unknown links/directories and the active payload are preserved.
- Failed record-sidecar cleanup exposes `recordDebt` and disables metadata-write
  eligibility. Unreferenced candidate/payload debt is separately visible and does
  not block current selection or eligibility when space permits. `canWrite` is a
  protocol eligibility flag, **not** a free-space/permission guarantee or an
  implemented import/replacement command. Recovery retries safe cleanup.
- Payload/record readers and inventory handles are captured before use. Pending
  close returns `RESOURCES_PENDING`, blocks another recovery I/O pass and keeps
  the lease. Explicit disposal retries the captured resources and cannot release
  ownership while any remains live; a new store can recover after successful
  disposal. No replacement worker/thread is started to conceal pending cleanup.

Root/type/containment/syscall behavior is **compiled only** for Android. Pure
root-validation faults are not real Android symlink tests. Payload resolution
opens/checks readability with constant-size reads, not a complete body/hash/GGUF
check, RAM-fit estimate, or inference compatibility claim. Large-length tests
use fake stats; they are **not** real >2 GiB imports or heap/device evidence.

### Test-first evidence and verification

All RED runs compiled and discovered nonzero tests, then failed assertions;
none was a missing-file/symbol/test-discovery failure. Each was followed by GREEN:

| Retained RED XML/log | Discovered in that class | Assertion failures |
|---|---:|---:|
| `lease-red` | 2 | 2 |
| `store-red` | 4 | 2 |
| `recovery-red` | 5 | 3 |
| `reset-order-red` | 7 | 1 |
| `root-guard-red` | 10 | 1 |

The root-guard case found and fixed a real orchestration gap: recovery must
revalidate the root after initial inventory and **before** publishing the empty
record, not merely before/after the whole operation. Existing Android record-I/O
root checks are retained as an additional boundary, not used to waive this test.

Exact repository-root command families, with relevant source/test edits or
expanded regression scope between repetitions:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelStoreTest'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelStoreTest' --tests '*ModelRecoveryTest'
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python --version
java -version
./gradlew --version
```

Focused RED/GREEN runs and the last focused run passed through 30 store/recovery
methods; the final unfiltered run includes the two additional boundary methods,
so **all 32 MI-03 tests pass**. Full final roster: store **17**, recovery **15**,
checked record **19**, codec **21**, names **8**, native probe **24**, native
launcher **19** = **123 tests**, 0 failures/errors/skips. The two synchronized
lease workers use bounded latch handshakes, are joined and asserted terminated
before the race test passes; no worker is left to mutate a later fixture.

The matrix covers old/new/empty file-image recovery, lost initial publication
acknowledgement, initialization faults/retry, stale next records, corrupt/missing
metadata, every owned entry's unsafe kinds/hardlinks, exact/missing/mismatched
lengths, read failure, changed payload/root identity, malformed/unowned names,
source-byte preservation, independent roots/simultaneous lease attempts,
record-versus-payload debt, inventory faults and record/payload/inventory pending
close ownership. File-image reconstruction and injected errors are not process
termination or physical-disk evidence.

Final Gradle result: **BUILD SUCCESSFUL in 23s**, 80 actionable tasks (6 executed /
74 up-to-date). Production compilation and both APK builds had also executed
successfully during the 34-second store/root integration runs; unchanged outputs
are reused in the final test-only expansion. Lint: **0 errors / 4 unchanged
warnings** (`RedundantLabel`, two `GradleDependency`, `ChromeOsAbiSupport`), no
suppression. Native stage verification passes in each Gradle invocation.
Python **3.14.7**, Temurin **17.0.21+9**, Gradle **8.10.2**/project plugins unchanged;
SDK remains the existing `local.properties` path. The prior 94 Python passes
remain applicable because their scripts, fixtures and provenance inputs are
unchanged; that successful suite was not redundantly rerun.

### Artifacts and retained evidence

| Built artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,482,963 | `9a27a9aaccbd4d1acc690ade9c4361673f971aa4a02b0d6228061a1b938258f7` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Production DEX symbol presence was checked for `ModelStore`, `ModelOwnership`
and `AndroidModelRoot`; this is a narrow packaging presence check, not a full
APK audit/manifest review or runtime test. Full clean-output/audit work stays
MI-19. Android instrumentation remains compiled, **not executed**.

Local evidence: `.cache/model-import/mi-03-20261116T095717Z/`. It retains preflight
identities/tracker snapshot, all five RED and subsequent GREEN XML/logs, focused
and unfiltered command output, exit codes, tools log, final seven JUnit reports,
lint XML, source/report/artifact hashes and final preservation check. Source,
tests and evidence documentation are committed. Tracker bookkeeping remains
with the owner's pre-existing uncommitted planning changes; these were not swept
into code commits. Native payloads/receipt, `.gitignore`, `app/.cxx/`, other
planning documents, toolchain and later tasks/native history are unchanged.
No model roots or owner data were accessed on Android: no ADB, installs,
instrumentation, launch/stop/reset, or device writes.

### Checkpoint F1 — Storage foundation ready for owner review

**Host evidence ready; owner review pending, not checkpoint approval.** All
MI-01–MI-03 cases run in the final regression (80 model-import + 43 existing JVM
cases); lint/APK checks pass, with no required preservation/ownership case
skipped or waived. Checkpoint review should examine the committed codecs,
checked replacement and recovery/root boundaries plus the retained fault and
resource results before MI-04. Approval of MI-03 execution does not itself
approve F1 or authorize the next slice.

No platform, actual process-death, power-loss, backup/transfer or physical-phone
result is inferred from this foundation. At MI-03 closure the next action was
F1 review/MI-04 authorization; the subsequent review and standing owner
instruction are recorded below. Model import, server lifecycle, inference and
WebUI milestones remain incomplete.

## F1 technical review — 2026-11-16

The owner requested the next step (F1 review), then explicitly requested
uninterrupted host work until a phone is required. Reviewed implementation:
**`fcdb9efaf08d1ece6a7f7a98440e3110e6aaa3f1`**, unchanged at review HEAD
`836799d4f6dc8733da42b304de1002c5edd1e0a8` (documentation-only difference).
All eight production model-import sources and six JVM test/fixture sources were
read against the P1 contract. No blocking defect was identified within that
contract; this is a bounded review, not a claim that no bugs can exist.

| Review boundary | Source/evidence checked | Finding |
|---|---|---|
| Bounded untrusted metadata | `ModelName`, `ModelRecordCodec`, independent wire fixtures, truncation/UTF-8/length tests | Fixed/bounded allocation, canonical safe names and strict record schema; no source path fields or parser-data diagnostics. |
| Old-record preservation | `CheckedModelRecord`, operation/fault matrix | Base unchanged until same-directory rename; temp readback/identity/closed-handle checks; old/new/ambiguous reconciliation rather than blind rollback. |
| False normal returns / debt | Write/rename/close/remove silent-failure cases | Readback/postinspection detects violated postconditions; unclosed resources block another I/O pass; known-base debt is not guessed publication. |
| Root/type/path ownership | `ModelOwnership`, `AndroidModelRoot`, `AndroidModelFileOps`, owned-kind/link/id/path tests | Canonical trusted parent and fixed child, no-follow single-link regular entries, generated payload names; lazy closed inventory. Actual Android syscall behavior still needs MI-15/MI-20. |
| Old/new/empty recovery | `ModelStore`, reconstructed file images and corruption tests | Only base authority; no `.next` promotion; corrupted/missing metadata does not invent a model or authorize deletion. |
| Cleanup / reset boundary | Full preflight, current-selection checks, source preservation, record/payload debt and reset-order cases | Cleanup remains limited to owned unreferenced entries; reset is reported pending, not executed before MI-13. |
| Resource and concurrency assumptions | Captured reader/inventory cleanup, lease tests, bounded latch worker teardown | Second store excluded; one store remains caller-serialized, not an implicit concurrent queue. No cross-process/hostile-writer guarantee is inferred. |

Fresh review audit compared every implementation/test SHA-256 with the retained
MI-03 results, verified all seven JUnit XML hashes and their nonzero **123/123**
results (80 model-import + 43 regression, no failures/errors/skips), inspected the
4-warning/0-error lint report and checked both APK byte sizes/SHA-256 identities.
No implementation or test inputs changed, so the successful test/build commands
were **not rerun merely for reassurance**. Exact commands/results/source hashes
remain in the MI-01–MI-03 evidence above; current audit is retained at
`.cache/model-import/f1-20261116T111011Z/review-audit.json`.

**F1 technical review is complete.** This does not claim a separate human review,
Android/process-death result or module closure. The latest owner instruction
authorizes incremental host progression, including technical checkpoint reviews,
through **MI-19/H2**; still record each actual review and any fixes before the
next slice. Do not mark a human/device-only checklist item as performed merely
because host progression is authorized. **Stop at MI-20**, before inventory,
installation/test/package-stop/data actions requiring physical-phone consent.
No MI-04 code, phone access or unrelated-work changes were made in this review.

## MI-04 — Fixed header and sequential byte copy

Authorized by the standing host-only instruction. Commits **`40c74d5`** (header)
and **`4d088df`** (ports/engine/tests). Exactly five approved deliverables:
`GgufHeader.kt`, `ModelSource.kt`, `ModelImportEngine.kt` under the production
`modelimport/` package, and `GgufHeaderTest.kt`, `ModelImportEngineTest.kt` under
the matching JVM test package. No prior production/test files were modified.

`GgufHeader` accepts the approved byte-level `GGUF` magic with little-endian v2/v3
and big-endian v3 version bytes; other magic/version/byte-order combinations and
prefixes shorter than 24 bytes fail. The two untrusted unsigned counts are never
used for allocation/traversal. Counts/body integrity and compatibility are not
validated; a header-passing, body-corrupt fixture intentionally passes.

The pure engine retains the 24-byte prefix once, creates a candidate only after
validation, writes prefix then a single 256 KiB buffer through actual source EOF,
and tolerates short/zero-progress bulk reads without seek, reopen or trusting
`available()`. Long counters use checked addition. Source close precedes explicit
candidate flush/sync/close; closed postconditions and a final cancellation check
precede `CopiedCandidate`. The engine cannot publish metadata. Failure retains
its primary cause, candidate ownership and pending-resource flag; cleanup closes
captured resources once, without concealing unresolved close. The source and
candidate/cancellation ports have no MI-05/MI-07 forward dependencies. Candidate
delete/space/reported-size policy is deliberately the next MI-05 slice.

RED evidence: header **5/5 assertions failed**, then GREEN; initial copy **2/2
failed**, then GREEN. An additional test found metadata could be accessed after
copy outside classified cleanup: **1/13 engine tests failed**, then metadata/name
snapshotting was moved inside owned preparation before candidate creation.
All RED runs compiled/discovered nonzero cases, no errors/skips.

Exact commands (RED/GREEN repetitions separated by source/test edits):

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*GgufHeaderTest'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*GgufHeaderTest' --tests '*ModelImportEngineTest'
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Final regression **141/141 pass** (5 header + 13 engine + prior 123), zero
failures/errors/skips; **BUILD SUCCESSFUL in 21s**, 80 tasks (17 executed / 63
up-to-date). Lint 0 errors / the same 4 warnings; both APKs pass. Tests cover exact
prefix/body identity across multiple buffers, short/non-seekable unknown-length
sources and actual EOF, early invalid/truncated input before destination creation,
maximal untrusted counts, source/destination read/write/close/flush/sync failures,
silent close, primary-cause privacy/retention, cooperative cancellation and the
header-only integrity limitation. Android adapter/instrumentation behavior is
still unexecuted; no model store/Activity integration or phone access is claimed.

Evidence: `.cache/model-import/mi-04-20261116T111752Z/` retains commands/exit codes,
RED/GREEN XML, final nine JUnit reports/lint XML, all source hashes and artifacts
in `results.json`. Production APK SHA-256:
`57f05a050650002bf8bf07d647d3f6b064741e58706a45bad1f0a68db58c9439`;
instrumentation APK remains
`f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6`.
Prior P1 source/report identities, native stage/receipt, `.gitignore`, `app/.cxx/`
and unrelated documents are unchanged. Next: MI-05 under standing authorization.

## MI-05 — Space, size and stopped-resource cleanup

Host-only implementation, commits `08935dc` and `b3af807`. Exactly the five
planned files: `ImportPolicy.kt`, `ModelImportEngine.kt`, `ModelImportEngineTest.kt`,
`ModelImportFailureTest.kt`, `FakeModelSource.kt`. The engine requires an explicit
injected destination-space policy (no permissive production default).

Known positive sizes require the entire new payload plus **67,108,864 bytes**
before source reads/candidate creation. Every pending write, for known or unknown
sizes, remeasures available space and checks the reserve. Failed/negative
measurements fail conservatively; checks subtract the reserve without overflow.
Unbuffered destination writes are a documented adapter obligation. Over-reported
sizes fail at actual EOF; under-reported sizes fail before an excess write.
Counters use checked addition, and percentage uses bounded exact integer ratio
arithmetic even near signed-Long limits.

Failure/cancellation closes each captured resource once; candidate deletion is
attempted only after both source and destination confirm closed. Pending close
blocks deletion; failure keeps candidate identity, resource/cleanup-pending flags,
the original cause and at most three typed secondary warnings. A discard failure
does not mask a read/write/cancellation cause. No selection/source/old payload
mutation is possible through these ports; publication integration remains MI-09.

RED: policy **5/5 failed**; engine-policy integration **2/20 failed** (one assertion,
one wrong-primary-type cast), then GREEN. Extended fault/boundary runs pass.
Final command:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

**155/155 JVM tests pass**, zero failures/errors/skips; lint **0 errors / 4 existing
warnings**, both APKs pass, **BUILD SUCCESSFUL in 19s** (80 tasks, 14 executed).
The legal sequential synthetic **2,147,483,649-byte** stream returns no more than
requested, uses only the 24-byte prefix / 256 KiB buffer and counts output without
allocating a model. This is not a physical file/provider/device result. Coverage
includes known/unknown/unusable size, exact reserve boundary, mid-copy depletion
and measurement errors, strict size mismatch, near-Long arithmetic, create/read/
write/flush/sync/close/discard errors, pending close, and cancellation at preparation,
read, space, progress and closed handoff. Fake resources are settled by tests.

Evidence `.cache/model-import/mi-05-20261116T113651Z/`: preflight, exact command logs,
exit codes, RED/GREEN XML, final ten reports/lint and source/artifact hashes in
`results.json`. Production APK `76f0f0aac8ca93cc332f60b0c25a003e0ce18b804f0bf5318fedbb284ea00d4c`
(79,482,963 bytes); test APK `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6`
(927,596 bytes). Preservation audit confirms all other preflight app/test sources,
native stage/receipt, `.cxx`, `.gitignore` and unrelated planning/spec files unchanged.
No phone access; next MI-06 and an actual F2 technical review.

## MI-06 — Android provider adapter and isolated fixtures

Commits `59e6a7a`, `632be2e`, `6e6eab6`; exactly five approved deliverables:
`AndroidImportIo.kt`, and test-only `FixtureModelProvider.kt`, `AndroidImportIoTest.kt`,
`ModelImportTestStore.kt`, `app/src/androidTest/AndroidManifest.xml`.

The adapter rejects main-thread/non-content access, queries only name/size with
cancellation, bounds/sanitizes accepted names and treats null/empty/missing/
unparseable sizes as unknown. Cancellation/read/revocation errors do not become
successful metadata fallbacks. Cursor-close failure retains an explicit pending
owner. Read-only `openAssetFileDescriptor` and `createInputStream` retain selected
slice offsets/lengths and pipes; EOF checks reliable-pipe errors. Descriptor
acquisition is published before stream construction, and a source-local lock
serializes construction/close without using a future cancellation/commit lock.
Acquisition failures attempt close once, retaining any pending resource and the
primary cause. `StatFs.availableBytes` measures the supplied destination directory.
Controller registration of the signal **before** metadata/open and immutable
source ownership remain explicit MI-07/MI-09 integration obligations.

The fixture provider is in the **test APK only**, separate `:model_import_fixture`
process, with bounded fixed ids, generated 50-byte documents, UID/signature caller
validation, no owner paths and no target production-class dependency. Slices have
unselected prefix/suffix bytes; pipes include reliable-error delivery. Tiny pipe
contents are populated synchronously, below minimum pipe capacity, with **no
producer thread**. Shared test-store construction uses fresh generated no-backup
containers, root leases and checked no-follow cleanup of known regular files;
it never uses the production `models/` root or recursive/app-data deletion.

Commands: `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passed **in 24s**, 80 tasks / 20 executed, **155 JVM tests, zero failures/errors/skips**,
lint **0 errors / 4 unchanged warnings**. After the fixture-only review correction,
`:app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` passed **in 15s**;
unchanged JVM inputs were not pointlessly rerun. **Seven Android adapter test
methods compile; none was executed.** There is no Android RED/GREEN/runtime claim.

Evidence `.cache/model-import/mi-06-20261116T114504Z/`: preflight/command logs,
exit codes, ten passing JUnit reports, lint, source/artifact hashes, decoded
production/test manifests and apkanalyzer version. Actual SDK tool:
`D:/softwares/android-sdk/cmdline-tools/latest/bin/apkanalyzer.bat` (JDK 17).
Production has **no provider**; test authority is
`io.github.greprohan.llamadroid.test.modelimport.fixture`, separate process as
above. All 14 packaged ARM64 native-library hashes match the unchanged stage.
Production APK `dc2e6ad8103fb56cba978697ef0720fc82428a5e06eaee566cf89b6300d266e3`
(79,482,963 bytes), test APK `3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`
(954,174 bytes). Existing sources/native/excluded owner work unchanged; no phone access.

## F2 technical review — 2026-11-16

Actually reviewed 13 exact source/test/manifest files across MI-04–MI-06, retained
passing reports and decoded APK declarations. Review audit:
`.cache/model-import/f2-20261116T115422Z/review-audit.json` with exact hashes.
Reviewed fixed-prefix/byte-order limits, bounded read/count/space/percentage logic,
size mismatches, original/secondary failure retention and stopped-resource-only
deletion. Reviewed adapter acquisition/close ownership, provider caller/process
isolation and generated-root-only mutation. A fixture pipe EOF/semaphore-release
race was identified and **removed**, not waived (`6e6eab6`); pending cursor-close
ownership was also retained during implementation. No unresolved host-contract
blocker found for MI-07. Android/provider descriptor behavior, process death,
physical >2 GiB import and backup/transfer remain unverified until approved device
batches. This is an assistant technical review, not invented owner/human sign-off.

## MI-07 — Cancellation/commit gate and captured source ownership

Commit `90ba6d1`; four planned files added: `ModelImportState.kt`,
`ImportOperation.kt`, `ImportOperationTest.kt`, `ImportResourceRaceTest.kt`.
The pure operation has immutable identity, monotonic copy/promotion phases,
and one short in-memory gate for accepted Cancel versus one-shot COMMITTING.
No provider/file call executes inside it. Late Cancel after commit does not set
cancellation or dispatch helpers. Acquisition requires a registered provider
signal; cancellation forbids starting a new acquisition. An already-running
acquisition retains the owner until its late resource is disposed/closed.

At most two immutable abort tasks per operation capture its signal and single
source lease. Duplicate Cancel queues nothing. Dispatch rejection retains the
cancel flag, bounded causes and truthful unresolved ownership. A blocked signal
does not block independent source close. Source construction/close handoff is
single-owner: worker/helper race for one close claim, pending close is not retried
blindly or reported closed, and a post-close exception cannot cause raw descriptor
reuse/double-close. Failed close remains pending until separately confirmed closed.
No helper can reference a candidate/selection/reset writer. Completion and commit
require no acquisition/helper/resource owner; finished operations release references.
The dispatcher contract is explicit: enqueue nonblocking, never execute inline;
the bounded production executor is a later MI-09 integration obligation.

Meaningful RED runs: gate **5/5 failed**, source ownership **3/8 failed**, signal-
before-acquisition **1/6 failed**, followed by GREEN. Fifteen new tests include
**100 concurrent Cancel/commit pairs**, explicit both-winner outcomes, queued
helper duplication, late acquisition, immutable old-operation capture, dispatch
rejection, post-close errors, blocked signal and worker-close races. Latches are
released in `finally`; actual worker threads/executor futures are joined and
asserted terminated before passing. These are JVM fakes, not Android FD evidence.

Final command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 25s**, 80 tasks / 16 executed, **170 JVM tests**, zero failures/errors/
skips. Lint **0 errors / 4 existing warnings**, both APKs pass. Evidence:
`.cache/model-import/mi-07-20261116T115656Z/` with RED/GREEN/final logs/XML, preflight,
12 reports, lint and all source/artifact hashes in `results.json`. Production APK
`e8192d6ac2c99b4486362ecee535c85158d64d2f6e8ca88a8b65e426f5ffa3ea`
(79,482,963 bytes); test APK remains
`3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`.
All pre-existing app/test/native/excluded owner inputs unchanged. No phone access,
Activity wiring, Android execution or fabricated checkpoint/human result. MI-08 next.

## MI-08 — Durable picker identity and bounded launch/result routes

Commits `713d6c2`, `df4f406`, `115b005`; five planned files added/modified:
`PickerRequest.kt`, `PickerCoordinator.kt`, `ModelStore.kt`, `PickerRequestTest.kt`,
`ModelStoreTest.kt`. Reservation/consumption uses checked record replacement with
fresh opaque epoch/nonce identity, preserving active id/name/size and old payload
bytes. Only a confirmed matching durable token clears before source opening.
Old/ambiguous/descriptor-pending outcomes cannot authorize launch/import; actual
post-rename errors are reconciled as confirmed new rather than rolled back.
Failures retain their primary cause internally, not in rendered diagnostics.

The coordinator separates RESERVING, RESERVED, LAUNCH_CLAIMED, AWAITING_RESULT,
CONSUMING, restored-uncertain and blocked states. Only a same-process unclaimed
reservation can be passively claimed once. Disk calls run outside its memory gate;
duplicates cannot queue a second reservation/consume. Process restoration never
auto-launches. Explicit retired-route supersession writes a new token; an old A
result cannot consume B before or after B consumption. Dismissal/launch failure
clears only its own token. Worker reconciliation cannot overwrite a live transition.
The saved logical Activity route has one binding and monotonically allocates
`0x0001..0xFFFE`, never wraps/reuses codes, rejects malformed state, and refuses
new launch until its binding returns or the entire route is retired. No URI/path
is persisted. Actual Android picker/lifecycle wiring remains MI-11/MI-12.

RED: store **2/19 failed**, route **3/4 failed**, coordinator **3/7 failed**, then
GREEN (no missing/zero/skipped tests). Final full command:
`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 23s**, 80 tasks / 17 executed. **188 JVM tests**, zero failures/errors/
skips; lint **0 errors / 4 existing warnings**; both APKs pass. Eighteen new tests
cover preserved active/source bytes, single consumption, epoch/stale results,
record write/rename/ambiguous/close failures, bounded restored routes, explicit
abandonment and a latch-blocked reservation proving memory claims remain usable.
The test reservation worker is released/joined with no survivor.

Evidence `.cache/model-import/mi-08-20261116T120826Z/`: preflight, exact logs/exit
codes, RED/GREEN XML, final thirteen reports/lint and source/artifact hashes in
`results.json`. Production APK `d6fafad25f964c5925b83701162db650d0f80694bc2b3e45c8cbd4300851043e`
(79,482,963 bytes); test APK unchanged
`3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`.
Only planned store/test edits plus three additions; native and excluded owner
work unchanged. No Android/device run or phone access. MI-09 controller next.

### MI-09 storage-integration split review

Before controller implementation, reviewed the actual `ModelStore`/file-operation
ports: recovery and picker updates exist, but candidate creation/promotion/active
publication do not. The writer only accepts a whole byte array, which would force
extra production copy buffers for partial chunks. The approved normal-import
protocol therefore needs a small **MI-09S** storage prerequisite, separately limited
to five files (`ModelFileOps`, `AndroidModelFileOps`, `ModelStore`, `FakeModelFiles`,
`ModelStoreTest`). The original five controller deliverables remain separate.
This technical split implements already-approved behavior under standing host
scope; it does not invent owner/human approval or permit device access. Acceptance
requires checked exclusive candidate ownership, no live-candidate cleanup, bounded
subsection writes, closed promotion, checked old/new/ambiguous publication and safe
cleanup debt, with tests/lint/both APKs before the next slice.

## MI-09S — Checked store integration

Reviewed five-file split completed in `e1c2318`, `1c61538`, `93d625e`:
`ModelFileOps.kt`, `AndroidModelFileOps.kt`, `ModelStore.kt`, `FakeModelFiles.kt`,
`ModelStoreTest.kt`. Unbuffered production writes accept array offset/count directly,
without per-chunk copies. Exclusive candidate ownership blocks recovery/second
writers even after close until discard/publication. Acquisition validation captures
handles before fallible checks; unknown identity is preserved for later recovery,
not guessed safe to delete. Unconfirmed close is not blindly retried. Promotion
requires successful flush/sync/close, checked inode/length, unchanged selection and
absent final path; old/new/ambiguous rename outcomes never overwrite another payload.

Checked selection publication confirms new before retiring old; failures preserve
old bytes, unresolved records preserve both payloads, and authoritative-read
reconciliation never promotes `.next`. Source/helper quiescence and the operation's
commit gate are explicit caller obligations. Proven unreferenced cleanup debt is
nonblocking and distinct from a successful new selection; stale candidate handles
cannot delete an active payload or affect a later owner.

RED stages: candidate **2/25 failed**, promotion **3/28**, publication **3/31**,
reconciliation **1/32**, unsynced handoff **1/37**, then GREEN. Fourteen new tests
exercise sequential fake-source/store paths, first import/replacement, exact bytes,
all those transaction outcomes, close/ownership guards, cleanup debt and recovery.
Final full command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 42s**, 80 tasks / 17 executed: **202 JVM tests**, no failures/errors/skips;
lint **0 errors / 4 existing warnings**, both APKs pass. No Android/device run.

Evidence `.cache/model-import/mi-09s-20261116T122004Z/` retains exact command logs,
RED/GREEN/final XML, thirteen reports/lint, source/artifact identities and preservation
review in `results.json`. Production APK
`3d99bbec6b2edbbfb8823d461eb61afb84775ce1da07968fe48ff86e3d4b0ada`;
test APK unchanged `3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`.
Exactly the reviewed five files changed; other source/native/excluded owner work
is preserved. Controller/singleton/notifications and checkpoint A remain next.

## MI-09 — Serialized controller, bounded cancellation and notifications

Commits `87fa7db`, `bb4e390`, `9796e92`; exactly five approved deliverables:
`ModelImportController.kt`, `ModelImports.kt`, `ModelImportState.kt`,
`ImportOperation.kt`, `ModelImportControllerTest.kt`. Lazy singleton ownership is
constrained to **Application**, not an Activity/generic Context. There is one
bounded I/O executor and two bounded abort workers; command reservation precedes
enqueueing. Provider query/open registers cancellation before blocking and publishes
an immutable owned source before stream construction. Resource-completion callbacks
run outside the operation gate; pending cleanup retains the command and checks late
completion at worker exit so it cannot lose a finalization wakeup or launch a writer.

The integrated fake-source flow consumes the matching durable picker first, copies,
promotes while still cancellable, then wins the atomic commit gate before checked
publication. Ambiguity preserves both payloads until authoritative reconciliation;
confirmed old/cancel/error and confirmed new/cleanup-debt remain distinct. Snapshots
contain typed/sanitized issues, never raw exceptions or provider error text.
One replaceable main runnable serves all state kinds. Increasing revisions/current
subscriptions prevent stale delivery; copy progress is eligible within 100 ms and
capped at 10 Hz, with immediate stage/final supersession. Activity binding is not yet
wired. Android provider/Handler behavior is **compiled, not device-executed**.

RED: command/notification **2/2 failed**, pipeline **1/4**, pending finalization
**1/6**, stale classification boundary **1 case** (a separate late-cancel fixture
initially targeted the earlier consumption rename and was corrected), then GREEN.
Seventeen controller tests cover first/replacement exact old-byte preservation,
consumption/stale/cancel failures, late commit, promotion cancellation, transient/
persistent ambiguity, cleanup debt, pending close, notification ordering/rate and
real I/O plus two helper workers with a blocked signal/independent source close.
Latches are released and all executor futures/termination checked; no survivor.

Final current-source command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 39s** (80 tasks / 18 executed): **219 JVM tests**, zero failures/errors/
skips, lint **0 errors / 4 baseline warnings**, both APKs pass. An intermediate
`StaticFieldLeak` warning was fixed by the Application-only type, **not suppressed**.
Evidence `.cache/model-import/mi-09-20261116T124121Z/` retains logs/exit codes,
RED/GREEN XML, fourteen final reports/lint, decoded production manifest and all
source/artifact hashes. Production APK
`4fa65a918691cf010f401a5d318c7f09777f3888bb0f2a10fa8d831803800c2c`;
test APK unchanged `3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`.
All 14 packaged native hashes and excluded owner inputs preserved. No phone access.

## Checkpoint A technical review — 2026-11-16

Actually reviewed 16 exact production/test files across ownership, picker, controller,
store, streaming and Android acquisition, current reports/APK identities and decoded
manifest. Audit `.cache/model-import/checkpoint-a-20261116T155637Z/review-audit.json`
records exact hashes, checks and resolved findings. No unresolved host-contract blocker
for MI-10. Production declares no new permission/provider/background component;
native bytes are unchanged. Android/runtime/SAF/Handler/lifecycle and physical >2 GiB
results remain unverified. This is **assistant technical review**, not an invented
human owner review. The tracker keeps that distinct human-only checkbox unclaimed;
the owner's standing uninterrupted-host-work instruction authorizes progression.

### MI-10 fixture integration review

The required before-EOF unknown-size screen case cannot be established with the
existing finite/prepopulated 50-byte provider fixtures. Review adds the existing
**test-only** `FixtureModelProvider.kt` as MI-10's fifth file: one validated token-
bound producer, release handshake and bounded join. No production command/provider
or owner-root override is introduced. Original screen/layout/strings/test scope
is unchanged; all Android cases remain compiled-only until MI-20 consent.

## MI-10 — Import Views and gated unknown-size contract

Commit `016cac3`; five reviewed deliverables: `ModelImportScreen.kt`,
`main_activity.xml`, `strings.xml`, `ModelImportScreenTest.kt`, and the explicitly
reviewed test-only `FixtureModelProvider.kt` extension. The screen does not open a
store/singleton or retain a controller: active private selection and pending new
copy are separate, actual Long sizes remain intact, known progress uses checked
percent and unknown progress has copied bytes/indeterminate display without a
fabricated total. Preparing/cancelling/finishing/resource-pending states and typed
recovery/error/cleanup messages remain explicit. Import is not a compatibility
claim. Actions are disabled while unbound, reset hidden until wired, and detach
removes callbacks. Launcher/system picker are still MI-11, not claimed working.

Four Android methods **compile only**: unbound/recovery actions, active-vs-pending/
finishing/detach, cancellation/warnings, and
`unknownSizeProviderRendersCopiedBytesWithoutPercent`. The latter uses the shared
isolated no-backup store helper plus real Android adapter/engine/controller and a
test-provider reliable pipe. A token-bound producer emits 24 bytes, waits for an
observer/render handshake, then releases the remaining 26 bytes/EOF. The test checks
copied bytes/no percent **before EOF**, then 50-byte completion; finite latches,
worker futures and producer join are required, with no sleep timing or production
singleton. This procedure is **not executed/evidence of Android behavior yet**.
Test controls remain solely in the test APK/provider. Provider failure is surfaced
through reliable-pipe error and checked finish status, not concealed as clean EOF.

Incremental provider/layout/binder/state-test/integrated-test build checks pass.
Final full command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 44s**, 80 tasks / 27 executed: **219 JVM tests**, zero failures/errors/
skips; lint **0 errors / 4 baseline warnings**, both APKs pass. New plural/unused-
placeholder warnings were corrected using plural resources and removing the now-
unreferenced placeholder, without suppressions or Long-to-Int display truncation.
The standing Definition of Done was reviewed: Android runtime/visual behavior and
independent human acceptance remain **unverified**; this is host task scope, not
feature/ship completion or a waived device gate.

Evidence `.cache/model-import/mi-10-20261116T160118Z/` retains all exact build logs/
exit codes, fourteen passing JVM reports/lint, source/artifact hashes and reviewed
fixture/DoD limits in `results.json`. Production APK
`27cdde6fc007959249371d1c2e2dc9e5d8e8a3068cf420c25d584c49d3bd6e41`;
test APK `db612a60d6eee9e826a7703a6d04b08fbd1078dfcb97ba9c6aaeca7ead976ed5`
(969,945 bytes). Exactly five reviewed files changed; packaged 14 native hashes,
other sources and excluded owner work are unchanged. No phone access. MI-11 next.

### MI-11 UI-message integration review

Accurate missing-picker, malformed-result, busy-owner and retired/exhausted-route
messages require fixed resource strings, not raw provider exceptions or hardcoded
text. The existing `strings.xml` is explicitly MI-11's fifth file; original Activity,
route/test and manifest scope is unchanged. This is host-only decomposition under
standing authorization, not permission to invoke the real picker or access a phone.

## MI-11 — Launcher and opaque SAF result routing

Commits `ce6a83d`, `b9a6450`; five reviewed files: `MainActivity.kt`,
`AndroidPickerRoute.kt`, `AndroidPickerRouteTest.kt`, production manifest and
`strings.xml`. MainActivity binds only the Views/controller and observes while
started, detaching/clearing screen callbacks on stop/destroy. It reads **no incoming
Activity data/path/model/argument extras**. One memory-only, token-bound result can
wait for serialized store recovery; no URI or dead copy is saved/restored. Additional
same-process configuration-retention/lifecycle cases remain the next MI-12 slice.

A same-process reserved request is claimed once before `ACTION_OPEN_DOCUMENT`,
OPENABLE/`*/*`, single-document mode with temporary read access. Route identity/code/
epoch/nonce are strictly bounded in one <=2 KiB tested Bundle contract; no URI.
Unknown/malformed saved state retires the logical route rather than restarting
codes at 1; exhaustion requires a fresh Activity route, never wrap/rebind. Only a
matching single content URI with read flag reaches controller consumption; null,
multiple, conflicting/non-content/ungranted/stale results cannot open a source.
Picker cancellation/launch failure clears only its matching token. Actual readable
provider access is still checked off-main and can fail safely. No persistent grant,
external import filter or test/path control was added.

Six Android memory/dispatch tests **compile, not run**: SAF flags/opaque saved
bounds, restored matching/single consumption, bad inputs, failed dispatch/code
non-reuse, malformed saved routes and exhaustion/retirement. Dispatch fakes never
invoke the owner's picker/store. Full command
`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 25s** (80 tasks / 18 executed): **219 JVM tests**, zero failures/errors/
skips; lint **0 errors / 4 baseline warnings**, both APKs pass. An intermediate
`InflateParams` warning was fixed using Activity `setContentView`/content root,
not suppressed. No Android runtime/lifecycle/visual/picker or human/ship result.

Decoded **both actual APK manifests** using
`D:/softwares/android-sdk/cmdline-tools/latest/bin/apkanalyzer.bat`, installed
command-line tools **12.0** (`source.properties` retained), existing JDK 17.
Production has exactly one exported **MAIN/LAUNCHER** Activity and no VIEW/SEND,
permissions, provider, service or receiver. The fixture provider remains test-only.
All 14 packaged native hashes match the unchanged stage.
Evidence `.cache/model-import/mi-11-20261116T162132Z/`: exact logs/exit codes,
fourteen JVM reports/lint, decoded declarations/tool provenance and source/artifact
hashes. Production APK `88b7669755b1ac40a0f5ebab373abbb3a669472147b1b92e2d92c1286c4efa51`;
test APK `8a7eb4f0fb49e849abd7d7b48346036ded6a08b69c9083fd13c5a57169a32e11`
(975,121 bytes). Five-file scope and excluded owner work preserved. No phone access.

## MI-12 — Recreation ownership and transient result handoff

Commits `a6f05b9`, `2c401db`; four of the planned five files changed:
`MainActivity.kt`, `AndroidPickerRoute.kt`, `AndroidPickerRouteTest.kt`,
`ModelImportControllerTest.kt`. Existing `PickerCoordinator` phase guards are reused
unchanged. One route/result object is retained only through Android in-process
non-configuration retention, with **no Activity/View/callback** references. It can
hold one token-bound URI while recovery is busy; handoff clears only that exact
result when a controller command reserves ownership. Bundle saving excludes the
URI; process restoration never reopens a dead copy. Destroying a non-configuration
route retires it and drops transient data. Duplicate UI actions cannot create a
second command/launch; stopped/destroyed observers detach.

Four new JVM controller regressions pass: replacing an observer mid-copy without
another writer/old delivery, passive reattachment before/after launch claim, a new
fake file-image/store owner recovering an outstanding request before a delivered
result can start, and old-A/wrong-epoch callbacks before/after B consumption.
These are **not actual Android rotation or process-death tests**. Three additional
Android memory contracts compile: retained-vs-saved transient result, stale handoff/
retirement, and an adversarial malformed Intent getter. F3 review found the last
parser exception could escape, so it now becomes a fixed Invalid result without
raw exception/URI diagnostics. No instrumentation is executed here.

Final current-source command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 25s**, 80 tasks / 20 executed: **223 JVM tests**, zero failures/errors/
skips; lint **0 errors / 4 baseline warnings**, both APKs pass. Twenty model-import
Android methods compile only. Evidence `.cache/model-import/mi-12-20261116T163622Z/`
retains focused/final logs, fourteen JVM reports/lint, decoded APK manifests, source/
artifact identities and preservation review. Production APK
`caf3826dd85a24e00d634c29bd74aa5c90f4b79dfb99762cf1cfc5e07f562796`;
test APK `0e89543ba3185181f52a05040cf6ebb45760bf285e8e3d9db3aa0cf5a1a908c3`
(976,319 bytes). Native hashes and all excluded owner inputs unchanged. No phone
access or Android runtime/human/ship completion claim.

## F3 technical review — 2026-11-16

Actually reviewed 14 exact Activity/route/screen/controller/provider/test/resource/
manifest files, current reports/APK hashes and declarations. Audit:
`.cache/model-import/f3-20261116T164425Z/review-audit.json`. The malformed-result
getter exception was fixed and recompiled/regression-tested, not waived. Reviewed
observer detachment, in-process-only transient URI ownership, no saved/dead-copy
resume, stale callback/code/epoch rejection, separated active/pending display and
test-only gated producer/isolated-root cleanup. Reset UI is still hidden/unwired;
no owner selection reset/replacement was executed by these host checks. No
unresolved host-contract blocker for MI-13. Actual Android SAF, visual/lifecycle,
provider/process-death and physical fixtures remain unverified until approved
MI-20/MI-21 batches. This is assistant technical review, not invented human approval.
