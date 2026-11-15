# Physical S25 native-runtime verification

## Outcome — retained-installation retest passed

**Original native smoke test passed: 25/25 instrumentation cases on the owner's physical
Samsung Galaxy S25.** The real pinned upstream server exited 0 under the target
app UID in 101 ms; stream EOF/reaping/worker cleanup checks passed. This is
version-command evidence only, not model loading, inference or WebUI evidence.

**Current result:** after the owner confirmed there was no prior app data and
approved a fresh install, both audited APKs were installed and the full suite
rerun with direct `am instrument`: **25/25 passed**, real exit **0 in 102 ms**.
Both packages remained installed with unchanged paths, UIDs and audited APK
hashes before/after; no target-UID processes remained. T9 is verified;
Checkpoint C still requires human review. See the retained-installation section below.

**Historical unexpected test-tool cleanup:** after Gradle's `connectedDebugAndroidTest`
finished, both the target and test packages were absent. AGP/UTP has an
`uninstallAfterTest` installer configuration; this run removed both packages.
Uninstall can remove private app data. The agent's earlier statement that
replacement installs would preserve data did not account for framework cleanup
and was incorrect for the complete procedure. The agent made no pre-run private-data
inventory or backup; at discovery, prior contents, external backups and actual
loss were unknown.
No data recovery is claimed. Device writes were paused after discovery until
the owner confirmed: **"there was no data, fresh install ok"**. That confirmation
resolved the data-impact decision and authorized the fresh-install/direct-test
sequence; it does not retroactively make Gradle cleanup data-preserving.

**Checkpoint C/module completion awaits human review.** Use owner-approved
installs followed by direct `am instrument`, not a repeat of the uninstalling
Gradle task on a data-bearing installation. The retest verified installed APK
identities before and after testing without framework removal.
Do not uninstall, downgrade or clear app data to resolve an installation issue
without separate owner approval. Pairing codes, connection endpoints, device
GUID and unique serial are deliberately excluded from this document and Git.

## Tested device (device-reported values)

| Field | Observed value |
|---|---|
| Manufacturer / model | samsung / **SM-S931B** (Galaxy S25 base) |
| ABI list | `arm64-v8a` |
| Android / API | **17 / 37** |
| Security patch | `2026-10-05` |
| One UI property | `ro.build.version.oneui=90000` (raw; no guessed translation) |
| Build fingerprint | `samsung/pa1qxins/pa1q:17/CP2A.260605.016/S931BXXSDDZJ1_OXMDDZJ1:user/release-keys` |
| Emulator property | `ro.kernel.qemu=0` |
| OS-visible memory | `/proc/meminfo`: **MemTotal: 11379964 kB**; not marketed RAM or available-memory/model-fit proof |
| Device page size | **4096 bytes**; no 16 KiB-device compatibility claim |
| Access | Owner-authorized wireless ADB; exactly one connected/authorized target selected explicitly |

No root, Termux, copied writable-storage executable, model or external inference
service was used. Only the informational `--version` command was launched.
ADB development transport is not an app LAN-listening feature.

## Source and artifacts

