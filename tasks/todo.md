# Native Runtime — Approved Task List

Spec: `SPEC-native-runtime.md` (approved). Plan: `tasks/plan.md` (approved). Task list: approved by the user.

**Status: incomplete; blockers 1–5 host work verified on `fix/native-runtime`.** User approved Checkpoint B, then final source commit `955bfe3f58cf515943f3ebfa6b76748dcc11092d` was built from a clean detached checkout with a fresh pinned archive download. 94 Python, 43 JVM and 12 Gradle/APK integration checks pass; both APKs compile and their hashes/SDK manifests are recorded. Original checkout remains untouched. **Next blocker: physical S25 access/authorization and ART/native execution.** T5 installed extraction, T7 device cases and T9 stay unchecked; no native startup or module completion is claimed.

Standing completion bar: applicable tests pass with meaningful nonzero discovery, no regressions or unrelated scope, diagnostics and failure cleanup verified, relevant docs updated, and no unsupported runtime claims. Missing device evidence blocks completion; marking a task blocked does not complete the module.

## Current evidence and actionable blockers

### Current — blocker 5 host harness/clean-build verification

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
2. **Resolved: byte-preserved artifact/staging contract (T4).** User chose option A; repaired and verified the fourteen-file importer, strict receipt/ELF checks and failure/rollback contract. Fifty meaningful host tests and the genuine archive pass; provenance and every staged digest were rechecked. No merge was copied/attempted; native linking still needs device evidence. See current evidence above.
3. **Host work resolved: fail-closed Gradle packaging (T5).** User chose option A. Gate, verified JNI snapshot, extraction settings/byte preservation and generated attribution are implemented; 12 actual Gradle/APK checks and the real clean-output build pass. Source/installed execution remains separate: actual extraction/permissions/linking is still unchecked under T5/T9. No device requirement is waived.
4. **Resolved: standalone ZIP/payload auditor (T8 host portion).** User chose option A. New auditor and 44 adversarial tests pass; real native APK passes with pinned hashes and strict attribution; separate SDK inspection passes against the same APK identity. No original untracked auditor was copied and no manifest-decoding/monolithic fallback is used. Clean-source build and instrumentation compilation evidence was subsequently completed under blocker 5; ART/native execution still requires the physical device.
5. **Host work resolved: Checkpoint B review, bounded harness and clean-source reproduction (T7/T8).** User chose option A. Probe implemented/host-tested, both APKs compiled from committed source in a clean detached worktree, fresh archive verified/staged, actual versions/results/hashes/SDK manifests recorded. Source status clean before/after; no untracked implementation input needed. T7 ART/native execution remains a device blocker, not a host-test completion claim.
6. **Authorize the physical S25 and prove installed-app launch (T9) — next interactive gate, currently blocked.** Device authorization can be arranged in parallel, but install/test only the audited build from steps 1–5. Select only the S25 for ADB and Gradle; record properties without serials, app UID, installed native paths/permissions, pinned version output, exit 0 within 30 seconds, and child cleanup. Capture exact linker errors if it fails; no Android 17/`LD_LIBRARY_PATH` restriction is established by the evidence currently recorded. Exit: full instrumentation suite passes with hash-linked evidence in `docs/verification/native-s25.md`.
7. **Review completion evidence (Checkpoint C).** Trace all seven spec criteria, update README/capability status, and obtain human review before downstream work. If any prerequisite remains blocked, the module remains incomplete.

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

- [ ] Complete T5 — **host packaging implemented/compiled/APK-inspected; installed extraction remains blocked under T9.**

**Description:** Integrate verification-only staging into Gradle and configure extraction/preservation for the installed native-library directory, retaining license text in the APK.

**Acceptance:**
- [x] Gradle fails clearly for absent/tampered/extra native stage instead of producing a stale or empty native APK.
- [ ] ARM64 payloads are extracted by the intended packaging settings and preserved byte-for-byte; no source path is used as a runtime executable location. **Host evidence:** fourteen exact ZIP hashes, compression and `extractNativeLibs=true` confirmed; source launcher resolves installed `nativeLibraryDir` only. **Still blocked:** actual installed extraction/permissions must be proven in T9; this clause is not marked complete from manifest inspection alone.
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

- [ ] Complete T7 — **implemented/compiled, 24 shared fake-Process cases pass on JVM; ART and real-server execution blocked by no device.**

**Description:** Add instrumentation that launches upstream `--version` from the installed app context, consumes output safely, enforces a deadline, and proves child cleanup. Test the harness's timeout/exit/error paths with a fake `Process`, not fake native ELF files.

**Acceptance:**
- [x] Instrumentation compiles with the pinned test-only dependencies; no test hook is exported in the production app. Probe, fake cases and provenance asset are absent from the production APK and present only in the instrumentation APK.
- [ ] Instrumentation-side harness tests execute and cover timeout, abnormal exit, large output, and cleanup failure with a fake `Process`; cleanup/reaping and reader shutdown have no unbounded waits. **Host evidence:** identical shared sources execute 24 cases on JVM, including stuck wait/destroy/read/close, caller interruption, reader Errors and false EOF after forced close. **Still blocked:** their ART/instrumentation run requires the device; compilation is not an Android test result.
- [ ] Real-server test requires pinned version/build/commit identification and exit 0 within 30 seconds; both output streams must drain to EOF concurrently with bounded byte tails, child/reader/control-operation cleanup must succeed. **Blocked: no connected/authorized device.**

**Verify:** `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` — **BUILD SUCCESSFUL, 81 executed tasks; 43 JVM tests (19 launcher + 24 probe), zero failures/errors/skips**, four existing lint warnings. On an authorized physical S25, `./gradlew --no-daemon :app:connectedDebugAndroidTest` — **blocked: no device**.

