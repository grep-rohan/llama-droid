# Build Instructions

> **Isolated branch: blockers 1–4 host work verified.**
> On `fix/native-runtime`, 94 host tests (50 importer + 44 APK auditor),
> 19 JVM tests and 12 opt-in Gradle/APK integration checks pass. A clean native debug APK build/lint
> passes; packaged payload hashes, attribution and SDK manifest were checked.
> Installed extraction/permissions/linking and S25 execution remain unproven.
> The original dirty checkout and stage remain untouched. Instrumentation
> and clean-checkout/device evidence remain pending. See [ordered blockers](../tasks/todo.md#blockers-in-precedence-order)
> and [branch evidence](verification/native-build.md). No native execution
> or WebUI success is claimed.

## Prerequisites

Installation entries below reflect previously documented host setup,
not a fresh SDK inventory. The blocker-1 build reverified Gradle 8.10.2
and Temurin JDK 17.0.14; compiler/lint output confirms the minimal APK build.

### Java

| Requirement | Current status |
|---|---|
| JDK 17 (full, with `javac`) | **Installed** — Temurin 17.0.14 at `D:\softwares\jdk\jdk-17.0.14+7` |
| `JAVA_HOME` set to JDK 17 root | **Required** — set before building |

### Android SDK

| Requirement | Current status |
|---|---|
| SDK command-line tools at `D:\softwares\android-sdk\cmdline-tools\latest` | **Present** |
| Platform `android-35` | **Installed** |
| Build tools `35.0.0` | **Installed** |

### NDK (for artifact inspection only)

| Requirement | Current status |
|---|---|
| NDK r27 (27.2.12479018) at `D:\softwares\android-sdk\ndk\27.2.12479018` | **Installed** |
| `llvm-readelf` / `llvm-nm` | **Available** at `D:\softwares\android-sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin\` |

The approved plan uses the NDK for ELF inspection (T1), not native
compilation. The original checkout's CMake/JNI/merge additions were not
copied into this branch. Its NDK discovery failure and unvalidated merge
are historical diagnostics, not requirements to build this branch's
minimal APK. Do not enable that original merge merely by setting
`ANDROID_NDK_HOME`; review any native transformation separately.

### Gradle Wrapper

The project ships an official Gradle 8.10.2 wrapper:

| File | Source | SHA-256 |
|---|---|---|
| `gradlew` | `gradle/gradle@v8.10.2` | — |
| `gradlew.bat` | `gradle/gradle@v8.10.2` | — |
| `gradle/wrapper/gradle-wrapper.jar` | `gradle/gradle@v8.10.2` | `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046` |
| Distribution ZIP | `services.gradle.org` | `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26` (in `gradle-wrapper.properties`) |

The wrapper validates the distribution SHA-256 automatically via `distributionSha256Sum` in `gradle-wrapper.properties`.

## Staging native payloads (T4 → T5)

Obtain the pinned archive using the [plan's acquisition commands](../tasks/plan.md#exact-artifact-and-verification-commands),
then stage it (the archive may be outside the worktree; it is read-only input):

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only
```

The verified stage is fourteen byte-preserved files in
`app/src/main/jniLibs/arm64-v8a/` plus `.payload_receipt.json`. The importer
never downloads, merges, strips or relinks. It checks the archive size/hash
before copying, scans all tar entries for duplicates/unsafe paths/links/
special files, copies only the explicit flat APK names, checks each digest
and ELF64 little-endian AArch64 ET_DYN header, then writes a versioned
receipt containing archive identity, release/commit and payload mapping.
The isolated stage was successfully produced and reverified with the real
pinned archive; all fourteen digests remain unchanged.

### Failed/interrupted staging and safe retry

The importer prepares a complete temporary sibling candidate and receipt
before renaming directories. An OS lock excludes concurrent imports;
the persistent `.arm64-v8a.lock` file is normal and must not be deleted
while a process might hold it. A `.arm64-v8a.importing` intent marker or
`.arm64-v8a.previous` backup makes verification fail closed, even if the
old payload bytes are intact. The marker is cleared only after complete
successful publication. Copy/receipt/publish failures preserve or restore
old bytes where possible; failure to roll back is reported with the backup
path. Missing archives and Ctrl+C also leave the stage unaccepted.

**Recovery:** fix the reported cause, then rerun the full import with the
pinned archive and `--verify-only`. Do not remove a marker or forge a receipt
to bypass the failure. A successful retry recovers an interrupted backup
rename and replaces the stage. Abandoned `.arm64-v8a.candidate-*` siblings
from a hard exit are never accepted as the stage; remove only those known
generated folders after confirming no import is active, if disk cleanup is
needed. Never clean the original checkout or unrelated user files.

`--verify-only` checks transaction state both before and after its read,
requires exactly the allowlisted regular files and strict receipt, rejects
links/junctions/unexpected directories, and recomputes hashes/ELF headers.
It does not create, extract, download, repair or acquire a writer lock.
Exit codes: 0 success, 1 verification/import failure, 2 invalid CLI usage,
130 interrupted CLI. Use `--staging-dir DIR`, not a positional directory
with `--verify-only`. Ordinary process interruption was tested; physical
power-loss durability and Android native execution were not.

### Fail-closed Gradle packaging

`verifyNativeStage` now runs the read-only verifier on every Android build,
including unchanged builds and direct `:app:packageDebug` invocations. It
also rejects missing/empty attribution sources. Python defaults to `python`;
select an installed interpreter with `-PnativeVerifier.python=<executable>`.
Missing Python or a rejected stage fails the build with a diagnostic, never
a fallback/no-op. The explicit verification interface is:

```powershell
python scripts/import_llama_release.py --verify-only --staging-dir app/src/main/jniLibs/arm64-v8a
```

After verification, `stageVerifiedNative` syncs only the fourteen `.so`
files to `app/build/generated/verifiedNative/arm64-v8a/`, which is the
exclusive JNI packaging input. Receipts, backups, transaction candidates
and sibling ABIs are not packaging inputs. Generated output tampering is
detected by Gradle and repaired from the verified source; a bad original
stage fails even if an older APK already exists.

`generateAttributionAssets` syncs `third_party/llama.cpp-LICENSE` and
`third_party/NOTICE.md` to `app/build/generated/attributionAssets/` as
`THIRD_PARTY_LICENSE.txt` / `THIRD_PARTY_NOTICES.txt`. Nothing is copied into
source assets manually. Task-provider source directories plus `preBuild`
dependencies wire verification/sync before native and asset consumers.

`useLegacyPackaging = true` compresses native ZIP members and AGP generates
`extractNativeLibs=true`; `keepDebugSymbols` preserves payload bytes. Do not
set the extraction flag redundantly in the source manifest (AGP warns).
All fourteen native ZIP-member digests and both attribution asset bytes
were compared with their pinned/source inputs. The real packaged manifest
was inspected with SDK `apkanalyzer`, not UTF-8 decoding of binary XML.
These are packaging/configuration checks, **not installed extraction,
executable permission or Android linker/process proof** (T9 remains blocked).

### Opt-in real packaging integration checks

After configuring JDK/SDK environment variables and staging the genuine
archive, run:

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
$env:ANDROID_HOME = 'D:\softwares\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
python scripts/tests/check_native_packaging.py -v
```

This twelve-test suite copies a disposable project/stage under `.cache/`,
never mutates the genuine stage, and runs actual Gradle builds. It checks
missing/tampered/extra stage content, missing receipt, incomplete import,
missing Python/notice, direct package-task bypass, unchanged-build checking,
exact APK hashes/attribution and repair/exclusion of generated/sibling data.
A red baseline build succeeded without the stage; all twelve checks pass
after implementation. Logs: `.cache/blocker3/*.log`. It is separate from
fast `test_*.py` discovery (50 tests); do not call either zero-test success
or a static Gradle-text match packaging verification. Linux execution is
not established; these integration checks ran on the documented Windows host.
Do not import/modify the stage concurrently with a build; this suite does
not claim a lock spanning verification, copying and Android packaging.

## Building

Create `local.properties` with your SDK path (host-specific, ignored by Git):

```powershell
echo 'sdk.dir=D:/softwares/android-sdk' > local.properties
```

Then build:

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
./gradlew --no-daemon clean :app:assembleDebug
```

The blocker-3 clean build produced
`app/build/outputs/apk/debug/app-debug.apk`; inspection confirms:

- 14 native `.so` files (launcher + 6 libraries + 7 CPU backends) in `lib/arm64-v8a/`
- `assets/THIRD_PARTY_LICENSE.txt` and `assets/THIRD_PARTY_NOTICES.txt`
- No RPC backend, CLI tools, or unrelated payloads

## Verifying the wrapper

```powershell
# Wrapper version check
./gradlew --version
# Expected: Gradle 8.10.2, Kotlin 1.9.24, Launcher JVM 17

# Java version check
java -version
# Expected: openjdk version "17.0.x"

# SDK components
& "D:\softwares\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat" --list_installed
# Expected: platforms;android-35, build-tools;35.0.0

# NDK tooling
& "D:\softwares\android-sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe" --version
# Expected: LLVM version 18.x
```

## Testing (T6)

The launcher JVM tests now run on the host without executing an ARM64
ELF. The focused suite passes **19 tests, zero failures/errors/skips**:

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*NativeServerLauncherTest*'
```

Coverage includes trusted path/argument/environment construction,
missing payloads (including every dependency/backend), permission decisions,
start failures and caller-owned streams. Fixture files are never executed;
permission decisions/process creation are injected internal test seams,
not proof of Android extraction, permissions or linking.

## Instrumentation smoke test (T7)

The intended smoke test uses `NativeProcessProbe` to drain output with
bounded tails, enforce a 30-second deadline and verify child cleanup.
The original untracked instrumentation was not copied. A reviewed harness
must be implemented after native packaging; this branch's launcher/layout
now compile. Fake-Process source/compilation is not a passing device test.

### Compile instrumentation APK

```powershell
$env:JAVA_HOME = 'D:\softwares\jdk\jdk-17.0.14+7'
./gradlew --no-daemon :app:assembleDebugAndroidTest
```

### Run on device

Connect an authorized ARM64 device (Galaxy S25), then:

```powershell
./gradlew --no-daemon :app:connectedDebugAndroidTest
adb logcat -d -s llama.droid
```

**Blocked by host prerequisites and no connected/authorized device.**
`adb devices -l` listed no devices in the documentation review. If multiple
devices are present later, explicitly select only the S25 for ADB and
Gradle (see the task plan). Compilation alone does not execute these tests.

## APK audit (T8)

The standalone `scripts/verify_apk.py` is implemented and the real native
APK passes. It is read-only: no extraction, mutation, download, native
execution or binary-XML text decoding. The CLI uses committed provenance
and attribution sources, with no alternate/size-only monolithic mode.
Always inspect the binary manifest separately with SDK `apkanalyzer` as
shown in [the build evidence](verification/native-build.md); that separate
check was repeated against the same audited APK/hash.

```powershell
python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Exit 0 means **ZIP/payload scope passed**, not Android compatibility. The
CLI prints the audited size/SHA-256 and explicitly says SDK manifest,
signing and device checks are not verified by this tool. Exit 1 reports
bounded diagnostics; invalid CLI usage exits 2.

Checks:
- Exact allowlisted ARM64 native names, pinned hashes, ELF header sanity and
  legacy DEFLATE compression; no missing/extra backend or ABI. A single
  oversized launcher never enables a merge mode.
- Attribution assets byte-identical to nonempty `third_party/` sources;
  nonempty `AndroidManifest.xml` present, but its contents are not decoded.
- Duplicate/case-aliased/NUL/unsafe paths, links/special records, staging
  controls, excluded utility names and native files outside the allowlist.
- GGUF suffixes and renamed-member GGUF/ELF magic, regular-file CRCs and
  declared sizes, encryption/unsupported compression and directory records.
- Standard, non-prefixed single-disk ZIP only; ZIP64 is rejected. Preflight
  bounds metadata before ZIP parsing: APK <=512 MiB, directory <=8 MiB,
  <=10,000 entries; streaming limits are 256 MiB/member and 1 GiB total
  uncompressed. Attribution sources <=1 MiB; at most 100 findings reported.

These limits fit the inspected pinned APK (30 entries, 269,605,833 bytes
uncompressed). They are not broad APK compatibility or malware guarantees.
Content checks inspect directory-listed member names/signatures, not nested
archives, arbitrary obfuscation or APK signing-block contents. Do not modify
the APK during audit; tie separate SDK/device evidence to its printed hash.

Fast auditor tests (44); full host discovery now runs 94 tests:

```powershell
python -m unittest discover -s scripts/tests -p 'test_verify_apk.py' -v
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

The twelve opt-in packaging tests also invoke the shipped auditor CLI on
real APKs built inside disposable projects. No SDK/device execution is
inferred from synthetic ZIP fixtures or from the auditor's success.

## Unresolved prerequisites

The minimal Views/direct-process launcher source set is now isolated and
host-verified. Safe staging and fail-closed packaging are verified against
fixtures and the pinned archive/native APK, and the standalone auditor
passes with separate SDK manifest evidence. Current packaging/launcher
review, instrumentation and complete clean-checkout reproduction are still
required before device testing. The physical S25 must be
connected and authorized. Tool installations alone do not remove these
blockers; see the [precedence list](../tasks/todo.md#blockers-in-precedence-order).