Functional source: **`955bfe3f58cf515943f3ebfa6b76748dcc11092d`**, built from the
retained clean detached worktree `D:/workspace/projects/llama-droid-native-runtime-clean`.
The code/host reproduction is detailed in [native-build.md](native-build.md).

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Target `app-debug.apk` | 79,208,764 | `19c39bfbb8315d8deb0de5efab9b7f1ffe2b9c68f5306907e6c6b2086afba2b1` |
| `app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |
| Upstream CPU ARM64 archive b12020 | 78,982,432 | `64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec` |

Both APKs were rehashed locally, replacement-installed successfully, and the
initial installed `base.apk` files were hashed through ADB and matched the
table. App/test UIDs at that point were **10436 / 10058**. Existing target was
versionCode 1/versionName 0.1.0; neither `-d`, explicit uninstall nor data-clear
was issued by the driver. The later Gradle task reinstalled from these unchanged
local artifacts (packaging tasks were up-to-date), then uninstalled them during
cleanup. Local APK hashes remained unchanged after the run. A final installed
`base.apk` hash could not be collected because both packages had been removed;
this limitation and data-loss risk are not hidden by the passing test result.

During the actual test, every one of the fourteen installed payloads was hashed
under the target app context and matched `third_party/llama.cpp.json`. Thus the
executed native boundary was tied to the pin independently of the earlier
ADB APK hash check. The test provenance asset belongs only to the test APK;
no probe, fake case or instrumentation hook is in the user APK.

## Execution evidence

Selected sanitized log excerpts (device log marker `11-15 11:25`; no year inferred
from that short logcat timestamp):

```text
Target uid=10436 parentPid=17598 model=SM-S931B API=37
Installed nativeLibraryDir=/data/app/~~J5CEUqOV6GtBZA83LejTAQ==/io.github.greprohan.llamadroid-thueBspz8S7a2JN9JmZb1A==/lib/arm64
Installed libllama-server.so bytes=7192 mode=755 fileOwnerUid=1000 executable=true
Version result exit=0 timeout=false reaped=true readersStopped=true streamsDrained=true operationsStopped=true exitMs=101 totalMs=116
stdout bytes=0 tail=
stderr bytes=95 tail=version: 0.8.0-dev (build 12020, commit 0ec4027a2)
built with Clang 21.0.0 for Android aarch64
```

The executable path is that native directory plus `/libllama-server.so`, not a
source-stage path. All fourteen logged payloads had **0755**, file-owner UID
**1000**, readable/matching bytes and executable=true. File owner UID is not the
child's UID: instrumentation asserted current UID == target application UID
**10436**, and direct `ProcessBuilder` execution inherited the app context.
No privilege-changing wrapper or shell was involved in server launch.

The single timing observation follows installed-file hash checks, which can
warm the page cache. It is not a cold-start, model-load or generation benchmark.
The upstream producer compiler is Clang **21.0.0** as printed by the binary;
the local NDK LLVM **18.0.3** was used for inspection, not rebuilding it.

The earlier broad Android-17/`LD_LIBRARY_PATH` failure claim was **not reproduced
with this exact APK/context/device**. The unmodified fourteen-file payload,
trusted installed-directory working directory and controlled library path
worked; no monolithic merge, ELF patch or security weakening was needed.
Do not generalize this result to all Android builds, other devices or backends.

## Test results and cleanup cross-check

Command actually executed in the clean worktree (target connection identifier
provided privately, never published):

```bash
./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
```

**This is a historical command, not a safe data-preserving recommendation.**

- Gradle exit **0**, `BUILD SUCCESSFUL in 41s`, 67 tasks (2 executed,
  65 up-to-date).
- Instrumentation XML: **25 tests, 0 failures, 0 errors, 0 skipped**;
  reported suite time **2.926s**.
- Case classes checked directly: **24 NativeProcessProbeTest cases on ART**
  plus **1 NativeVersionSmokeTest real native case**. These are not merely
  compiled instrumentation or host fake results.
- Native test required normal exit 0 before the 30s deadline, EOF on both
  concurrently drained streams, reaping and stopped readers/control workers,
  exact build/commit header, and `child.isAlive == false` before test completion.
- `ps -A -o UID,PID,PPID,NAME`, filtered to target UID **10436**, showed no
  processes before/after. The post-run check occurred after framework cleanup,
  so the in-test reaping/aliveness assertions are the primary evidence of
  voluntary child completion, not an inference from package removal.

After the run, explicit package-list checks confirmed both target/test packages
were **not installed**. This is why no post-run installed-APK verification or
app-data-preservation claim is made. Read-only SDK cache inspection found the
pinned AGP UTP installer's `setUninstallAfterTest` paths; no undocumented Gradle
option was silently applied to change behavior. Future verification should
avoid this cleanup path and explicitly preserve the target package/data.

## Retained-installation retest — owner-approved fresh install

The owner confirmed no prior app data and approved fresh installation. Exactly
one authorized physical S25 was selected privately; properties matched the table
above. Both packages were absent before installation. The retained clean
worktree was Git-clean at `955bfe3`; the target ZIP/payload audit passed again,
and both local APK sizes/SHA-256 values matched the artifact table. No build,
native transformation, release change or production source edit was needed.

Actual commands, with the private device selector omitted:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r io.github.greprohan.llamadroid.test/androidx.test.runner.AndroidJUnitRunner
```