**Dependencies:** T6.

**Files (four):** `app/build.gradle.kts`, `app/src/sharedTest/java/io/github/greprohan/llamadroid/NativeProcessProbe.kt`, `app/src/sharedTest/java/io/github/greprohan/llamadroid/NativeProcessProbeTest.kt`, `app/src/androidTest/java/io/github/greprohan/llamadroid/NativeVersionSmokeTest.kt`. Shared test-only sources execute on JVM and compile for instrumentation without duplicating cases or shipping probe code in production. Test-only provenance assets are generated from `third_party/llama.cpp.json`; no new dependency/version or `gradle.properties` change was needed.

**Scope:** Medium.

## T8: Audit a clean-build APK

- [x] Complete T8 — **host clean-source reproduction/audit verified, including instrumentation APK compilation; T5/T7/T9 device checks are not waived.**

**Description:** Add automated ZIP/payload auditing and reproduce the complete build from a clean worktree with only documented prerequisites. Inspect Android's packaged manifest using SDK tooling separately from ZIP inspection.

**Acceptance:**
- [x] Audit tests reject missing/extra/modified native payloads, wrong ABI, missing attribution, and embedded GGUFs; the real APK passes a meaningful integrity audit. Forty-four auditor tests pass; inventory/hash/ELF/source-attribution and recognized forbidden-name/magic checks are enforced, not size-only merge acceptance. SDK manifest/signing/device checks remain separate.
- [x] Host tests, JVM tests, lint, debug APK, and instrumentation APK **compile** from clean committed source; no untracked local implementation is required. 94 Python/43 JVM/12 packaging checks execute on host. Android instrumentation run remains T7/T9, not claimed here.
- [x] Actual tool versions, commands/results, both APK hashes/sizes, and SDK-decoded manifest extraction/SDK/export/permission checks are recorded for clean source revision `955bfe3`. Installed behavior remains unverified.

**Verify:** Clean detached checkout at `955bfe3`: fresh pinned download + `sha256sum --check`, import/verify, 94 host tests, `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`, standalone audit, separate SDK manifest inspection and 12 opt-in packaging checks all pass. JUnit XML confirms 43 tests, zero failures/errors/skips. Full commands/tool inventory/both APK identities are in `docs/verification/native-build.md`. No device execution.

**Dependencies:** T5, T6, T7.

**Files (five):** `scripts/verify_apk.py`, `scripts/tests/test_verify_apk.py`, `docs/verification/native-build.md`, `docs/BUILDING.md`, `README.md`.

**Scope:** Medium.

## T9: Verify packaged launch on the physical S25

- [ ] Complete T9 — **blocked: no connected/authorized physical S25; installed extraction and ART/native cases still unverified.**

**Description:** Install the audited APK and run the complete instrumentation suite on the owner's S25, recording device properties and exact process evidence. Remain within the version-command boundary.

**Acceptance:**
- [ ] Device evidence records model/ABI/current Android/API/security patch/build, One UI availability, OS-visible memory, installed executable path/permissions, and app UID without publishing unique serials.
- [ ] `--version` executes from the installed native directory under the app UID, exits 0 within 30 seconds with recognizable pinned build output, and leaves no child process; harness failure cases also pass.
- [ ] Evidence ties the installed/test APKs and upstream artifact to hashes; README/spec outcome distinguishes host-tested, compiled, APK-inspected, and device-tested, with no inference/WebUI claim.

**Verify:** Spec's device-property commands; `adb install -r app/build/outputs/apk/debug/app-debug.apk`; `./gradlew --no-daemon :app:connectedDebugAndroidTest`; `adb logcat -d -s llama.droid`; app-context instrumentation cleanup assertions and a post-run process-list cross-check. If more than one device is connected, explicitly select only the S25 for both ADB and Gradle.

**Dependencies:** T8; physical S25 access.

**Files (three):** `docs/verification/native-s25.md`, `README.md`, `CAPABILITY-MAP.md` (module verification status only). Any required source fix becomes a separately reviewed small task with a regression test.

**Scope:** Medium, device-dependent.

### Original checkout linker diagnosis: unverified, not a confirmed fix

The pinned launcher is ET_DYN and has six non-platform dependencies (T1 evidence). The previous claim of an Android 17 security restriction was not accompanied by device properties, linker stderr, or a hash-linked test run; do not treat it as established.

The original dirty checkout's importer attempts `ld.lld -shared --whole-archive`, not the previously documented `-r` command. That merge is not present in this isolated branch. No successful merged ELF or installed-app execution is recorded. The three monolithic tests are synthetic APK-audit tests, not importer/linker integration tests. See blocker 2 before executing import with NDK discovery enabled. Do not overwrite the genuine stage merely to try this proposal.

## Checkpoint C: Native-runtime completion review

- [ ] T3–T9 revalidation evidence reviewed; real S25 smoke test passed. If blocked, record the blocker without checking this completion box.
- [ ] All seven approved spec success criteria traced to evidence; no unchecked requirement is relabeled complete.
- [ ] Human review before moving to the `model-import` specification. No model import, model loading, service, or chat implementation has been added here.

**Current status:** Host blockers 1–5 verified: Checkpoint B approved, clean-source download/staging/build/audit of both APKs reproduced, 94 Python + 43 JVM + 12 packaging checks pass. T5 installed extraction/permissions and T7/T9 ART/native execution remain blocked by no device. Next interactive action: connect/authorize the physical S25, then run the fixed informational smoke suite and record properties/cleanup evidence. No model-import, server lifecycle or chat work until Checkpoint C review; module remains incomplete.
