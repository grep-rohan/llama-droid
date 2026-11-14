# Native Runtime — Approved Task List

Spec: `SPEC-native-runtime.md` (approved). Plan: `tasks/plan.md` (approved). Task list: approved by the user.

**Status: incomplete; documentation reconciled with the current working tree.** The original implementation hold is historical, not a description of the files now present. This review authorizes documentation changes only, not further implementation or architecture changes. Earlier completion claims are not evidence for the changed checkout; affected tasks below are reopened. T1–T2 remain recorded historical completions, not newly reverified toolchain results.

Standing completion bar: applicable tests pass with meaningful nonzero discovery, no regressions or unrelated scope, diagnostics and failure cleanup verified, relevant docs updated, and no unsupported runtime claims. Missing device evidence blocks completion; marking a task blocked does not complete the module.

## Current evidence and actionable blockers

This documentation review ran:
- `python -m unittest discover -s scripts/tests -p 'test_*.py' -v`: **34 pass = 22 importer + 12 APK audit**. The duplicate-member test has no rejection assertion; the three monolithic tests exercise synthetic APK checks, not a real native merge. Passing counts do not establish all acceptance coverage.
- `python scripts/import_llama_release.py --verify-only`: **passes** for the existing fourteen-file stage. This verifier currently does not validate the receipt or ELF architecture.
- `python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk`: **fails with `UnicodeDecodeError`** while decoding the binary Android manifest. This is an existing APK, not a fresh clean-build result.
- `adb devices -l`: **no devices attached**.
- Importer `_find_ndk_toolchain()`: **None** in this environment, although the documented NDK's `llvm-readelf.exe` exists. Tool discovery/configuration, not a proven missing installation, is the issue.

No Gradle build, JVM test, instrumentation compile/run, installation, or native execution was performed in this review. See [build verification](../docs/verification/native-build.md) for the current evidence boundary.

### Blockers in precedence order

1. **Restore an agreed native-runtime scope and launch boundary (T3/T6/T7).** Review/isolate the existing JNI/Ktor/inference changes without discarding user work. `NativeRuntimeLayout.kt`, `NativeServerLauncher.kt`, and launcher JVM tests are absent; instrumentation still references those classes. Restore the approved direct-process contract or obtain explicit approval for an alternative. Exit: minimal non-listening Activity, internal launcher and meaningful JVM tests available; no downstream inference/listener dependency required for this module.
2. **Repair the artifact/staging contract before relying on a linker remedy (T4).** The current merge proposal is unverified and differs from the approved byte-preserving fourteen-file plan. `--whole-archive` does not combine shared-library contents as it does static archives; the code also uses `libllama-server.so` as both input/output and later unlinks every input, including that path. `--verify-only` still expects fourteen original digests. Preserve the genuine stage; reproduce any proposed transformation only in temporary fixtures after review. Add real duplicate-member, ELF-architecture, receipt, interrupted-publication/rollback tests. Exit: one approved artifact format, deterministic verification and safe failure handling. If native transformation is approved, configure the existing pinned NDK explicitly and record output ELF/dependency/entry-point evidence; merely setting `ANDROID_NDK_HOME` is not a fix.
3. **Reinstate fail-closed Gradle packaging (T5).** `verifyNativeStage` is disabled with `onlyIf { false }`; its command passes the staging directory positionally instead of via `--staging-dir`. Re-enable verification for the agreed stage, reject missing/tampered/extra payloads, and prove extraction/byte preservation and attribution in the APK. Exit: deliberate fixture corruption fails the build; genuine stage passes.
4. **Make the real APK audit trustworthy (T8).** Stop decoding binary `AndroidManifest.xml` as UTF-8; inspect it with SDK tooling separately or implement supported decoding. Replace size-only monolithic acceptance with approved provenance/digest checks (or reject that format). Exit: real APK audit completes with meaningful payload integrity checks; malformed/tampered fixtures fail; `apkanalyzer` records SDK, extraction, exports and permissions.
5. **Reproduce the complete host build from reviewable source (T7/T8).** Required harness/auditor files are currently untracked and launcher JVM sources are absent. Select the intended source set without committing generated binaries. From a disposable clean checkout/worktree with only documented prerequisites, run host tests, JVM tests, lint, debug APK and instrumentation APK builds; record nonzero counts, versions, logs, both APK hashes/sizes and audit results. Exit: no untracked implementation or stale build output is needed.
6. **Authorize the physical S25 and prove installed-app launch (T9).** Device authorization can be arranged in parallel, but install/test only the audited build from steps 1–5. Select only the S25 for ADB and Gradle; record properties without serials, app UID, installed native paths/permissions, pinned version output, exit 0 within 30 seconds, and child cleanup. Capture exact linker errors if it fails; no Android 17/`LD_LIBRARY_PATH` restriction is established by the evidence currently recorded. Exit: full instrumentation suite passes with hash-linked evidence in `docs/verification/native-s25.md`.
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

