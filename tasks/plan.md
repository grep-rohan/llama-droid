# Implementation Plan: Model Import

Module: `model-import`

Spec: [SPEC-model-import.md](../SPEC-model-import.md) — **owner-approved after iterative review**.

Plan status: **Owner-approved after iterative plan/tracker review.** The subsequent [detailed task list](todo.md#model-import-approved-task-list) was also explicitly approved after its own review/fixes. Implementation scope/batch authorization and device consent remain separate; current authorization is recorded in the [task gate](todo.md#model-import--approval-and-task-gate).

The completed native-runtime plan is preserved below, including its existing command anchors. Nothing here reopens its tasks or changes its evidence. This active section plans model import only, not server startup, inference or chat.

## Model-import overview

Build a dependency-free platform Views/SAF flow around a process-wide import owner. Stream one document into non-backed-up private storage, preserve the old selection until a new immutable payload is committed, and recover cancellation/process interruption safely. Production checks remain fixed-header/observed-EOF/size checks, not complete GGUF validation.

This section preserves the approved design and planned checks, not their execution results. At planning time, the source was a placeholder Activity/layout plus native-runtime infrastructure. For actual implementation and verification status, see the [task gate](todo.md#model-import--approval-and-task-gate) and [verification evidence](../docs/verification/model-import.md). The plan proposes no new dependency, SDK/native change, CI change, import service, persistent background worker or network permission.

## Model-import dependency graph

```text
Bounded records + file-operation boundary
    └─ Empty/active selection, atomic publication, restart recovery
         ├─ Streaming/header/space/cancellation engine
         │    └─ Android document/storage adapters
         │         └─ Process-wide controller + picker-result ownership
         │              └─ Views/launcher/picker/progress integration
         └─ Authorized reset + interrupted-reset recovery

Integrated flow + adversarial tests
    └─ Host/JVM/lint/APK regression checkpoint
         └─ Owner-approved S25 picker/cancel/rotation/process-death/>2 GiB evidence
```

Prioritize publication/recovery and non-cooperative I/O risks before polishing UI. Task decomposition follows plan approval; implementation does not begin at either gate without the required authorization.

## Model-import approved architecture

### 1. Small internal boundaries; no framework migration

Proposed responsibilities under `app/src/main/java/io/github/greprohan/llamadroid/modelimport/`:

- `ModelImportState`: immutable active-model, operation, warning and error snapshots. Errors never carry a raw source URI/display name to diagnostics.
- `ModelRecordCodec` / `ModelStore`: bounded selection records, strict path ownership, atomic metadata updates, recovery/cleanup/reset. File operations have an internal injectable boundary for deterministic tests.
- `ModelImportEngine`: sequential header/copy/space checks and cancellation, independent of Activity and provider IPC.
- `AndroidImportIo`: read-only ContentResolver metadata/stream and destination-space adapters. Core code does not depend on a seekable descriptor.
- `ModelImports`: application-context singleton/controller, serialization, bounded I/O/cancellation helpers, observer delivery and picker request ownership. No Activity references are retained.
- `ModelImportScreen` if needed: reusable Views rendering/binding, keeping Activity limited to Android lifecycle/picker dispatch. Do not introduce a chat UI.

These are proposed names, not implementation evidence. Keep each class focused; final small-file/task boundaries are reviewed at the task gate. Existing native launcher/layout classes are not repurposed or modified.

Proposed consumer surface: async store initialization/resolution, `beginPickerRequest`, result acceptance, `requestCancel`, confirmed-reset request, and observation of immutable snapshots. A completed active-model record contains a generated payload location, sanitized name and actual size. A lookup cannot expose an unchecked restored path; downstream file-lifetime/exclusion rules remain deferred to server-lifecycle.

### 2. Destination and bounded record format

Use a dedicated child of `Context.noBackupFilesDir`, such as `models/`, for **all** payloads, candidates, selection metadata and recovery/reset sidecars. No cache/external storage, FileProvider or custom backup agent. Inspect actual installed paths/packaged declarations; do not equate `allowBackup=false` with proven OEM transfer behavior.

Generate independent lower-case 32-hex identifiers, never user-derived paths:

```text
models/
    selection                 Authoritative bounded selection record
    selection.next            Uncommitted, exclusively created replacement record
    reset                     Authoritative, explicitly confirmed reset authorization
    reset.next                Uncommitted replacement authorization
    candidate-<id>.part        Uncommitted stream output
    model-<id>.gguf            Closed, immutable published payload
```

Use a versioned, bounded binary selection record through a small codec, avoiding a new JSON dependency or JVM reliance on Android JSON stubs. Store a magic/version, store epoch, the **currently outstanding picker nonce or none**, and either an explicit empty selection or payload id/actual signed-64-bit byte count/bounded UTF-8 name. Do not store URIs or arbitrary paths. Maximum encoded record: 2 KiB; read at most that bound plus one overflow-probe byte, then reject unsupported versions, invalid flags/ids, truncated/trailing bytes, invalid UTF-8 and inconsistent lengths. Epoch/nonce/payload ids obey the same 32-hex grammar; an active byte count must be at least 24, and an empty selection cannot contain active-payload fields. Allocate only fixed/bounded buffers, never an unchecked declared field/file length. A name is at most 256 Unicode code points and 1,024 UTF-8 bytes; neutralize control/format characters and use a bounded fallback.

Resolve the Android-provided no-backup parent canonically, then reject a symlink/non-directory dedicated root. Validate strict filename grammar, containment and no-follow regular-file type for payloads **and every atomic-record base/sidecar** before opening or deleting them. Create candidates exclusively, never by truncating an existing path. Enumerate directories lazily and close enumeration handles. Unknown files/links are not swept into cleanup. Actual payload length/readability must match the committed record on resolution; hashing/full-body parsing remains excluded.

Initialize an explicit **empty** selection atomically and verify it before reserving a picker/import. A brand-new empty root may be initialized; after interrupted initialization with no candidate/payload/reset state, an owned `selection.next` alone may be discarded and empty initialization retried. Never promote it as authoritative. If a payload/candidate, reset state or unknown entry exists without a valid selection, use the corresponding recovery/reset path instead of inventing an empty state. This keeps first initialization separate from unexpectedly missing committed metadata.

### 3. Publication protocol and ambiguity handling

Use one store owner for every read/modify/recover operation, including picker-state writes. Production construction is confined to the application-context singleton in the app's one production process; adding a second process/writer would require a separately reviewed ownership protocol. Test construction must also acquire a root-keyed in-process ownership lease so two live stores cannot mutate the same root. Distinct test roots may run independently. Keep immutable payloads plus one authoritative selection record; no extra routine-import journal is required by this design.

**Use a small checked record-replacement adapter, not Android `AtomicFile`.** Read-only source review of Android 8/API 26 found that `AtomicFile.startWrite()` can continue after a failed backup rename and then open/truncate the old base; other releases also log some failures instead of throwing. That is incompatible with preserving the old record under every confirmed pre-commit failure. The approved spec does not mandate this helper.

For each selection/reset update, exclusively create the fixed same-directory `.next` file after safe cleanup of an abandoned owned predecessor, write the bounded record, explicitly check flush/descriptor-sync and close, then use checked `android.system.Os.rename(next, base)` for the atomic namespace switch. The old base must remain untouched until that rename; do not rename it aside, truncate it, or implement delete-then-rename. Check base/next no-follow types before every operation. All these APIs exist before minSdk 26. A sync/close failure leaves the old base authoritative; keep/report any undeletable `.next` as cleanup debt that blocks another record write, not as a new selection.

After the switch, re-read/validate the whole authoritative base, compare it with the expected record, and establish that no writer/descriptor or unresolved `.next` can still mutate it before reporting commit. A read never promotes `.next` or silently repairs/deletes records. On intact storage, crash before rename retains the old base, and crash after rename retains the new base; physical-power-loss durability/directory-fsync behavior remains outside the claim. Fault tests must include rename errors, close/sync failures, a simulated exception after a successful rename, and an adapter returning without establishing its promised postconditions.

Normal import sequence:

1. Reconcile/validate the store and read the current selection. There must be no unresolved writer/reset/transaction.
2. Accept only the result bound to the currently persisted `(store epoch, outstanding nonce)`. Atomically clear that outstanding nonce while preserving the active payload, and confirm the whole record before opening the source. No outstanding match means no import, even if an Activity restores an older pending request. This prevents replay after any number of intervening requests, not just replay of the last result.
3. Query metadata, check destination space, open the source read-only and validate its 24-byte prefix. Then create one exclusively owned candidate, preserving the prefix exactly once.
4. Stream to actual EOF with incremental space/progress/cancellation checks; enforce any positive reported total exactly. Close source, complete/flush/sync/close destination and handle each failure before publication.
5. Check cancellation and promote the closed candidate to its unique final name in the same private directory. Under the single-owner namespace, verify the final path is absent and fail on collision; never overwrite it. A promoted but unselected file is still uncommitted and may be cancelled/cleaned up.
6. Use the controller's atomic cancellation/commit gate immediately before metadata publication: enter `COMMITTING` only if no cancellation was accepted and no abort helper still owns a resource. Cancellation winning this gate preserves the old selection, including when requested during final-name promotion. After commit wins, later Cancel requests must neither dispatch helpers nor promise rollback. Finish the atomic selection update and re-read/validate the authoritative record before declaring the new payload active; show finishing during this non-cancellable section.
7. Only after confirmation, delete the retired payload and other positively unreferenced owned files. Failed deletion is visible cleanup debt, not failed import or restored old selection.

Do not blindly roll back after the rename attempt throws or returns an unexpected postcondition: commit may already have happened. Stop the write attempt before reading/recovery. Keep both payloads and reconcile under the same owner; confirmed old record means pre-commit failure, confirmed new record means success, and unreadable/ambiguous outcome means recovery unavailable with neither file deleted. This applies equally to request reservation/consumption, active-payload publication and reset authorization. Never recover by promoting `.next` just because its contents look valid.

At startup, first establish whether a committed reset authorization exists; if so, resume that authorized operation before ordinary selection recovery. Otherwise read/validate the authoritative selection and owned inventory to classify candidates/unreferenced payloads. With intact storage, interruption before the selection switch retains old/empty; interruption after it retains new. Cleanup debt for unreferenced payloads need not block using the current selection or another import if space permits; an uncleared record `.next` blocks the next metadata write until safely removed. Corrupt/missing metadata, a mismatched payload length or unresolved ownership is an error, never a guessed active model. Power-loss durability and equal-length body corruption are not claimed.

### 4. Streaming, storage and progress bounds

- One 256 KiB copy buffer and a 24-byte retained prefix; no model-size allocation, mmap or seek/reopen requirement. Short reads are normal; read loops cannot mistake a short read for EOF.
- Accept byte-level `GGUF`, little-endian v2 and little-/big-endian v3. Decode version/endian explicitly and treat counts as unsigned data, never allocation sizes. A corrupt body can pass this deliberately limited check.
- Use checked signed-64-bit byte/space arithmetic. For percentage, use overflow-safe ratio arithmetic and clamp only for display; when copied bytes exceed a reported positive total, fail inconsistent-size rather than publish.
- Use available bytes on the destination filesystem, not device capacity/total free storage. Require known payload bytes plus the approved 67,108,864-byte reserve; unknown sizes are checked per write. Avoid extra buffered output that could hide pending payload allocation from accounting. Actual I/O/metadata errors remain authoritative even after successful preflight.
- Cap copy-progress rendering at 10 updates/second; while I/O advances, make progress eligible for delivery at least once per second. Use **one queued controller notification for all snapshot types**, not an extra runnable per progress/stage/result. Give snapshots increasing revisions; a stage/final update replaces any delayed progress notification and makes that same slot immediately eligible. Delivery reads only the newest revision and current subscriptions, so stale progress cannot overwrite completion or retain destroyed Activities. No wall-clock delivery/cancellation deadline is promised while provider I/O or the UI thread is blocked.
- Query only name/size columns with a `CancellationSignal`; safely fall back for absent/unusable optional metadata, but never swallow accepted cancellation as a fallback. Treat a null source descriptor/stream as an open failure. Use read-only `ContentResolver.openAssetFileDescriptor(uri, "r", signal)` and `AssetFileDescriptor.createInputStream()` so descriptor offsets/declared subsections and non-seekable pipes are respected. Do not read an underlying descriptor directly and accidentally copy bytes outside the selected document. Give the stream one close owner; if stream construction fails after descriptor acquisition, close that descriptor explicitly.
- Input means the bytes delivered by that provider stream, as the approved spec defines, not a guaranteed snapshot/raw representation of underlying remote storage. Android's read-only resolver can route through wildcard typed opening; `openFileDescriptor()` delegates through that route too and is not a conversion bypass. The app does not implement format conversion, decompression, typed-format negotiation or network-URL fallbacks. Test byte identity against the controlled source stream, sliced-descriptor boundaries, pipe/short-read behavior, null/open failures and reliable-pipe error reporting. Provider representation/metadata inconsistency remains subject to the approved strict size rule; suggest a local copy/provider when it fails.

### 5. Execution, cancellation and Activity/picker ownership

Use one bounded in-process I/O command slot/executor owned by the singleton. Reserve the operation before enqueueing: duplicate requests cannot create an unbounded queue. All file/provider calls, initialization and cleanup run there, not on the main thread. Deliver immutable snapshots with a main-thread Handler and detach observers when Activities stop/destroy.

Cancellation acceptance and entry to `COMMITTING` share one short in-memory lock/CAS gate, with **no provider/disk calls inside it**. Before commit wins, Cancel marks the operation's flag and invalidates commit eligibility immediately. After commit wins it returns finishing without setting an accepted-cancellation flag or launching abort helpers. The copy checks cancellation between operations and at the final gate, not only before renaming.

For an accepted cancellation, dispatch at most two bounded helper operations once: cancellation of the published provider signal and closing the published source handle. Bind each helper to the immutable operation id and captured resource generation, never a mutable 'current stream' reference that may later belong to another operation. Opening/query acquisition must register its cancellation signal before the blocking call and hand off a newly returned descriptor/stream through a cancellation-aware ownership handshake: cancellation before/during acquisition causes that late resource to be closed, not used or leaked. Ensure the descriptor-to-stream transition cannot race a second close of a reused raw file descriptor. No helper touches a selection/reset write stream.

Do not create replacement workers or report cleanup complete while a writer/helper still owns a resource; repeated Cancel cannot accumulate threads. A blocked signal cancellation must not prevent an independent source-close attempt. Helpers that cannot terminate leave a truthful cancelling/recovery state, not a false success. Release/clear resource registrations only when no helper can still act on them. Any proposed use of interruption must demonstrate that it cannot block the caller or bypass resource ownership.

Retain the owner across same-process Activity recreation; another task attaches or is rejected. Reserve an opaque outstanding nonce plus store epoch **atomically before launching the picker**, preserving the active payload. In memory, distinguish reservation in progress, reserved/not yet launched, launch claimed, and awaiting result. A foreground Activity atomically claims the first launch for the explicit user action, binds its request route, then dispatches on the main thread. Rotation before that claim lets the replacement Activity claim it once; rotation after dispatch must not launch again. Process restart never auto-dispatches a stored reservation whose launch status cannot be established; use an actually delivered matching result or a fresh explicit user action.

Correlate platform request codes to that exact token in bounded Activity saved state; Android's result does not echo a caller nonce automatically. Do not rebind an old result/request code to the currently pending token. Each live Activity may retain one unresolved result binding; refuse a new launch through that binding until it returns or its Activity result route is retired. Allocate request codes monotonically from `0x0001` through `0xFFFE` per logical Activity result route, saving the next value across recreation; never wrap or reuse one within that route. On exhaustion, refuse a new launch and require a fresh Activity route rather than risk misbinding an old callback. This keeps request identity state bounded without retaining an unbounded token history. No source URI is persisted.

Only a matching outstanding token may be cleared/consumed, once, before source open. Picker cancellation, bad results and launch failure clear only their own matching token. If an Activity disappears with no restorable result route, a user-requested fresh picker reservation may supersede the abandoned token, invalidating every older result; passive reattachment may complete the same-process first-launch claim above but never relaunches an already-dispatched or uncertain restored request. A stale Activity restoring request A cannot import A after B has been reserved or consumed. On process restart, reconcile authoritative pending state with the restored Activity token; mismatches are discarded, while missing routing state offers a fresh user action. Epoch changes on reset invalidate all pre-reset requests. Ambiguous request-record writes block launch/import until reconciled, exactly like other metadata writes.

Use platform `ACTION_OPEN_DOCUMENT`/`CATEGORY_OPENABLE`/`*/*`, one `content://` result, and temporary read access; no persistent grant is needed for the finished copy. Catch unavailable picker, bad results and permission failures; keep the result URI only in the reserved in-memory operation until source access finishes. If a restored transient grant is no longer valid, fail safely and ask the user to pick again. Any long-lived grant would need separate acquisition/release evidence and must not revoke another consumer's grant.

Expose an ordinary MAIN/LAUNCHER Activity with the required exported setting, but no VIEW/SEND import filter, raw-path/argument extras, external receiver/service/provider or test control. Rotation must neither relaunch the picker nor restart copying. A restored outstanding picker result may start fresh after recovery; dead running copies never resume from saved URIs. Leaving/locking may interrupt the ordinary in-process worker; no foreground service, WorkManager or wake lock is added.

### 6. Confirmed reset and interrupted-reset recovery

If reconciliation cannot establish a usable state, render an in-app reset action. Confirmation explains removal of private model copies/selection and preservation of source documents; declining does nothing. Do not use reset automatically to free space or conceal an import failure.

After all prior live writer/recovery/cancellation operations stop, acquire the exclusive reset operation and persist/verify a versioned authorization of at most 2 KiB with a fresh reset id and the intended post-reset store epoch. Use the same checked record-replacement/ambiguity rules as selection writes. Only a valid authoritative `reset` permits deletion; `reset.next` never authorizes it. A committed authorization takes precedence over ordinary selection recovery, so process death mid-reset resumes the authorized reset rather than restoring a half-deleted model.

Delete only positively owned, no-follow regular import files and selection sidecars, preserving `reset` until a new empty selection with its intended epoch has been written and verified. Using the recorded epoch makes restart repeatable instead of inventing a different epoch on each retry. Remove an abandoned owned `reset.next` first, then checked-unlink `reset` **last**, verifying both are absent before accepting imports; otherwise a sidecar left after marker removal could look like an unconfirmed reset. Unknown entries and failed deletions surface a recovery error and remain untouched.

An interrupted `reset.next` write never triggers deletion or automatically becomes committed. Keep ordinary mutations blocked while authorization is unresolved; offer a fresh explicit confirmation that may replace positively owned regular authorization files with a verified valid `reset`. An unreadable authoritative `reset` likewise needs fresh confirmation, not guessed consent. Record/publication failure must leave existing model payloads intact unless a previously committed reset already authorized their deletion. Test interruption before/during/after marker publication and removal.

Do not recursively delete the directory or clear all app data. A partial reset is not ordinary replacement and is never labelled cancellation of the previous import. The reset UI cannot run while a hung writer/helper still owns resources; after actual process termination, startup can re-establish exclusive ownership and recover or request confirmation.

## Model-import execution order and checkpoints

These approved work-package boundaries are decomposed by the separately [owner-approved task list](todo.md#model-import-approved-task-list) into small tasks with acceptance checks, dependencies and about five touched files maximum. Task approval remains separate from implementation authorization or completion.

1. **P1 — Transaction/recovery proof:** bounded records, owned-file checks, explicit empty state and immutable-payload/atomic-selection protocol. Test first-import/replacement death boundaries and commit ambiguity before UI work.
2. **P2 — Stream into private storage:** header/endian checks, short-read/non-seekable copying, unknown/misreported sizes, space reserve, safe cancellation and resource cleanup through pure seams and Android adapters.
3. **P3 — One operation owner:** bounded dispatch/notifications, atomic cancel/commit handoff, acquisition-safe cancellation helpers, immutable snapshots and durable outstanding-request/epoch matching. **Checkpoint A:** host safety tests pass, both APKs compile, and publication/cancellation/picker race tests are reviewed before UI integration.
4. **P4 — First user-visible import:** minimal launcher/Views and the real picker, active selection, progress and Cancel. Prove source independence and rotation/picker-result ownership; use a local document offline.
5. **P5 — Recover/reset through the same UI:** visible cleanup debt/unavailable states and explicit reset confirmation backed by restart-safe authorization. **Checkpoint B:** integrated first-import/replacement/cancel/rotation/recovery behavior and test isolation reviewed; regressions/lint/APK inspection pass.
6. **P6 — Adversarial and process-kill evidence:** extend the safety tests required from P1–P3 to real adapters/providers, two-phase externally terminated instrumentation, and retained-installation checks. Include record-adapter postcondition failures, stale A/B picker results, late descriptors, cancel-versus-commit, delayed UI notifications and interrupted reset authorization. Do not defer basic safety tests until this phase or treat construction of a second store object as proof of process death.
7. **P7 — S25 acceptance and evidence:** owner-approved real picker/Cancel/rotation/leaving/locking/restart, plus a copied fixture strictly larger than 2,147,483,648 bytes. **Checkpoint C:** every spec success criterion has evidence; blockers stay open, and model import alone may be closed only after owner review.

Publication, controller and UI state changes are sequential because they share one store contract. After that contract is stable, independent pure test fixtures and verification-document scaffolding can be prepared in parallel. No parallel writer/agent may mutate the native stage or owner's model store during verification.

## Model-import verification and data safety

Use current JUnit 4/AndroidX test dependencies only. Pure tests use deterministic stream/clock/space/file-operation seams; Android instrumentation validates checked platform rename/sync/unlink/no-follow operations, offset-aware provider streams, no-backup paths and actual process boundaries. Test-only providers, injected faults and orchestration classes live in test sources, not production components or APK entry points. Internal dependency-injection interfaces are not exported test commands.

Automated mutation/fault tests use a separate test-owned root **and selection metadata**, for example a validated generated child of `noBackupFilesDir/model-import-tests/`, never the user's `models/`. Shared view binding can be exercised with an isolated controller without giving the production Activity a test-path override. Real production-picker/UI acceptance is manual with owner consent to the tested selection/replacements; destructive reset cases stay isolated unless separately authorized.

For actual target-process termination, a host orchestrator should run a targeted setup instrumentation case, wait for an exactly matched run-id/checkpoint from the expected test process, stop the approved target package, and run a separate verification case against that same isolated store. A run id correlates evidence; it is not an authentication/security boundary. Accept only a strictly validated generated test-store id; never take an arbitrary root path or permit the production `models/` root. Bind the run to verified target/test APK hashes and isolate/store all checkpoint evidence without URIs or device serials.

The injected file-operation checkpoint exists only in androidTest code. A forced-stop phase is an intentional interrupted run, not a passed instrumentation test or proof of every low-memory-kill/picker-restoration behavior. Verify the expected checkpoint, target-process disappearance, successful nonzero post-restart assertions and independent cleanup. Include test-provider processes/pipes in bounded teardown; never use broad process-kill patterns or leave a hung provider alive for the next case. Never uninstall, downgrade, clear data or fill the owner's disk as a shortcut; verify retained target/test packages before and after. This device work requires separate owner approval.

Planned checkpoints include candidate creation, mid-copy, source/destination close, final-name promotion, selection update before/after commit, retired-file deletion, reset authorization, reset deletion and empty-state publication. Include a provider that ignores cancellation: UI stays live and no second worker starts; all test-owned hung processes/resources must be terminated by the approved test controller, with failure recorded if cleanup cannot be proved.

### Spec-to-verification matrix

| Spec success criteria | Earliest work package | Required evidence before closure |
|---|---|---|
| 1 — launcher/picker/offline | P4 | Real S25 launcher and local SAF selection; ignored external extras; no storage/network permissions; harmless picker cancellation/unavailability. |
| 2, 6 — completed copy and replacement | P1/P2 | Exact test-byte identity, first/replace/persist paths, old selection retained until confirmed commit; test-owned source removal/revocation after import; real private-path resolution. |
| 3 — bounded progress and >2 GiB | P2/P3 | Short/non-seekable streams, endian headers, unknown/unusable/positive-mismatched sizes, arithmetic overflow and bounded allocation/notification assertions; S25 copied length strictly >2,147,483,648 bytes with heap evidence. |
| 4 — recreation and one owner | P3/P4 | Rotation during reservation, before/after first-launch claim, with picker open and during copy; multiple Activities; A/B/stale/cancelled result ordering and request-code reuse rejection; process restart versus same-process retention; lock/leave/return on S25. |
| 5 — failures/cancellation/space | P2/P3 | All metadata/open/read/write/flush/sync/close failures; failed space measurement, reserve boundary and mid-copy exhaustion; early/late resource acquisition, uncooperative provider and atomic cancel/commit race. |
| 7 — recovery/cleanup/reset | P1/P5/P6 | Old/new/empty publication outcomes, actual target-process termination at every listed boundary, corrupt metadata/paths/lengths, cleanup debt, confirmed/declined/interrupted reset and fresh-confirmation recovery. |
| 8 — privacy/packaging | P4/P5 | No-backup payload **and all records**, raw-URI/exception sanitization, test-only component/class exclusion, unchanged native bytes/attribution; untested backup/transfer transports explicitly unverified. |
| 9 — regressions/device evidence | P1–P7 | Nonzero passing host/JVM/isolated-instrumentation discovery, lint, both APKs and audit; final clean-output rebuild from reviewed source; mandatory S25/large-file checks passed or module remains blocked. |

Boundary tests start with the work package that introduces the invariant, then run against real Android adapters in P6. A fake 'normal return' must not satisfy a record-adapter commit assertion. Declined reset, cancelled/stale picker results and failed imports must not mutate the active selection; compare old payload bytes as well as metadata, and keep secondary cleanup errors from masking the primary cause. Large fixtures are generated/streamed into test-owned storage or supplied with owner consent, never committed or embedded as multi-GB APK assets. Actual disk-full/transport testing is separate and is not implied by injected space/backup-location checks.

Future verification commands from the repository root, after existing JDK/SDK/native-stage setup:

```bash
python scripts/import_llama_release.py --verify-only
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
adb devices -l

# Only after separate owner/device approval and selecting the intended S25.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Proposed test-only suite name; must exist and pass the isolation review first.
adb shell am instrument -w -r \
  -e class io.github.greprohan.llamadroid.modelimport.ModelImportIsolatedTestSuite \
  io.github.greprohan.llamadroid.test/androidx.test.runner.AndroidJUnitRunner
# Manual real-UI step only with consent for the production selection being tested.
adb shell am start -n io.github.greprohan.llamadroid/.MainActivity
```

The later task list must specify the isolated suite/case roster, host crash-orchestrator CLI, checkpoint names, bounded waits/teardown and pass/fail protocol before those tests are implemented/run. The ordinary isolated suite includes only bounded, self-contained expected-pass cases; it excludes real-owner UI mutation and setup/pause/crash/non-cooperative-hang cases that require external control. Those cases run only as explicitly filtered setup/verification phases under the host orchestrator. Compile/package the suite before invoking its proposed name; missing/zero-discovered/skipped cases cannot pass. Use explicit target selection privately when more than one device is present; do not persist serials in committed evidence. No `connectedDebugAndroidTest`, automatic uninstall or unfiltered device suite on a data-bearing install. Run existing native smoke cases separately under their own non-mutating, reviewed scope. The adb example above documents the filter; actual task execution uses the bounded host-wrapper modes/deadlines/teardown defined in the approved task list, not a standalone unbounded instrumentation command.

Inspect production/test manifests and APK class inventories separately: only the production launcher is newly exported; no app network/storage permissions, test provider/fault controls, model fixtures or weights leak into production. Retain native payload hashes/attribution and existing regression coverage. Record real nonzero test discovery, warnings versus failures, APK identities, tested provider/size and precise blocked checks in `docs/verification/model-import.md`; source provenance/hash can be external test evidence without production hashing UI.

## Model-import risks and pending decisions

| Risk | Mitigation / acceptance boundary |
|---|---|
| Provider IPC or close cannot be cancelled promptly | Bounded independent abort helpers; honest pending state; no new worker; test orchestrator owns teardown. No cancellation deadline claim. |
| Record helper corrupts old state or conceals an outcome | Do not use legacy AtomicFile; checked exclusive-temp/sync/close/rename protocol, full authoritative read-back and descriptor/sidecar postconditions; injected post-rename failures and real process-boundary tests. |
| Stale picker result replays after later requests, process death or reset | Persist the one authoritative outstanding token before launch; clear only a matching result before source open; bind platform request codes correctly; never restore a token from Activity state alone. |
| Source size is stale or unknown | Approved strict positive-size mismatch failure; unknown-size progress/reserve enforcement; no body-integrity claim. |
| Cleanup/reset interrupted or cannot remove bytes | Visible debt, persistent reset authorization, confirmed empty state before new import; never delete unowned entries. |
| Test tooling removes data or fault tests touch live selection | Isolated store/metadata, retained direct installs, explicit owner consent for real UI mutation; no uninstall/clear-data. |
| Large fixture/device unavailable | Required physical >2 GiB and S25 checks stay blocked; small/fake tests cannot close the module. |

Approved plan decisions: platform-only controller/adapters, offset-aware provider-stream semantics and representation limits, checked same-directory record replacement (rather than AtomicFile), bounded record/outstanding-request/epoch design, concrete resource bounds, atomic cancel/commit handoff, reset protocol and staged verification order. The separately approved task list records class/task splitting and the crash-script interface. Device availability/storage and provider-specific behavior remain verification requirements, not assumed facts. Implementation must use a reviewed clean source/worktree and preserve the current unrelated `.gitignore`/`app/.cxx/` work; this plan does not authorize overwriting it.

### Platform-contract review basis

- [Android 8 AtomicFile source](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/util/AtomicFile.java) and [Android 15 AtomicFile source](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/util/AtomicFile.java): legacy failed-backup-rename continuation and logged rather than thrown failures explain why this plan uses a checked adapter instead.
- [ContentResolver descriptor contracts](https://developer.android.com/reference/android/content/ContentResolver) and [Android 15 resolver source](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/content/ContentResolver.java): read-only resolver opening may invoke provider representation selection; `openFileDescriptor` delegates through asset opening rather than guaranteeing a conversion bypass. Use the asset stream to respect document offsets/lengths.
- [Android Os contract](https://developer.android.com/reference/android/system/Os): checked rename/fsync/lstat APIs predate minSdk 26. These contracts are design input, not Android/device test evidence.

**Plan gate: passed.** The owner approved this plan and subsequently approved the separately reviewed detailed task list. Neither approval changes the approved spec policies or establishes execution evidence. The [task gate](todo.md#model-import--approval-and-task-gate) records later authorization and results; new device batches still require separate consent.

---

# Implementation Plan: Native Runtime

Module: `native-runtime`  
Spec: [SPEC-native-runtime.md](../SPEC-native-runtime.md), approved by the user.  
Plan status: **Approved baseline; original implementation hold is historical.** Implementation files now exist, with deviations requiring review. This document preserves the approved design, not current execution evidence or approval of those deviations.

Task target: [tasks/todo.md](todo.md), the current status and ordered-blocker tracker. The user subsequently selected isolated worktree option A for blocker 1: minimal Activity, direct-process launcher and JVM tests. That scoped implementation is verified on `fix/native-runtime`. The user then selected option A for blocker 2: retain the fourteen byte-preserved payloads and repair staging. T4 now passes 50 host tests plus genuine-archive verification. The user then authorized blocker 3 packaging: 12 Gradle/APK checks, clean native build/lint, hash/attribution and SDK manifest inspection pass; installed extraction/linking is now separately verified under T9. The user then authorized blocker 4: a standalone ZIP/payload auditor now passes 44 adversarial tests and the real APK; SDK inspection is separate and passed against the same hash. Full host discovery is 94 passing tests. User then approved Checkpoint B/blocker 5: the test-only bounded harness passes 24 shared JVM cases (43 JVM total), both APKs compile, and a clean detached checkout at `955bfe3` reproduced download/staging/build/audit with 12 packaging checks. T8 host evidence is complete. The later physical S25 run passed 25/25 ART/native cases and verified installed extraction, but Gradle removed both packages afterward. The owner then confirmed no prior data and approved fresh installation. Direct instrumentation passed 25/25, real exit 0 in 102 ms, retaining both audited APKs with matching before/after hashes/UIDs/paths. T1–T9 are verified; the owner subsequently selected option A after evidence commit `ca55ef5`, approving Checkpoint C and closing native-runtime only. The module is complete; the owner separately requested merging into `main` (merge `c8ea610`). Downstream specification/implementation still requires separate approval.

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
# Historical Gradle connected run passed but uninstalled both packages afterward.
# Only after owner approval, prefer direct instrumentation without that cleanup:
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r io.github.greprohan.llamadroid.test/androidx.test.runner.AndroidJUnitRunner
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

The original implementation hold was later superseded for the user's chosen blocker-1 scope (isolated worktree option A). That host boundary is implemented/tested. The user subsequently retained the approved fourteen-file format and authorized blocker 2 staging repairs, now verified with 50 host tests and the genuine archive. The user also authorized blocker 3's fail-closed Gradle packaging, now host-verified with native bytes/attribution/SDK manifest inspection and 12 integration checks. T5's installed-extraction requirement is now separately verified under T9. The user also authorized blocker 4's standalone auditor, now verified against 44 adversarial fixtures and the real APK (94 host tests total); SDK inspection remains separate. User subsequently approved Checkpoint B and blocker 5, now host-verified from clean committed source at `955bfe3` with 94 Python/43 JVM/12 packaging checks and both APKs compiled/audited as applicable. Shared test-only sources avoid shipping probes or duplicating JVM/ART cases. The physical S25 later passed 25/25 ART/native tests, including normal pinned version exit. Unexpected Gradle cleanup then removed both packages. The owner confirmed no prior data and approved fresh installation; direct instrumentation now also passed 25/25 and retained both audited packages with identical before/after hashes/UIDs/paths. T1–T9 are verified; the owner subsequently selected option A after evidence commit `ca55ef5`, approving Checkpoint C and closing native-runtime only. That closure did not authorize merging or downstream work. The owner then separately requested merging into `main` (merge `c8ea610`); each later module retains its separate specification/plan/task gates. No uninstall/downgrade/data-clear without separate consent.
