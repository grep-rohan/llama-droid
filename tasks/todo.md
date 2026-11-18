# Model Import — Approval and Task Gate

Module: `model-import`. The [specification](../SPEC-model-import.md), [technical plan](plan.md#implementation-plan-model-import), and revised detailed task list below are **owner-approved after iterative review**. Task approval explicitly covers MI-00–MI-23 including MI-17A and the checkpoint structure; it is not an execution or device-write result.

| Gate | Current state |
|---|---|
| Specification/policies | Owner-approved: 64 MiB reserve, GGUF v2/v3 fixed-header/byte-order checks, strict positive-size mismatch rejection, single-file scope, non-backed-up payload/all records, confirmed in-app recovery reset. |
| Technical plan / prior tracker gate | Owner-approved: checked record replacement, picker/cancel ownership, provider-stream semantics, resource bounds and verification protocol. |
| Detailed model-import tasks | All 25 tasks (MI-00–MI-23 including MI-17A) and checkpoints are owner-approved; MI-00–MI-19 host scope (including reviewed MI-09S/MI-13R/MI-14W splits) and technical reviews through H2 are complete. MI-20 bounded device acceptance is now complete across separately approved batches; MI-21–MI-23/module closure still require separate evidence/consent. |
| Implementation | MI-00–MI-19/H2 host scope complete: prior clean build, 242 JVM / 12 packaging tests and lint 0 errors / 4 baseline warnings remain applicable to unchanged app/build inputs. The committed-copy postflight host regression passed 250/250 Python tests. Both original audited APKs retained-updated on S25. Native smoke 1/1 PASS on original test APK; isolated retry 26/26 PASS on revised Java-provider test APK. Production APK unchanged; native test disassembly/provenance identical across test APKs, no revised-APK native run invented. All 27 interruption pairs remain unexecuted; no module closure. |
| Device writes/tests | MI-20 bounded device acceptance complete across separately authorized S25 batches. After owner ready, bounded stops and read-only inventory preserved the existing selection record/model payload; only revised test APK retained-updated. Isolated26 PASS, retained APK/UID baseline PASS, postflight record SHA/model stat inventory unchanged and packages quiet. Earlier native1 PASS remains attributed to original test APK and identical production APK; smoke test disassembly/provenance identical. MI-21 bounded non-replacing MAIN/LAUNCHER + real SAF-open/cancel slice now PASS after owner unlocked: verified Import tap/picker Back/Ready return, no file selected, active record/epoch/payload stat preserved and nonce cleared. Selection SHA preserved with legitimate inode change; full MI-21 remains incomplete. One real model replacement was separately approved and now PASS (1,846,240,768 bytes); model/mmproj sources' metadata preserved. One approved 4 GiB fixture import/restoration/cleanup is complete and basic Cancel is owner-manually confirmed. Owner then requested emulator-5554 only: fresh audited production install and empty-store idle/picker rotation, screen-off return and new-PID idle restart PASS; no genuine keyguard observed, no model imported and no S25 contacted. The observer CRLF/4-GiB comparison blockers are now runtime-resolved: emulator active-import rotation PASS with new Activity/same partial candidate and 4-GiB commit. External source removed; selected private synthetic test copy retained. The latest separately approved emulator 4 GiB copy committed and its external fixture was cleaned up; live progress/heap measurements remain unverified. Further fixtures and MI-22 remain separately gated; no module/inference/WebUI closure. |

**Standing execution scope:** Continue host-only MI-04–MI-19/H2 (including MI-17A) and actual technical checkpoint reviews incrementally without per-task approval pauses; stop at MI-20 before physical-phone inventory/installation/testing/data/package-stop work. See [current evidence](../docs/verification/model-import.md). That direction authorized host progression, not invented human review, phone writes, module closure or waived technical checks. **2026-11-17 update:** after separate MI-20 approval the owner connected wireless ADB. Inventory and both retained updates succeeded; native smoke passes, isolated suite fails. See the wireless batch evidence. The old exact-APK consent was not silently extended: the owner subsequently approved fixture repair/re-audit/revised-test-APK retained update and bounded isolated retry. After owner ready, existing production data was inventoried/preserved, the revised test APK retained-updated and isolated26 passed. Each successful suite retained its own correct post-install baseline; native1 remains earlier provenance. MI-20 bounded acceptance is complete; no later manual/interruption scope is inferred. MI-00's reviewed-source exclusions remain in effect; unrelated work is preserved and no clean-checkout reproduction is claimed. Android compilation/fakes are not platform/process-death/runtime evidence.

The approved task list traces [P1–P7 and checkpoints](plan.md#model-import-execution-order-and-checkpoints) and the [spec-to-verification matrix](plan.md#spec-to-verification-matrix). Fixture/device unavailability does not waive mandatory evidence. The [completed native-runtime plan](plan.md#implementation-plan-native-runtime) and task/evidence record below remain historical, with original anchors; none of their checked tasks or old commands authorizes model-import execution.

## Model-import approved task list

**Status: owner-approved; MI-00–MI-19 host scope including reviewed splits and actual technical reviews through H2 are complete. MI-20 bounded acceptance is complete across authorized S25 batches: revised isolated26 PASS, earlier native1 PASS on unchanged production APK, production selection/model metadata preserved. No new native run is claimed for the revised test APK. No module closure is claimed.** The existing checkout is the reviewed implementation source with unrelated-work exclusions, not a clean-checkout reproduction. Approval accepts the decomposition and checkpoint requirements, not completion or permission to write to the phone. Obtain explicit scope/batch authorization before later implementation; before each device batch, confirm package/data impact and owner consent. Completing code/compilation tasks does not complete their separate device acceptance.

### Shared execution rules and paths

- Use TDD for safety logic: demonstrate meaningful failing tests, implement, then rerun. Missing/zero tests, skipped required cases and mocked native/Android behavior are not success.
- File lists below are implementation/test deliverables, at most five per task. Updating this tracker with actual results is bookkeeping. If a fix needs additional substantive files, split/review the task rather than quietly broadening it.
- After each code task, also run `./gradlew --no-daemon :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`. Focused JVM commands below are additional checks. Android test compilation is **not** a device pass; actual runs belong to MI-20/MI-22.
- At every checkpoint, retain exact commands, nonzero test discovery/results and source identity. Never commit weights/generated native files, change toolchain/dependencies, overwrite unrelated work, use broad storage permissions, or add production fault/path/URI import controls.
- All automated mutation uses generated test roots and metadata, not the owner's active `models/`. Preserving selection means preserving the old payload bytes and active id/name/size, not forbidding the protocol's legitimate pending-picker reservation/consumption updates; store epoch changes only on initial creation or authorized reset. Do not use uninstall, downgrade, app-data clear, uncontrolled disk filling, `connectedDebugAndroidTest`, or an unfiltered instrumentation suite.

Path aliases (literal repository prefixes; proposed files need not exist yet):

```text
A = app/src/main/java/io/github/greprohan/llamadroid
M = app/src/main/java/io/github/greprohan/llamadroid/modelimport
U = app/src/test/java/io/github/greprohan/llamadroid/modelimport
I = app/src/androidTest/java/io/github/greprohan/llamadroid/modelimport
R = app/src/main/res
```

All proposed test classes under U/I use package `io.github.greprohan.llamadroid.modelimport`. The production Activity remains under A. Dependencies below form an ordered baseline; any parallel test/document work must not mutate a shared store/stage.

### V-manifest — Decode packaged declarations

From the repository root, with the actual SDK/JDK environment recorded by MI-00, use the matching shell variant below. These commands inspect already-built artifacts; neither variant installs or launches an APK.

Bash on a Unix SDK host:

```bash
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer" manifest print app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer" manifest print app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

PowerShell on the verified Windows SDK host (not Bash syntax):

```powershell
& "$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin\apkanalyzer.bat" manifest print app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin\apkanalyzer.bat" manifest print app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

Record the exact tool path/version and decoded production/test declarations separately; do not upgrade command-line tools to silence existing SDK metadata warnings.

### MI-00 — Establish the reviewed implementation workspace

- [x] Complete MI-00 (P1 prerequisite; depends on task + implementation authorization).

**Description:** Establish a clean, reviewed source/worktree and baseline without disturbing current `.gitignore`, `app/.cxx/` or other user work.

**Acceptance:**
- [x] Record source/worktree, approved-document provenance, tool/stage prerequisites and exact unrelated-work exclusion; no stash/reset/unreviewed copying into the implementation source.
- [x] Run baseline applicable host/JVM/lint/build checks with nonzero discovery; classify failures/blockers rather than assuming historical native results apply.
- [x] Record no phone writes and no model-import runtime claim.

**2026-11-15 result after authorized blocker repair:** [MI-00 evidence](../docs/verification/model-import.md). The initial receipt failure was resolved by re-staging the verified local archive with the current importer, not by editing/bypassing the receipt. Native verification passes and all 14 library hashes remain unchanged. The Gradle baseline is **BUILD SUCCESSFUL in 24s**: 43 JVM tests pass with no failures/errors/skips; lint has 0 errors / 4 existing warnings; debug and instrumentation APKs build. The earlier 94/94 passing Python results remain applicable because their inputs are unchanged. Tracked build/source remains unchanged in the existing dirty worktree; no clean-checkout reproduction is claimed. `.gitignore`, `app/.cxx/`, other planning work and phone state are untouched. Owner subsequently approved reconciliation and MI-01: the existing reviewed tracked source with these explicit exclusions satisfies MI-00; no extra clean-worktree requirement or deletion of unrelated work is imposed.

**Verify:** `git status --short`; `git worktree list`; `python scripts/import_llama_release.py --verify-only`; `python -m unittest discover -s scripts/tests -p 'test_*.py' -v`; `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`.
**Files:** `docs/verification/model-import.md` (one substantive file). **Scope:** Small, prerequisite-dependent.

### MI-01 — Bound persistent records and display names

- [x] Complete MI-01 (P1; depends on MI-00).

**Description:** Define empty/active selection and reset-authorization records with strict, bounded codecs and sanitized names.

**Acceptance:**
- [x] Enforce version/id/flag/UTF-8/schema rules, active size >=24, empty-state exclusion of payload fields, 2 KiB record bound plus one probe byte, and rejection of truncated/trailing/oversized input.
- [x] Preserve epoch/pending-picker fields without URIs/paths; reset records carry reset id and intended new epoch, never implicit consent from a partial record.
- [x] Names obey 256-code-point/1,024-byte limits with control/format neutralization and bounded fallback; malformed input cannot drive unbounded allocation or leak through diagnostics.

**2026-11-15 result:** [MI-01 evidence](../docs/verification/model-import.md#mi-01--bounded-records-and-display-names). Five planned Kotlin files added; commits `dc14cd1` (names) and `4e68a8d` (records/codec). Meaningful RED assertion runs retained (names 7/8 failed; initial selection 5/5 failed; reset/read extension 4/10 failed), followed by passing GREEN runs. Final focused 29/29 and full regression 72/72 JVM tests pass, no failures/errors/skips. Lint: 0 errors / 4 existing warnings. Both APK builds pass; final **BUILD SUCCESSFUL in 28s**. No source URI/path fields, no unbounded record/name allocation, fixed malformed-record diagnostics. Record values/codecs only: no file persistence, reset deletion, Activity wiring, Android adapter or phone execution. Native bytes/toolchain/dependencies and excluded owner work are unchanged. MI-02 and Checkpoint F1 remain pending.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelRecordCodecTest' --tests '*ModelNameTest'`.
**Files (5):** `M/ModelRecord.kt`, `M/ModelRecordCodec.kt`, `M/ModelName.kt`, `U/ModelRecordCodecTest.kt`, `U/ModelNameTest.kt`. **Scope:** Medium.

### MI-02 — Implement checked record replacement

- [x] Complete MI-02 (P1; depends on MI-01).

**Description:** Implement the small file-operation boundary and checked exclusive-temp/sync/close/rename record protocol, not AtomicFile.

**Acceptance:**
- [x] No-follow/exclusive creation and checked platform operations leave the old base untouched until same-directory rename; no delete-then-rename, base truncation, link following or ignored boolean failure.
- [x] Explicit write/flush/sync/close, bounded read-back and descriptor/sidecar postconditions distinguish old/new/ambiguous outcomes, including a post-rename exception or a false normal return.
- [x] Pure fake-I/O fault tests pass; Android adapter compiles, with actual platform behavior reserved for MI-15/MI-20. Use public checked APIs such as `Os.rename` and checked removal of a validated regular file, not an assumed public `Os.unlink` method.

**2026-11-15 result:** [MI-02 evidence](../docs/verification/model-import.md#mi-02--checked-record-replacement). Exactly five planned files added, commits `bb5fb4f`, `13407f3`, `443bdfe`. RED runs retained: publication 1/1 failed; reconciliation 3/4 failed (assertions, no missing tests/compile errors), then GREEN. Final **19 new / 91 total JVM tests pass**, 0 failures/errors/skips; full Gradle verification **BUILD SUCCESSFUL in 18s** (corrected Android adapter/build also passed in 21s). Lint: 0 errors / 4 existing warnings; both APK builds pass. Initial lint failure for API-27-only `O_CLOEXEC` was fixed using the verified API-26 ARM64 Linux UAPI flag value, with no suppression/minSdk change. Checked `Os.rename`/`Os.remove`, no-follow/exclusive creation, inode/content/sidecar verification, close ownership, old/new/ambiguous outcomes and cleanup debt are implemented. Platform syscall/FD behavior is compiled only, reserved for MI-15/MI-20. No phone writes, native changes or owner-store access; root lease/recovery is MI-03. Checkpoint F1 remains pending.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*CheckedModelRecordTest'`.
**Files (5):** `M/ModelFileOps.kt`, `M/AndroidModelFileOps.kt`, `M/CheckedModelRecord.kt`, `U/CheckedModelRecordTest.kt`, `U/FakeModelFiles.kt`. **Scope:** Medium.

### MI-03 — Recover the authoritative selection

- [x] Complete MI-03 (P1; depends on MI-02).

**Description:** Implement root ownership, explicit empty initialization, immutable payload selection and safe recovery/cleanup.

**Acceptance:**
- [x] A root-keyed lease excludes simultaneous owners; validate root/base/sidecar/payload types, ids, containment, readability and recorded length before exposing a model.
- [x] Empty initialization and old/new/empty crash states obey the plan; `.next` never becomes authoritative by inspection, and ambiguity/corruption blocks mutation without deleting possibly referenced payloads.
- [x] Cleanup proves ownership/unreferenced status, preserves sources/unrelated files, reports debt and retries safely; record-sidecar debt versus unreferenced-payload debt has the documented distinct blocking behavior.

**2026-11-16 result:** [MI-03/F1 evidence](../docs/verification/model-import.md#mi-03--authoritative-selection-recovery-and-root-ownership). Exactly five planned files added/modified; commits `4aba8e4`, `6fcbea3`, `b1cb474`, `fcdb9ef`. Five meaningful RED stages retained (lease 2/2 failed; initial store 2/4; recovery 3/5; reset ordering 1/7; root recheck 1/10), then GREEN. Final 17 store + 15 recovery = **32 new / 123 total JVM tests pass**, no failures/errors/skips. Lint: 0 errors / 4 existing warnings; both APK targets pass. Final **BUILD SUCCESSFUL in 23s**; production/root integration builds also compiled/built in 34s. Old/new/committed-empty file images, strict ownership/length/readability, ignored `.next`, reset-pending ordering, source-byte preservation, cleanup debt and pending-resource lease retention are covered. A root recheck gap before initial publication was reproduced/fixed. Android no-backup root setup/lazy inventory is compiled only; no actual Android filesystem, process-death, backup, >2 GiB import or phone result is claimed. F1 host evidence is ready; owner review and MI-04 authorization remain pending.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelStoreTest' --tests '*ModelRecoveryTest'`.
**Files (5):** `M/ModelStore.kt`, `M/ModelOwnership.kt`, `U/ModelStoreTest.kt`, `U/ModelRecoveryTest.kt`, `U/FakeModelFiles.kt`. **Scope:** Medium.

### Checkpoint F1 — Storage safety foundation

- [x] MI-01–MI-03 focused and applicable regression tests pass, both APKs compile, no old-record preservation or ownership case is waived.
- [x] Review evidence before the next slice; no Android/process-death/device result is inferred from fakes or compilation.

**2026-11-16 technical review:** [Actual F1 review](../docs/verification/model-import.md#f1-technical-review--2026-11-16). All source/report/APK hashes match the passing 80 model-import + 43 existing JVM results. Eight production sources and six test/fixture sources reviewed; no blocker identified within the host contract. No unchanged test/build rerun, new production code or phone access. This is an assistant technical review, not a fabricated separate human sign-off. Owner's subsequent uninterrupted-host-work instruction authorizes MI-04 onward through MI-19/H2 while retaining actual checkpoint reviews and the separate MI-20 phone-consent gate.

### MI-04 — Stream and validate the fixed GGUF header

- [x] Complete MI-04 (P2; depends on MI-03 and F1 review).

**Description:** Build the pure sequential-copy path with a minimal source boundary and fixed-header validation.

**Acceptance:**
- [x] Accept byte-level GGUF with little-endian v2 / little- or big-endian v3; tolerate short reads, reject EOF before 24 bytes and unsupported headers, and never allocate from tensor/metadata counts.
- [x] Copy the retained prefix exactly once with a 256 KiB buffer through actual source EOF, including unknown-length/non-seekable sources; no seek/reopen, whole-file allocation or body-integrity claim. Define the minimal source/cancellation/closed-candidate ports here using existing or same-task types, so this slice compiles without forward references to MI-05 policy or MI-07 operation implementations.
- [x] Close source and complete/sync/close candidate before handing off a closed candidate; source/destination failure cannot publish a selection. Include the documented header-passing/body-corrupt limitation.

**2026-11-16 result:** [MI-04 evidence](../docs/verification/model-import.md#mi-04--fixed-header-and-sequential-copy). Exactly five planned files; commits `40c74d5`, `4d088df`. Meaningful RED stages (header 5/5 failed; copy 2/2; metadata failure 1/13), then GREEN. Final **18 new / 141 total JVM tests pass**, no failures/errors/skips; lint 0 errors / 4 existing warnings; both APKs pass, **BUILD SUCCESSFUL in 21s**. Short/zero reads, retained prefix identity, actual EOF, bounded memory, close/sync ordering and safe failure handoff are covered. Header-passing corrupt body remains an explicit limitation. No selection publication, Android/device execution, native changes or owner-source mutation; MI-05 policy remains next.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*GgufHeaderTest' --tests '*ModelImportEngineTest'`.
**Files (5):** `M/GgufHeader.kt`, `M/ModelSource.kt`, `M/ModelImportEngine.kt`, `U/GgufHeaderTest.kt`, `U/ModelImportEngineTest.kt`. **Scope:** Medium.

### MI-05 — Enforce space and copy-failure policy

- [x] Complete MI-05 (P2; depends on MI-04).

**Description:** Apply the approved reserve, reported-size rule, checked arithmetic and failure/cancellation cleanup to streaming.

**Acceptance:**
- [x] Known sizes require new bytes plus 67,108,864 available bytes; unknown/unusable sizes use per-write checks; failed space measurement and real write/flush/sync/close errors are actionable, never permission to delete the old model.
- [x] Positive-size mismatch rejects publication; actual empty/header-invalid input fails; counters/progress/space math handle >2 GiB and signed-64-bit boundaries without model-sized allocations or invalid fake stream contracts.
- [x] Read/create/write/flush/sync/close/cleanup and cooperative-cancel faults preserve old selection/source, stop the writer before deletion, and keep the primary cause plus bounded secondary cleanup warnings.

**2026-11-16 result:** [MI-05 evidence](../docs/verification/model-import.md#mi-05--space-size-and-stopped-resource-cleanup). Five planned files; commits `08935dc`, `b3af807`. RED policy 5/5 and integration 2/20 failed, then GREEN. **14 new / 155 total JVM tests pass**, no failures/errors/skips; lint 0 errors / 4 existing warnings; both APKs pass, **BUILD SUCCESSFUL in 19s**. Includes legal synthetic >2 GiB streaming (not a phone fixture), exact/near-Long arithmetic, per-write checks for all size classes, strict EOF mismatch, I/O/cancellation faults, stopped-resource-only discard and bounded secondary warnings. Prior selection/source are outside these ports; end-to-end publication remains MI-09. No phone access or native changes. MI-06/F2 next.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelImportEngineTest' --tests '*ModelImportFailureTest'`.
**Files (5):** `M/ImportPolicy.kt`, `M/ModelImportEngine.kt`, `U/ModelImportEngineTest.kt`, `U/ModelImportFailureTest.kt`, `U/FakeModelSource.kt`. **Scope:** Medium.

### MI-06 — Adapt Android document streams safely

- [x] Complete MI-06 (P2; depends on MI-04/MI-05).

**Description:** Bind provider metadata/asset streams and destination-space queries to the pure engine; add a standalone test-only fixture provider.

**Acceptance:**
- [x] Query only needed columns off-main with cancellation; null/empty/missing/overflowing metadata falls back safely, while accepted cancellation and null/open/revocation errors are not swallowed.
- [x] Read-only asset streams respect offsets, declared slices and pipes; close all acquisition-failure paths exactly once, preserve delivered bytes and surface reliable-pipe errors without claiming representation conversion is bypassed.
- [x] Introduce the shared `ModelImportTestStore` root/lease/cleanup helper here, before any Android mutation test. The test-only provider generates bounded fixtures in its own package storage, enforces validated ids/test-caller access, and never reads owner paths or loads production-dependent helper classes in its separate process. Instrumentation uses the shared isolated target-root helper, not the production singleton; tests compile, with runtime pending MI-20.

**2026-11-16 host result:** [MI-06/F2 evidence](../docs/verification/model-import.md#mi-06--android-provider-adapter-and-isolated-fixtures). Five planned files, commits `59e6a7a`, `632be2e`, `6e6eab6`. **155 JVM tests pass**, no failures/errors/skips; lint 0 errors / 4 existing warnings; both APKs pass (24s full build, 15s fixture-only correction). **7 Android test methods compiled, NOT executed**; actual provider/FD/cancellation/platform results remain MI-20. Decoded APKs confirm no production provider, one validated test-only authority/process; 14 packaged native hashes unchanged. F2 actually reviewed 13 files and removed a fixture producer-thread/EOF release race. No phone access or fabricated human approval; MI-07 next.

**Verify:** `./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest`; inspect test/production manifest separation. Run `AndroidImportIoTest` only in the approved isolated suite at MI-20.
**Files (5):** `M/AndroidImportIo.kt`, `I/FixtureModelProvider.kt`, `I/AndroidImportIoTest.kt`, `I/ModelImportTestStore.kt`, `app/src/androidTest/AndroidManifest.xml`. **Scope:** Medium.

### Checkpoint F2 — Bounded streaming and Android compilation

- [x] MI-04–MI-06 host tests and compile/lint checks pass; delivered-byte identity, size/space errors and cleanup have explicit coverage.
- [x] Review adapter/resource boundaries and provider isolation; mark instrumentation compiled, not executed, until device approval.

**2026-11-16 technical review:** [F2 actual review](../docs/verification/model-import.md#f2-technical-review--2026-11-16), exact 13-file/report/APK audit `.cache/model-import/f2-20261116T115422Z/review-audit.json`. Pipe fixture race removed and verified; no unresolved host-contract blocker. Signal-before-acquisition and immutable published-resource handoff remain explicit MI-07/MI-09 integration obligations. Assistant technical review under standing host authorization, not human/device sign-off.

### MI-07 — Linearize cancellation and resource ownership

- [x] Complete MI-07 (P3; depends on MI-05/MI-06 and F2 review).

**Description:** Implement operation identity/phases and the atomic cancel-versus-commit gate with bounded abort helpers.

**Acceptance:**
- [x] Cancel/COMMITTING share a short in-memory gate with no foreign I/O; accepted cancellation before commit, including during promotion, cannot publish; late Cancel cannot dispatch helpers into metadata writes.
- [x] At most two abort operations capture immutable operation/resource identity; register signals before blocking acquisition and dispose late-returned handles through an ownership handshake without double-close/reused-fd races.
- [x] Repeated Cancel, blocked signal cancellation, late descriptors and pending source close remain truthful and bounded; deterministic JVM race tests release their own latches/helpers and prove no surviving worker before passing.

**2026-11-16 result:** [MI-07 evidence](../docs/verification/model-import.md#mi-07--cancellationcommit-gate-and-captured-source-ownership), commit `90ba6d1`, four planned additions. RED gate 5/5, resource 3/8, acquisition guard 1/6 failed, then GREEN. **15 new / 170 total JVM tests pass**, zero failures/errors/skips, lint 0 errors / 4 existing warnings; both APKs pass (**25s** final build). Includes 100 concurrent cancel/commit pairs, immutable captured aborts, blocked-signal independent close, late source, close/rejection faults and explicit no-surviving-worker/helper assertions. Android descriptor/provider behavior remains unexecuted; nonblocking bounded executor and adapter integration remain MI-09. No phone writes/native changes; MI-08 next.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ImportOperationTest' --tests '*ImportResourceRaceTest'`.
**Files (4):** `M/ModelImportState.kt`, `M/ImportOperation.kt`, `U/ImportOperationTest.kt`, `U/ImportResourceRaceTest.kt`. **Scope:** Medium.

### MI-08 — Persist one outstanding picker request

- [x] Complete MI-08 (P3; depends on MI-03/MI-07).

**Description:** Implement durable epoch/nonce reservation and consumption, plus bounded platform-route identity logic.

**Acceptance:**
- [x] Persist the one outstanding token before launch and clear only its matching result before source open; ambiguity blocks launch/import, with no URI persisted and no stale A result accepted after B reservation/consumption.
- [x] Model reservation/first-launch-claim/awaiting-result phases, abandoned-route supersession by fresh user action and process-restoration matching; passive restoration cannot relaunch an uncertain request or restart a dead copy.
- [x] Request-code allocation is nonwrapping 0x0001–0xFFFE per saved logical route, with bounded pending identity; cancellation/bad result/launch failure clears only its matching request and preserves the active payload.

**2026-11-16 result:** [MI-08 evidence](../docs/verification/model-import.md#mi-08--durable-picker-identity-and-bounded-launchresult-routes), commits `713d6c2`, `df4f406`, `115b005`, five planned files. RED store 2/19, route 3/4, coordinator 3/7 failed, then GREEN. **18 new / 188 total JVM tests pass**, no failures/errors/skips; lint 0 errors / 4 existing warnings; both APKs pass (**23s** full build). Checked reservation/consumption preserves old active bytes; old/ambiguous/pending-close failures cannot authorize launch/import. Stale/restored epoch/results, one memory launch claim, explicit route supersession, nonwrapping saved codes and blocked-reservation memory-gate behavior covered. No URI persistence, actual Android picker/device run, phone/native changes or surviving test worker. MI-09 next.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*PickerRequestTest' --tests '*ModelStoreTest'`.
**Files (5):** `M/PickerRequest.kt`, `M/PickerCoordinator.kt`, `M/ModelStore.kt`, `U/PickerRequestTest.kt`, `U/ModelStoreTest.kt`. **Scope:** Medium.

### MI-09S — Reviewed storage integration prerequisite (host-only split)

- [x] Complete MI-09S before the MI-09 controller.

**2026-11-16 technical split review:** MI-03 exposes recovery only; MI-08 adds picker updates. Neither provides exclusive streamed candidates, final-name promotion or checked active-selection publication. MI-09 cannot wire the approved flow without those ports, and `ModelFileOps.Writer.write(ByteArray)` currently cannot accept a bounded buffer subsection without copying it. Rather than quietly touch a sixth controller file, implement this storage seam as its own five-file, tested/committed subtask under the standing host-only direction. This is an assistant technical decomposition of already-approved behavior, not fabricated separate owner/human approval or expanded device scope.

**Acceptance:** One owned exclusive candidate blocks live recovery/second writers; offset/count writes add no production buffer; closed-candidate promotion checks final absence and reconciles rename errors; checked publication preserves old/new/ambiguous outcomes, old bytes until confirmation and separate cleanup debt. Test actual fake-source/store paths and failure boundaries; run JVM/lint/both APKs. No UI/provider/device/native changes.

**Files (5):** `M/ModelFileOps.kt`, `M/AndroidModelFileOps.kt`, `M/ModelStore.kt`, `U/FakeModelFiles.kt`, `U/ModelStoreTest.kt`.

**2026-11-16 result:** [MI-09S evidence](../docs/verification/model-import.md#mi-09s--checked-store-integration). Commits `e1c2318`, `1c61538`, `93d625e`; five-file split respected. Meaningful RED candidate 2/25, promotion 3/28, publication 3/31, reconciliation 1/32, unsynced handoff 1/37, then GREEN. **14 new / 202 total JVM tests pass**, no failures/errors/skips; lint 0 errors / 4 existing warnings; both APKs pass (**42s** final build). Exact fake-source/store first import/replacement, old/new/ambiguous publication, close/inode/collision/unsynced guards and separate cleanup debt covered. No phone/Android/native changes; controller still pending.

### MI-09 — Orchestrate one process-wide import

- [x] Complete MI-09 (P3; depends on MI-07/MI-08 and reviewed MI-09S storage integration).

**Description:** Connect engine/store/operation ownership in a pure controller and lazy application-context singleton.

**Acceptance:**
- [x] Reserve one bounded I/O command slot before enqueueing; duplicate Activities/actions attach or reject, never create a second writer; no Activity is retained in the singleton or background component added.
- [x] Publish immutable increasing-revision snapshots using one replaceable main notification, 10 Hz copy-update cap and <=1-second eligibility while I/O advances; stage/results replace stale progress and unsubscribe prevents destroyed-Activity delivery.
- [x] End-to-end fake-source first import, replacement, cancellation, commit ambiguity and cleanup-debt flows preserve old bytes until confirmed publication; expose only validated active paths and useful sanitized failures.

**2026-11-16 result:** [MI-09/A evidence](../docs/verification/model-import.md#mi-09--serialized-controller-bounded-cancellation-and-notifications), commits `87fa7db`, `bb4e390`, `9796e92`, five planned files. Meaningful RED command/notification 2/2, pipeline 1/4, pending finalization 1/6 and stale classification, then GREEN. **17 new / 219 total JVM tests pass**, zero failures/errors/skips; lint 0 errors / 4 baseline warnings; both APKs pass (**39s** final build). Application-only ownership fixes the intermediate static-context lint warning without suppression. Real JVM workers/latches/futures confirm bounded independent aborts and no survivors; fake transaction/notification/picker paths cover old bytes, ambiguity, cancellation, cleanup debt and progress ordering/rate. Android/Handler/SAF runtime unexecuted; no phone access. A technical review complete; distinct human-only sign-off is not invented.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelImportControllerTest' --tests '*ImportOperationTest' --tests '*PickerRequestTest'`.
**Files (5):** `M/ModelImportController.kt`, `M/ModelImports.kt`, `M/ModelImportState.kt`, `M/ImportOperation.kt`, `U/ModelImportControllerTest.kt`. **Scope:** Medium.

### Checkpoint A — Controller safety before UI integration

- [x] P1–P3 host tests, regressions, lint and both APK builds pass; inspect race/notification/resource evidence and preserved native payloads.
- [ ] Owner reviews publication, cancellation and picker-state safety before P4; no runtime/phone claim from this checkpoint.

**2026-11-16 actual technical review:** [A technical audit](../docs/verification/model-import.md#checkpoint-a-technical-review--2026-11-16), `.cache/model-import/checkpoint-a-20261116T155637Z/review-audit.json`, 16 exact source/test files plus reports/artifacts/declarations. No unresolved host-contract blocker for MI-10; source/resource/commit/picker/notification and native-preservation checks performed. This is assistant technical review, not the separate human-only checkbox above. Owner's latest standing “don't stop until phoen required” instruction (reaffirmed “continue”) authorizes host progression through MI-19/H2 without fabricating that human/device result.

### MI-10 — Render the import screen

- [x] Complete MI-10 host implementation/compilation (P4; depends on MI-09 and Checkpoint A; separate Android runtime acceptance remains MI-20).

**Description:** Add the small reusable Views binder for active/pending model, progress, Cancel, warnings and recovery states.

**Acceptance:**
- [x] Render safe names/actual size, known versus unknown progress, preparing/cancelling/finishing states and imported-not-compatible wording; pending replacement never looks like the active model.
- [x] Reset affordance is initially hidden/disabled unless unavailable-state recovery is wired; callbacks are explicit, detached with the screen, and never act from model URI/argument intent extras.
- [x] Use the MI-06 isolated-store helper and real provider adapter/engine/controller in `ModelImportScreenTest#unknownSizeProviderRendersCopiedBytesWithoutPercent`: a deterministically gated unknown-size producer must render copied bytes before EOF, without a fabricated total/percentage, and then complete correctly; use bounded producer/observer handshakes rather than sleep-based timing assumptions. Other binding tests cover state-only rendering; no production singleton is initialized, and unbound actions remain disabled until MI-11. These cases compile here and execute at MI-20.

**Verify:** `./gradlew --no-daemon :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`.
**2026-11-16 host result:** [MI-10 evidence](../docs/verification/model-import.md#mi-10--import-views-and-gated-unknown-size-contract), commit `016cac3`, five reviewed files. **219 JVM tests pass**, no failures/errors/skips; lint 0 errors / 4 baseline warnings; both APKs pass (**44s** final build). Four new screen methods **compile, NOT executed**, including a real-adapter/controller isolated-root test with token-bound before-EOF unknown-size producer/observer/release/join handshakes; no sleeps or production singleton. Views separate old active/new pending, preserve Long byte displays, provide explicit states/errors and disable unbound/reset actions. Standing DoD reviewed: runtime/visual/human/ship completion is not claimed. No phone/native/other-owner changes. MI-11 next.

**Files (5, reviewed fixture integration):** `M/ModelImportScreen.kt`, `R/layout/main_activity.xml`, `R/values/strings.xml`, `I/ModelImportScreenTest.kt`, `I/FixtureModelProvider.kt`. **Scope:** Medium.

**2026-11-16 technical fixture review:** Existing provider fixtures are finite, prepopulated 50-byte streams and cannot prove the required before-EOF UI state. Extend the same test-only provider with one token-bound gated unknown-size producer and bounded release/join handshakes; add no production test command/provider/path override. This is the fifth file (not a quiet sixth), implementing the already-required MI-10 acceptance case under standing host authorization. Instrumentation is compiled here, not run on a phone.

### MI-11 — Wire the launcher and system picker

- [x] Complete MI-11 host implementation/compilation (P4; depends on MI-10; real system picker/device acceptance remains pending).

**Description:** Connect the existing Activity to the lazy import owner and platform picker with safe saved request routing.

**Acceptance:**
- [x] MAIN/LAUNCHER and explicit exported declaration make the app user-launchable without adding storage/network permissions, VIEW/SEND import filters or production fault/path controls; incoming import extras are ignored.
- [x] `ACTION_OPEN_DOCUMENT`/OPENABLE/`*/*` requests one document; only a matching single readable content URI can reach the reserved operation. Picker cancellation, missing handler, bad/duplicate/multiple result and denied access preserve selection.
- [x] Bundle/route tests validate bounded tokens/request codes, no saved URI, and launch/result dispatch without invoking the owner's real picker/store from automated mutation tests.

**Verify:** `./gradlew --no-daemon :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`; run [V-manifest](#v-manifest--decode-packaged-declarations); compile `AndroidPickerRouteTest` for MI-20. Manual real-UI checks wait for device/data consent.
**2026-11-16 host result:** [MI-11 evidence](../docs/verification/model-import.md#mi-11--launcher-and-opaque-saf-result-routing), commits `ce6a83d`, `b9a6450`, five reviewed files. **219 JVM tests pass**, no failures/errors/skips; lint 0 errors / 4 baseline warnings; both APKs pass (**25s** corrected final build). Six route methods **compiled, NOT run**; no real picker, default store or phone accessed. Actual APK manifests decoded with SDK command-line tools 12.0: sole exported MAIN/LAUNCHER, no VIEW/SEND/new permissions/provider/service/receiver in production; fixture test-only. Strict opaque Bundle/code bounds, single content/read flag result and fixed failure handling implemented; incoming extras ignored. Full configuration/lifecycle retention is MI-12 next; no runtime/ship claim. Native/excluded owner work preserved.

**Files (5, reviewed UI error strings):** `A/MainActivity.kt`, `M/AndroidPickerRoute.kt`, `I/AndroidPickerRouteTest.kt`, `app/src/main/AndroidManifest.xml`, `R/values/strings.xml`. **Scope:** Medium.

**2026-11-16 technical integration review:** Missing system picker, malformed result, busy owner and retired/exhausted Activity route need accurate fixed user-facing messages. Reuse the existing strings file as the fifth deliverable instead of hardcoded/provider exception text. No extra component, external import extra, production test control or device execution is authorized by this bookkeeping change.

### MI-12 — Verify Activity recreation routing

- [x] Complete MI-12 host implementation/regressions/compilation (P4; depends on MI-11; actual S25 lifecycle remains MI-21).

**Description:** Complete the lifecycle integration and race cases around rotation, multiple Activity instances and restored picker callbacks.

**Acceptance:**
- [x] Same-process reservation/claim/picker-open/copy rotation reconnects to one owner without duplicate launch/copy or a destroyed-Activity reference; leaving/locking has no background-completion promise.
- [x] Restored pending versus consumed/superseded/old-epoch requests, stale request codes and code exhaustion cannot launch an unintended copy; genuinely delivered matching results start only after store recovery.
- [x] Pure routing/controller tests pass and Android lifecycle-route cases compile; real S25 rotation/lock/leave/restart acceptance remains pending MI-21.

**2026-11-16 host result:** [MI-12/F3 evidence](../docs/verification/model-import.md#mi-12--recreation-ownership-and-transient-result-handoff), commits `a6f05b9`, `2c401db`. Four planned files changed; existing coordinator guards reused unchanged. **4 new / 223 total JVM tests pass**, no failures/errors/skips; lint 0 errors / 4 baseline warnings; both APKs pass (**25s** final build). One transient result survives only same-process non-configuration retention with no Activity/View/callback reference, no saved URI/dead-copy restart, identity-bound handoff and stale-route guards. Three new Android route contracts compile, NOT run (20 model-import Android methods total). A malformed-result getter escape was found/fixed in actual F3 review; 14 exact files/reports/artifacts reviewed. No actual Android rotation/process death/phone/human/ship result; MI-13 next.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*PickerRequestTest' --tests '*ModelImportControllerTest'`; `./gradlew --no-daemon :app:assembleDebugAndroidTest`.
**Files (5):** `A/MainActivity.kt`, `M/AndroidPickerRoute.kt`, `M/PickerCoordinator.kt`, `I/AndroidPickerRouteTest.kt`, `U/ModelImportControllerTest.kt`. **Scope:** Medium.

### Checkpoint F3 — First import path integrated

- [x] MI-10–MI-12 pass applicable tests/lint/build/manifest checks; real SAF and Activity lifecycle results remain explicitly unverified until device acceptance.
- [x] Review screen/route boundaries before adding destructive recovery UI; no owner-state reset or replacement is performed by compilation tests.

**2026-11-16 technical review:** [F3 audit](../docs/verification/model-import.md#f3-technical-review--2026-11-16), `.cache/model-import/f3-20261116T164425Z/review-audit.json`, 14 exact files plus reports/artifacts/declarations. Malformed-result exceptions are rejected, not waived. No unresolved host-contract blocker for MI-13; reset affordance remains hidden/unwired, all real Android/device behavior unverified. Assistant review under standing host authorization, not a fabricated human/phone sign-off.

### MI-13 — Recover an explicitly authorized reset

- [x] Complete MI-13 (P5; depends on MI-03/MI-09 and F3 review), host scope only.

**Description:** Implement reset authorization and idempotent interrupted-reset recovery under the store's exclusive owner.

**Acceptance:**
- [x] Only verified committed `reset` authorizes deletion; `reset.next` never does. Preserve authorization until an empty selection with its recorded new epoch is confirmed, clear reset sidecar first and authoritative marker last.
- [x] No reset while store writers/descriptors own resources; delete only positively owned no-follow regular files, leave unknown/unowned entries alone, and keep source/unrelated files unchanged. Controller helper/consent integration follows MI-14.
- [x] Host tests cover fresh/no-confirmation/interrupted authorization, first-marker write failure, corrupt authorization, partial deletion/empty publication/marker removal, and fresh-confirmation repair without promoting `.next` or claiming reclaimed bytes. Actual UI decline remains MI-14/MI-20.

**Host result:** [MI-13 evidence](../docs/verification/model-import.md#mi-13--explicit-authorization-and-repeatable-reset-recovery). Four planned files changed; actual review required a separately tested/committed two-file MI-13R checked-record repair prerequisite (`a63f203`), not a quiet sixth file. Failed corrupt-marker repair preserves the old base until atomic rename. RED assertions retained; final **238 JVM passes**, zero failures/errors/skips, lint 0 errors / 4 baseline warnings, both APKs pass (**27s**). Fifteen new tests; 14 packaged native hashes unchanged; 20 Android methods still compiled only. No phone/runtime/owner-store result. MI-14 next.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelResetTest' --tests '*ModelRecoveryTest'`.
**Files (5):** `M/ModelReset.kt`, `M/ModelStore.kt`, `U/ModelResetTest.kt`, `U/ModelRecoveryTest.kt`, `U/FakeModelFiles.kt`. **Scope:** Medium.

### MI-14 — Require current in-app reset confirmation

- [x] Complete MI-14 (P5; depends on MI-10/MI-13), host scope only.

**Description:** Expose the existing reset affordance only for actionable recovery failure and connect an explicit confirmation to the controller.

**Acceptance:**
- [x] Explain private-copy/selection removal and source preservation; no consent is persisted, and decline/dismiss/destruction has no command. Android lifecycle behavior is compiled only, pending MI-20/MI-21.
- [x] Bind confirmation to the current recovery revision/epoch and opaque dialog identity; reject stale confirmation after state changes and require quiescent exclusive ownership before reset work.
- [x] Isolated view/controller contracts cover success, failed deletion/debt, refusal and safe retry; there is no external reset command, production test-path override or app-data-clear fallback. View tests compile only.

**Host result:** [MI-14/B evidence](../docs/verification/model-import.md#mi-14--current-in-app-consent-and-checkpoint-b), commits `5530c11`, `fb56faa`, `89e515b`; separately reviewed one-file MI-14W Activity binding `9e3f1a4`, not a silent sixth file. Four new JVM passes / **242 total**, lint 0 errors / 4 baseline warnings; both APKs pass (**25s** final build). Two new screen cases compile only, **22 Android methods not run**. Actual B review fixed superseded same-revision dialog callbacks; audit `.cache/model-import/b-20261116T170332Z/review-audit.json`. No phone/runtime/human sign-off. MI-15 next.

**Verify:** `./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelImportControllerTest' --tests '*ModelResetTest'`; `./gradlew --no-daemon :app:assembleDebugAndroidTest`.
**Files (5):** `M/ModelImportScreen.kt`, `M/ModelImportController.kt`, `R/values/strings.xml`, `I/ModelImportScreenTest.kt`, `U/ModelImportControllerTest.kt`. **Scope:** Medium.

### Checkpoint B — Integrated import and recovery boundary

- [x] MI-13/MI-14 host tests and compile/lint/APK checks pass; actually reviewed destructive-action consent, namespace isolation and the P4/P5 flow.
- [x] Assistant technical review completed before adversarial harness work; injected/JVM evidence is distinct from compiled Android cases and later real device results. No separate human approval is invented.

### MI-15 — Compile the isolated Android contract suite

- [x] Complete MI-15 (P6; depends on MI-06/MI-12/MI-14 and actual B technical review), compilation scope only.

**Description:** Add real filesystem/record contract cases and a deliberately explicit, bounded instrumentation-suite roster.

**Acceptance:**
- [x] Tests target generated `model-import-tests/<run-id>` roots and selection metadata only, with validated ids, no links to owner paths and cleanup confined to positively identified fixtures.
- [x] Actual rename/sync/no-follow/record recovery, source adapter, view-binding and route tests compile; no runtime/platform pass is inferred.
- [x] `ModelImportIsolatedTestSuite` includes exactly the four bounded expected-pass classes below; excludes production-owner UI mutation, setup/pause/crash/hang classes and native smoke. Compiled teardown closes fixture descriptors/leases.

**Host result:** `7d1e7f1`, two of four planned files needed; [MI-15 evidence](../docs/verification/model-import.md#mi-15--isolated-android-contract-suite-compiled). Lint/both APKs pass in 18s, 0 errors / 4 baseline warnings. Four additional real-filesystem contracts compile, **26 Android methods NOT run**; unchanged 242-JVM evidence remains applicable. No phone/native/owner-state access. MI-16 next.

**Verify:** `./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest`; inspect suite source, test manifest and APK class separation. Runtime is MI-20.
**Files (4):** `I/AndroidModelStoreTest.kt`, `I/ModelImportTestStore.kt`, `I/ModelImportIsolatedTestSuite.kt`, `M/AndroidModelFileOps.kt` (only contract fixes backed by tests). **Scope:** Medium.

### MI-16 — Compile two-phase process-death cases

- [x] Complete MI-16 (P6; depends on MI-15), compilation scope only.

**Description:** Add test-only setup/checkpoint/verification instrumentation, separate from the ordinary suite.

**Acceptance:**
- [x] Compiled setup requires validated run id/scenario/checkpoint/orchestrated arguments, creates isolated fixtures only and emits one correlated ready marker; bad arguments fail before mutation.
- [x] Compiled real-file-operation wrappers cover all 23 first/replace/reset pairs; separate verification checks old/new/empty, bytes/metadata, request consumption/no relaunch, cleanup and reset epoch. No production entry point is used.
- [x] No checkpoint/fault controls ship in production; paused setup is excluded from passing tests and has a finite 120-second failure fallback. External stop/identity proof/cleanup is MI-17A and real execution MI-22.

**Host result:** [MI-16/H0 evidence](../docs/verification/model-import.md#mi-16--two-phase-process-death-fixtures-compiled--h0-review), commits `06e1bb4`, `5d426e1`, `c0e4579`, `fbfdbd8`. Final lint/both APKs pass in 18s, 0 errors / 4 baseline warnings. **26 ordinary + 2 crash methods compiled only**, 23 pairs unexecuted. 242 JVM results unchanged/applicable. Actual seven-file H0 audit retained via `.cache/model-import/h0-current-path.txt`; no phone/process-death/human result. MI-17 next.

**Verify:** `./gradlew --no-daemon :app:assembleDebugAndroidTest`; inspect that setup/verify classes are excluded from `ModelImportIsolatedTestSuite`; host checkpoint-protocol parsing is tested in MI-17A. Runtime is MI-22.
**Files (4):** `I/ModelImportCrashSetupTest.kt`, `I/ModelImportCrashVerifyTest.kt`, `I/ModelImportCrashProtocol.kt`, `I/ModelImportTestStore.kt`. **Scope:** Medium.

### Checkpoint H0 — Isolated fixture/checkpoint boundaries

- [x] MI-15/MI-16 compile with shared test-root ownership and explicit suite exclusions; actually reviewed provider-package isolation and test-only checkpoint controls.
- [x] H0 assistant technical review complete before device-host work. No Android/platform/process-death behavior is marked passed from compilation; separate human/device outcomes remain unverified.

### MI-17 — Bound filtered device-test execution

- [x] Complete MI-17 (P6; depends on MI-16 and actual H0 technical review), host/fake scope only.

**Description:** Implement the Python-standard-library device session, APK identity pre/postflight and bounded ordinary-suite/native-smoke modes, separate from crash orchestration. The entry script owns fixed mode/filter/checkpoint tables; `model_import_device.py` owns generic checked transport/identity/deadline/teardown primitives, so MI-18 can extend the protocol without duplicating that allowlist or adding a sixth touched file.

**Acceptance:**
- [x] Require private `ANDROID_SERIAL`, explicit `--allow-device-writes`, fixed package/class identities and installed/local APK-hash agreement; no install/clear/uninstall/downgrade/arbitrary class/package/root inputs. `--help` has no ADB calls.
- [x] Fixed suite/native filters, complete nonzero fixed-roster reporting, required isolated unknown-size case, bounded operations/reserved teardown and retained APK/UID baseline are host/fake tested. Actual runs await MI-20.
- [x] Fake tests cover absent/stale/noisy/newline-free output, inherited-open pipes without reader threads, zero/skipped/wrong class/count/package/APK/UID, budget/teardown expiry, and private exclusive evidence. Run-id/custom-marker cases follow MI-17A.

**Host result:** [MI-17 evidence](../docs/verification/model-import.md#mi-17--bounded-filtered-host-execution--fake-adb), **22 Python fake/transport tests pass** (0 failures/errors/skips); RED assertions retained; help passes without ADB. Source hashes/logs via `.cache/model-import/mi-17-current-path.txt`. No phone or actual APK-retention/runtime result. Prior 242 JVM and compiled artifacts unchanged/applicable. MI-17A next.

**Verify:** `python -m unittest discover -s scripts/tests -p 'test_model_import_recovery.py' -v`; `python scripts/check_model_import_recovery.py --help` (no ADB/device writes). Real suite runs wait for MI-20 consent.
**Files (4):** `scripts/check_model_import_recovery.py`, `scripts/model_import_device.py`, `scripts/tests/test_model_import_recovery.py`, `docs/verification/model-import.md`. **Scope:** Medium.

### MI-17A — Orchestrate retained process-death cases

- [x] Complete MI-17A (P6; depends on MI-17), host/fake scope only.

**Description:** Extend the checked device session with the first/replace/reset scenario state machine and fake-ADB protocol tests; cancel-hang support follows in MI-18.

**Acceptance:**
- [x] Fixed first/replace/reset pairs, fresh generated run ids, exact framed ready/process match, controlled target termination before separate nonzero verification are fake tested; setup is never counted as pass.
- [x] Reserved teardown/work budgets, complete verification marker + reporting, distinct process-start identity, independent final teardown and original APK/UID baseline are host checked. Failure halts mutation with explicit later `not_run` rows; unknown fixture cleanup remains false, not a success.
- [x] Fault tests cover setup/stop/verify/final transitions, timeout, cleanup/postflight failure, all 23 first/replace/reset boundary pairs and invalid combinations. No arbitrary production root/path/install or inferred consent.

**Host result:** [MI-17A/H1 evidence](../docs/verification/model-import.md#mi-17a--retained-process-death-orchestration--h1-review), **33 fake/transport tests pass**, no failures/errors/skips; all 23 pair simulations, RED regressions retained. Source/report audit via `.cache/model-import/h1-current-path.txt`, eleven exact files reviewed. No phone/actual process-death/runtime result. MI-18 next.

**Verify:** `python -m unittest discover -s scripts/tests -p 'test_model_import_recovery.py' -v`; `python scripts/check_model_import_recovery.py --help`. Actual scenario execution waits for MI-22 approval.
**Files (4):** `scripts/check_model_import_recovery.py`, `scripts/model_import_device.py`, `scripts/tests/test_model_import_recovery.py`, `docs/verification/model-import.md`. **Scope:** Medium.

### Checkpoint H1 — Device harness compiled and host-tested

- [x] MI-17/MI-17A host tests pass; actual eleven-file H1 technical review covers fixed suite/protocol exclusions, isolated roots and retained-package/teardown checks. Android fixtures remain compiled only.
- [x] No device test is inferred from compilation/fake ADB; device-write consent remains absent. This is assistant technical review, not human/device acceptance.

### MI-18 — Add externally controlled non-cooperative I/O cases

- [x] Complete MI-18 (P6; depends on MI-07/MI-17A and actual H1 technical review), host/compilation scope only.

**Description:** Extend the harness with the cancel-hang scenario without adding permanent hangs to the ordinary suite.

**Acceptance:**
- [x] Compiled query/open/read cancellation and post-Cancel late-resource handoff cases; blocked close is explicitly simulated, not every OEM close implementation.
- [x] Compiled assertions check main responsiveness, <=2 helpers/no second command/reset, complete cancellation or truthful pending work and unchanged old bytes. Actual behavioral proof remains MI-22.
- [x] Separate isolated setup/verify + host protocol covers all four cancel-hang pairs and provider/target teardown; finite fixture fallbacks, normal suite exclusions. No runtime pass inferred.

**Host result:** [MI-18 evidence](../docs/verification/model-import.md#mi-18--non-cooperative-cancellation-fixtures-compiled), exactly five planned files, **34 Python passes** / 27 fake pairs, zero failures/errors/skips. Android lint/both APKs pass in 19s, 0 errors / 4 baseline warnings. **26 ordinary + 4 crash/cancel Android methods compiled only**; unchanged 242 JVM evidence applies. No phone/actual cancellation/process-death evidence. MI-19 next.

**Verify:** `./gradlew --no-daemon :app:assembleDebugAndroidTest`; `python -m unittest discover -s scripts/tests -p 'test_model_import_recovery.py' -v`. Device execution is MI-22.
**Files (5):** `I/FixtureModelProvider.kt`, `I/ModelImportCancellationDeviceTest.kt`, `I/ModelImportCrashProtocol.kt`, `scripts/check_model_import_recovery.py`, `scripts/tests/test_model_import_recovery.py`. **Scope:** Medium.

### MI-19 — Rebuild and inspect the complete host result

- [x] Complete MI-19 (P6/P7 boundary; depends on MI-18), host verification only.

**Description:** Reproduce all non-device verification from reviewed source and inspect production/test artifacts before any installation.

**Acceptance:**
- [x] Full nonzero host/JVM tests, lint and clean-output APK builds pass; 242 JVM / 128 Python / 12 disposable packaging tests, no failures/errors/skips; 0 lint errors / 4 baseline warnings retained.
- [x] Fourteen native hashes/attribution unchanged; decoded production launcher-only/no permissions or extra components; seven test-only classes/control strings absent from production DEX; no weights/fixtures; exact 26 ordinary + 4 crash/cancel methods and 27 interruption-pair rosters compile only.
- [x] Source/tool/clean-output APK identities, no-backup design, private bounded driver evidence, 41 owner-file / 94 source-input preservation hashes and explicit runtime blockers retained; no backup-transport claim.

**Host result:** [MI-19/H2 evidence](../docs/verification/model-import.md#mi-19--clean-output-host-regression-and-h2-device-gate). Full clean build **38s / all 81 tasks executed**; packaging integration **12/12 in 103.109s**, Python **128/128**, JVM **242/242**, both APKs/signatures/decoded manifests/native ZIP audits pass. Current APK SHA-256: production `76284abdf487403266e26114a24246638b4422a1dee3e0d84f6dd7ec2069f291`, test `3867922538ccec2a1abe25b9c6496440e69dee7dc41b7e3ee89cce3850abbddc`; these supersede earlier incremental artifacts. H2 focused 27-source/changes technical review plus 48-source inventory rehash retained via `.cache/model-import/h2-current-path.txt`. No phone inventory/access/ADB, actual Android/process-death or human/ship pass.

**Verify:** `python scripts/import_llama_release.py --verify-only`; `python -m unittest discover -s scripts/tests -p 'test_*.py' -v`; `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`; `python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk`; `python scripts/tests/check_native_packaging.py -v` (mutates only its disposable host project); [V-manifest](#v-manifest--decode-packaged-declarations) on both APKs.
**Files:** `docs/verification/model-import.md` (results); any discovered code defect becomes a separately reviewed small regression task, not an unbounded catch-all edit. **Scope:** Small verification task.

### Checkpoint H2 — Host-ready and device-write gate

- [x] MI-18/MI-19 host/compile scope complete with actual H2 technical review, exact compiled roster, passing host protocol/race tests, clean APK identities and explicit runtime blockers; no harness has reached the phone.
- [ ] All device-batch gates complete: MI-20 bounded inventory/retained/isolated/native acceptance is complete with explicit per-batch test-APK provenance. MI-21 production-selection/manual work and MI-22 interruption work still require separate consent; no later approval is inferred.

### MI-20 — Obtain consent and run retained isolated instrumentation

- [x] Complete MI-20 (P7; depends on MI-19, H2 review and separate physical-device/data approval). Bounded acceptance only, across separately authorized batches; no module closure.

**Description:** Inventory the intended S25/packages privately, obtain explicit installation/test/force-stop scope consent, and run the bounded isolated suite.

**Historical pre-retry status — native PASS; isolated FAIL.** The owner connected wireless ADB after MI-20 approval. One authorized SM-S931B ARM64 reports Android 17/API 37; both packages already existed, no active package processes, about 125 GiB free, checked production model paths/records absent. Both exact audited APKs retained-updated successfully with unchanged UIDs and installed-byte baseline verified before tests. Native smoke passes 1/1. Three bounded isolated runs fail/incomplete; diagnosed AndroidImportIoTest.kt:98 expects regular size 50 but gets null. Host DEX audit finds Kotlin helpers referenced but missing from the standalone provider APK; likely fixture packaging cause, no confirming device stack captured. All wrapper teardowns and retained identity checks pass; production paths remain absent/unchanged. [Wireless MI-20 evidence](../docs/verification/model-import.md#mi-20--wireless-s25-batch-native-pass-isolated-failure--2026-11-17), pointer `.cache/model-import/mi-20-current-path.txt`. Three small host-harness fixes have RED/GREEN evidence and commits; latest Python suite 133/133 passes, app/build/APK inputs unchanged. **Approved follow-up:** Java-only self-contained provider implemented in four tested commits; final build/lint pass (0 errors/4 baseline warnings), Python 133/133, six-class DEX closure with zero Kotlin/target-helper dependencies. Production APK unchanged; revised test APK SHA `660379acb43023afdafdbd0b23509f18ab890ffadc88af7244b0f65a4c9eb1fa`, NOT installed/device-tested. Before its approved retained update, original installed baseline matched but target main was active and `no_backup/models` appeared; read-only gate stopped with no phone writes/stops. Finish/cancel manual import and confirm ready; existing approval covers retry. Re-inventory/preserve the new production state, never reset/delete it or require the directory absent. Evidence pointer `.cache/model-import/mi-20-fixture-current-path.txt` and [fixture follow-up](../docs/verification/model-import.md#mi-20--approved-self-contained-fixture-repair-active-app-gate--2026-11-17). No uninstall/downgrade/data clear/owner-model deletion/reset; MI-21/MI-22 remain gated.

**Current result — MI-20 bounded acceptance PASS.** Owner ready confirmed no concurrent manual import. Read-only current-store inventory found one selection record/one model payload, no candidates; both bounded stops and revised-test-only retained update succeeded. New installed APK/UID baseline verified before fixed isolated suite: **26/26 PASS**, no failures/skips, retained/teardown checks pass. Production selection SHA and payload/root stat inventory unchanged before/after update and tests; no full payload-body hash claimed. Native1 remains the earlier pass on the identical production APK; actual native smoke disassembly and provenance asset match across old/new test APKs. No new native run invented. [Retry evidence](../docs/verification/model-import.md#mi-20--approved-isolated-retry-pass-and-production-store-preservation--2026-11-17), pointer `.cache/model-import/mi-20-retry-current-path.txt`. MI-21/MI-22 still separately gated.

- [x] Separate MI-20 batch consent recorded; revised fixture/update/retry and readiness were also explicitly approved/confirmed.

**Acceptance:**
- [x] Record owner consent, existing-selection/data impact, enough storage and privately selected device; no fresh-install/uninstall assumption or inherited native-runtime authorization. Both packages existed; initial batch had absent model paths, about 125 GiB available. Retry inventoried/preserved the now-existing one-record/one-payload store and had 129,378,608 KiB available.
- [x] Inventory before any authorized update and require consent for initially absent packages; signature/update failure never authorizes uninstall. After installation, verify installed bytes against the audited local APKs and take the **post-install, pre-test** APK/UID baseline; suites must retain that baseline, not the intentionally replaced pre-update APK hash. Both retained updates/baseline and all wrapper retained checks pass.
- [x] Run fully passing nonzero isolated-suite and separately scoped native smoke through the bounded host wrapper, then record real platform/provider/path/unknown-size integration results and retained-package checks. Isolated26 passes revised APK, native1 passed original test APK with identical production APK/native-test disassembly/provenance. Per-batch baselines retained; no single combined 27-test/new-APK native run claimed. Manual real-SAF/lifecycle/interruption checks remain MI-21/MI-22.

**Verify:** `adb devices -l` privately; authorized `adb install -r app/build/outputs/apk/debug/app-debug.apk`; `adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`; bounded `--suite isolated` and `--suite native` commands below. Installation is separately supervised/bounded and not performed by the host wrapper; establish and record the post-install baseline before either suite. Never use a standalone unbounded, unfiltered or connected test run.
**Files:** `docs/verification/model-import.md`. **Scope:** Small, device-dependent.

### MI-21 — Verify real S25 SAF and large-file behavior

- [ ] Complete MI-21 (P7; depends on MI-20 and consent for each production-selection replacement).

**Description:** Exercise the actual launcher, local provider/picker and Activity lifecycle manually, including a real copied length beyond 2 GiB.

**Latest emulator run (2026-11-18): commit and source cleanup verified; live progress/heap still open.** One approved SAF import promoted the candidate observed at 223,694,848 bytes to the selected 4,294,967,296-byte private payload. The strictly-partial guard stopped live sampling before a verifiable series was saved; this is not a heap bound or a UI-progress pass. The generated external source was removed, and the new LIVE-PROGRESS test copy remains selected. No retry or S25 access. [Run evidence](../docs/verification/model-import.md#emulator-live-copy-result), `.cache/model-import/mi-21-emulator-live-progress-20261118T031102Z/`.

**Partial PASS — real picker/import, 4 GiB copied length/restoration/cleanup, owner-manual basic Cancel.** Earlier picker and real-model import evidence remains valid. The explicitly approved temporary fixture batch completed a real **4,294,967,296-byte** SAF import, then restored the **1,846,240,768-byte** real model and deleted only the generated source; normal restoration cleaned up the private fixture payload. Final automated inventory: one selection/one real-model payload/no candidates, epoch preserved/nonce clear, original Downloads model/mmproj stats and owner41 hashes preserved before own bookkeeping. Automated Cancel remained **INCOMPLETE_CANCEL_NOT_PROVEN** (partial candidate 27,263,000 bytes, later idle views, zero eligible Cancel taps), not a Cancel-handler failure. Owner then said **“i tested `cancel` manually, it works”**: accept basic Cancel as owner-manually confirmed, separately from unobserved automated cancellation; do not repeat Cancel. No agent phone commands or post-manual snapshots followed that report. [Fixture evidence](../docs/verification/model-import.md#approved-4-gib-fixture--importrestorationcleanup-pass--2026-11-17), pointer `.cache/model-import/mi-21-fixture-current-path.txt`, batch `.cache/model-import/mi-21-fixture-20261117T191721Z/`, including `owner-manual-cancel.json`. **Full MI-21 remains unchecked:** numeric UI progress/lifecycle/offline/heap and selected-fixture source-independence still need evidence; >2 GiB actual completed copied length is verified, heap is not. All granted copy/fixture/restoration scopes are complete; no new imports, fixtures, settings, package stops or MI-22 are inferred.

**Emulator supplementary PASS:** At owner request, only `emulator-5554` was used (qemu=1, Android 17/API 37, x86_64/ARM64 translation). No installed/retained target data existed, so the existing audited production APK was newly installed without replacement/reset/test APK. Empty-store idle rotation and picker rotation (same nonce, verified picker Back, recreated Activity), screen-off/wake return, and target-only idle force-stop/new-PID relaunch passed; original rotation settings and empty selection SHA/epoch preserved, final APK/UID retained. No actual keyguard observed: genuine secure lock/unlock remains untested. No model/fixture imported, native inference tested, S25 contacted or basic copy Cancel repeated. [Emulator evidence](../docs/verification/model-import.md#emulator-only-non-importing-lifecycle-batch--2026-11-17), `.cache/model-import/mi-21-emulator-20261117T194524Z/`. These are not active-model/in-flight-copy or S25/OEM lifecycle results; full MI-21 remains unchecked. Honor emulator-only direction for further work.

**Addressed observer blockers — active-import rotation PASS (emulator):** Owner “address blocker” authorized fresh recorded work without resetting prior intent ledgers. CRLF arming is runtime-verified; a second shell built-in 4-GiB comparison error was diagnosed and replaced by verified Toybox 64-bit tests. Latest `.cache/model-import/mi-21-emulator-active-20261117T212906Z/` captured a new landscape Activity while the same candidate grew from 14,417,944 to 86,507,544/136,577,048 bytes; that inode committed as the new 4,294,967,296-byte selected copy. Same PID/UID/epoch, nonce clear, one payload/no candidates, old private test copy cleaned normally. A busy-UI/transient-status verifier failure was resolved by read-only observation of the exact new persisted record; no extra import for resolution. `resolved-result.json`/`batch-audit.json` PASS; two previous failure directories byte-preserved. Original rotation restored; external source removed. **Emulator is no longer empty: selected synthetic private 4 GiB test copy remains**, no app-data clear. S25/basic Cancel/inference untouched. [Resolved evidence](../docs/verification/model-import.md#observer-blockers-addressed--emulator-active-import-rotation-pass--2026-11-17). Full MI-21 remains open: numeric UI progress, active leave/restart, genuine keyguard, heap/offline and remaining source-independence/platform evidence; do not rerun old empty-store helpers or overwrite previous markers.

**2026-11-18 emulator active-import leave/return PASS:** Owner “start next, don't stop until blocker” continued emulator-only work. `.cache/model-import/mi-21-emulator-leave-20261118T001309Z/` proves the resolved Home launcher actually focused while the same partial candidate grew 6,815,768 → 74,973,208 bytes, then app return at 116,916,248 bytes; that inode committed as the full 4 GiB copy, same PID/UID/epoch, nonce clear. Return screenshot visibly shows 123207704 / 4294967296 bytes (2%), distinct active/pending names and truthful copying controls; one snapshot, not a monotonic series. Fifteen host guards PASS after 12 meaningful RED assertions. External source removed; new selected private synthetic copy retained, one payload/no candidates. APK/UID/rotation unchanged; parent ledger and owner41 hashes preserved before bookkeeping. No S25/basic Cancel/MI-22/inference or product changes. Active restart, complete progress, heap/offline, genuine keyguard and source-revocation/platform acceptance remain open. [Evidence](../docs/verification/model-import.md#emulator-active-import-leavereturn-pass--2026-11-18).

**2026-11-18 next slice stopped at restart harness blocker; safe recovery PASS:** `.cache/model-import/mi-21-emulator-restart-20261118T002103Z/` armed before one new SAF selection; candidate 4,718,616 bytes, target-only force-stop left same inode at 23,068,696 bytes, process absent and old selection SHA preserved. Idle-only host record decoder rejected the expected leftover candidate before planned restart. Original failure/stage ledgers retained. Read-only diagnosis verified exact dead candidate, old record SHA/payload stat/prefix and root; only that candidate was projected out for decoding, no file deletion. Safe same-case planned launcher recovery then observed new PID 12572 vs 4851, same UID10234, old active record/payload/epoch preserved, Ready/no candidate/no auto-resume. This observes cold restart after selected fixture source removal, not URI revocation/inference. Owned new external source cleaned; previous selected private synthetic 4 GiB copy retained. APK/UID/rotation, parent ledger and owner41 preserved before bookkeeping. `cleanup-recovery-result.json` PASS; `batch-audit.json` BLOCKED_HARNESS_WITH_SAFE_RECOVERY_PASS; original automated case remains BLOCKED. Host guards 16 restart + 15 changed shared leave PASS. No further progress/heap/offline test, S25, basic Cancel, MI-22 or product change. Next: host-only stopped-candidate verifier regression/fix, not automatic copy/stop repetition. [Evidence](../docs/verification/model-import.md#current-status--restart-harness-blocker-safe-same-case-recovery-pass--2026-11-18).

**2026-11-18 host verifier fixed; next progress/heap case BLOCKED safely:** Tracked host-only stopped-store verifier passes 11 focused / 144 full Python tests and actual historical snapshot replay; original restart failure ledger unchanged. Scoped restart adapter now passes 24 guards, with unchanged idle decoder and process/store stability checks. Fresh emulator reconciliation passed. One new progress/heap SAF copy then completed 4 GiB but the idle-waiting UI observer captured zero numeric in-flight samples (one busy dump miss); progress/heap result correctly remains BLOCKED. Before/idle-after heap measurements are not an in-flight/peak pass. Safe postflight: same PID12572/UID10234/epoch, exact new fixture nonce prefix/stat/record, Ready, one selected payload/no candidate/pending route. Generated external source removed; **new PROGRESS-case synthetic private 4 GiB copy remains**, replacing the old LEAVE copy normally. APK/UID/rotation and parent ledgers preserved; owner41 matched before bookkeeping. Host guards 15 progress + 15 shared leave + 24 shared restart PASS. No further copy, S25, basic Cancel, force-stop, settings, MI-22 or product/build change. Next: host-only non-idle-blocking observer design/test, not automatic import repetition. [Current evidence](../docs/verification/model-import.md#current-status--progressheap-observer-blocked-completed-copy-safely-cleaned--2026-11-18), `.cache/model-import/mi-21-emulator-progress-20261118T004307Z/`. MI-21 remains unchecked.

**2026-11-18 next host observer slice PASS; runtime readiness BLOCKED without import:** Added bounded/redacting live-event parser with explicit recognized-event listener guard. Initial16 and readiness2 meaningful RED assertions, then18 focused /162 full Python PASS. Two separately recorded observation-only fixed-emulator probes preserved the current PROGRESS fixture nonce/stat/record, APK/UID/PID/Activity/UI/rotation and old ledgers; no fixture/copy/input/settings/stop/S25. Actual bounded event stream delivered one event-header/class/footer frame, but the guard recognized no complete frame: BLOCKED_LISTENER_READINESS_NOT_OBSERVED. No false readiness/progress/heap pass or automatic import retry. Next: bounded redacted actual-frame schema and host compatibility regressions before any fresh copy. [Evidence](../docs/verification/model-import.md#current-observer-status--host-pass-event-frame-readiness-blocked--2026-11-18), `.cache/model-import/progress-events-readiness-probe/`. Original progress test and full MI-21 remain blocked/incomplete.

**2026-11-18 actual event compatibility resolved; live heap guards host PASS:** Redacted schema identified the real extra `TextChangeTypes` header; specific support added after two failing compatibility assertions. Fresh bounded observation-only probe now recognizes a complete event/listener delivery, preserving current PROGRESS fixture, APK/UID/PID/UI/rotation and old ledgers. No new import/fixture/input/settings/stop/test APK/S25. Added nine synthetic host guards for memory bracketed by the same owned partial candidate, prior payload/root/record and PID/UID/start ticks; initial fail-closed stub rejected both required valid cases, then9 focused/177 full Python PASS. Idle read-only `/proc` epoch/meminfo prerequisite passes, explicitly not in-flight evidence. Next: bounded collector integration, then separately scoped/reconciled copy if approved. Full MI-21 remains unchecked; no numeric-progress/in-flight-heap/device-matrix pass inferred. [Evidence](../docs/verification/model-import.md#current-status--event-compatibility-pass-in-flight-heap-not-yet-tested--2026-11-18), `.cache/model-import/progress-events-compatible-probe/`, `.cache/model-import/live-heap-host/`.

**2026-11-18 collector host/idle integration PASS; positive copy pending:** Added one-shot fresh-reader collector + leased fixed-emulator transport, with pre-copy uptime cutoff before sequence checks, ordered snapshot/meminfo/snapshot, stable owned candidate/process epoch, cross-interval monotonicity, bounded deadlines/polls and verified remote teardown. Sixteen collector /10 reader cases; full205 Python PASS. Actual observation-only idle probe armed from a fresh event, verified no candidate, refused before sampling meminfo (1 snapshot/0 memory callbacks), closed both objects and verified remote observer124. Exact current PROGRESS fixture/APK/UID/PID/UI/rotation and previous ledgers preserved; other40 owner hashes match. No new fixture/import/input/settings/stop/install/S25/basic Cancel/MI-22. Remaining: real positive-candidate/memory callback wiring and a separately scoped/reconciled copy/final postflight. Idle refusal is not live progress/heap acceptance; full MI-21 remains unchecked. [Evidence](../docs/verification/model-import.md#current-status--collector-hostidle-integration-pass-positive-copy-pending--2026-11-18), `.cache/model-import/live-collector-host/idle-probe-result.json`.

**2026-11-18 actual callbacks implemented/idle verified; active-copy evidence pending:** Added deadline-bound callbacks around validated inventory, real `/proc` UID/start ticks, strict candidate path/64-byte scoped-prefix binding, unchanged root/legacy/old payload/record checks and meminfo parsing. Positive synthetic snapshot→memory→snapshot integrates with existing guard;21 callback /226 full Python tests PASS. First actual idle probe blocked on CRLF UID framing; same-case diagnosis/postflight proved unchanged expected UID/model state, exact trailing-CR fix committed, old failure retained. Fresh separate actual-callback probe PASS_IDLE_ONLY: real snapshot none, collection rejects before sampling meminfo (1/0), remote124/objects closed; separate actual idle meminfo callback passes. No new source/copy/input/settings/stop/install/S25/basic Cancel/MI-22; current PROGRESS fixture and old ledgers preserved, other40 hashes match. Next: final committed-copy postflight wiring/tests and separately scoped/reconciled active copy. Positive Android candidate path, copying latency, progress/heap and full MI-21 remain unverified. [Evidence](../docs/verification/model-import.md#current-status--real-callbacks-implemented-actual-idle-checks-pass--2026-11-18), `.cache/model-import/live-callbacks-compatible-probe/idle-probe-result.json`.

**Acceptance:**
- [ ] Offline real-local-document first import/replacement, progress, Cancel, picker cancel/error, rotation before launch/with picker/during copy, multiple Activities, leave/lock/return and restart follow the spec; no duplicate copy or stale progress/state. Unknown-size provider/feedback coverage must execute on the S25 through the actual ContentResolver plus isolated controller/view tests if a real-picker unknown-size source is unavailable; label that boundary rather than claim it was exercised through the real picker.
- [ ] Import a generated/approved fixture >2,147,483,648 bytes with recorded actual length, available-space headroom and bounded-heap evidence. Source independence/removal/revocation uses only approved test-owned source fixtures; do not delete the owner's GGUF.
- [ ] Record final saved selection/cleanup debt, actual tested provider and limitations; no RAM-fit/inference/WebUI/full-GGUF-integrity claim. Do not fill the phone or reset owner state without explicit consent.

**Verify:** `adb shell am start -n io.github.greprohan.llamadroid/.MainActivity` only as approved, then the manual acceptance matrix in `docs/verification/model-import.md`; collect scoped identity/size/heap evidence without source URIs/serials. Unsupported/unavailable required checks remain blocked.
**Files:** `docs/verification/model-import.md`. **Scope:** Medium, device/storage-dependent.

### MI-22 — Execute the controlled interruption matrix

- [ ] Complete MI-22 (P7; depends on MI-18/MI-20/MI-21 and explicit package-stop/test-provider teardown approval; never run concurrently with manual import/UI testing).

**Description:** Run the real two-phase process-termination and non-cooperative-I/O matrix against isolated stores, not the production selection.

**Acceptance:**
- [ ] Every applicable scenario/checkpoint pair below reaches the expected marker, actual target termination and nonzero successful post-restart assertions; requested failures/skips/timeouts/cleanup debt are not silently dropped.
- [ ] First/replace/pending-result/reset and cancel-hang outcomes preserve authoritative old/new/empty semantics with no partial selection; provider/worker cleanup is proved, packages/APK identities/UIDs retained and owner store untouched.
- [ ] State explicitly that forced stop is not a proof of every low-memory kill, power-loss durability or actual disk-full/backup transport; unresolved runtime checks keep the module blocked.

**Verify:** `python scripts/check_model_import_recovery.py --scenario all --checkpoint all --evidence-dir .cache/model-import/recovery --allow-device-writes`, with `ANDROID_SERIAL` selected privately and consent already recorded. Review the full per-pair report, not only process exit code.
**Files:** `docs/verification/model-import.md`. **Scope:** Medium, device-dependent.

### MI-23 — Prepare model-import completion review

- [ ] Complete MI-23 (P7; depends on MI-19/MI-20/MI-21/MI-22).

**Description:** Reconcile evidence against all nine spec criteria and request owner Checkpoint C review before claiming module closure. Submit the prepared evidence with the first two acceptance items checked if ready; the post-approval status-update item and MI-23 completion remain unchecked until C approval, avoiding a circular requirement to close the task before reviewing it.

**Acceptance:**
- [ ] Every criterion maps to exact nonzero results/source/APK/device evidence or an explicit blocker; code-implemented, compiled, injected-tested and physical-device-tested are distinguished.
- [ ] Documentation describes one-model import, cancellation/storage/reset limits and reproduction, without claiming downstream server/inference/chat or universal provider/phone compatibility.
- [ ] Only after owner Checkpoint C approval mark model-import complete in status documents/tracker; no unchecked/blocking criterion is waived. Before approval, status is verified/ready-for-review at most.

**Verify:** Cross-check the spec matrix and recorded commands/test reports; `git diff --check`; verify local document links/anchors and unchanged historical native records. Any missing physical result blocks closure.
**Files (4):** `docs/verification/model-import.md`, `README.md`, `CAPABILITY-MAP.md`, `tasks/todo.md`. **Scope:** Small documentation/review task.

### Checkpoint C — Model-import completion, not M0 completion

- [ ] All mandatory spec criteria have passing evidence; all runtime/source/provider limitations and backup/disk-full/power-loss non-claims remain explicit.
- [ ] Owner reviews evidence and explicitly closes **model-import only**. Server lifecycle, compatibility/loading, inference and WebUI remain separate modules/gates.

### Instrumentation roster and interruption protocol

The ordinary `io.github.greprohan.llamadroid.modelimport.ModelImportIsolatedTestSuite` includes exactly:

1. `AndroidImportIoTest`
2. `ModelImportScreenTest`
3. `AndroidPickerRouteTest`
4. `AndroidModelStoreTest`

All mutate only validated test-owned roots/metadata and bounded fixture data. No production singleton is initialized to obtain a test path. The suite must include and pass the real-provider/isolated-controller/view method `ModelImportScreenTest#unknownSizeProviderRendersCopiedBytesWithoutPercent`, not merely separate fake state and provider-unit checks. Setup/verify/hang cases are excluded; existing native smoke runs separately.

The bounded host wrapper, not a standalone `adb ... am instrument -w`, owns actual execution. Its fixed filters are:

| Mode | Fixed class filter |
|---|---|
| `--suite isolated` | `io.github.greprohan.llamadroid.modelimport.ModelImportIsolatedTestSuite` (all four classes above, complete case start/end reporting, integrated unknown-size case required) |
| `--suite native` | `io.github.greprohan.llamadroid.NativeVersionSmokeTest` (nonzero, complete expected native-smoke reporting) |

Both use only `io.github.greprohan.llamadroid.test/androidx.test.runner.AndroidJUnitRunner`; there is no arbitrary `--class`/`--package` option or wildcard/unfiltered execution. Expected-pass test assertions must establish resource cleanup before passing; subsequent forced host teardown cannot turn a failed assertion or cleanup into a pass.

Checkpoint framing uses `Instrumentation.sendStatus`: code **1001** with one `model_import_checkpoint` JSON field for a ready/paused setup, and code **1002** with one `model_import_verified` JSON field after post-restart assertions/fixture cleanup. Each JSON object is <=1,024 UTF-8 bytes and has exactly `v=1`, `runId`, `scenario`, `checkpoint`, `pid`, `uid`, and `startedElapsedMs` (from `Process.getStartElapsedRealtime()`). Id/enumeration/numeric grammar and the expected package/UID/process are checked; no paths, names, URIs or arbitrary diagnostic fields belong in these objects. Accept a custom status only when its complete bundle/status-code frame has arrived, not from an arbitrary matching substring in stdout.

Only one matching ready frame is allowed, followed by a live paused setup until the controlled stop; unexpected setup completion/failure before that stop fails the case. Verification requires the matching 1002 frame **and** successful nonzero JUnit start/end/result reporting for the exact verification method, with no failures/skips. A verified frame is not a passing test by itself. Confirm the old target process has terminated before launching verification; the verified `(pid, startedElapsedMs)` identity must differ from setup even if a PID is reused. Keep output parsing incrementally bounded, rejecting oversized/malformed protocol frames without unbounded line accumulation.

The host script calls `ModelImportCrashSetupTest#stageUntilCheckpoint` and `ModelImportCrashVerifyTest#verifyAfterRestart` with validated `runId`, `scenario`, `checkpoint` and `orchestrated=true` arguments. The cancel-hang scenario instead uses `ModelImportCancellationDeviceTest#stageUntilCheckpoint` and `ModelImportCancellationDeviceTest#verifyAfterRestart` under the same protocol. Run ids are fresh 32-hex values, correlate evidence rather than authenticate it, and map only to a generated child of `model-import-tests/`; no arbitrary path/URI/package argument is accepted.

CLI contract after MI-17/MI-17A/MI-18 implementation (not an existing executable today):

```bash
python scripts/check_model_import_recovery.py --help
python scripts/check_model_import_recovery.py \
  --suite isolated --evidence-dir .cache/model-import/isolated --allow-device-writes
python scripts/check_model_import_recovery.py \
  --suite native --evidence-dir .cache/model-import/native --allow-device-writes
python scripts/check_model_import_recovery.py \
  --scenario all --checkpoint all \
  --evidence-dir .cache/model-import/recovery --allow-device-writes
```

`--help` never contacts ADB. `--suite` and `--scenario` modes are mutually exclusive; scenario mode requires `--checkpoint`, suite mode rejects it. Execution requires `ANDROID_SERIAL` in the private environment, separate recorded owner consent, local audited APKs at the standard build paths and matching retained installed packages. It does not install/update either package. The wrapper establishes a post-install/pre-test baseline and compares each postflight against it; a consented installation's pre-update APK is not the unchanged-test baseline. Scenarios are `first`, `replace`, `reset`, `cancel-hang`, or `all`; explicit inapplicable pairs fail before mutation. `all` expands exactly the applicable pairs below, not a silently filtered/skipped run.

| Scenario | Applicable checkpoint ids | Required post-restart outcome |
|---|---|---|
| first | `initial-selection-next`, `request-reserved`, `request-consumed`, `candidate-created`, `mid-copy`, `streams-closed`, `payload-renamed`, `selection-next-synced`, `selection-committed` | Empty before active-selection commit, new model after; pending-result state matches the authoritative reservation/consumption record and no dead copy auto-resumes. |
| replace | `request-reserved`, `request-consumed`, `candidate-created`, `mid-copy`, `streams-closed`, `payload-renamed`, `selection-next-synced`, `selection-committed`, `old-payload-delete` | Complete old model before commit, complete new afterward; no partial/dangling active path. |
| reset | `reset-next-synced`, `reset-committed`, `reset-delete`, `reset-empty-committed`, `reset-cleared` | Uncommitted authorization does not delete old payloads; committed reset resumes to verified empty state/intended epoch. |
| cancel-hang | `query-hung`, `open-hung`, `read-hung`, `close-hung` | Either fully cancelled with cleanup proved or honestly pending before external termination; after restart, old/empty selection with no partial active model or resumed dead copy. |

Checkpoint placement: `*-next-synced` is after the temporary record is synced/closed but before rename; `*-committed` is immediately after rename, before caller acknowledgement/retired-file cleanup; `streams-closed` is before payload promotion; `old-payload-delete` is immediately after old unlink; `reset-delete` is after the first owned payload deletion; `reset-cleared` is after marker unlink. Request checkpoints are after the matching request record's commit. `initial-selection-next` is before the first empty-record rename, with no payload created yet. These distinguish both sides of each publication boundary.

Short ADB control-command waits are capped at 15 seconds; setup/checkpoint arrival at 30 seconds and crash-verification instrumentation at 90 seconds. A crash case has a **180-second total budget including a reserved final 30 seconds for teardown**; `--suite isolated` has 300 seconds including that reserve, and `--suite native` has 150 seconds including it. Cap every phase by both its local limit and the remaining operation budget; transition to cleanup when the non-teardown budget is exhausted, not after spending the entire total budget. Teardown cannot extend that total. Pre/postflight (including APK transfer/hash checks) are each bounded separately at 120 seconds; no unbounded retry is allowed. Expiry/failure stays failed even if cleanup later succeeds. Retain only bounded parsed protocol/status and redacted error evidence (64 KiB maximum diagnostic tail per phase); do not persist raw command output/serials/URIs. Missing marker, unexpected process identity, verification with zero/skipped tests, changed APK/UID, surviving test resource/process or failed cleanup is failure, not success.

**Task approval gate: passed.** The owner explicitly approved the revised MI-00–MI-23/MI-17A task list and checkpoint requirements after review/fixes. No execution/completion checkbox is marked by approval. Explicit implementation scope/batch authorization and separate device consent are still required.

---

# Native Runtime — Approved Task List

Spec: `SPEC-native-runtime.md` (approved). Plan: `tasks/plan.md` (approved). Task list: approved by the user.

**Status: native-runtime complete; T1–T9 verified and Checkpoint C approved by the owner.** On `fix/native-runtime` source `955bfe3`, the physical SM-S931B/Android 17 API 37 passed **25/25** direct-instrumentation cases (24 ART fake cases + real pinned version command). Exit 0 in **102 ms**, installed hashes/mode 0755 and EOF/reaping/worker cleanup verified. Earlier Gradle cleanup removed both packages; the owner confirmed **no prior data** and approved fresh installation. Direct `am instrument` retained both audited APKs with unchanged hashes/UIDs/paths and empty target-UID process checks before/after. No model/inference/WebUI claim or downstream implementation approval.

Standing completion bar: applicable tests pass with meaningful nonzero discovery, no regressions or unrelated scope, diagnostics and failure cleanup verified, relevant docs updated, and no unsupported runtime claims. Missing device evidence blocks completion; marking a task blocked does not complete the module.

## Integration into main — separately owner-approved

The owner subsequently requested **merge**. Merge commit `c8ea610` integrates
`fix/native-runtime` at `9036aa9` into `main`'s history; its tree exactly matches
the approved branch. Integration used the clean worktree
`D:/workspace/projects/llama-droid-integration`, leaving the original dirty
`D:/workspace/projects/llama-droid` checkout and its local `main` at `4641a20`
unmodified by merge commands. Its dirty diff/untracked inventory changed during
integration (including `.gitignore`); those separate changes were left in place,
not incorporated or restored. Publication targets `origin/main`; the dirty local
checkout must be reconciled separately before advancing it. No stash/reset/checkout
of user work, phone commands or downstream work was used. **94 host tests pass** on the merge
tree. The later integration-status edits are documentation-only; no new native
build or device-test result is claimed.

## Current evidence and actionable blockers

### Current — owner-approved direct retest passed; packages retained

- Owner confirmed: **"there was no data, fresh install ok"**. This resolved the data-impact decision and approved fresh installation/direct testing, not a data-restoration claim or general uninstall permission.
- Clean source `955bfe3` remained Git-clean; both audited APK sizes/hashes were rechecked and the real target ZIP/payload audit passed. Exactly one authorized physical S25 was selected privately; properties matched the first run. Both packages were absent before installation.
- Explicit audited target/test installs succeeded, followed only by direct `adb shell am instrument -w -r io.github.greprohan.llamadroid.test/androidx.test.runner.AndroidJUnitRunner`. **25 unique matched start/pass pairs, 0 failures/errors/skips**, 24 ART harness + 1 real native; runner `OK (25 tests)`, **2.321s**. No Gradle/UTP, uninstall, downgrade or data-clear command.
- Real pinned version exited **0 in 102 ms** (102 ms total), timeout=false, reaped/readersStopped/streamsDrained/operationsStopped=true; exact build/commit header and all fourteen installed hashes/modes passed again. App UID **10437**, test UID **10439**; file owner UID 1000 is separate from app identity.
- Both installed APK hashes, paths and UIDs matched before/after; packages are intentionally retained. Target-UID process checks were empty before/after with packages still installed. **T9 verified; Checkpoint C subsequently approved by the owner (option A, native-runtime closure only).** Full evidence: [native-s25.md](../docs/verification/native-s25.md).

### Historical — first S25 run and unexpected test-tool cleanup

The following is the earlier run, superseded for current status by the direct retest above. Its cleanup warning remains applicable.

- Owner authorized installation/test on one connected physical S25; no serial, pairing code, GUID or connection endpoint is published. Device reports **SM-S931B, Android 17/API 37**, security patch `2026-10-05`, raw One UI `90000`, `arm64-v8a`, page size **4096**, OS-visible MemTotal **11379964 kB**.
- Replacement installs succeeded and initial installed APK hashes matched the audited local files. Gradle then ran the exact source artifacts: **25 tests, zero failures/errors/skips**, class breakdown checked (24 NativeProcessProbeTest + 1 NativeVersionSmokeTest), suite time 2.926s. Gradle succeeded in 41s, 67 tasks (2 executed, 65 up-to-date).
- Real installed native directory `/data/app/.../lib/arm64`, app UID **10436**, parent PID **17598**. All fourteen installed digests matched, file owner UID 1000/mode 0755, executable=true. Actual header: `version: 0.8.0-dev (build 12020, commit 0ec4027a2)`, `built with Clang 21.0.0 for Android aarch64`. Native exit **0**, **101 ms** observed exit/116 ms total, timeout=false, reaped/readersStopped/streamsDrained/operationsStopped=true; stdout 0 bytes, stderr 95 bytes. No model/listener started.
- In-test child-alive assertion passed before framework cleanup; UID-scoped post-run process list was empty. The earlier blanket Android-17 linking restriction was not reproduced with this APK/context/device; no binary transform or security workaround needed.
- **Operational issue:** after `connectedDebugAndroidTest`, target and test packages were both absent. Gradle/UTP uninstall-after-test cleanup can remove private app data. No original private data was inventoried/backed up; contents/loss unknown. Agent acknowledged the mistaken preservation assurance and stopped all device writes. No data recovery claim.
- T9 is held for owner disposition/safer retained-installation verification and Checkpoint C review. Do not repeat connected Gradle testing on data-bearing installs; propose explicit replacement installs + direct `am instrument`, or restore only target, or pause. No uninstall/downgrade/data-clear without separate approval. See [sanitized S25 evidence](../docs/verification/native-s25.md).

### Snapshot — blocker 5 host harness/clean-build verification

- User selected option A explicitly approving Checkpoint B and the harness/clean-build work. Shared test-only probe/cases are under `app/src/sharedTest/java/`; real installed-app smoke test is under `src/androidTest/`. Nothing was copied from the original untracked harness or shipped in production.
- Probe consumes both streams concurrently with fixed byte rings (default 16 KiB each), 30s execution deadline plus <=2s cleanup budget, bounded wait/destroy/force/reap/close/join operations, explicit EOF-before-forced-close evidence and caller-interrupt restoration. A 90s whole-test guard covers hashing/OS startup; no JVM fake proves Android system startup reliable.
- Red/green cases caught reader Errors/forced-close false successes and Java cancellation blocking in a foreign NIO interruptor. Final cancellation avoids worker interrupts; noncooperative operations are failed cleanup, not success. **24 probe JVM tests pass**, plus 19 launcher cases = **43 JVM tests**, zero failures/errors/skips. The same cases compile for instrumentation but **have not executed on ART**.
- Real smoke code checks target UID, installed file paths/modes and every digest, launches only `--version`, requires exit 0 within 30s, EOF/cleanup and the pinned `version: ... (build 12020, commit 0ec4027a2)` header. Pinned source and actual common-library strings established that format; no generic-name fallback.
- Clean detached worktree `D:/workspace/projects/llama-droid-native-runtime-clean` at **`955bfe3f58cf515943f3ebfa6b76748dcc11092d`** (advanced cleanly from initial `82ea394` and fully reverified after cancellation hardening), source status empty before/after, fresh URL download/size/hash verification and independent staging from that archive. No untracked implementation or copied native stage/local SDK properties used; global dependency caches/installed tools/debug key were reused (not a hermetic release claim).
- Final clean build **succeeded in 39s**, all 81 tasks executed; debug + instrumentation APKs compile. **94 host tests and 12 real packaging checks pass**. Lint: 0 errors, 4 existing warnings. SDK tools `latest/` are actually 12.0 and SDK inventory reports a non-blocking XML-v3/v4 warning; no unreviewed tool upgrade.
- Main APK **79,208,764 bytes**, SHA-256 `19c39bfbb8315d8deb0de5efab9b7f1ffe2b9c68f5306907e6c6b2086afba2b1`; test APK **927,596 bytes**, SHA-256 `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6`. Standalone audit passes; SDK decoded both manifests. Production excludes probe/fake/smoke classes and test provenance; test APK includes them, correct runner/target package, no native binaries. AndroidX test-only permission/activities are documented separately, not mistaken for production hooks.
- `adb devices -l` returned **no devices**. No install or connected test run. T5 extraction/permissions, T7 ART/version execution and T9 remain blocked. Clean worktree/artifacts retained for the device step; [full reproduction/tool/hash evidence](../docs/verification/native-build.md).

### Isolated branch — blocker 4 standalone auditor snapshot

- Added `scripts/verify_apk.py`: read-only, committed provenance/attribution, no downloads/extraction/native execution or binary-XML decoding. No monolithic/size-only fallback. Shared staging schema/path/ELF sanity helpers avoid divergent validation rules.
- Red: tests could not import the absent auditor. Green: **94 host tests pass = 50 importer + 44 adversarial auditor tests**, zero skips. ZIP fixtures exercise wrong ABI/payload/digests/ELF, missing/modified attribution, duplicate/case/NUL/unsafe names, links/special records, GGUF/renamed ELF, utility/staging leaks, CRC/size errors, encryption, unsupported compression, directory metadata, resource limits, multi-disk/ZIP64 (including maximum-comment locator preflight), CLI failure diagnostics and read-only behavior. No fake native execution.
- Auditor preflights bounded ZIP metadata and streams listed regular files with CRC/hash checks. It requires the exact pinned native inventory, legacy compression and source-identical attribution. Its name/magic checks are not nested-archive, obfuscation, signing-block or malware analysis; SDK/runtime/signing checks are explicitly outside its success scope.
- Real existing native debug APK passes: **79,208,764 bytes**, SHA-256 `19c39bfbb8315d8deb0de5efab9b7f1ffe2b9c68f5306907e6c6b2086afba2b1` unchanged. The CLI prints this identity and an explicit SDK/signing/device-not-verified notice.
- Separate SDK `apkanalyzer manifest print` was rerun against that same hash: package/SDK 26/35, extraction=true, debug=true, one unexported Activity, no permissions, aliases/services/receivers/providers/native-library declarations. Binary XML was not decoded as UTF-8 by the auditor.
- **12 real Gradle/APK checks pass** in 91.001s; positive APK checks now invoke the shipped CLI inside disposable project copies. Forced JVM/lint regression: **BUILD SUCCESSFUL in 17s, 35 executed tasks**, 19 JVM tests pass, zero errors/skips; same four lint warnings. No new Android APK build, device run or clean-Git-checkout reproduction was claimed in this step.
- Original checkout/stage and existing APK remain unchanged. Current source is ready for the next review gate; T5 installed extraction, T7, remaining T8 evidence and T9 remain blocked/pending. See [audit/build evidence](../docs/verification/native-build.md).

### Isolated branch — blocker 3 host packaging snapshot

- `verifyNativeStage` always runs read-only Python verification before `preBuild`/packaging, including unchanged builds and direct `packageDebug`. Missing Python and attribution sources fail with visible diagnostics; no silent bypass or downloads.
- Verified `.so` files and source-derived attribution are synced to separate `app/build/generated/` roots, with task dependencies and no source-assets duplication. Only the verified ABI snapshot is a JNI input; orphan candidates, receipts/backups and sibling ABIs are not copied. Do not concurrently import/mutate the stage while building; no cross-pipeline lock is claimed.
- Red: the original baseline **successfully built an empty-native APK after its fixture stage was removed**, so the rejection test failed as intended. Green: **12 real Gradle/APK integration tests pass** using disposable project/stage copies, including missing/tampered/extra stage, missing receipt, incomplete import, missing interpreter/notice, direct-task bypass, unchanged-build verification, generated-output repair and exact ZIP hashes/assets. Source stage unchanged.
- Clean `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: **BUILD SUCCESSFUL in 21s, 55 executed tasks**. Fast host discovery: **50 pass**; JVM XML: **19 pass, no failures/errors/skips**. Lint: 0 errors, same 4 existing warnings.
- APK **79,208,764 bytes**, SHA-256 `19c39bfbb8315d8deb0de5efab9b7f1ffe2b9c68f5306907e6c6b2086afba2b1`. All fourteen native entries are compressed, hash-identical to provenance and ARM64-only; both attribution assets match source bytes. No RPC/extra natives, GGUF, receipt or transaction controls in the APK.
- SDK `apkanalyzer manifest print` confirms minSdk 26, targetSdk 35, `extractNativeLibs=true`, debug=true, no permissions, exported Activity or service/receiver/provider/native-library declarations. AGP derives extraction from `useLegacyPackaging=true`; no redundant source-manifest extraction flag. `keepDebugSymbols` preserves bytes.
- **Not verified:** actual package-manager extraction, executable permissions, linking or process launch. T5's device extraction clause stays unchecked and is traced to T9. Standalone auditor, instrumentation and full clean-checkout reproduction are still pending; this is a clean-output build and copied-fixture integration test, not T8 completion. See [build evidence](../docs/verification/native-build.md).

### Isolated branch — blocker 2 snapshot

- User retained fourteen byte-preserved payloads; no native transformation or new toolchain.
- **50 importer host tests pass** after a red run of 43 tests (32 failures + 56 subtest/errors against old code). Coverage now includes actual duplicate tar entries, selected/unselected unsafe members, matching-digest wrong ELF headers, strict receipts, real partial-output failure, publication/rollback/marker failures, concurrent import rejection, and subprocess hard exit after backup rename with successful retry.
- Import is fail-closed: durable intent marker invalidates old bytes after a failed attempt, complete candidate+receipt precede publication, OS writer lock is released on termination, and read-only verification checks transaction state at both ends. Tests exercise process termination, not power loss or Android execution.
- Real pinned archive: **78,982,432 bytes**, SHA-256 `64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec`; import and `--verify-only` pass for exactly fourteen unchanged payloads + receipt in the isolated worktree. Original archive was read-only input; original stage was untouched.
- Full source commit confirmed from upstream tag and release APIs: `0ec4027a2a0183de87c007069adddc06e665f7de`; corrected the 39-character transcription in manifest/notice/evidence.
- NDK 27.2.12479018 / LLVM 18.0.3: all fourteen payloads are ELF64 AArch64 ET_DYN, LOAD alignment `0x4000`, no RPATH/RUNPATH, dependencies match provenance; only the launcher has `/system/bin/linker64`. Embedded UI symbols reconfirmed, not hosting.
- Regression: launcher JVM tests/lint pass; no native APK packaging or device test performed. Existing APK remains blocker 1's no-native scaffold. See [artifact evidence](../docs/verification/native-artifact.md) and [build evidence](../docs/verification/native-build.md).

### Isolated branch — blocker 1 snapshot

- Platform `Activity` + Views with no production dependencies, listener or exported launch endpoint.
- `NativeRuntimeLayout`/`NativeServerLauncher` resolve `nativeLibraryDir`, check all fourteen payloads and executable permission, use explicit argument arrays and separate output pipes, restrict `LD_LIBRARY_PATH`, remove `GGML_BACKEND_PATH`/`LD_PRELOAD`, and preserve useful start failures. Only internal JVM seams select test paths/processes.
- Red: focused JVM test compilation failed for missing launcher/layout classes. Green: **19 JVM tests pass, zero skips/failures/errors**; fixture files are never executed. Permission failure decisions use an injected predicate, not an Android execution claim.
- `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: **BUILD SUCCESSFUL**, 50 executed tasks. Lint has 0 errors and 4 warnings (redundant label, two dependency-version suggestions, Chrome OS ABI advice); no toolchain versions were changed to silence them.
- At blocker 1, the existing importer suite had **22 passing tests with gaps**; superseded by the current 50-case suite above. Auditor/harness from the dirty checkout were not copied.
- This APK has **no native payloads**. T5 packaging, T7 instrumentation, T8 native APK audit and T9 device proof remain pending. See the [branch build evidence](../docs/verification/native-build.md).

### Original checkout snapshot — documentation review, not branch results

The earlier documentation review ran:
- `python -m unittest discover -s scripts/tests -p 'test_*.py' -v`: **34 pass = 22 importer + 12 APK audit**. The duplicate-member test has no rejection assertion; the three monolithic tests exercise synthetic APK checks, not a real native merge. Passing counts do not establish all acceptance coverage.
- `python scripts/import_llama_release.py --verify-only`: **passes** for the existing fourteen-file stage. This verifier currently does not validate the receipt or ELF architecture.
- `python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk`: **fails with `UnicodeDecodeError`** while decoding the binary Android manifest. This is an existing APK, not a fresh clean-build result.
- `adb devices -l`: **no devices attached**.
- Importer `_find_ndk_toolchain()`: **None** in this environment, although the documented NDK's `llvm-readelf.exe` exists. Tool discovery/configuration, not a proven missing installation, is the issue.

That earlier review performed no Gradle/JVM/instrumentation/installation/native-execution command. Its 34-test count, disabled stage gate, audit crash and merge attempt describe the original dirty checkout, not this isolated branch.

### Blockers in precedence order

1. **Resolved: agreed native-runtime scope and launch boundary (T3/T6).** User chose isolated worktree option A. The platform Activity, internal launcher/layout and 19 meaningful JVM tests now compile/pass without JNI/Ktor/inference code or production dependencies. Original user work remains untouched. This resolves only the host boundary; native payload integration and instrumentation are later blockers.
2. **Resolved: byte-preserved artifact/staging contract (T4).** User chose option A; repaired and verified the fourteen-file importer, strict receipt/ELF checks and failure/rollback contract. Fifty meaningful host tests and the genuine archive pass; provenance and every staged digest were rechecked. No merge was copied/attempted; native version-command linking is now separately verified under T9. See current evidence above.
3. **Resolved: fail-closed Gradle packaging (T5).** User chose option A. Gate, verified JNI snapshot, extraction settings/byte preservation and generated attribution are implemented; 12 actual Gradle/APK checks and the real clean-output build pass. Actual installed extraction/permissions and version-command linking are separately verified under T5/T9; no device requirement is waived.
4. **Resolved: standalone ZIP/payload auditor (T8 host portion).** User chose option A. New auditor and 44 adversarial tests pass; real native APK passes with pinned hashes and strict attribution; separate SDK inspection passes against the same APK identity. No original untracked auditor was copied and no manifest-decoding/monolithic fallback is used. Clean-source build and instrumentation compilation evidence was subsequently completed under blocker 5; physical ART/native execution is separately verified under blocker 6.
5. **Resolved: Checkpoint B review, bounded harness and clean-source reproduction (T7/T8).** User chose option A. Probe implemented/host-tested, both APKs compiled from committed source in a clean detached worktree, fresh archive verified/staged, actual versions/results/hashes/SDK manifests recorded. Source status clean before/after; no untracked implementation input needed. T7 ART/native execution is separately verified under blocker 6, not inferred from host tests.
6. **Resolved: physical S25 native launch and retained-installation verification (T9).** After the first Gradle run removed the packages, the owner confirmed no prior data and approved fresh installation. The audited target/test APKs were installed and direct instrumentation passed **25/25** with real exit 0 in **102 ms**. Both packages remained installed with identical audited hashes/UIDs/paths before/after; native cleanup and independent UID-scoped process checks passed. No Gradle test task, uninstall, downgrade or data-clear was used. Full hash-linked evidence is in `docs/verification/native-s25.md`.
7. **Resolved: completion evidence reviewed (Checkpoint C).** All seven spec criteria are traced below; README/capability status is updated. After evidence commit `ca55ef5`, the owner selected **A — Approve Checkpoint C and close native-runtime only**. No downstream specification, implementation, merge or further device changes are authorized by that approval.

## T1: Establish the artifact provenance boundary

- [x] Complete T1.

**Description:** Reproduce the downloaded archive inspection with standard ELF tools; commit the provenance allowlist and required license/notice text while excluding generated/downloaded binaries from Git.

**Acceptance:**
- [x] The URL, release/commit, archive size/SHA-256, fourteen selected payload mappings and individual digests are recorded; launcher/dependency/CPU variant evidence matches the plan.
- [x] ELF, platform dependency, embedded-UI, and CPU feature evidence/uncertainties are documented; RPC/unrelated payloads are explicitly excluded.
- [x] Applicable upstream notices/licenses are preserved; `.cache`, native stage, local SDK paths, and build outputs are ignored.

**Verify:** Use the plan's exact `curl` and `sha256sum` commands. Run NDK `llvm-readelf -h -l -d` on the launcher and selected libraries and inspect UI symbols with `llvm-nm`; compare with pinned source. Use `git status --short --ignored` to confirm binary exclusions. No Android execution claim.

**Dependencies:** None. NDK inspection tooling is a local prerequisite; coordinate setup with T2.

**Files (maximum five):** `.gitignore`, `third_party/llama.cpp.json`, `third_party/llama.cpp-LICENSE`, `third_party/NOTICE.md`, `docs/verification/native-artifact.md`.

**Scope:** Medium.

## T2: Establish the pinned wrapper

- [x] Complete T2.

**Description:** Add the official Gradle 8.10.2 wrapper with verified JAR/distribution provenance and document local JDK/Android SDK setup. Do not change global PATH or install tooling without identifying what is already present.

**Acceptance:**
- [x] Gradle wrapper scripts/JAR/properties are official, pinned, and checksum-verified, including `distributionSha256Sum`.
- [x] Host setup identifies JDK 17, SDK platform 35/build-tools 35.0.0, and NDK 27.2.12479018 inspection tools with executable setup/verification commands.
- [x] `./gradlew --version` reports Gradle 8.10.2 on JDK 17; no Android source is needed for this checkpoint.

**Verify:** Official checksum comparisons; `java -version`; `./gradlew --version`; `sdkmanager --list_installed`; the pinned NDK `llvm-readelf --version`. Record unavailable prerequisites rather than inventing a successful run.

**Dependencies:** None; coordinate with T1 on local tools.

**Files (five):** `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `docs/BUILDING.md`.

**Scope:** Medium.

## T3: Build the minimal Views APK

- [x] Complete T3 — **isolated branch: platform Views Activity, clean debug build and lint verified; no native payload.**

**Description:** Establish the smallest functioning Android project, a platform Views Activity, and the pinned plugin/SDK configuration. This APK has no native launch UI.

**Acceptance:**
- [x] Package/SDK/ABI/JVM settings match the approved spec and plan; a platform theme avoids extra production dependencies.
- [x] A minimal Activity compiles without model import, service, WebView, permissions unrelated to this module, or exported launch hooks.
- [x] A clean debug APK and lint result are produced with actual Gradle output for the agreed source set.

**Verify:** `./gradlew --no-daemon clean :app:lintDebug :app:assembleDebug`; `unzip -l app/build/outputs/apk/debug/app-debug.apk`; inspect the merged manifest. At this stage native payloads are intentionally absent and do not constitute a native-runtime success.

**Dependencies:** T2.

**Files (five):** `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/io/github/greprohan/llamadroid/MainActivity.kt`.

**Scope:** Medium.

## Checkpoint A: Artifact and build foundation

- [x] T1–T3 evidence reviewed against the current source set; selected artifact/dependency boundary is consistent.
- [x] Pinned toolchain and APK reverified from clean committed source; no claim of native execution.
- [x] Human review before packaging work — historical approval; does not approve current architecture drift.

## T4: Stage a verified native payload safely

- [x] Complete T4 — **50 host tests + real archive import/read-only verification pass; fourteen original payloads preserved.**

**Description:** Implement the manifest-driven Python importer and verification-only mode with adversarial archive tests before implementation. Keep extraction constrained and failure output unpublishable.

**Acceptance:**
- [x] Archive digest/size and selected ELF payloads are checked before publishing the exact fourteen-file stage; unrelated files are not copied.
- [x] Tests cover checksum mismatch, wrong architecture, missing/duplicate members, traversal/absolute paths, links/special files, partial-write failure, rollback, and stale/unexpected/tampered staged files.
- [x] `--verify-only` validates payload bytes/receipt without mutation or downloads; a failed/interrupted import cannot leave a stage accepted as the new artifact.

**Verify:** `python -m unittest discover -s scripts/tests -p 'test_import_llama_release.py' -v` (real cases, red before green); `python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz`; `python scripts/import_llama_release.py --verify-only`. Reproduce checksum/stage mismatch failures on temporary test fixtures, not the user's files.

**Dependencies:** T1.

**Files (three):** `scripts/import_llama_release.py`, `scripts/tests/test_import_llama_release.py`, `docs/BUILDING.md`. Native outputs/receipt are generated, ignored files.

**Scope:** Medium.

## T5: Package the exact native payload

- [x] Complete T5 — **APK and physical S25 installed payload hashes/0755 permissions/version execution verified. Operational test-tool cleanup is separately held under T9.**

**Description:** Integrate verification-only staging into Gradle and configure extraction/preservation for the installed native-library directory, retaining license text in the APK.

**Acceptance:**
- [x] Gradle fails clearly for absent/tampered/extra native stage instead of producing a stale or empty native APK.
- [x] ARM64 payloads are extracted by the intended packaging settings and preserved byte-for-byte; no source path is used as a runtime executable location. Physical S25 test read all fourteen installed files under target app UID 10436: every digest matched, mode 0755/file owner UID 1000/executable=true; bundled version command exited 0. This checkbox is based on actual installed-app evidence, not manifest inspection alone.
- [x] APK contains the allowlisted native files and required attribution, but no RPC backend, utility executables, GGUFs, or unrelated native backend.

**Verify:** Stage with the documented importer, then `./gradlew --no-daemon :app:assembleDebug`; compare ZIP-member SHA-256 against the manifest. Demonstrate build rejection using temporary staged fixtures and restore/re-verify the genuine stage. Inspect merged manifest extraction settings.

**Dependencies:** T3, T4.

**Files (maximum four):** `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `docs/BUILDING.md`, and an asset notice-generation input if required. License assets are generated from `third_party/`, not manually duplicated binary inputs.

**Scope:** Medium.

## T6: Implement the internal direct-process launcher

- [x] Complete T6 — **implemented/compiled/APK-integrated; 19 JVM tests pass. No device native-startup claim.**

**Description:** Resolve trusted installed native paths, construct an explicit process command and controlled lookup environment, and return the child process without adding service orchestration.

**Acceptance:**
- [x] The internal `NativeServerLauncher.start(arguments: List<String>): Process` uses only the bundled executable resolved from `nativeLibraryDir`, never a shell or caller-selected executable.
- [x] Working directory/`LD_LIBRARY_PATH` use the trusted native directory; `GGML_BACKEND_PATH` is removed; missing payload/permission/start failures produce useful diagnostics.
- [x] JVM tests cover argument separation, trusted path/environment construction, and configuration failure paths without pretending to execute an ARM64 binary on the host.

**Verify:** Focused JUnit tests: `./gradlew --no-daemon :app:testDebugUnitTest --tests '*NativeServerLauncherTest'`; `./gradlew --no-daemon :app:lintDebug :app:assembleDebug`. Review production manifest for absent external launch endpoints.

**Dependencies:** T5.

**Files (maximum four):** `app/src/main/java/io/github/greprohan/llamadroid/NativeRuntimeLayout.kt`, `app/src/main/java/io/github/greprohan/llamadroid/NativeServerLauncher.kt`, `app/src/test/java/io/github/greprohan/llamadroid/NativeServerLauncherTest.kt`, `app/build.gradle.kts`.

**Scope:** Medium.

## Checkpoint B: Payload and launch boundary

- [x] T4–T6 host/JVM tests pass with meaningful test discovery; intentional stage tampering is rejected.
- [x] Packaged native payloads match the allowlist/digests and exclude RPC; no unsupported loader assumptions have been treated as proof.
- [x] User selected option A at blocker 5, explicitly approving the current packaging/launcher boundary before harness integration. This approval is not device evidence.

## T7: Add the bounded app-context smoke harness

- [x] Complete T7 — **24 shared cases pass on ART plus real pinned native smoke (25/25), no source fix required. This is not model/service/WebUI verification.**

**Description:** Add instrumentation that launches upstream `--version` from the installed app context, consumes output safely, enforces a deadline, and proves child cleanup. Test the harness's timeout/exit/error paths with a fake `Process`, not fake native ELF files.

**Acceptance:**
- [x] Instrumentation compiles with the pinned test-only dependencies; no test hook is exported in the production app. Probe, fake cases and provenance asset are absent from the production APK and present only in the instrumentation APK.
- [x] Instrumentation-side harness tests execute and cover timeout, abnormal exit, large output, and cleanup failure with a fake `Process`; cleanup/reaping and reader shutdown have no unbounded waits. All 24 shared cases passed on the physical S25's ART, including stuck APIs, interruption, reader Errors, false EOF and blocking NIO interruptors.
- [x] Real-server test identified pinned build/commit and exit 0 in 101 ms (within 30s); both streams reached EOF, child was reaped, readers/control workers stopped and in-test child-alive assertion passed. Exact evidence is in `docs/verification/native-s25.md`.

**Verify:** `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` — **BUILD SUCCESSFUL, 81 executed tasks; 43 JVM tests (19 launcher + 24 probe), zero failures/errors/skips**, four existing lint warnings. Physical S25 Gradle run passed 25/25, **but its cleanup removed both packages and may have removed private data**. Treat that historical command as unsafe for data-bearing installs; the owner-approved direct retest now also passed 25/25 and retained both packages.

**Dependencies:** T6.

**Files (four):** `app/build.gradle.kts`, `app/src/sharedTest/java/io/github/greprohan/llamadroid/NativeProcessProbe.kt`, `app/src/sharedTest/java/io/github/greprohan/llamadroid/NativeProcessProbeTest.kt`, `app/src/androidTest/java/io/github/greprohan/llamadroid/NativeVersionSmokeTest.kt`. Shared test-only sources execute on JVM and compile for instrumentation without duplicating cases or shipping probe code in production. Test-only provenance assets are generated from `third_party/llama.cpp.json`; no new dependency/version or `gradle.properties` change was needed.

**Scope:** Medium.

## T8: Audit a clean-build APK

- [x] Complete T8 — **host clean-source reproduction/audit verified, including instrumentation APK compilation; T5/T7/T9 device checks are not waived.**

**Description:** Add automated ZIP/payload auditing and reproduce the complete build from a clean worktree with only documented prerequisites. Inspect Android's packaged manifest using SDK tooling separately from ZIP inspection.

**Acceptance:**
- [x] Audit tests reject missing/extra/modified native payloads, wrong ABI, missing attribution, and embedded GGUFs; the real APK passes a meaningful integrity audit. Forty-four auditor tests pass; inventory/hash/ELF/source-attribution and recognized forbidden-name/magic checks are enforced, not size-only merge acceptance. SDK manifest/signing/device checks remain separate.
- [x] Host tests, JVM tests, lint, debug APK, and instrumentation APK **compile** from clean committed source; no untracked local implementation is required. 94 Python/43 JVM/12 packaging checks execute on host. Android instrumentation run remains T7/T9, not claimed here.
- [x] Actual tool versions, commands/results, both APK hashes/sizes, and SDK-decoded manifest extraction/SDK/export/permission checks are recorded for clean source revision `955bfe3`. Installed behavior is separately verified by the T9 version-command tests, not by this host audit.

**Verify:** Clean detached checkout at `955bfe3`: fresh pinned download + `sha256sum --check`, import/verify, 94 host tests, `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`, standalone audit, separate SDK manifest inspection and 12 opt-in packaging checks all pass. JUnit XML confirms 43 tests, zero failures/errors/skips. Full commands/tool inventory/both APK identities are in `docs/verification/native-build.md`. No device execution.

**Dependencies:** T5, T6, T7.

**Files (five):** `scripts/verify_apk.py`, `scripts/tests/test_verify_apk.py`, `docs/verification/native-build.md`, `docs/BUILDING.md`, `README.md`.

**Scope:** Medium.

## T9: Verify packaged launch on the physical S25

- [x] Complete T9 — **owner-approved fresh install/direct retest passed 25/25; both audited APKs retained with identical before/after hashes/UIDs/paths.**

**Description:** Install the audited APK and run the complete instrumentation suite on the owner's S25, recording device properties and exact process evidence. Remain within the version-command boundary.

**Acceptance:**
- [x] Device evidence records model/ABI/current Android/API/security patch/build, raw One UI, OS-visible memory, actual installed executable path/permissions and app UID; unique serial/connection details omitted.
- [x] Direct retest: `--version` executed from installed native directory under app UID 10437, exited 0 in 102 ms with exact build/commit header; in-test reaping/EOF/aliveness checks and all 24 ART harness cases passed. UID-scoped pre/post process lists empty with both packages still installed. Historical first-run UID/timings and framework-cleanup caveat remain documented.
- [x] Retained installed/test APK identity and operational review finalized. Owner confirmed no prior data and approved fresh installation after historical Gradle cleanup. Direct retest passed 25/25; both APKs remained installed with unchanged audited hashes/UIDs/paths before/after. Target UID 10437, test UID 10439; real version exit 0 in 102 ms, independent process checks empty. This is not existing-data preservation or inference/WebUI proof.

**Verify:** The historical Gradle connected run passed, then removed both packages. Do not repeat that task on data-bearing installs. After owner approval, use spec property commands, explicit replacement installs of audited target/test APKs and direct `adb shell am instrument -w -r io.github.greprohan.llamadroid.test/androidx.test.runner.AndroidJUnitRunner`, plus time/UID-scoped logs, before/after APK identity and process checks. Select only the S25 privately. No uninstall, downgrade, data-clear or claim of data restoration without separate consent.

**Dependencies:** T8; physical S25 access.

**Files (three):** `docs/verification/native-s25.md`, `README.md`, `CAPABILITY-MAP.md` (module verification status only). Any required source fix becomes a separately reviewed small task with a regression test.

**Scope:** Medium, device-dependent.

### Original checkout linker diagnosis: unverified, not a confirmed fix

The pinned launcher is ET_DYN and has six non-platform dependencies (T1 evidence). The previous claim of an Android 17 security restriction was not accompanied by device properties, linker stderr, or a hash-linked test run; do not treat it as established.

The original dirty checkout's importer attempts `ld.lld -shared --whole-archive`, not the previously documented `-r` command. That merge is not present in this isolated branch. No successful merged ELF or installed-app execution is recorded. The three monolithic tests are synthetic APK-audit tests, not importer/linker integration tests. See blocker 2 before executing import with NDK discovery enabled. Do not overwrite the genuine stage merely to try this proposal.

## Checkpoint C: Native-runtime completion review

- [x] T3–T9 revalidation evidence reviewed; real S25 smoke test passed. No native-runtime prerequisite remains blocked.
- [x] All seven approved spec success criteria traced to evidence; no unchecked requirement is relabeled complete.
- [x] Human review complete: after evidence commit `ca55ef5`, the owner selected option A approving Checkpoint C and closing **native-runtime only**. No model import, model loading, service, or chat implementation has been added here; downstream work still requires separate approval.

### Seven-criterion evidence trace (Checkpoint C approved)

| Spec criterion | Evidence |
|---|---|
| 1. Pinned clean-source APK build/toolchain | [native-build.md](../docs/verification/native-build.md): fresh download/staging at `955bfe3`, 81 executed build tasks, both audited APKs and exact tool versions |
| 2. Real layout/ELF/dependency/CPU/UI inspection | [native-artifact.md](../docs/verification/native-artifact.md), committed provenance: fourteen payloads, dependency closure, interpreter, CPU limitations and static embedded UI evidence |
| 3. Staging failure tests/JVM/lint | [native-build.md](../docs/verification/native-build.md): 94 Python, 43 JVM, 12 actual packaging checks; lint zero errors/four existing warnings |
| 4. APK native inventory/attribution/no weights | [native-build.md](../docs/verification/native-build.md): standalone audit plus separate SDK manifest checks, fourteen pinned ARM64 payloads, no RPC/GGUF |
| 5. S25 installed app-UID launch/exit/cleanup | [native-s25.md](../docs/verification/native-s25.md): direct 25/25, UID 10437, exit 0 in 102 ms, EOF/reaping/stopped workers, empty UID process checks, packages/hashes retained |
| 6. Current device properties/privacy | [native-s25.md](../docs/verification/native-s25.md): SM-S931B/API 37, ABI/patch/build/raw One UI/memory/page size; no unique transport identifiers published |
| 7. Internal consumer launch contract only | `NativeRuntimeLayout`/`NativeServerLauncher`, 19 JVM cases, [native-build.md](../docs/verification/native-build.md); no downstream import/service/chat code or production test hooks |

**Current status:** Native-runtime is **complete and merged into `main`**, with T1–T9 verified and Checkpoint C explicitly approved by the owner. Host verification and physical native smoke pass (94 Python/43 JVM/12 packaging checks; direct 25/25 ART/native instrumentation). The owner-approved fresh-install/direct retest resolved the operational hold and retained both audited packages. This closes the native build/packaging/version-launch foundation only, not M0 or the full app. The owner separately authorized integration into `main` via merge `c8ea610`; no model compatibility, inference, service/WebUI success or downstream module approval is claimed.