- [ ] Complete T3 — **reopened: current Activity starts a Ktor server; minimal module scope has drifted.**

**Description:** Establish the smallest functioning Android project, a platform Views Activity, and the pinned plugin/SDK configuration. This APK has no native launch UI.

**Acceptance:**
- [ ] Package/SDK/ABI/JVM settings match the approved spec and plan; a platform theme avoids extra production dependencies.
- [ ] A minimal Activity compiles without model import, service, WebView, permissions unrelated to this module, or exported launch hooks.
- [ ] A clean debug APK and lint result are produced with actual Gradle output for the agreed source set.

**Verify:** `./gradlew --no-daemon clean :app:lintDebug :app:assembleDebug`; `unzip -l app/build/outputs/apk/debug/app-debug.apk`; inspect the merged manifest. At this stage native payloads are intentionally absent and do not constitute a native-runtime success.

**Dependencies:** T2.

**Files (five):** `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/io/github/greprohan/llamadroid/MainActivity.kt`.

**Scope:** Medium.

## Checkpoint A: Artifact and build foundation

- [ ] T1–T3 evidence reviewed against the current source set; selected artifact/dependency boundary is consistent.
- [ ] Pinned toolchain and minimal APK reverified; no claim of native execution.
- [x] Human review before packaging work — historical approval; does not approve current architecture drift.

## T4: Stage a verified native payload safely

- [ ] Complete T4 — **reopened: safety/receipt coverage gaps and unverified merge proposal.**

**Description:** Implement the manifest-driven Python importer and verification-only mode with adversarial archive tests before implementation. Keep extraction constrained and failure output unpublishable.

**Acceptance:**
- [ ] Archive digest/size and selected ELF payloads are checked before publishing the exact fourteen-file stage; unrelated files are not copied.
- [ ] Tests cover checksum mismatch, wrong architecture, missing/duplicate members, traversal/absolute paths, links/special files, partial-write failure, rollback, and stale/unexpected/tampered staged files.
- [ ] `--verify-only` validates payload bytes/receipt without mutation or downloads; a failed/interrupted import cannot leave a stage accepted as the new artifact.

**Verify:** `python -m unittest discover -s scripts/tests -p 'test_import_llama_release.py' -v` (real cases, red before green); `python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz`; `python scripts/import_llama_release.py --verify-only`. Reproduce checksum/stage mismatch failures on temporary test fixtures, not the user's files.

**Dependencies:** T1.

**Files (three):** `scripts/import_llama_release.py`, `scripts/tests/test_import_llama_release.py`, `docs/BUILDING.md`. Native outputs/receipt are generated, ignored files.

**Scope:** Medium.

## T5: Package the exact native payload

- [ ] Complete T5 — **reopened: Gradle stage verification is disabled.**

**Description:** Integrate verification-only staging into Gradle and configure extraction/preservation for the installed native-library directory, retaining license text in the APK.

**Acceptance:**
- [ ] Gradle fails clearly for absent/tampered/extra native stage instead of producing a stale or empty native APK.
- [ ] ARM64 payloads are extracted by the intended packaging settings and preserved byte-for-byte; no source path is used as a runtime executable location.
- [ ] APK contains the allowlisted native files and required attribution, but no RPC backend, utility executables, GGUFs, or unrelated native backend.

