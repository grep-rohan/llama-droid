# llama.droid — Agent Context

## Objective

Build **llama.droid**, a minimal, fully open-source Android APK that packages and runs the upstream **llama.cpp `llama-server`** locally and opens its **built-in WebUI** in an Android WebView.

**User experience:** Install APK → import a GGUF → adjust basic arguments → Start server → Chat. No Termux, root, ADB, separate inference service, cloud API, or custom chat UI should be required for end users.

## Current status

This repository is an **experimental scaffold**, **not a proven functioning APK**. Do not claim that native startup, WebUI hosting, CI builds, or phone compatibility work until verified. The immediate priority is an end-to-end **CPU-only ARM64** build and physical-device smoke test.

## Existing code (read selectively)

- `app/src/main/java/io/github/greprohan/llamadroid/MainActivity.kt` — basic Android Views UI, GGUF picker, context/thread settings, Start/Stop buttons, `/health` polling.
- `ModelStore.kt` — stream-copy GGUF from an Android document URI into private app storage.
- `LlamaServerService.kt` — user-started foreground service, `ProcessBuilder` launcher, localhost server, output to logcat.
- `ChatActivity.kt` — WebView pointing to `http://127.0.0.1:8080/`.
- `scripts/import_llama_release.py` — stages upstream Android ARM64 `llama-server` as `libllama-server.so` plus adjacent shared libraries into `jniLibs/arm64-v8a`.
- `.github/workflows/android.yml` — debug APK workflow, downloads pinned upstream CPU release **b12020**.
- `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `README.md` — scope and known limitations.

The current package/application ID is `io.github.greprohan.llamadroid`; minSdk 26, targetSdk/compileSdk 35; JDK 17, AGP 8.8.2, Kotlin 2.0.21, Gradle 8.10.2. The UI intentionally uses Android Views; **do not migrate to Compose during bootstrap**.

## Architecture

```text
MainActivity (model, CPU arguments, start/stop)
       |
       v
LlamaServerService (foreground service + ProcessBuilder)
       |
       v
Bundled upstream ARM64 llama-server + required shared libraries
       |
       v
127.0.0.1:8080  <---->  ChatActivity (upstream WebUI in WebView)
```

The app is a **thin controller/packager**, not an inference implementation. Preserve the upstream WebUI and llama.cpp API instead of building a separate chat or tool frontend.

## Execution priorities

### M0 — Make the APK actually work (current task)

1. Inspect the pinned upstream Android release's **real archive layout**, `llama-server` ELF type/interpreter, `DT_NEEDED` libraries, supported CPU features, and embedded WebUI assets. Verify the download URL and artifact before relying on them.
2. Validate or fix the staging/packaging approach. **Renaming an ELF executable to `libllama-server.so` is a hypothesis, not proof of Android compatibility.** Confirm Android package-manager extraction, executable permissions, dynamic linking, and process launching. Keep everything packaged in the APK; no Termux.
3. Build the debug APK from a clean checkout. Fix manifest, Kotlin/Gradle, native import, and runtime errors; do not call a build successful without compiler output.
4. On a physical ARM64 Android device, import a **small compatible GGUF**, start the service, observe startup logs, poll `/health`, open WebUI, and generate a response. A build or emulator-only test is not the same as device validation.
5. Verify reliable Stop, restart, crash cleanup, and that the foreground notification/service are valid on target Android versions.
6. Document exact supported devices/versions, reproduction steps, limitations, and evidence of what was tested. If device access is unavailable, explicitly mark runtime validation **blocked** and give concrete manual commands/tests.

### Later, only after M0 passes

- M1: model list/import progress, persisted presets, logs in-app, error diagnostics, polished UI.
- M2: independently test Snapdragon Adreno/OpenCL and Hexagon NPU backends, library packaging, quantization support, and runtime selection. **Do not assume CPU/GPU/NPU builds are interchangeable.**
- M3: reproducible releases, pinned hashes, proper OSS attribution, signing, F-Droid support.

## Non-negotiable constraints

- **FOSS**, local-first, offline inference; no telemetry, account, API key, hosted dependency, or model download requirement for MVP.
- Bind `llama-server` to **`127.0.0.1` only** by default; port currently 8080.
- Keep **`--tools`, shell execution, MCP integration, and LAN access disabled** unless explicitly designed, permissioned, and security-reviewed in a future milestone.
- Use Android Storage Access Framework for model import; do not request broad storage permissions. Account for multi-GB copies, cancellation, low storage, and cleanup.
- Foreground-service lifecycle and Android/OEM background restrictions must be treated as reliability requirements, not assumed solved.
- Do not ship GGUF weights or upstream binaries in Git history. Obtain upstream artifacts in builds, pin versions/hashes, preserve upstream attribution/licenses.
- Prefer small, modular, verifiable changes. Avoid architecture rewrites and unnecessary dependencies.

## Known scaffold risks to investigate

- Native ELF executable-as-`.so` packaging and library lookup; Android target SDK/W^X restrictions.
- `libllama-server.so` filename and how the Android packaging pipeline handles executable ELF files.
- Upstream tarball may have additional runtime files, library symlinks or separate WebUI assets omitted by the import script.
- `/health` can be unavailable while loading; differentiate startup, ready, exited, and failed states.
- Current service state is a process-local static string; stop/destroy races, stdout blocking, and stale port ownership need testing.
- WebView main-frame URL filtering alone is not a complete security boundary; assess subrequests, intent/file schemes, and tool-enabled functionality before exposing them.
- No Gradle wrapper JAR or pinned binary SHA-256 is bundled in the seed; CI downloads dependencies.

## Verification commands

```bash
python3 scripts/import_llama_release.py /path/to/llama-android-arm64.tar.gz
gradle --no-daemon :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s llama.droid
```

Check actual native library dependencies with `readelf -h -l -d` where available; verify the packaged APK contents with `unzip -l`. Test readiness and actual text generation through the app, not just a process launch.

## Agent working rules

- Start by identifying the **smallest failing step**, then fix and verify it. Do not broaden scope into M1/M2 while M0 is unproven.
- Read only files relevant to the active step; **do not repeatedly ingest every document or the full codebase**. Consult `docs/` on demand.
- Before changing a native build assumption, check the actual upstream release/runtime artifacts rather than guessing.
- Report: **changed files → tests executed/results → unresolved blockers → next smallest action**.
- Distinguish **implemented**, **compiled**, **device-tested**, and **unverified**. Never mark a milestone done without evidence.
