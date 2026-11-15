# Capability Map: Reliable Single-Model Baseline

Status: module boundaries and build order approved by the user on 2026-07-19.

## Objective

Build a minimal FOSS Android wrapper around upstream llama.cpp `llama-server` and its built-in WebUI. Verify dependable offline CPU inference on the owner's physical Samsung Galaxy S25 before expanding scope.

| Module id | Responsibility | Depends on | Specification |
|---|---|---|---|
| `native-runtime` | Build the APK, bundle CPU ARM64 `llama-server`, verify ELF dependencies and packaged launch | — | `SPEC-native-runtime.md` (complete on `fix/native-runtime`; host + S25 direct 25/25 verified, both audited APKs retained; Checkpoint C owner-approved, not merged) |
| `model-import` | Import GGUF through SAF; handle cancellation, insufficient storage, and partial-file cleanup | — | Not drafted |
| `server-lifecycle` | Start, readiness, failure reporting, Stop/restart, foreground-service cleanup | `native-runtime`, `model-import` | Not drafted |
| `local-chat` | Open upstream WebUI in a restricted localhost WebView; verify actual generation | `server-lifecycle` | Not drafted |

Build order: `native-runtime` → `model-import` → `server-lifecycle` → `local-chat`.

The owner approved Checkpoint C and closure of **native-runtime only** after
evidence commit `ca55ef5`. Downstream specification/implementation and merging
remain separately gated; this is not completion of the offline-chat baseline.

Each module goes through separate Specify → Plan → Tasks → Implement approval gates. Physical-device checks belong in the relevant module's acceptance criteria; successful compilation does not establish runtime reliability.

## Shared decisions

- Target device: owner's Samsung Galaxy S25 (base model). Published baseline: Snapdragon 8 Elite for Galaxy, 12 GB RAM, 128/256/512 GB storage variants; launched with Android 15 / One UI 7.0. Sources: https://www.sammobile.com/samsung/galaxy-s25/ and https://www.gsmarena.com/samsung_galaxy_s25-13610.php
- Published specifications are sufficient for initial hardware targeting. Current S25 evidence records Android 17/API 37, raw One UI `90000`, OS-visible memory and successful native `--version` only; see `docs/verification/native-s25.md`. Storage variant, model fit and inference remain unverified; launch software versions must not be presented as current installed versions.
- Selected model: `LiquidAI/LFM3-1.7B-GGUF`, file `LFM3-1.7B-QAD-Q4_0.gguf`.
- Source: https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/blob/main/LFM3-1.7B-QAD-Q4_0.gguf
- The source uses the mutable `main` branch. Record the actual revision, SHA-256, and size of the tested file before claiming a reproducible baseline.
- Model compatibility and memory fit are unverified. This baseline does not prohibit importing other GGUFs.
- CPU-only first; upstream WebUI, localhost binding (`127.0.0.1:8080`), no integrated downloads or model catalog.
- No custom chat UI, GPU/NPU work, tools, shell execution, MCP, LAN access, or charging/battery-saver automation.
- ADB may be used for development verification, but is not required for everyday use.

## Observed starting point

The current checkout contains `README.md`, `AGENTS.md`, licensing, and intent/idea documents; it has no Android source or Gradle build files. References to existing scaffold code in `AGENTS.md` are not evidence that those files exist here.

On 2026-07-19, `adb devices -l` returned no attached devices. Phone inspection and physical-device runtime checks are blocked until the phone is connected and authorized.
