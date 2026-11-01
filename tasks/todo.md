# Native Runtime — Approved Task List

Spec: `SPEC-native-runtime.md` (approved). Plan: `tasks/plan.md` (approved). Task list: approved by the user.

**Implementation explicitly on hold at the user's request. Do not start until instructed.** All implementation boxes remain unchecked. Commands below refer to files that will be created, not existing working tooling.

Standing completion bar: applicable tests pass with nonzero test discovery, no regressions or unrelated scope, diagnostics and failure cleanup verified, relevant docs updated, and no unsupported runtime claims. A task requiring unavailable device evidence stays blocked.

## T1: Establish the artifact provenance boundary

- [ ] Complete T1.

**Description:** Reproduce the downloaded archive inspection with standard ELF tools; commit the provenance allowlist and required license/notice text while excluding generated/downloaded binaries from Git.

**Acceptance:**
- [ ] The URL, release/commit, archive size/SHA-256, fourteen selected payload mappings and individual digests are recorded; launcher/dependency/CPU variant evidence matches the plan.
- [ ] ELF, platform dependency, embedded-UI, and CPU feature evidence/uncertainties are documented; RPC/unrelated payloads are explicitly excluded.
- [ ] Applicable upstream notices/licenses are preserved; `.cache`, native stage, local SDK paths, and build outputs are ignored.

**Verify:** Use the plan's exact `curl` and `sha256sum` commands. Run NDK `llvm-readelf -h -l -d` on the launcher and selected libraries and inspect UI symbols with `llvm-nm`; compare with pinned source. Use `git status --short --ignored` to confirm binary exclusions. No Android execution claim.

**Dependencies:** None. NDK inspection tooling is a local prerequisite; coordinate setup with T2.

**Files (maximum five):** `.gitignore`, `third_party/llama.cpp.json`, `third_party/llama.cpp-LICENSE`, `third_party/NOTICE.md`, `docs/verification/native-artifact.md`.

**Scope:** Medium.

## T2: Establish the pinned wrapper

- [ ] Complete T2.

**Description:** Add the official Gradle 8.10.2 wrapper with verified JAR/distribution provenance and document local JDK/Android SDK setup. Do not change global PATH or install tooling without identifying what is already present.

**Acceptance:**
- [ ] Gradle wrapper scripts/JAR/properties are official, pinned, and checksum-verified, including `distributionSha256Sum`.
- [ ] Host setup identifies JDK 17, SDK platform 35/build-tools 35.0.0, and NDK 27.2.12479018 inspection tools with executable setup/verification commands.
- [ ] `./gradlew --version` reports Gradle 8.10.2 on JDK 17; no Android source is needed for this checkpoint.

**Verify:** Official checksum comparisons; `java -version`; `./gradlew --version`; `sdkmanager --list_installed`; the pinned NDK `llvm-readelf --version`. Record unavailable prerequisites rather than inventing a successful run.

**Dependencies:** None; coordinate with T1 on local tools.

