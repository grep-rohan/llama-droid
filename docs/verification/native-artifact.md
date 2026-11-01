# Native Artifact Verification Evidence

## Archive Provenance

| Field | Value |
|---|---|
| URL | https://github.com/ggml-org/llama.cpp/releases/download/b12020/llama-b12020-bin-android-arm64.tar.gz |
| Release | b12020 |
| Source commit | `0ec4027a2a0183de87c007069adddc6e665f7de` |
| Archive size | 78,982,432 bytes |
| Archive SHA-256 | `64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec` |

## ELF Inspection (NDK llvm-readelf)

Tool: `D:/softwares/android-sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe`

### Common properties (all 14 allowlisted payloads)

| Property | Value |
|---|---|
| Class | ELF64 |
| Type | DYN (Shared object file) |
| Machine | AArch64 |
| Interpreter | `/system/bin/linker64` |
| PT_LOAD alignment | `0x4000` (16 KiB) |
| RPATH/RUNPATH | None |
| libc++_shared.so | Not a DT_NEEDED dependency |

### Tar member modes

Confirmed executable permissions in the archive:
- `llama-b12020/llama-server`: `-rwxr-xr-x` (0755)
- `llama-b12020/libllama.so`: `-rwxr-xr-x` (0755)

### DT_NEEDED dependency chains

```
llama-server (libllama-server.so)
  ├── libllama-server-impl.so
  ├── libllama-common.so
  ├── libmtmd.so
  ├── libllama.so
  │     ├── libggml.so
  │     │     └── libggml-base.so (leaf)
  │     └── libggml-base.so
  ├── libggml.so (redundant via libllama.so)
  ├── libggml-base.so (redundant via libllama.so)
  ├── libm.so (platform)
  ├── libdl.so (platform)
  └── libc.so (platform)

libllama-server-impl.so
  ├── libllama-common.so
  │     ├── libllama.so
  │     │     ├── libggml.so
  │     │     │     └── libggml-base.so (leaf)
  │     │     └── libggml-base.so
  │     ├── libggml.so (redundant via libllama.so)
  │     └── libggml-base.so
  ├── libmtmd.so
  │     ├── libllama.so
  │     │     ├── libggml.so
  │     │     │     └── libggml-base.so (leaf)
  │     │     └── libggml-base.so
  │     ├── libggml.so (redundant via libllama.so)
  │     └── libggml-base.so
  ├── libllama.so
  │     ├── libggml.so
  │     │     └── libggml-base.so (leaf)
  │     └── libggml-base.so
  ├── libggml.so
  │     └── libggml-base.so (leaf)
  ├── libggml-base.so (leaf)
  ├── libm.so (platform)
  ├── libdl.so (platform)
  └── libc.so (platform)

libllama-common.so
  ├── libllama.so
  │     ├── libggml.so
  │     │     └── libggml-base.so (leaf)
  │     └── libggml-base.so
  ├── libggml.so
  │     └── libggml-base.so (leaf)
  ├── libggml-base.so (leaf)
  ├── libm.so (platform)
  ├── libdl.so (platform)
  └── libc.so (platform)

libmtmd.so
  ├── libllama.so
  │     ├── libggml.so
  │     │     └── libggml-base.so (leaf)
  │     └── libggml-base.so
  ├── libggml.so
  │     └── libggml-base.so (leaf)
  ├── libggml-base.so (leaf)
  ├── libm.so (platform)
  ├── libdl.so (platform)
  └── libc.so (platform)

libllama.so
  ├── libggml.so
  │     └── libggml-base.so (leaf)
  ├── libggml-base.so (leaf)
  ├── libm.so (platform)
  ├── libdl.so (platform)
  └── libc.so (platform)

libggml.so
  ├── libggml-base.so (leaf)
  ├── libm.so (platform)
  ├── libdl.so (platform)
  └── libc.so (platform)

libggml-base.so
  ├── libm.so (platform)
  ├── libdl.so (platform)
  └── libc.so (platform)

libggml-cpu-android_armv8.* / libggml-cpu-android_armv9.* (all 7 variants)
  ├── libggml-base.so (leaf)
  ├── libm.so (platform)
  ├── libdl.so (platform)
  └── libc.so (platform)
```

### CPU backend libraries

All 7 CPU variant libraries share the same DT_NEEDED chain: `libggml-base.so` + platform libs.

