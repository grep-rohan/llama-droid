# Spec: Model Import

Module id: `model-import`

Status: **Owner-approved requirements.** The [technical plan](tasks/plan.md) and [detailed task list](tasks/todo.md#model-import-approved-task-list) were separately approved. Implementation and host verification through MI-19 and bounded device verification in MI-20 are complete; MI-21–MI-23 and module closure remain open. See the [current task gate](tasks/todo.md#model-import--approval-and-task-gate) and [verification evidence](docs/verification/model-import.md). New device batches require separate consent.

Parent: [Approved capability map](CAPABILITY-MAP.md)

Downstream consumer: `server-lifecycle`.

## Objective

Let the owner select a single-file GGUF using Android's system document picker, copy it safely into private app storage, and retain one active model across app restarts. Show progress, support cancellation, and handle insufficient storage without losing the previous model or leaving abandoned multi-GB copies indefinitely.

Everyday use must require neither ADB nor broad storage permissions. The first physical-device acceptance target is the owner's Samsung Galaxy S25; this is not a general phone-compatibility claim.

Success means a complete, basically validated private copy is selected and recoverable after restart. It does **not** mean llama.cpp can load it, that it fits RAM, or that inference works.

## Confirmed Scope

- One active model, one import at a time; no model library.
- Use the Storage Access Framework (SAF), not a filesystem-path chooser.
- Copy source bytes into private storage; never move, modify, or delete the source.
- Keep the previous model and saved selection until the replacement succeeds.
- Show progress and a Cancel action, including during a replacement.
- Handle low storage before and during copying, including unknown source sizes.
- Preserve the running operation and displayed progress through Activity recreation/rotation.
- No background-copy guarantee. Leaving the app or locking the phone may interrupt copying; retry starts from the beginning.
- Clean up partial files on ordinary failure/cancellation, and recover abandoned files on next launch after process death.
- Check basic GGUF validity, without claiming complete structural integrity or runtime compatibility.
- Show source display name and actual copied size; generate internal filenames independently.
- Defer checksum computation/presentation for model identity. A separately recorded trusted hash remains useful for later reproducibility evidence.

## Planning-time Evidence and Approved Policies

At specification time, `MainActivity.kt` displayed a placeholder Views layout, there was no `ModelStore.kt` or model-import flow, and the manifest's Activity was non-exported and had no launcher intent filter. A minimal user-launchable entry point is therefore part of this module; no external import intent endpoint is required.

The existing native-runtime specification records completed native launch work. This module must preserve that boundary and does not change native artifacts, toolchain, or launch behavior. Existing native evidence is not evidence of model import.

The owner approved the following policies as part of this specification:

1. Input is a single-file GGUF. No split/sharded-model assembly is provided. Basic header checks cannot reliably identify every unsupported multi-file model.
2. The baseline header check accepts little-endian GGUF v2 and little- or big-endian v3. Detect byte order from the version bytes, not the phone's native endianness. Other versions produce an unsupported-version error, not a runtime-compatibility claim.
3. Keep **64 MiB (67,108,864 bytes)** of available space as the safety reserve on the destination filesystem, including headroom for transaction metadata. This is an import policy, not a guarantee against concurrent storage consumption or a RAM-fit estimate.
4. Model payloads and all selection/transaction metadata are excluded from Android-managed backup/device-transfer restoration. Use app-private non-backed-up storage, or an equivalently verified exclusion policy; app updates preserve them, uninstall/clear-data removes them. Merely setting `allowBackup=false` is not sufficient evidence of transfer exclusion on every supported Android version/OEM.
5. A positive provider-reported size is treated conservatively: a mismatch at EOF rejects the import even if its header passes. Providers with stale positive sizes may therefore require a different source provider or local copy. This is an approved safety/compatibility trade-off, not proof that a matching size establishes integrity.

These policies are approved requirements, including the validation/size trade-offs and recovery reset described below. Exact classes, threading/retention mechanisms, persistence format, and transaction implementation belong in the separately reviewed technical plan. The source observations above describe specification time, not implementation evidence.

## User Experience

### Idle and picker

- Show either `No model imported` or the active model's display name and actual size.
- Provide Import/Replace. Launch `ACTION_OPEN_DOCUMENT` with `CATEGORY_OPENABLE`, a generic `*/*` route, and multiple selection disabled. No custom file browser is required.
- The launcher entry point needs `MAIN`/`LAUNCHER` and the required explicit exported setting. Being user-launchable must not create an external import API: ignore model URIs/paths and import instructions in incoming launch intents.
- Do not rely on extension or a GGUF-specific MIME type: providers may label GGUF as generic binary or supply no useful type. Validate the bytes after selection.
- Accept a single readable `content://` result only for the outstanding picker request. Handle cancellation, missing URI, unexpected/multiple results, absent picker handler and denied/revoked access without crashing or changing the selection. A malformed result cannot become a filesystem path or network URL.
- Picker cancellation creates no candidate copy. Duplicate results must not start duplicate imports.
- Query only needed metadata off the main thread; close cursors and descriptors on every path. Missing columns, empty/null cursors and optional metadata-query failures fall back to an unknown size/name if the content can still be opened.
- Sanitize and bound both supplied display names and fallback names before persistence/UI/saved-state use; neutralize control characters. The plan must specify the numeric name limit, copy-buffer bound, maximum progress-update interval while I/O advances, and maximum queued-update count so they are testable. Never log raw provider names/URIs through exception messages. Display metadata is not a path or proof of content.
- Treat missing, negative, zero, overflowing or otherwise unusable reported sizes as unknown until the stream establishes the actual size. Actual empty content is rejected by validation.
- Local-document import must work offline with no app network permission or integrated downloader. SAF may also expose cloud providers, whose own availability/network behavior is outside this module; use a local provider for offline acceptance.

### Copying and validation

- Show preparation, copying, and finishing distinctly, with the selected display name, copied bytes and Cancel where cancellation can still take effect. Keep the existing active model visibly distinct from the pending replacement. Explain that background completion is not guaranteed.
- Show percentage only when a credible positive total is available. If bytes exceed the reported total, switch to indeterminate progress; never show greater than 100%.
- For unknown size, show indeterminate progress plus copied bytes, not a fabricated percentage or ETA. Reaching 100% copied must not hide validation/publication still in progress.
- Metadata queries, opening/reading the source, destination I/O, validation, persistence and recovery all run off the UI thread with bounded memory. Bound/coalesce progress notifications so a fast stream cannot flood the UI queue.
- Support sequential, non-seekable streams and short reads; do not require reopening the source for validation. Retain/copy the validated prefix exactly once so bytes are neither lost nor duplicated.
- Use 64-bit sizes/counters and overflow-safe progress arithmetic so files larger than 2 GiB cannot overflow progress or storage calculations.
- Do not report successful import merely because copying reached the provider's advertised length. Success requires actual EOF, the specified header/size checks, successful source close and destination completion/close, and confirmed publication of the saved selection. Source/destination close failures before commit are import failures, not success with an unexamined exception.
- Under the approved conservative size policy, a positive reported size differing from the final byte count is an inconsistent-source error. Bytes above an underestimated size may be observed for progress purposes but cannot result in successful publication; reading may stop once the mismatch is established.
- On success, show the active name and actual copied size. Wording must say imported/saved, not loaded/compatible/ready to chat.

### Exact validation boundary

- Read and check the fixed header early, before consuming/copying the whole document. Reject actual EOF before 24 bytes, wrong byte-level `GGUF` magic, and unsupported version/byte-order combinations. Header reads must tolerate short reads.
- The fixed header comprises 4 magic bytes, a 32-bit version, and two unsigned 64-bit counts in the detected byte order. Do not allocate or traverse metadata/tensors using those untrusted counts.
- Full metadata/tensor parsing, tensor-count plausibility, split-model detection, checksums and immutable source-snapshot guarantees are not provided. A header-passing file with a truncated/corrupt body may pass these checks, especially when no trusted total is available. Test this limitation explicitly; do not claim that all malformed GGUFs are rejected.
- Here, a complete copy means the bytes delivered by the source through observed EOF, subject to the approved reported-size check. It does not mean the original model's logical completeness has been established.

### Cancellation and failures

- Cancel requests cancellation without blocking the UI and cancels/closes provider queries, opens and source I/O where the APIs/provider permit. Check cancellation between operations and before publication; normal close/error cleanup must not run on the UI thread either.
- Serialize imports and recovery store-wide, not per Activity/button. A second Activity/task or duplicate callback must attach to the same operation or be rejected. Late callbacks cannot change a newer selection.
- Before the publication commit point, accepted cancellation stops the worker, removes its candidate where possible, and preserves the old selection. Once publication begins its non-cancellable commit section, show finishing rather than promise rollback; resolve any cancellation/commit race against the confirmed result.
- If commit outcome is uncertain, keep the relevant payloads, show recovery pending, and resolve the transaction before reporting success/cancellation/failure or accepting another import. An exception alone does not prove that metadata was not committed.
- Do not promise a fixed cancellation deadline for a hung third-party provider. Show a cancelling state while termination is pending; do not start a second worker or delete a file still being written.
- Distinguish unreadable/revoked source, invalid/truncated header, unsupported GGUF version/byte order, inconsistent source size, storage-check failure, insufficient storage, generic read/write failure, save-selection failure, and cleanup failure. Classify disk-full only when supported by the error/space evidence; not every I/O failure means low storage.
- Give an actionable recovery message, such as choosing a readable file, freeing space, or retrying. Preserve the previous model on every confirmed pre-commit error. A post-commit cleanup warning is separate from successful import, not a reason to claim the old model is still active.

## Storage and Safety Contract

### Private destination

- Store generated candidates and completed payloads in a dedicated app-private, non-cache, non-backed-up model area. Keep all selection/transaction metadata, including temporary/backup records used for recovery, under the same backup policy.
- Generate internal names; never join a picker display name, document identifier, or URI path into a destination path. Duplicate names, separators, traversal strings, and control characters must not change where bytes are written.
- Treat persisted selection/transaction metadata as untrusted too: validate its schema, generated-name format, size and path containment before resolving or deleting anything. Cleanup must not follow links or unexpected subdirectories outside its owned file set, including elsewhere in private app storage.
- Keep user-selected URI access read-only and scoped to the import. No ongoing URI permission is needed for the completed private copy; release only long-lived grants acquired and owned by this importer when no longer needed, if any are used.
- After success, the app must still resolve the private model if the original is renamed, deleted, or its grant revoked.
- Never execute imported bytes, package weights into the APK, expose private model files externally, or log source URIs/model contents.

### Low storage

- Before copying, inspect available bytes on the destination filesystem. For a positive known size, require room for the entire new copy plus the approved reserve; the old model and any cleanup debt remain allocated and already reduce the reported available space.
- If the size is unknown, explain that total space cannot be predicted and enforce the reserve incrementally rather than rejecting all unknown-size providers. If available-space measurement itself fails, report a storage-check error instead of assuming unlimited room.
- Check space during copying for known and unknown sizes. Handle real create/write/flush/sync/close and metadata-publication failures even if every earlier space check passed; preflight is advisory, not a reservation.
- Stop before an intentional payload write would breach the reserve when detectable. Use overflow-safe arithmetic, bounded buffers and space checks that account for buffered pending writes; leave headroom for cancellation/recovery metadata rather than treating it as payload capacity.
- On failure, remove only module-owned uncommitted files after the writer stops. Never delete the previous model automatically to make space, and never touch the source.

### Publication, persistence, and recovery

- A candidate is never returned to downstream consumers as active. The saved selection references only a completed private payload, with sanitized display name and actual byte count.
- Complete and close the candidate before publication. Replace the selection metadata atomically with a verified, recoverable old-or-new outcome, under the store's single-owner serialization. An atomic-file helper alone does not serialize concurrent readers/writers or make a multi-file transaction atomic.
- Confirm the new selection before reclaiming the previous payload; do not overwrite the old payload in place. A confirmed pre-commit metadata failure preserves the old selection. After an ambiguous commit error, retain both payloads and reconcile the authoritative metadata before deleting either.
- After successful commit, an old-file cleanup failure is a visible warning/deferred cleanup debt. It must not change the active selection or falsely report the import as rolled back.
- On store initialization after process death, reconcile transactions before exposing a saved model or accepting imports. With intact, readable storage, process death alone at any publication stage resolves to a complete old or complete new model (or no model for an uncommitted first import), never a partial candidate or an importer-caused dangling selection. Independent corruption/storage failure instead follows the unavailable/reset path below.
- Check recovered metadata/path validity, file readability and actual length against the committed byte count. Missing/unreadable/size-mismatched files or corrupt metadata produce an explicit unavailable/recovery error, not a usable model result. This check does not add full-body integrity validation or hashing.
- Remove abandoned candidates and unreferenced module-owned payloads only after establishing that no active writer, committed selection or unresolved transaction needs them. Retry failed cleanup on later initialization/import attempts and report bytes still allocated; do not claim reclaimed space without deletion.
- Cleanup debt for files proven unrelated to the active selection does not block using that selection or retrying import if space permits. An unresolved writer/transaction or inability to establish file ownership blocks ordinary import/automatic cleanup; fail closed rather than guessing what may be deleted. The separately confirmed reset below may discard unresolved selection/transaction state, but may not bypass live-writer exclusion or file-ownership checks.
- For corrupt/missing metadata, do not silently select an arbitrary orphan or automatically delete payloads whose ownership/reference status is unresolved. Surface recovery failure and preserve them pending safe reconciliation or an explicitly confirmed reset. An empty new store with no payloads/transaction is ordinary `No model imported`, not corruption.
- If automatic reconciliation cannot establish a valid state, offer an in-app `Reset import state` recovery action; do not require ADB, reinstall or clearing all app data. Confirmation must explain that imported model copies/selection will be removed, but source documents will not. Reset is an explicitly authorized recovery operation, not ordinary replacement or automatic space reclamation. Run it only after all live writers/recovery workers have stopped; an unresolved on-disk transaction is not a live writer. Persist reset authorization before its first destructive step so interrupted reset can finish on recovery without being mistaken for an ordinary import failure. Delete only positively identified module-owned files without following links, report any cleanup failure, and allow a fresh import only after a safe empty state is confirmed. Never delete unknown/unowned files to force recovery.
- Recovery guarantees cover tested cancellation/process-death boundaries. Do not claim physical-power-loss durability without separate evidence.

## Activity and Process Lifecycle

- Retain one store-wide operation owner across same-process rotation/Activity recreation; reconnect the Views UI without restarting the stream, opening another picker, or retaining a destroyed Activity. Multiple Activity instances must not create independent import owners.
- Track an outstanding picker request separately from a running copy. Recreation while the picker is open must not relaunch it or consume its result twice. Returning from the picker is normal flow, not user-abandonment cancellation merely because the Activity paused.
- After process death with a picker outstanding, a restored result may start a fresh import only after store recovery and only if it belongs to that pending request. A previously started dead copy is never silently resumed or automatically restarted from a persisted URI.
- Background execution is neither required nor promised. No import foreground service, persistent background worker (such as WorkManager), or wake lock is added. An in-process I/O worker is required and may remain alive briefly off-screen. If still alive on return, reconnect to its real state; if interrupted, clean up and require a fresh retry.
- A destroyed process cannot run cleanup immediately. On next launch, reconcile its transaction and attempt candidate cleanup before a new import, using the cleanup-debt/blocking rules above. Never restore progress as though a dead worker were still running.
- Saved selection survives app process restart and ordinary app update. Uninstall/clear-data removes the private copy; this is not a portable model backup feature.

## Module Boundary / Consumer Contract

The later `server-lifecycle` module must be able to:

- Resolve either no active model or a readable completed private model, with a safe private location, display name, and actual byte count.
- Observe explicit states for idle/preparing, copying, validating/finishing, cancelling, recovery pending/unavailable, and succeeded/cancelled/failed outcomes. Cleanup warnings/debt are separate from the committed-selection result. Concrete API names may differ in the approved plan.
- Distinguish the saved active model from a pending replacement. A replacement in progress must not invalidate the currently committed selection.
- Receive actionable import/persistence errors rather than a successful result containing a missing or partial path.

This module does not start a server or coordinate replacement with a running inference process. Before downstream implementation permits import during inference, that module must explicitly define exclusion or file-lifetime ownership; a returned path is not a permanent lease on an old payload.

## Tech Stack and Project Structure

Keep the existing Kotlin 2.0.21, Android Views, JDK 17, AGP 8.8.2, Gradle 8.10.2, minSdk 26 and compile/targetSdk 35 baseline. Use platform SAF and file APIs with existing test dependencies: JUnit 4.13.2, AndroidX test ext 1.2.1 and runner 1.6.2. No new production dependency is assumed.

Relevant existing files and proposed locations:

```text
app/src/main/AndroidManifest.xml                    Minimal launcher and backup policy
app/src/main/java/io/github/greprohan/llamadroid/
    MainActivity.kt                                 Views/picker/progress integration
    [import controller/store/state types]            Copy, cancellation, transaction and recovery
app/src/main/res/layout/main_activity.xml            Import and saved-selection UI
app/src/main/res/values/                             User-facing strings
app/src/test/                                       Pure copy/state/storage/recovery tests
app/src/androidTest/                                SAF, private storage, rotation and device tests
app/src/sharedTest/                                 Shared tests where appropriate
SPEC-model-import.md                               This requirements/approval document
docs/verification/model-import.md                   Proposed results and physical-device evidence
```

Paths in brackets are responsibilities, not mandated class names. Do not repurpose native artifact staging code into the app's model store.

## Commands

Run from the repository root after the existing native-stage prerequisites in [docs/BUILDING.md](docs/BUILDING.md). These are future verification interfaces, not results achieved by this specification.

```bash
python scripts/import_llama_release.py --verify-only
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
adb devices -l

# Only with owner approval and the intended S25 selected.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r io.github.greprohan.llamadroid.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start -n io.github.greprohan.llamadroid/.MainActivity
```

Windows may use `gradlew.bat`. The Activity command is expected to work only after the reviewed launcher change. Prefer ordinary launcher use for manual acceptance. Do not use `connectedDebugAndroidTest` without reviewing its installation/cleanup behavior: prior native testing recorded package removal. Never uninstall, clear data, or fill the owner's phone to provoke low storage without explicit permission.

## Code Style

Use four-space indentation, idiomatic Kotlin, `PascalCase` types and `lowerCamelCase` members. Keep I/O, cancellation, storage calculation and recovery logic separate from Activity rendering. Bound memory and diagnostics, close streams on all paths, and preserve useful error causes without exposing source URIs or private contents.

Existing style example from `MainActivity.kt`:

```kotlin
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
    }
}
```

Extend that Views entry point rather than migrating to Compose. Prefer messages such as `Not enough space; previous model kept` to generic `Import failed` or misleading `Model ready`.

## Testing Strategy

### Host/JVM tests

Use deterministic fake streams, storage providers and publication failures. Cover:

- Exact byte preservation with short reads and non-seekable streams; bounded buffers and notification queues; unknown/unusable sizes; positive-size mismatches under the approved policy; counters/storage/progress arithmetic beyond 2 GiB and near signed-64-bit limits without giant fixtures.
- Empty input, truncated fixed header, wrong byte-level `GGUF` magic, supported little-/big-endian headers and unsupported versions/byte orders. Arbitrary unsigned header counts must not drive allocations. Include a header-passing, body-corrupt fixture that documents the limits rather than asserting full validation.
- Null/empty/missing-column metadata; oversized/control-character display names; metadata/open/read/source-close failures; null/unexpected/duplicate picker results; destination create/write/flush/sync/close failures; failed space measurement, low-space preflight and mid-copy exhaustion.
- Cancellation during metadata/open/read and at publication boundaries; simultaneous import/recovery from multiple owners; stale callbacks; a hung provider that cannot be cancelled promptly.
- Initial import and replacement; identical/untrusted/missing names; source preservation and independence; strict path/schema validation for persisted metadata, link/unexpected-directory rejection, and confinement of cleanup to owned files.
- Restart at every candidate/final-payload/metadata/old-file-cleanup boundary, including an exception after metadata actually committed. Test no-model recovery during a first import, old-or-new recovery during replacement, blocked ambiguous recovery, missing/size-mismatched payloads, corrupt metadata, and nonblocking cleanup debt followed by retry.
- Reset-confirmation cancellation leaves all files/selection untouched. Confirmed reset removes only identifiable owned import state, preserves sources/unrelated app files, excludes live writers, survives process interruption, and permits a new import only after recovery establishes a safe empty state.

No numeric coverage quota is imposed. Each safety invariant needs a meaningful test; zero discovered tests is not success.

### Instrumentation and physical S25 acceptance

- Exercise the actual SAF result/ContentResolver boundary, private files and saved-selection persistence. A controlled test DocumentProvider may supply unknown/misreported size, short reads, non-seekable pipes, slow/hung operations, revocation and failures; it is test-only and absent from the production APK.
- Verify launcher access, ignored external import extras, picker cancellation/unavailable-picker handling, real local-document import offline, progress, Cancel, rotation during picker and copying, multiple Activity instances, leaving/returning, lock/unlock and interruption/relaunch on the S25. Exercise reset confirmation/cancellation and its safe recovery in an isolated test-owned store.
- Demonstrate recovery after actual target-app process termination at controlled publication boundaries, not just constructing a fresh store object. Keep any fault-injection controls out of the production APK and use a test-owned store. Inspect packaged backup policy/storage locations and diagnostics for URI leakage; explicitly mark real backup/transfer transport behavior unverified unless exercised.
- Verify source and previous payload hashes externally in tests/evidence where useful; deferring production checksum UI does not prohibit test assertions.
- Exercise low-storage logic through deterministic injection; record that this does not prove actual disk-full behavior. Real filesystem-exhaustion checks require an owner-approved isolated environment, not filling the owner's device by default.
- Import a document **larger than 2 GiB (2,147,483,648 bytes)**, subject to available space/owner approval. Record its actual copied length and bounded-memory evidence; a 2 GB decimal file does not cross this boundary. A synthetic GGUF-header fixture proves importer behavior only, not a usable inference model.
- Record the tested provider, file size, commands, app/Android build and results without publishing source URIs or unique device identifiers. If using the selected LFM3 baseline, record its actual source revision, size and SHA-256 separately; mutable `main` is not reproducible provenance.
- Automated mutation/failure tests use an isolated test-owned store and selection metadata, not just different filenames in the owner's active store. Keep test-path overrides/fault controls out of production entry points. Manual real-UI replacement needs explicit consent if a model is already selected. Use retained installation/direct instrumentation practices from native-runtime verification; uninstall/clear-data is not a test-cleanup shortcut.

Compilation and fake-provider tests do not replace the real picker/physical-device checks. If device access or an approved large fixture is unavailable, label the relevant acceptance item **blocked**, not passed.

## Boundaries

- **Always:** Keep the previous selection until commit; stream off the main thread; use private generated paths and 64-bit counters; clean up safely; preserve source bytes; distinguish imported from compatible; run applicable tests before commits; report implemented, compiled and device-tested separately.
- **Ask first:** Add dependencies; change SDK/toolchain/native artifacts or CI; introduce background execution/resume; change the agreed one-model policy; weaken backup/privacy policy; perform destructive test/maintenance operations outside a user-requested successful replacement or explicitly confirmed in-app reset; fill device storage; change this specification's validation/reserve policy after approval.
- **Never:** Request broad storage permissions; trust a display name as a path; load multi-GB files into memory; delete a source or old active model to make room; publish partial files; add downloads/catalog/chat/inference/GPU/NPU/tools/shell/MCP/LAN features; commit weights, personal URIs or device identifiers; remove failing tests to pass.

## Success Criteria

1. The installed app is user-launchable; its system document picker selects a generic-binary GGUF without broad storage permissions. Picker cancellation is harmless.
2. A successful first import produces a byte-identical completed private copy and a persisted active selection showing display name and actual size. Revoking/deleting the original does not invalidate that copy.
3. Known-size progress and unknown-size byte feedback remain responsive within the plan's explicit resource/update bounds. Copying is bounded-memory and counters work beyond 2 GiB; the approved physical fixture exceeds 2,147,483,648 bytes and its result is recorded separately from synthetic JVM tests.
4. Rotation reconnects to one live operation without duplicate copying. Leaving/locking and process interruption follow the documented no-background-guarantee policy, with honest state and safe retry.
5. Accepted pre-commit Cancel, header-invalid/unsupported input, inconsistent reported sizes, read/write failures and low storage preserve the previous selection/source. Partial files are removed, or cleanup failure is reported and safely retried. Header-passing corrupt bodies are an explicit validation limitation, not a claim of rejection.
6. Replacement requires temporary space for both copies, never overwrites the old payload, and becomes active only after complete copy/validation and saved-selection publication.
7. Fault-injection and actual process-termination tests establish each publication/recovery boundary, including ambiguous commit errors and no-model first-import recovery. No partial/dangling selection is exposed; automatic cleanup does not delete a committed payload, live candidate or unresolved transaction's files. Safe cleanup debt remains visible/retryable. Unrecoverable state has an explicitly confirmed, safely bounded in-app reset path; reset cancellation is harmless.
8. Payload/metadata storage and packaged policy exclude them from Android-managed backup/transfer; any untested transport behavior is labelled unverified. No imported content, source URI, test control/provider, new background service, app network permission or unnecessary storage permission appears in the production APK/diagnostics.
9. JVM tests, applicable regression tests, lint and APK compilation pass with recorded results; pre-existing warnings are distinguished from regressions. Real SAF/offline/rotation/cancellation/process-recovery and approved large-file checks pass on the S25. Unavailable mandatory checks remain blocked, so this module cannot be declared complete. No inference or full GGUF-integrity claim is made.

## Approval Decisions and Remaining Verification

- **Approved:** 64 MiB reserve; GGUF v2/v3 fixed-header/byte-order policy; strict positive-reported-size mismatch rejection; single-file-only scope; non-backed-up payload/transaction/selection metadata; explicitly confirmed in-app reset for otherwise unrecoverable import state.
- Physical test scheduling, sufficient free storage and an approved fixture larger than 2 GiB must be established before device acceptance; they do not block reviewing this specification.
- Provider-specific cancellation behavior and exact publication/recovery mechanisms must be demonstrated in the reviewed plan and tests, not assumed to be solved by specification approval.

## Reference Basis

- [Android SAF guidance](https://developer.android.com/training/data-storage/shared/documents-files): user-granted document access, generic provider metadata and possibly unknown sizes.
- [Android Auto Backup guidance](https://developer.android.com/identity/data/autobackup): backup/transfer policy and excluded locations; an `allowBackup` switch alone is not a universal OEM transfer guarantee.
- [Android AtomicFile contract](https://developer.android.com/reference/android/util/AtomicFile): commit/rollback helpers do not supply locking; the plan still owns transaction ordering and serialization.
- [Upstream GGUF format](https://github.com/ggml-org/ggml/blob/master/docs/gguf.md): fixed header layout and v3 big-endian support. This is a format reference, not a change to pinned native-runtime artifacts or evidence of model compatibility.

## Approval Gate

The owner explicitly approved this specification after the final review/fix pass. This authorizes drafting the scoped technical plan only. Plan approval, detailed task approval and implementation authorization remain separate gates; no code, build or device-test result is established by approval.