**Verify:** Stage with the documented importer, then `./gradlew --no-daemon :app:assembleDebug`; compare ZIP-member SHA-256 against the manifest. Demonstrate build rejection using temporary staged fixtures and restore/re-verify the genuine stage. Inspect merged manifest extraction settings.

**Dependencies:** T3, T4.

**Files (maximum four):** `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `docs/BUILDING.md`, and an asset notice-generation input if required. License assets are generated from `third_party/`, not manually duplicated binary inputs.

**Scope:** Medium.

## T6: Implement the internal direct-process launcher

- [ ] Complete T6 — **reopened: launcher/layout classes and JVM test sources are absent.**

**Description:** Resolve trusted installed native paths, construct an explicit process command and controlled lookup environment, and return the child process without adding service orchestration.

**Acceptance:**
- [ ] The internal `NativeServerLauncher.start(arguments: List<String>): Process` uses only the bundled executable resolved from `nativeLibraryDir`, never a shell or caller-selected executable.
- [ ] Working directory/`LD_LIBRARY_PATH` use the trusted native directory; `GGML_BACKEND_PATH` is removed; missing payload/permission/start failures produce useful diagnostics.
- [ ] JVM tests cover argument separation, trusted path/environment construction, and configuration failure paths without pretending to execute an ARM64 binary on the host.

**Verify:** Focused JUnit tests: `./gradlew --no-daemon :app:testDebugUnitTest --tests '*NativeServerLauncherTest'`; `./gradlew --no-daemon :app:lintDebug :app:assembleDebug`. Review production manifest for absent external launch endpoints.

**Dependencies:** T5.

**Files (maximum four):** `app/src/main/java/io/github/greprohan/llamadroid/NativeRuntimeLayout.kt`, `app/src/main/java/io/github/greprohan/llamadroid/NativeServerLauncher.kt`, `app/src/test/java/io/github/greprohan/llamadroid/NativeServerLauncherTest.kt`, `app/build.gradle.kts`.

**Scope:** Medium.

## Checkpoint B: Payload and launch boundary

- [ ] T4–T6 host/JVM tests pass with meaningful test discovery; intentional stage tampering is rejected.
- [ ] Packaged native payloads match the allowlist/digests and exclude RPC; no unsupported loader assumptions have been treated as proof.
- [x] Human review of packaging and launcher contract before device harness integration — historical approval; current deviations require review.

## T7: Add the bounded app-context smoke harness

- [ ] Complete T7 — **reopened: instrumentation references missing launcher classes; current compilation/run unverified.**

**Description:** Add instrumentation that launches upstream `--version` from the installed app context, consumes output safely, enforces a deadline, and proves child cleanup. Test the harness's timeout/exit/error paths with a fake `Process`, not fake native ELF files.

**Acceptance:**
- [ ] Instrumentation compiles with the pinned test-only dependencies; no test hook is exported in the production app.
- [ ] Instrumentation-side harness tests execute and cover timeout, abnormal exit, large output, and cleanup failure with a fake `Process`. Source/compilation alone is not a passing test result.
- [ ] Real-server test requires pinned version identification and exit 0 within 30 seconds; both output streams are drained concurrently with bounded retained tails. **Blocked: host prerequisites above and no connected/authorized device.**

**Verify:** `./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest` — prior compilation reported, **not revalidated for current sources**. On an authorized target S25, `./gradlew --no-daemon :app:connectedDebugAndroidTest` — **blocked**.

**Dependencies:** T6.

**Files (five):** `app/build.gradle.kts`, `gradle.properties`, `app/src/androidTest/java/io/github/greprohan/llamadroid/NativeProcessProbe.kt`, `app/src/androidTest/java/io/github/greprohan/llamadroid/NativeProcessProbeTest.kt`, `app/src/androidTest/java/io/github/greprohan/llamadroid/NativeVersionSmokeTest.kt`.

**Scope:** Medium.

## T8: Audit a clean-build APK

- [ ] Complete T8 — **reopened: real APK audit crashes; clean-checkout evidence incomplete.**

**Description:** Add automated ZIP/payload auditing and reproduce the complete build from a clean worktree with only documented prerequisites. Inspect Android's packaged manifest using SDK tooling separately from ZIP inspection.

**Acceptance:**
- [ ] Audit tests reject missing/extra/modified native payloads, wrong ABI, missing attribution, and embedded GGUFs; the real APK passes a meaningful integrity audit. Current twelve synthetic audit tests pass, but monolithic acceptance is size-only and the real APK manifest crashes decoding.
- [ ] Host tests, JVM tests, lint, debug APK, and instrumentation APK build/run as applicable from a clean worktree; no untracked local implementation file is required for verification.
- [ ] Actual tool versions, commands/results, both APK hashes/sizes, and packaged manifest extraction/SDK/export/permission checks are recorded. `apkanalyzer` evidence is still pending.

**Verify:** `python -m unittest discover -s scripts/tests -p 'test_*.py' -v` — **34 pass (22 importer + 12 audit), coverage limitations noted above**. `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` — **pending for agreed current source set**. `python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk` — **fails with binary-manifest `UnicodeDecodeError` on existing APK**.

**Dependencies:** T5, T6, T7.

**Files (five):** `scripts/verify_apk.py`, `scripts/tests/test_verify_apk.py`, `docs/verification/native-build.md`, `docs/BUILDING.md`, `README.md`.

**Scope:** Medium.

## T9: Verify packaged launch on the physical S25

- [ ] Complete T9 — **blocked by T3–T8 revalidation and no connected/authorized phone**.

**Description:** Install the audited APK and run the complete instrumentation suite on the owner's S25, recording device properties and exact process evidence. Remain within the version-command boundary.

**Acceptance:**
- [ ] Device evidence records model/ABI/current Android/API/security patch/build, One UI availability, OS-visible memory, installed executable path/permissions, and app UID without publishing unique serials.
- [ ] `--version` executes from the installed native directory under the app UID, exits 0 within 30 seconds with recognizable pinned build output, and leaves no child process; harness failure cases also pass.
- [ ] Evidence ties the installed/test APKs and upstream artifact to hashes; README/spec outcome distinguishes host-tested, compiled, APK-inspected, and device-tested, with no inference/WebUI claim.

**Verify:** Spec's device-property commands; `adb install -r app/build/outputs/apk/debug/app-debug.apk`; `./gradlew --no-daemon :app:connectedDebugAndroidTest`; `adb logcat -d -s llama.droid`; app-context instrumentation cleanup assertions and a post-run process-list cross-check. If more than one device is connected, explicitly select only the S25 for both ADB and Gradle.

**Dependencies:** T8; physical S25 access.

**Files (three):** `docs/verification/native-s25.md`, `README.md`, `CAPABILITY-MAP.md` (module verification status only). Any required source fix becomes a separately reviewed small task with a regression test.

**Scope:** Medium, device-dependent.

### Linker diagnosis: unverified, not a confirmed fix

The pinned launcher is ET_DYN and has six non-platform dependencies (T1 evidence). The previous claim of an Android 17 security restriction was not accompanied by device properties, linker stderr, or a hash-linked test run; do not treat it as established.

The current importer attempts `ld.lld -shared --whole-archive`, not the previously documented `-r` command. No successful merged ELF or installed-app execution is recorded. The three monolithic tests are synthetic APK-audit tests, not importer/linker integration tests. See blocker 2 before executing import with NDK discovery enabled. Do not overwrite the genuine stage merely to try this proposal.

## Checkpoint C: Native-runtime completion review

- [ ] T3–T9 revalidation evidence reviewed; real S25 smoke test passed. If blocked, record the blocker without checking this completion box.
- [ ] All seven approved spec success criteria traced to evidence; no unchecked requirement is relabeled complete.
- [ ] Human review before moving to the `model-import` specification. No model import, model loading, service, or chat implementation has been added here.

**Current status:** T1–T2 historical completions retained; T3–T8 reopened against the current source tree; T9 blocked. Thirty-four host tests and the existing stage's payload digest check pass, with acceptance gaps. The real APK audit fails, current Android compilation/JVM execution is unverified, and no device execution is established. The module is incomplete until all success criteria and Checkpoint C pass; a blocked designation is not completion.