| Member | APK name | SHA-256 |
|---|---|---|
| `libggml-cpu-android_armv8.0_1.so` | `libggml-cpu-android_armv8.0_1.so` | `b40796f5c25297dc0c5c45ebb0493551a7339d5f0d94a3c3283266922edc5efe` |
| `libggml-cpu-android_armv8.2_1.so` | `libggml-cpu-android_armv8.2_1.so` | `d75114220b6e98e69e393bd83ad0deedc5f683d6e0a5d558d14fee852d879db8` |
| `libggml-cpu-android_armv8.2_2.so` | `libggml-cpu-android_armv8.2_2.so` | `92e86f5f2094e329043cba6e584aeb292b75a8005d023d948e82968872f7431e` |
| `libggml-cpu-android_armv8.6_1.so` | `libggml-cpu-android_armv8.6_1.so` | `d590576aa30bc9f2aad62173db8176a361e510e095bade9fbff6f8ffe057e3d2` |
| `libggml-cpu-android_armv9.0_1.so` | `libggml-cpu-android_armv9.0_1.so` | `ca5b0f2d6aa03c0afe8a24102028bb90185d4675e6735ce995fa36946cc60a76` |
| `libggml-cpu-android_armv9.2_1.so` | `libggml-cpu-android_armv9.2_1.so` | `5a9c63641ce54a7f530ad0c7f67ed762362bbd19a68f61eb6e0e80d1f9a9d1b6` |
| `libggml-cpu-android_armv9.2_2.so` | `libggml-cpu-android_armv9.2_2.so` | `d61441c3efbfcc3586bf15f1084209c4aed593fdc6d683b48241893537cc785c` |

### Embedded WebUI evidence (libllama-server-impl.so)

NDK `llvm-nm` output confirms embedded UI symbols:
- `llama_ui_use_gzip` — gzip compression support for UI assets
- `llama_ui_find_asset` — asset lookup by name
- `llama_ui_get_assets` — asset enumeration
- `_ZL7asset_0`, `_ZL7asset_1`, `_ZL7asset_2`, `_ZL7asset_3` — embedded asset data sections

No separate WebUI asset files exist in this archive; all UI content is compiled into `libllama-server-impl.so`.

## Excluded artifacts

The following archive members are **not** packaged in the APK:

| Category | Members |
|---|---|
| RPC backend | `libggml-rpc.so`, `ggml-rpc-server` |
| CLI executables | `llama`, `llama-batched-bench`, `llama-bench`, `llama-cli`, `llama-completion`, `llama-fit-params`, `llama-gemma3-cli`, `llama-gguf-split`, `llama-imatrix`, `llama-llava-cli`, `llama-minicpmv-cli`, `llama-mtmd-cli`, `llama-mtmd-debug`, `llama-perplexity`, `llama-quantize`, `llama-qwen2vl-cli`, `llama-results`, `llama-tokenize`, `llama-tts` |
| Build impl libraries | `libllama-batched-bench-impl.so`, `libllama-bench-impl.so`, `libllama-cli-impl.so`, `libllama-completion-impl.so`, `libllama-fit-params-impl.so`, `libllama-perplexity-impl.so`, `libllama-quantize-impl.so` |

## Uncertainties

1. **Device page size:** All sampled ELFs have 0x4000 (16 KiB) alignment. The actual device page size must be verified via `adb shell getconf PAGE_SIZE`.
2. **CPU feature compatibility:** All 7 CPU variants are packaged; no trimming based on SoC is performed. Actual runtime selection is determined by the llama.cpp backend loader.
3. **Android target SDK W^X restrictions:** The executable-as-`.so` packaging approach must be validated on the target device.
4. **No inference claim:** ELF inspection does not prove model loading, inference, or WebUI operation.

## Verification commands

```bash
# Archive provenance
curl --fail --location --retry 3 --output .cache/llama-b12020-bin-android-arm64.tar.gz \
  https://github.com/ggml-org/llama.cpp/releases/download/b12020/llama-b12020-bin-android-arm64.tar.gz
printf '%s  %s\n' \
  64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec \
  .cache/llama-b12020-bin-android-arm64.tar.gz | sha256sum --check --status

# ELF inspection (Windows Git Bash)
READELF="$ANDROID_SDK_ROOT/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe"
"$READELF" -h -l -d app/src/main/jniLibs/arm64-v8a/libllama-server.so

# UI symbols
NM="$ANDROID_SDK_ROOT/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-nm.exe"
"$NM" --defined-only app/src/main/jniLibs/arm64-v8a/libllama-server-impl.so | grep -E 'ui|asset|gzip'

# Git exclusion verification
git status --short --ignored
```