Both installs reported `Success`; no uninstall, downgrade or data-clear command
was issued. This run did **not** invoke Gradle/UTP. Installed `base.apk` hashes
were verified after installation and immediately before/after the test:

| Package | UID | Before/after SHA-256 |
|---|---:|---|
| `io.github.greprohan.llamadroid` | 10437 | `19c39bfbb8315d8deb0de5efab9b7f1ffe2b9c68f5306907e6c6b2086afba2b1` |
| `io.github.greprohan.llamadroid.test` | 10439 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Package paths and UIDs were also unchanged before/after. The test package is
intentionally retained; no test-APK cleanup or further phone changes are implied.
This fresh-install run does not prove preservation of existing app data.

Runner output reported **`OK (25 tests)`**, time **2.321 s**, final
`INSTRUMENTATION_CODE: -1` (successful instrumentation completion). Raw status
records were checked for 25 unique matched start/pass pairs, **24**
`NativeProcessProbeTest` cases and **1** `NativeVersionSmokeTest`, with no
failure/error/skip events. This is a new direct-run result, not reused Gradle XML.

Sanitized native excerpts (device log marker `11-15 12:05`; no year inferred):

```text
Target uid=10437 parentPid=25303 model=SM-S931B API=37
Installed nativeLibraryDir=/data/app/.../lib/arm64
Version result exit=0 timeout=false reaped=true readersStopped=true streamsDrained=true operationsStopped=true exitMs=102 totalMs=102
stdout bytes=0 tail=
stderr bytes=95 tail=version: 0.8.0-dev (build 12020, commit 0ec4027a2)
built with Clang 21.0.0 for Android aarch64
```

All fourteen installed payload hash assertions passed again; all logged modes
were **0755**, file-owner UID **1000**, executable=true. The test asserted app UID
**10437**, normal bounded exit, EOF, reaping, stopped workers and no live child.
Separate UID-filtered `ps -A -o UID,PID,PPID,NAME` checks were empty before/after,
this time with both packages still installed. No model or listener was started.

**T9's retained-installation/operational review is satisfied.** Only the explicit
Checkpoint C human review remains before module completion; inference, model
compatibility, services and WebUI remain outside this evidence.

## Local evidence and remaining action

Sanitized ignored evidence in the retained clean worktree `.cache/`:

- `blocker6-device-properties.json`, initial replacement-install logs and
  `blocker6-installed-artifacts.json` (pre-Gradle identity checks)
- `blocker6-connected.log`, `blocker6-instrumentation-summary.json`,
  `blocker6-logcat.log`, `blocker6-ps-{before,after}.log`
- SDK/UTP cleanup inspection outputs `blocker6-agp-*.log`

Generated Gradle reports under `app/build/` are ignored and may contain local
transport metadata; do not commit or publish them unredacted. No credentials,
serials, host IP/port, reports or binaries are inputs to the repository build.

New sanitized ignored evidence uses the `.cache/blocker6-direct-` prefix:
`preflight.json`, `install-{target,test}.log`, `installed.json`, `before.json`,
`after.json`, `instrumentation.log`, `summary.json`, `logcat.log`,
`log-start.txt` and `ps-{before,after}.log`. The local-only
`verify_s25_direct.py` driver never invokes Gradle, uninstall or data-clear.
The older `verify_s25.py run` action invokes the unsafe historical Gradle task;
do not reuse it as a retained-installation test.

**Next owner decision:** review Checkpoint C and the seven-criterion trace in
[tasks/todo.md](../../tasks/todo.md#checkpoint-c-native-runtime-completion-review).
Native startup success is not model compatibility, foreground-service
reliability, inference, WebUI hosting or permission to start those modules
without their separate approval gates.