**Files (five):** `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `docs/BUILDING.md`.

**Scope:** Medium.

## T3: Build the minimal Views APK

- [ ] Complete T3.

**Description:** Establish the smallest functioning Android project, a platform Views Activity, and the pinned plugin/SDK configuration. This APK has no native launch UI.

**Acceptance:**
- [ ] Package/SDK/ABI/JVM settings match the approved spec and plan; a platform theme avoids extra production dependencies.
- [ ] A minimal Activity compiles without model import, service, WebView, permissions unrelated to this module, or exported launch hooks.
- [ ] A clean debug APK and lint result are produced with actual Gradle output.

**Verify:** `./gradlew --no-daemon clean :app:lintDebug :app:assembleDebug`; `unzip -l app/build/outputs/apk/debug/app-debug.apk`; inspect the merged manifest. At this stage native payloads are intentionally absent and do not constitute a native-runtime success.

**Dependencies:** T2.

**Files (five):** `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/io/github/greprohan/llamadroid/MainActivity.kt`.

**Scope:** Medium.

## Checkpoint A: Artifact and build foundation

- [ ] T1–T3 evidence reviewed; selected artifact/dependency boundary is consistent.
- [ ] Pinned toolchain and minimal APK verified; no claim of native execution.
- [ ] Human review before packaging work; unresolved prerequisite failures remain visible.

## T4: Stage a verified native payload safely

- [ ] Complete T4.

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

- [ ] Complete T5.

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

- [ ] Complete T6.

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

- [ ] T4–T6 host/JVM tests pass with real test discovery; intentional stage tampering is rejected.
- [ ] Packaged native payloads match the allowlist/digests and exclude RPC; no unsupported loader assumptions have been treated as proof.
- [ ] Human review of packaging and launcher contract before device harness integration.

## T7: Add the bounded app-context smoke harness

- [ ] Complete T7.

**Description:** Add instrumentation that launches upstream `--version` from the installed app context, consumes output safely, enforces a deadline, and proves child cleanup. Test the harness's timeout/exit/error paths with a fake `Process`, not fake native ELF files.

**Acceptance:**
- [ ] Real-server test requires pinned version identification and exit 0 within 30 seconds; both output streams are drained concurrently with bounded retained tails.
- [ ] Instrumentation-side harness tests cover timeout, abnormal exit, large output, and cleanup failure; cleanup/reaping and reader shutdown have no unbounded waits.
- [ ] Instrumentation compiles with the pinned test-only dependencies; no test hook is exported in the production app.

**Verify:** `./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest`; on an authorized target S25, `./gradlew --no-daemon :app:connectedDebugAndroidTest`. Record compiled and device-run results separately; no connected phone means device tests are blocked.

**Dependencies:** T6.

**Files (five):** `app/build.gradle.kts`, `gradle.properties`, `app/src/androidTest/java/io/github/greprohan/llamadroid/NativeProcessProbe.kt`, `app/src/androidTest/java/io/github/greprohan/llamadroid/NativeProcessProbeTest.kt`, `app/src/androidTest/java/io/github/greprohan/llamadroid/NativeVersionSmokeTest.kt`.

**Scope:** Medium.

## T8: Audit a clean-build APK

- [ ] Complete T8.

**Description:** Add automated ZIP/payload auditing and reproduce the complete build from a clean worktree with only documented prerequisites. Inspect Android's packaged manifest using SDK tooling separately from ZIP inspection.

**Acceptance:**
- [ ] Audit tests reject missing/extra/modified native payloads, wrong ABI, missing attribution, and embedded GGUFs; the real APK passes.
- [ ] Host tests, JVM tests, lint, debug APK, and instrumentation APK compile from a clean worktree after explicit checksum-verified download/staging; no untracked local implementation file is required.
- [ ] Actual tool versions, commands/results, APK hash/size, and packaged manifest extraction/SDK/export/permission checks are recorded; unsupported device claims are absent.

**Verify:** `python -m unittest discover -s scripts/tests -p 'test_*.py' -v`; plan's full clean build command; `./gradlew --no-daemon :app:assembleDebugAndroidTest`; `python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk`; `apkanalyzer manifest print app/build/outputs/apk/debug/app-debug.apk`. Use a disposable worktree/archive of the candidate changes, never destructive cleanup of the user's working directory.

**Dependencies:** T5, T6, T7.

**Files (five):** `scripts/verify_apk.py`, `scripts/tests/test_verify_apk.py`, `docs/verification/native-build.md`, `docs/BUILDING.md`, `README.md`.

**Scope:** Medium.

## T9: Verify packaged launch on the physical S25

- [ ] Complete T9 — **currently blocked: no connected/authorized phone was observed**.

**Description:** Install the audited APK and run the complete instrumentation suite on the owner's S25, recording device properties and exact process evidence. Remain within the version-command boundary.

**Acceptance:**
- [ ] Device evidence records model/ABI/current Android/API/security patch/build, One UI availability, OS-visible memory, installed executable path/permissions, and app UID without publishing unique serials.
- [ ] `--version` executes from the installed native directory under the app UID, exits 0 within 30 seconds with recognizable pinned build output, and leaves no child process; harness failure cases also pass.
- [ ] Evidence ties the installed/test APKs and upstream artifact to hashes; README/spec outcome distinguishes host-tested, compiled, APK-inspected, and device-tested, with no inference/WebUI claim.

**Verify:** Spec's device-property commands; `adb install -r app/build/outputs/apk/debug/app-debug.apk`; `./gradlew --no-daemon :app:connectedDebugAndroidTest`; `adb logcat -d -s llama.droid`; app-context instrumentation cleanup assertions and a post-run process-list cross-check. If more than one device is connected, explicitly select only the S25 for both ADB and Gradle.

**Dependencies:** T8; physical S25 access.

**Files (three):** `docs/verification/native-s25.md`, `README.md`, `CAPABILITY-MAP.md` (module verification status only). Any required source fix becomes a separately reviewed small task with a regression test.

**Scope:** Medium, device-dependent.

## Checkpoint C: Native-runtime completion review

- [ ] T7–T9 evidence reviewed; real S25 smoke test passed, or module explicitly remains blocked.
- [ ] All seven approved spec success criteria traced to evidence; no unchecked requirement is relabeled complete.
- [ ] Human review before moving to the `model-import` specification. No model import, model loading, service, or chat implementation has been added here.
