# llama.droid — Reliable Single-Model Baseline

## Problem Statement
How might we make local chat dependable on the owner's Galaxy S25 without clutter, integrated downloads, or fragile model loading?

## Recommended Direction
Build a thin Android wrapper around upstream `llama-server` and its built-in WebUI. Keep the controller small, but make importing, loading, readiness, stopping, and failure understandable.

Validate one exact LFM3-series QAD Q4_0 GGUF with conservative CPU settings first. This is a tested baseline, not an artificial restriction against importing other models. Reliability takes priority over peak prompt-processing or token-generation speed.

## Key Assumptions to Validate
- [ ] Bundled native server launches and hosts its WebUI — verify packaged ELF dependencies and physical-device startup.
- [ ] The exact GGUF is supported and fits available memory — load it and generate a response on the S25.
- [ ] Stop/restart reliably releases resources — repeat cycles, including stopping during loading.
- [ ] Failures remain recoverable — test invalid imports, loading failures, and unexpected process exits.

## MVP Scope
- Import a browser-downloaded GGUF through Android's Storage Access Framework.
- Start CPU inference with conservative settings.
- Show explicit lifecycle states and useful failure information.
- Chat through upstream WebUI, bound to localhost only.
- Reliably stop and restart, without Termux, root, or a computer for everyday use.
- Record the exact model, device/software version, settings, and test results.

## Not Doing (and Why)
- **Downloader or model catalog** — the browser already handles acquisition.
- **Custom chat UI** — preserve upstream functionality and reduce maintenance.
- **GPU/NPU or peak-speed tuning** — reliability comes first.
- **Broad compatibility claims** — one verified baseline before expansion.
- **Charging/saver automation** — establish reliable lifecycle behavior before adding policy. Future configuration should account for charging status and Android's normal/battery-saver state, not an app-defined performance preset; exact behavior remains undecided.
- **Tools, shell execution, or LAN access** — outside the intended security boundary.

## Confirmed Baseline
- Selected GGUF: `LFM3-1.7B-QAD-Q4_0.gguf` from https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/blob/main/LFM3-1.7B-QAD-Q4_0.gguf. Compatibility remains unverified; record the tested revision and SHA-256.
- Base Galaxy S25 published specifications: Snapdragon 8 Elite for Galaxy, 12 GB RAM, 128/256/512 GB storage variants; launched with Android 15 / One UI 7.0. Sources: https://www.sammobile.com/samsung/galaxy-s25/ and https://www.gsmarena.com/samsung_galaxy_s25-13610.php

## Open Questions
- What Android/One UI version is currently installed on the owner's S25, and how much memory is available during inference? Published launch specifications do not answer current device state. This does not block initial specification or planning.

## Verification Boundary
Direction confirmed by the user. This document records intent, not implementation or test evidence. Compilation, native startup, model compatibility, and physical-device behavior remain unverified.
