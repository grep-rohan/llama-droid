# Model Import — Verification Evidence

## Emulator live-copy result

**2026-11-18: 4 GiB SAF import and cleanup verified; live progress/heap series incomplete.** The owner approved one replacement of the synthetic fixture on `emulator-5554` (qemu=1). The runner checked the prior selection and its 64-byte ownership prefix, APK/UID, and free space (10,871,582,720 bytes available; 8,657,043,456 required). A fresh event listener armed before the single source tap.

| Check | Observed result |
|---|---|
| Import and commit | The first bound candidate was **223,694,848 bytes** while the old payload was still selected. After observer teardown (expected exit 124), the new authoritative selection referenced that same inode and model ID at **4,294,967,296 bytes**. Source name, prefix, displayed size, store epoch and process incarnation matched. This verifies promotion, not full-body integrity or inference. |
| Live progress and heap | **BLOCKED.** A later snapshot failed the strictly-partial size guard. The failing size and any intervening progress events or heap intervals were not saved; the first candidate stat is not a UI progress reading. There is no verified series across 2 GiB or heap bound from this run. This does not demonstrate an app failure. |
| Cleanup | The app showed `IMPORTED` with one selected payload, no candidate or pending picker. Only the generated external source was deleted. The **new private synthetic fixture remains selected** (inode 1024002, replacing 1024047); APK/UID and rotation were unchanged. |

Evidence: `.cache/model-import/mi-21-emulator-live-progress-20261118T031102Z/` — `live-progress/first-partial.json`, `live-progress/commit-proof.json`, `result.json`, `fixture-cleanup.json` and `batch-audit.json`. The postflight proof does not attest external cleanup; the cleanup receipt does. The overall result remains `BLOCKED`. The host audit matched all 45 parent evidence files and 41 owner-file hashes before tracker bookkeeping. The changed cached setup passed 15 leave, 15 progress and 24 restart guards; no app or build inputs changed.

No second import, S25 access, Cancel retest or MI-22 test. Next: preserve each verified partial measurement and the size that triggered rejection, so a terminal snapshot cannot discard earlier evidence. Test that on the host before seeking another run. Full MI-21 remains open.

## Committed-copy postflight — host PASS, current selection read-only check PASS

Added `scripts/model_import_postflight.py` and host tests in `d4f5615`, `316da59`,
and `dcca715`. The postflight reads only after observer exit124 and a captured
candidate binding. It requires the same candidate inode and model ID in the new
selection, exact scoped source name/size/64-byte prefix, unchanged root, epoch,
APK/UID and process incarnation, one selected payload with no pending nonce or
extra entries, stable inventory across the reads, and matching idle UI name/size.
It makes no claim about model-body integrity, external source cleanup, or live
progress/heap. It does not authorize any mutation.

**24 focused / 250 full Python tests PASS**. Synthetic positive and negative
cases cover record bounds, candidate identity, cleanup state, UI, and callback
deadline overruns. Logs: `.cache/model-import/committed-postflight-host/`.
App/build inputs unchanged; Gradle not rerun.

A separate read-only `emulator-5554` (qemu=1) probe decoded the actual existing
144-byte selection and matched its visible name and 4,294,967,296-byte size.
`record-probe-result.json` is **PASS_CURRENT_RECORD_AND_UI_DECODE_ONLY**. The
selected PROGRESS fixture, APK/UID, process/Activity, rotation and prior ledgers
were preserved. No import, source generation, reset, app input, S25 access or
MI-22 action. No active-copy promotion or in-flight progress/heap result was
produced. The next runtime step requires separate scope and fresh reconciliation
before replacing the emulator's selected synthetic fixture.

## Previous status — real callbacks implemented; actual idle checks PASS — 2026-11-18

Owner requested the next slice; **no new import**. Added
`scripts/model_import_live_callbacks.py` and its tests (commit `4d499b5`):

- Actual `/proc` process-owner/start-tick and meminfo parsing; each callback
  checks PID/UID/start ticks before/after its reads. Snapshot reuses the supplied,
  validated read-only inventory adapter on the **same fixed Emulator object**;
  every underlying ADB command is clamped to the caller's absolute deadline.
  No physical selector/workflow is invoked from the historical inventory module.
- Snapshot checks inventory twice, preserving root/legacy state, old payload full
  stat and selection SHA/content metadata. Legitimate selection inode/mtime and
  directory mtime changes are not mistaken for payload changes. Unknown/extra
  entries, links, unsafe types, incomplete/completed/replaced/rewound candidates,
  changed records/roots/processes reject. Device method restoration is checked
  on failures; these callbacks require serial use of that Emulator object.
- Positive candidate binding requires a separately scoped fixture-prefix digest,
  a unique exact generated candidate path matching inventory name digest, and a
  bounded **64-byte** prefix read. This proves that prefix/metadata, not model-body
  integrity or an atomic no-follow descriptor lease. No prefix supplied means
  idle-only mode: an unexpected candidate rejects without prefix reading.
- Host fixtures exercise positive snapshot → meminfo → snapshot against the
  existing heap guard. They are explicitly synthetic, **not** an Android copy.
  Initial fail-closed stubs rejected required valid paths; meaningful guard
  assertions and runtime-framing regression are retained. Final **21 callback /
  226 full Python tests PASS**, logs `.cache/model-import/live-callbacks-host/`.

The first actual idle callback probe correctly blocked on `expected process UID
required`. Same-case read-only diagnosis proved the first UID line had a trailing
CR and matched the expected UID after framing removal; fixture/APK/UID/PID/UI/
rotation remained intact. `7c042f2` removes exactly that observed trailing CR,
without relaxing numeric UID/process identity. Original BLOCKED result and the
postflight/diagnosis remain unchanged; the fresh compatibility probe is separate:

`.cache/model-import/live-callbacks-compatible-probe/idle-probe-result.json`
**PASS_ACTUAL_CALLBACKS_IDLE_ONLY**. A fresh observer armed, the real callback
returned no candidate, and collection refused before sampling meminfo
(**snapshot1 / memory0**), with observer exit124 and both objects closed. A
**separate idle** meminfo callback then succeeded (0.293 s in this one observation),
with process-incarnation checks. That timing is not a live-copy performance bound.
Exact current PROGRESS fixture/stat/record/64-byte prefix, APK/UID, PID/Activity,
UI and rotation preserved; prior ledgers/other40 owner hashes match. No source,
fixture, app input/settings/stop/install, S25, Cancel or MI-22 action. No app/build
changes, unchanged Gradle run, in-flight heap or full MI-21 acceptance claim.

**Next:** committed-copy postflight wiring/tests, then a separately scoped fresh
copy with these callbacks. Positive candidate reads are host-tested but have not
run against an actual active copy; inventory latency under copying is unverified.
Fresh watcher/readiness and after-arm device-uptime cutoff are required before
any separately authorized source tap. Do not reuse the old idle probe readiness
or repeat an old intent ledger. Current selected PROGRESS fixture is unchanged.

## Current status — collector host/idle integration PASS; positive copy pending — 2026-11-18

Owner requested the next slice. Implemented the one-shot collector in
`scripts/model_import_live_collector.py` and fixed-emulator event transport in
`scripts/model_import_event_reader.py`. Neither selects a source, sends app input,
changes settings or starts/stops the app. Commits `01ca9b8`, `0a0c48e`, `d3b7b38`.

- A fresh event from the same reader must arm the collector. Prior probe flags,
  banners and elapsed time cannot arm it. Collection resets numeric feedback and
  excludes events at/before an actual device-uptime copy-start cutoff **before**
  monotonic checks, so prior-copy completion cannot poison the next sequence.
- Each iteration is event-read → snapshot → meminfo → snapshot, with the existing
  owned-partial-candidate/process-epoch guard. Across iterations, candidate bytes
  cannot rewind. Three distinct partial numeric points and at least three heap
  intervals must span 2 GiB; success is only `OBSERVED_PARTIAL_COPY_ONLY`, never a
  committed-import/peak-heap/MI-21 pass. Missing candidate, completion mid-bracket,
  stale/reordered feedback, EOF, overrun or insufficient samples reject.
- Fixed byte/line/sample/poll bounds and one absolute deadline, plus two-second
  teardown grace. Callback deadlines are a contract: overruns are rejected, not
  claimed to magically interrupt arbitrary blocking Python. The actual idle
  inventory wrapper clamps **every ADB command** to that deadline.
- The reader rejects non-`emulator-5554` selectors and qemu!=1 before spawning;
  a separate remote `timeout -s INT` lease bounds Android `uiautomator events`.
  Complete bounded lines only; no readiness from an expired/dead session. Close
  waits for expected remote exit124, not merely termination of the local ADB pipe.

Host RED/GREEN logs `.cache/model-import/live-collector-host/`: cutoff regressions,
**16 collector + 10 reader cases** after meaningful contract failures; the initial
reader test-discovery naming error is not counted as RED evidence. Final full
**205/205 Python tests PASS**, no app/build-input change or unchanged Gradle rerun.

Actual `.cache/model-import/live-collector-host/idle-probe-result.json` is
**PASS_IDLE_COLLECTOR_DEFENSIVE_INTEGRATION_ONLY**: fresh listener delivery,
one real verified idle-store snapshot, **zero sampling meminfo callbacks**,
expected refusal before memory, collector/reader closed and remote observer124.
No synthetic candidate was supplied as an observation. Current PROGRESS fixture
record/stat/64-byte prefix, APK/UID, PID/Activity, UI and rotation preserved;
previous progress/leave/restart/compatibility ledgers and other40 owner hashes
match. No new fixture/import/input/settings/stop/install, S25, Cancel or MI-22.
Probe helpers are ignored evidence, not clean-checkout reproduction; tracked
collector/parser/reader/guard unit tests are reproducible host coverage.

**Remaining:** wire and verify real positive partial-snapshot/meminfo callbacks
and final committed-copy postflight. A new copy requires a fresh recorded scope,
owned-source/current PROGRESS-fixture reconciliation and its own intent ledger.
No automatic import, live progress/heap acceptance or full MI-21 closure follows
from this idle negative-path pass. All old failed results remain untouched.

## Current status — event compatibility PASS; in-flight heap not yet tested — 2026-11-18

The fresh `.cache/model-import/progress-events-compatible-probe/probe-result.json`
now reports **listener_event_seen=true**, after reconciling the exact current
PROGRESS fixture. The bounded five-second interval emitted one recognized event;
expected observer exit124 / transport exit0. This verifies actual event-frame
compatibility/listener delivery only, **not** target progress or in-flight heap.
No raw event text retained, no new source/copy/input/settings/stop/test APK/S25.
Postflight preserves fixture record/stat/prefix, APK/UID, PID/Activity, UI, rotation,
owner40 hashes and all earlier ledgers. Old failed results were not rewritten.

Added `scripts/model_import_live_heap.py` and nine synthetic host tests. The
memory sample must be bracketed by the same strictly partial candidate, nonce
prefix/inode/name, immutable prior payload/root/record and stable PID/UID/**process
start ticks**. Reject completion, ownership/type/link changes, PID reuse, missing
or invalid memory metadata. The caller still must collect before → memory → after
in that order; pure validation is not Android evidence or peak-heap measurement.
The initial fail-closed stub rejected both required valid cases, then **9 focused /
177 full Python tests PASS**; logs `.cache/model-import/live-heap-host/`.

A separate read-only **idle** prerequisite probe confirmed that target-UID
`/proc/12572/stat` start ticks are readable and stable (1652174) around actual
meminfo, with model/prefix/UI/process preserved. Explicitly **not in-flight**:
`proc-epoch-probe.json`. No product/build inputs changed or unchanged Gradle rerun.
Next: integrate these guards into the bounded live collector. A fresh source/copy
requires a new recorded scope and current-model reconciliation; none was started.
Numeric progress and heap-during->2GiB acceptance and full MI-21 remain incomplete.

## Observed event-header compatibility fix — host PASS — 2026-11-18

Owner requested the next slice. Two bounded, observation-only schema captures
reconciled/preserved the current PROGRESS fixture and previous ledgers. Retained
only metadata field labels, numeric-sign categories, framing/separator shape;
no source names, package values or event text. Evidence:
`.cache/model-import/progress-events-{schema,header}-probe/`.
The actual header adds **`TextChangeTypes`** after `WindowChangeTypes`, which
explains the earlier strict parser rejection. Timestamp, one trailing CR and
record-footer separator matched the existing format; these were not guessed.

Added an optional, specifically named `TextChangeTypes` field to listener and
progress recognition, without accepting arbitrary extra headers. Two meaningful
compatibility RED assertions preceded the fix. Privacy/schema tests also cover
unknown body keys and non-event/oversized diagnostics. **24 focused cases are
included in 168/168 full Python tests PASS**; retained logs:
`.cache/model-import/progress-events-schema/`. Synthetic numeric field values
in regression tests are not actual in-flight feedback. App/build inputs unchanged;
no unchanged Gradle run, import, fixture generation, settings/input/stop/test APK,
S25 or MI-22 action. Next: a fresh, separately recorded observation-only probe to
verify actual recognition; keep every prior blocked result unchanged.

## Current observer status — host PASS; event-frame readiness BLOCKED — 2026-11-18

The next host slice adds an explicit listener-delivery flag: only a complete,
recognized accessibility event can acknowledge readiness. Arbitrary banners,
sleep/timeouts, truncated lines and numeric data alone cannot. A non-target
event may prove listener delivery but never target progress. Two meaningful RED
assertions preceded this guard; **18 focused / 162 full Python tests PASS**.
`progress-events-host/readiness-red.txt` and `readiness-regression.txt` retain logs.

Two separately recorded **observation-only** probes used fixed `emulator-5554`
and qemu=1, first reconciling the CURRENT PROGRESS fixture nonce/payload/stat,
APK/UID, idle UI, PID and rotation. No import, fixture, app input, setting,
force-stop, test APK, S25 or MI-22 action occurred. The actual launcher uses
`exec app_process`; remote `timeout -s INT 5` bounded the Android observer.
Both intervals ended with expected observer exit124 / transport exit0 and retained
no raw event text. The first checked only bounded availability; it did not claim
listener readiness. The second exercised the new recognition guard:

- `.cache/model-import/progress-events-readiness-probe/probe-result.json` is
  **BLOCKED_LISTENER_READINESS_NOT_OBSERVED**: 764 output bytes, one event-header,
  one class-field and one lowercase record-count-footer occurrence, but **no
  recognized complete event frame**. Actual event-format compatibility is
  unresolved; do not broaden the parser by guessing or treat timeout as ready.
- Zero numeric progress samples are expected in this idle probe and do not
  validate live progress/heap. No new copy was used to test recognition.
- Postflight confirms the exact current fixture/store, APK/UID, PID/Activity,
  idle UI and rotation preserved. Historical progress/leave/restart ledgers are
  byte-identical; other40 owner hashes match. Original 004307Z remains BLOCKED.

**Stop point:** before another import, obtain a bounded, redacted actual event-frame
schema/fixture and regression-test compatibility and readiness. Keep source names,
event text and unrelated packages out of retained diagnostics. Then separately
verify partial-candidate/PID/UID/memory observations before considering any fresh
copy. No new source/copy retry is implicit in these host tests or probe results.

## Non-idle progress event parser — initial host PASS — 2026-11-18

Owner requested the next slice. Added `scripts/model_import_progress_events.py`
and its reproducible host tests, without starting another copy. The parser uses
live `uiautomator events` feedback rather than an idle-waiting hierarchy dump.
It accepts only the exact production package/TextView/text-changed event and
numeric current `Text` field; it never treats `BeforeText`, another package,
notification or source name as progress. Only numeric summaries are retained.
Fragmented LF/CRLF streams, signed-Long sizes, monotonic event time/counters and
fixed 16 KiB line / 4 MiB transport / 512-sample bounds are tested.

Meaningful RED: **16 assertion failures** against the initial no-op parser.
GREEN: **15 focused / 159 full Python tests PASS**; logs in
`.cache/model-import/progress-events-host/`. Tests use explicitly synthetic event
records, not fabricated Android observations. App/APK/build inputs unchanged;
no unchanged Gradle rerun. A parser is **not** proof of observer readiness,
process/candidate identity, live progress or in-flight heap use. The original
004307Z blocked result remains unchanged.

Design checked against upstream [EventsCommand.java](https://android.googlesource.com/platform/frameworks/uiautomator/+/refs/heads/main/cmds/uiautomator/src/com/android/commands/uiautomator/EventsCommand.java)
and [AccessibilityEvent.toString](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/view/accessibility/AccessibilityEvent.java):
the event listener prints directly without waiting for idle, but the command has
**no listener-ready acknowledgement**. Before another import, a bounded emulator
probe must establish actual availability/format, a defensible readiness guard,
and independent partial-candidate/PID/UID/heap observations. No new fixture,
force-stop, Cancel, settings, test APK, S25 or MI-22 action is authorized by parsing.

## Current status — progress/heap observer BLOCKED; completed copy safely cleaned — 2026-11-18

After the host fix and fresh read-only reconciliation below, the owner’s
continue-until-blocker instruction continued with **one emulator-only progress /
sampled-heap case**, not another Cancel or force-stop. Evidence:
`.cache/model-import/mi-21-emulator-progress-20261118T004307Z/`.

- Exact selected test-copy record/stat and LEAVE-source ownership prefix,
  installed APK/UID, Ready UI, rotation and source-plus-private-copy headroom
  were checked before generating one exclusive synthetic 4 GiB source.
  Real SAF selected only that generated source once.
- The UI observer missed the active interval: one busy hierarchy-dump miss,
  then **zero numeric in-flight samples**. The verifier correctly rejected
  `three numeric samples required`; `result.json` remains **BLOCKED**. This is
  an observation/timing blocker, not evidence of a broken progress display.
  No automatic repeat or later offline/keyguard test followed.
- The app nevertheless completed the replacement as **4,294,967,296 bytes**.
  Safe idle postflight verified the new selected fixture's 64-byte nonce prefix,
  exact payload stat/record, same **PID12572 / UID10234 / store epoch**, no
  pending route or candidates, and one selection/one payload. Normal app cleanup
  removed the preceding private test payload. Only the generated external source
  was removed; the **new selected private synthetic test copy remains**.
- Heap samples exist only **before and idle after**, not during >2 GiB copying:
  Java heap size 2,921 → 4,477 KiB; native heap size 24,076 → 24,332 KiB;
  total PSS 52,803 → 54,894 KiB. These are not a peak or an in-flight bounded-heap
  pass. Numeric progress and >2 GiB heap acceptance remain unverified.
- APK/UID/rotation match; previous leave/restart ledgers are byte-preserved;
  owner41 hashes matched before tracker bookkeeping. `fixture-cleanup.json` and
  `batch-audit.json` confirm safe completed-copy cleanup despite the blocked test.
  No S25, basic Cancel, force-stop, settings change, MI-22, inference or app/build
  change occurred.

Host preparation: numeric/memory/series negative cases first produced **18
assertion failures plus one stub-result error**, followed by GREEN; explicit
progress-kind guards then had **two meaningful RED assertions** before enabling
only that bounded case. Final **15 progress + 15 changed shared leave + 24
changed shared restart guards PASS**. Executed helpers/tests/logs are retained
inside the ignored case directory; these UI helpers are not clean-checkout
reproducible. The separate tracked verifier remains reproducible and Python144
PASS applies to unchanged tracked Python inputs. No unchanged Gradle rerun.

**Stop point / next smallest action:** host-only design/test of a bounded observer
that does not wait out a continuously changing UI and can record live progress
and memory in the same copy interval. Do not reset this ledger or automatically
create another source/repeat the copy. Before any future mutation, reconcile the
**current PROGRESS-case** selected fixture and its nonce prefix, not the now-deleted
LEAVE-case payload. Full MI-21 remains incomplete; emulator results do not satisfy
S25/OEM/genuine-keyguard acceptance and no MI-22 scope is inferred.

## Host stopped-store verifier fix — 2026-11-18

Owner requested continuing emulator-only until a blocker. Added a reproducible,
**host-only** verifier in `scripts/model_import_stopped_store.py` and its tests.
It projects only the exactly captured interrupted candidate out of an in-memory
inventory after proving process absence, root identity, record content SHA and
unchanged active payload full stat. Unknown/duplicate entries, changed state,
unsafe types/links, completed candidates and live/unknown process state reject.
Checked record replacement may change selection inode/mtime, not content.
The existing idle record decoder is not relaxed. No process launch or deletion
is performed or authorized by this projection; callers must still decode the
record and recheck store stability/process absence before an authorized recovery.

Verification: the initial idle-only stub rejected both required valid cases;
then **11/11 focused and 144/144 full Python tests PASS**. Commands:

```bash
PYTHONPATH=scripts python -m unittest discover -s scripts/tests -p 'test_model_import_stopped_store.py' -v
PYTHONPATH=scripts python -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

Evidence `.cache/model-import/stopped-store-host-fix/` retains RED/GREEN/regression
logs and a **historical host-only replay PASS** against the actual stopped-store
snapshot. Every file in the original failed/recovered case remained byte-identical.
This fixes the verifier contract, not the historical automated run: its BLOCKED
result remains unchanged. No new import/force-stop, device access, app/build-input
change, S25 or MI-22. Gradle was not rerun on unchanged app/build inputs.
The ignored emulator restart helper now uses this verifier only at the stopped
checkpoint, followed by the unchanged record decoder and exact active-state,
process-absence and second-snapshot checks. **24/24 helper guards PASS**, including
changes during decoding and rejection before contacting non-emulator targets.
The idle-only adapter first failed the valid stopped-store and early-target guard
cases; the earlier missing-function discovery failure is not counted as contract
RED evidence. Revised helper/test sources and logs are saved alongside the audit.
No old run markers were removed and no new restart/copy was run.

Fresh **read-only emulator reconciliation PASS**: qemu=1, installed APK/UID match,
exact previous selection/root/payload stats and selected LEAVE-fixture 64-byte
ownership prefix match, Ready UI and original rotation. The other 40 owner-file
hashes and every original failed/recovered ledger file remain unchanged; mixed
`tasks/todo.md` is deliberately unstaged. Next is a separately recorded numeric
progress/heap observation while replacing only that verified synthetic test copy,
not another basic Cancel or force-stop, S25 test or MI-22 matrix.

## Current status — restart harness blocker; safe same-case recovery PASS — 2026-11-18

Owner requested continuing emulator-only **until a blocker**. Active-import
leave/return passed below. The next, single MI-21 process-interruption case is
**BLOCKED by an idle-only host record decoder**, not a demonstrated app failure.
No later progress-series/heap/offline case or MI-22 matrix was started.

Evidence `.cache/model-import/mi-21-emulator-restart-20261118T002103Z/`:

- Fresh APK/UID, exact selected test-owned record/stat/64-byte prefix, current UI
  and source/private-copy headroom verified. One exclusive 4 GiB synthetic source
  selected once through SAF; no app-data clear or owner-model access.
- Armed observer captured candidate **4,718,616 bytes**, then target-only
  force-stop left that exact inode at **23,068,696 bytes**, strictly below 4 GiB.
  Target process absent; original selection SHA unchanged. The next host call
  rejected the expected leftover candidate because its historical `record()`
  decoder requires an idle two-entry store. Planned launcher restart had not yet
  run. Original `result.json`/`stage.json` remain **BLOCKED**, unmodified.
- Read-only diagnosis verified exactly that candidate, original payload full
  stat/prefix, record SHA/content and root identity. A record-only projection
  excluded only the proven interrupted candidate for decoding; **no device file
  was deleted**. Selection mtime legitimately changed during picker
  reservation/consumption; requiring an identical mtime was also too strict.
- Only the already-planned launcher recovery of this same case was then sent
  from verified Home, not a new test/import/stop. App recovery removed its dead
  candidate normally. New **PID 12572** versus **4851**, same **UID 10234**, Ready,
  exact original active selection SHA/payload/stat/epoch/prefix, no pending route,
  no candidate or automatic copy resume. This also observes cold-restart
  preservation after that selected fixture's source was deleted in the preceding
  slice; it is not URI-revocation, full-body validation or inference evidence.
- Owned new external source removed only after safe idle recovery; selected
  **previous synthetic 4 GiB private copy remains**. APK/UID/rotation unchanged;
  parent ledger byte-preserved and owner41 matched before bookkeeping.
  `cleanup-recovery-result.json` PASS and `batch-audit.json`
  **BLOCKED_HARNESS_WITH_SAFE_RECOVERY_PASS** supersede historical cleanup-due
  flags, without rewriting the original failed automation result.

Host verification: nine meaningful RED restart assertions, then **16 restart
helper guards PASS**; changed shared helper also passes its **15 leave guards**.
Executed helper sources/test logs are retained in the ignored case directory.
No product source/build inputs changed; unchanged Gradle tests were not rerun.
No S25, basic Cancel retest, MI-22, new test APK or inference.

**Next smallest action:** host-only regression/fix for decoding a stopped store
with one exactly proven interrupted candidate, while rejecting all other extras
and changed selection/payloads. Do not rerun the copy/force-stop merely to bypass
old markers; preserve this blocked ledger and reconcile its recorded recovery.
Full MI-21 remains incomplete: complete progress series, heap/offline, genuine
keyguard and remaining source-revocation/platform/multi-Activity evidence.

## Emulator active-import leave/return PASS — 2026-11-18

Owner **“start next, don't stop until blocker”** continued emulator-only work.
`.cache/model-import/mi-21-emulator-leave-20261118T001309Z/` verifies the unchanged
APK/UID and exact retained test-owned selection/prefix before replacement. One new
exclusive 4 GiB source was selected through real SAF; no owner model was replaced.

The armed observer saw the **same candidate inode** at **6,815,768 bytes** before
Home, **74,973,208 bytes** while the resolved launcher was actually focused, and
**116,916,248 bytes** after MAIN/LAUNCHER return focused the app. All are strictly
partial. That inode subsequently committed as the new **4,294,967,296-byte**
payload, same PID/UID/epoch, nonce clear, one payload/no candidates. The actual
return screenshot visibly shows **123207704 of 4294967296 bytes (2%)**, copying
status, distinct active/pending names, disabled replacement and visible Cancel.
This is one numeric UI snapshot, not a monotonic progress series; the launcher
transition is still visible behind it. No Cancel tap occurred.

`result.json`, `leave/result.json`, `leave/armed-leave-proof.json`,
`visual-progress-review.json` and `batch-audit.json` PASS. Twelve meaningful RED
host assertions preceded implementation; all **15 final helper guards pass**.
Owned external source removed, selected synthetic private copy retained, rotation
settings/APK/UID unchanged, prior ledger byte-preserved and owner41 hashes matched
before bookkeeping. Product source/build inputs unchanged; no unchanged Gradle
rerun, S25, MI-22 or inference. Next slice is active-copy process interruption;
full MI-21 still lacks restart, complete numeric progress, heap/offline, genuine
keyguard and remaining source-revocation/platform evidence.

## Previous status — observer blockers resolved; emulator active-import rotation PASS

Owner **“address blocker”** authorized a fresh emulator-only attempt. The CRLF
handshake now works on the actual emulator. Diagnosis also exposed shell built-in
comparisons mishandling the 4 GiB threshold; the observer now uses **Toybox 64-bit
comparisons**, verified at seven real emulator boundary values.

Latest `.cache/model-import/mi-21-emulator-active-20261117T212906Z/` proves a new
landscape client Activity while the **same candidate** grew from **14,417,944**
through **86,507,544 / 136,577,048 bytes**, then committed that inode as a selected
**4,294,967,296-byte** private copy. PID/UID/epoch remained unchanged, nonce cleared,
old private test copy normally cleaned up, one payload/no candidates remain.
The initial busy-UI/over-strict postflight failure is retained; read-only resolution
accepts Ready only with the exact new source/name/size/reference and candidate
continuity verified. **No extra import was started to resolve postflight.**

`resolved-result.json`, `rotation/result-resolved.json` and `batch-audit.json`
are PASS. Both earlier failed intent ledgers are byte-preserved. Original rotation
settings are restored; the owned external source is deleted. **The emulator still
has the selected synthetic 4 GiB private test copy**; app data was not cleared.
No S25 contact, basic Cancel retest, production code/build change or inference.
Full MI-21 remains incomplete, including numeric UI progress, active leave/restart,
heap/offline and remaining source-revocation/platform evidence.

## Previous status — active-import rotation blocked by observer handshake; cleaned up

Owner authorized continuing emulator work **until a blocker**. A 4 GiB test-owned
source was created and the exact file located in the actual SAF picker. The armed
observer then timed out: the emulator emits **CRLF**, but its ready predicate
expected LF only. The failure occurred before a source tap/import was recorded;
read-only inspection found only our unchanged pending picker reservation, no
payload or candidate. **Active-import rotation is not verified.** No subsequent
lifecycle, heap or offline test was started.

Cleanup verified that same picker/nonce before Back, deleted only the owned
source, and restored the original empty selection SHA/epoch and rotation settings.
Final APK/UID match; app Ready, no model/payload/candidate. **S25 untouched; no
basic Cancel retest.** The observer now accepts LF/CRLF and eight host regression
cases pass, but **no runtime retry followed**, honoring the stop-at-blocker scope.
Evidence `.cache/model-import/mi-21-emulator-active-20261117T204053Z/`,
`batch-audit.json`, `observer-handshake-diagnostic.json`,
`observer-fix-host-tests.json` and `cleanup-after-blocker.json`.

## Previous emulator status — empty-store lifecycle checks PASS; S25 untouched

Owner explicitly requested **the running `emulator-5554`, not the live S25**.
Only that fixed selector was used, with `ro.kernel.qemu=1` enforced. A fresh
production-only install of the existing audited APK was necessary: neither the
package nor retained package data existed on the emulator. No test APK, model,
fixture, data clear or physical-phone action occurred in this batch.

**PASS on the empty-store emulator:** idle rotation with actual MainActivity
recreation in the same process; picker-open rotation with the same pending nonce
and clean return on verified picker Back; screen-off/wake return; idle target-only
force-stop/relaunch with a new PID. The original rotation settings and the empty
selection record SHA/epoch were preserved. Final APK bytes/UID match the postinstall
baseline. App remains installed and Ready. **No actual keyguard was observed**, so
this is screen-off return, not secure lock/unlock acceptance.

Evidence `.cache/model-import/mi-21-emulator-20261117T194524Z/`,
`batch-audit.json` and four case reports. Android 17/API 37, x86_64 with ARM64
translation; no native inference was tested. These are **empty-store emulator
results**, not active-model/in-flight-copy lifecycle or S25/OEM acceptance.
Numeric progress/offline/heap/source-independence and full MI-21 remain open.
Do not contact the S25 or repeat basic Cancel based on this supplementary batch.

## Previous device status — 4 GiB copy/restoration/cleanup PASS; basic Cancel owner-confirmed

Owner reports **“i tested `cancel` manually, it works”**. Record **basic Cancel
manually confirmed by the owner**, separately from the automation's missed timing
window; do not repeat Cancel or imply a demonstrated Cancel-handler failure.
The agent did not observe that manual interaction or take post-report snapshots.

The explicitly approved fixture batch completed a real **4,294,967,296-byte** SAF
import, then restored the real **1,846,240,768-byte** model and deleted only the
test-owned source. The app-private fixture payload was normally cleaned up by
restoration. The Downloads model/mmproj stats and all 41 owner-file hashes were
preserved before own bookkeeping. No inference ran. Automated Cancel remained
**INCOMPLETE_CANCEL_NOT_PROVEN**: no eligible live Cancel control was captured,
so zero Cancel taps were sent. The successful import is not an automated Cancel
pass, and the owner's subsequent manual result is not relabelled as one.

Latest evidence: `.cache/model-import/mi-21-fixture-20261117T191721Z/`,
`result.json`, `batch-audit.json`, `restoration/result.json`,
`fixture-cleanup.json` and `owner-manual-cancel.json`. **Full MI-21 remains
incomplete:** numeric UI progress, lifecycle, offline/network independence,
heap measurement and selected-fixture source-revocation checks still lack evidence.
Actual >2 GiB completed copied length is now verified; bounded heap is not.
The fixture/cleanup/restoration scope is complete; no further copy is automatic.

The owner approved the preceding real replacement, identified the phone's Downloads, and
corrected the assistant's mistaken classification: **one GGUF is the model; the
other is its mmproj, not a second model choice.** Filename-role checks confirmed
the correction. The actual Downloads SAF picker selected only the approved model.
The app reported Imported, selected a new private payload of **1,846,240,768 bytes**
and normally cleaned up the old private payload. Store epoch was preserved,
pending nonce cleared, and exactly one selection/one payload/no candidates remain.
The Downloads model and mmproj stat metadata were preserved; neither source was
modified/deleted by the assistant. No inference was started.

Evidence: `.cache/model-import/mi-21-import-20261117T183950Z/`, `result.json` and
`batch-audit.json`. The earlier real picker open/cancel pass remains valid.
All 41 inventoried owner files were preserved before own tracker bookkeeping.
No product source, APK installation, package stop, settings or fixture changed.
On that first real-model import, the first post-selection UI observation already
showed Imported, so in-flight progress/Cancel was not observed. Later fixture and
owner-manual results above supersede that timing/size boundary, not the remaining
lifecycle/heap/network acceptance requirements. Network was not disabled,
so this is not an offline/network-independence test. That first replacement and
the subsequently approved additional attempt are complete; neither authorizes
further copies or MI-22.

## MI-20 bounded device acceptance complete

After the owner confirmed **“ready”**, both packages were bounded-stopped and the
current production store was inventoried read-only: one selection record, one
model payload, no candidates. Only the approved revised test APK was
retained-updated. The fixed isolated suite then **PASSed 26/26 tests** on the S25,
including the previously failing regular metadata case and actual unknown-size
provider/controller/view integration. Both package UIDs, installed APK bytes and
production selection-record hash/model-file metadata were retained; both packages
are quiet. No production model/source was deleted, reset, replaced or launched.

MI-20 acceptance is complete **across separately authorized batches**: isolated
26/26 on revised test APK `660379ac…`, native smoke 1/1 earlier on original test APK
`38679225…`, with the same unchanged production APK `76284abd…`. The native test's
actual DEX disassembly and provenance asset are identical in both test APKs; native
was **not rerun or claimed on the revised APK**. Each run retained its own recorded
post-install baseline. This is not a single 27-test run or a model-inference pass.

No app/host source changed in this retry; unchanged successful host checks were
not repeated. MI-21 real SAF/lifecycle/>2 GiB work, MI-22 interruption/cancellation
matrix, MI-23/module/human/ship review and inference/WebUI remain incomplete and
separately gated. The final retry section records evidence and the exact boundary.

## Previous follow-up — fixture repair host-verified; retry paused for active app

The owner approved the test-only fixture repair, rebuild/audit, retained update of
its revised APK and bounded isolated retry. Four tested/committed host slices now
provide an Android/JDK-only Java provider, including all existing bounded modes;
the manifest selects it and the Kotlin provider is removed. The six-class DEX
closure has **zero Kotlin/target-helper references**. Final build/lint pass,
**133 Python tests pass**, production inputs/APK are unchanged. The revised test
APK is built/audited/preserved, **not installed or device-tested**.

Read-only pre-update identity matched the original installed baseline, but the
main app was active and `no_backup/models` had appeared since the earlier batch.
The safety gate stopped **before installation, force-stop or any phone write**.
Finish/cancel any manual import and confirm ready; the existing approval remains
valid. Re-inventory/preserve the now-existing production data before retry, never
reset or remove it. See the final fixture-repair section and its evidence pointer.
MI-20 remains incomplete; native passed on the original test APK, isolated failed
on that APK, and no runtime result is yet claimed for the revised test APK.

## Previous batch — MI-20 partial: native passes; isolated suite fails

**MI-00–MI-19/H2 remain complete only in their host/compilation scope.** On
2026-11-17 the owner connected wireless ADB after approving the exact MI-20 batch.
Private inventory selected one physical **SM-S931B, ARM64, reporting Android 17 /
API 37**. Both packages already existed. Retained updates of both audited APKs
succeeded with unchanged UIDs; installed bytes matched the original audited hashes.

**Native `--version` smoke: 1/1 PASS. Isolated 26-method suite: FAIL, incomplete.**
The diagnosed failure is `AndroidImportIoTest.kt:98`: regular metadata expected
`50`, received `null`. Host DEX inspection establishes that the separate-process
fixture provider references Kotlin helpers absent from the test APK, a likely
fixture packaging cause; no device exception confirming that cause was captured.
All four bounded wrapper runs verified retained APK/UID identities and stopped
both packages. Checked production model paths were absent before and after.

Three host-only harness fixes were tested/committed separately: bounded 64 KiB
process inventory, space-containing process names, and exact package UID selection
from substring-filtered package output. **133 Python tests pass**. Previous
**242 JVM / 12 packaging tests, lint 0 errors / 4 baseline warnings and APK builds**
remain applicable to unchanged app/build inputs; no unchanged host build was rerun.
MI-20 remains unchecked. A fixture fix and revised test APK need a fresh artifact
review/update scope, not repeated approval of the old batch. MI-21/MI-22, real SAF,
large imports, interruption/cancellation, inference/WebUI and module/ship readiness
remain unverified. Detailed evidence is in the wireless MI-20 section below.

### Baseline reconciliation

The owner subsequently requested **“address blocker”**, authorizing host-only
re-staging of the verified local archive and rerunning the blocked baseline.
The receipt/stage verification now passes, **43 JVM tests pass**, lint has
**0 errors / 4 existing warnings**, and both APKs build. The earlier **94 passing
Python tests** remain applicable; their inputs are unchanged. See the follow-up
results below for commands and artifact identities.

No production source, dependencies, toolchain, native library bytes, or phone
state changed during baseline verification. The owner subsequently authorized
reconciling MI-00 and implementing **MI-01 only** (“y” after the explicit proposal).
MI-00 is complete: the existing checkout is the reviewed implementation source,
with unchanged tracked app/build inputs and the documented unrelated-work
exclusions. A separate clean checkout is not required to establish this baseline;
no clean-checkout reproduction is claimed. That approval covered neither MI-02
nor phone access; the subsequent MI-02 authorization/results are recorded below.

## MI-00 — Initial host preflight (superseded baseline blocker)

Run date: **2026-11-15 UTC**. The owner replied **“auth”** to the explicit
proposal for **MI-00 only**: inspect workspace/tooling, run baseline host
checks, and document evidence. This authorizes neither MI-01 nor device access.

**Result:** 94 Python tests pass. The existing native stage is rejected by the
current verifier; Gradle stops at that same prerequisite before JVM tests,
lint, or either APK build. **MI-00 remains incomplete and MI-01 is blocked.**
No model-import implementation or runtime validation has occurred.

### Source and workspace boundary

- Worktree: `D:/workspace/projects/llama-droid`, branch `main`, baseline HEAD
  **`d903e24d664fb076de781f4877210304d6e623a9`**.
- `git diff HEAD -- app scripts gradle gradlew gradlew.bat build.gradle.kts
  settings.gradle.kts gradle.properties third_party` was empty before checks.
  This is a baseline of unchanged tracked build/source files in a **dirty
  worktree**, not a clean-checkout reproduction or a newly isolated worktree.
- Existing worktrees were inventoried, not modified:
  `llama-droid-integration` (`merge/native-runtime`, `d903e24`),
  `llama-droid-native-runtime` (`fix/native-runtime`, `9036aa9`), and
  `llama-droid-native-runtime-clean` (detached `955bfe3`).
- Exact pre-existing exclusions: modified `.gitignore`, `CAPABILITY-MAP.md`,
  `tasks/plan.md`, `tasks/todo.md`; untracked `SPEC-model-import.md`; and
  untracked `app/.cxx/` (36 files). No stash, reset, cleanup, source copying,
  worktree switching, or changes to those files during baseline commands.
  Only the targeted MI-00 tracker bookkeeping described below follows checks.
- `app/.cxx/` is not referenced by the current Gradle build configuration;
  its existing files were neither compiled nor removed. Before/after SHA-256
  inventories confirm all 36 file contents and paths are unchanged.
- `.gitignore` remains byte-for-byte unchanged. It ignores this new evidence
  file, so this file is added explicitly with `git add -f`, not an ignore edit.
  Existing planning changes are not included in the evidence-only commit.
- Host-generated output is confined to normal ignored caches/build directories
  and `.cache/model-import/mi-00-20261115T200724Z/`. The native stage is unchanged
  (before/after inventory: 14 libraries plus receipt). No native reimport,
  dependency/toolchain changes, or generated binary commits were performed.

### Approved-document provenance

Specification, plan, and detailed tasks identify themselves as owner-approved;
implementation authorization is separately limited to MI-00 by this conversation.
The approved documents are local, uncommitted work, not content at baseline HEAD.
These are SHA-256 identities of the exact inputs **before tracker bookkeeping**:

| Input | SHA-256 |
|---|---|
| `SPEC-model-import.md` | `5c94280b62bdac36c30377ba43f505c35c2e4a76fe5bc52541231b1fa7d48a52` |
| `tasks/plan.md` | `0440b74ada5eb995755e9d87ccfbeffbbc2a14d0d9dea28747a8411f8b4cf42f` |
| `tasks/todo.md` | `fc030791b728900cf81a8cb1b401990445c7b1987e28b8f84a3238d6c674b43c` |
| `CAPABILITY-MAP.md` | `418d7e63e3529fd431b81e08d48fb8bbce31f8042592a07c18c4c8cb64a5a589` |
| `.gitignore` | `e45555239189172c748faeda86a7f4d0069ec75cca3893f294e0a407f7739532` |
| `third_party/llama.cpp.json` | `c944ecfbca57152e998bf19581ed99037f258b3b57afbe2e914961b3cc7331e7` |

Local preflight JSON preserves the original status/worktree list, exclusion
hashes, wrapper identity, environment, and stage inventory. A local pre-edit
tracker snapshot and pre-existing document diff preserve the bookkeeping boundary.

### Actual host/tool prerequisites

| Component | Observed for this run |
|---|---|
| Host/shell | Windows 11 10.0 amd64, Git Bash; working directory `/d/workspace/projects/llama-droid` |
| Python | `C:/Program Files/Python314/python.exe`, **3.14.7** |
| Java | Temurin **17.0.21+9**, `C:/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot/bin/java` |
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot\` |
| SDK configuration | Existing ignored `local.properties`: `sdk.dir=D:/softwares/android-sdk`; `ANDROID_SDK_ROOT` and `ANDROID_HOME` are unset |
| Gradle | Wrapper **8.10.2**, revision `415adb9e06a516c44b391edff552fd42139443f7`; launcher/daemon JVM 17.0.21 |
| Project plugins | AGP **8.8.2**, Kotlin **2.0.21**; wrapper's embedded Kotlin **1.9.24** is separate |
| SDK inventory (read from `source.properties`) | Command-line tools **12.0** at `D:/softwares/android-sdk/cmdline-tools/latest`; platform `android-35` revision **2**; build-tools **35.0.0**; platform-tools **37.0.1** |

No SDK manager, ADB, installer, emulator, or manifest decoder was run. Tool
inventory is not a platform runtime result. The current JDK differs from the
historical native evidence's 17.0.14; no JDK was installed or selected by this task.

### Exact baseline commands and results

Commands ran once from the worktree root, using the existing environment:

```bash
git status --short
git worktree list
python scripts/import_llama_release.py --verify-only
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
./gradlew --version
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

| Check | Actual result |
|---|---|
| Native stage verification | **Exit 1**: `Invalid stage receipt: Receipt provenance/payload mapping differs from manifest` |
| Python discovery | **Exit 0**, **94 tests in 5.203s**, all pass, no skips (50 importer + 44 APK auditor) |
| Gradle version | **Exit 0**, 8.10.2 |
| Combined JVM/lint/APK baseline | **Exit 1**, `BUILD FAILED in 41s`, `:app:verifyNativeStage FAILED`; 1 actionable task executed |
| JVM discovery/execution | **Blocked** before execution; no current JVM test count or pass claimed |
| Lint / debug APK / instrumentation APK | **Blocked** by stage verification; no fresh lint or compiler/APK success claimed |

The Gradle `checkKotlinGradlePluginConfigurationErrors SKIPPED` line is a
conditional configuration task, not a test result. Existing `app-debug.apk`
output was not produced by this run and is not accepted as current evidence.
The opt-in native-packaging integration checks and final artifact/manifest audit
were not run: those belong to MI-19, not this blocked baseline.

Raw local evidence under `.cache/model-import/mi-00-20261115T200724Z/`:
`preflight.json`, `native-stage.log`, `python-tests.log`, `host-exit-codes.txt`,
`gradle-version.log`, `gradle-baseline.log`, `gradle-exit-codes.txt`,
`read-only-diagnosis.json`, `todo-before-bookkeeping.md`, and
`preexisting-document-changes.patch`. Logs were captured with Bash `pipefail`
and `${PIPESTATUS[0]}` so `tee` cannot conceal a failing check. These ignored
host logs are retained locally, not committed or presented as device evidence.

### Smallest failing step and next action

Read-only inspection of the actual receipt shows a legacy flat mapping of
archive-member names to digests. The current importer requires a versioned
receipt including archive/release/source provenance and the APK payload mapping.
All **14 actual staged library hashes match** the committed manifest, but that
does **not** make the stage valid or permit bypassing the receipt gate.

The existing local archive was independently read/hash-checked:

- `.cache/llama-b12020-bin-android-arm64.tar.gz`: **78,982,432 bytes**.
- SHA-256: `64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec`;
  size and digest match the committed provenance manifest.
- No archive extraction, receipt editing, re-staging, verifier bypass, or stage
  repair was performed. No earlier native test result substitutes for this run.

**Proposed next host-only action, requiring owner confirmation:** re-stage that
verified local archive using the current importer, then rerun the failed native
verification and the blocked Gradle baseline. This changes generated host stage
files, not production source or a phone; never hand-edit a receipt to pass.
The already-passing Python suite need not be repeated unless its inputs change.
A fully clean implementation worktree/source decision remains pending before
advancing beyond MI-00; no approved documents or native stage were copied into
another worktree by this task.

### Safety and completion boundary

- **No phone access or writes:** no ADB calls (including inventory), installs,
  instrumentation, launches, stops, package changes, or owner-model operations.
- **No model-import code was implemented, compiled, or device-tested.**
  Compilation was attempted only for the existing baseline and was blocked.
- All model-import runtime, provider, cancellation, process-death, >2 GiB,
  backup/transfer, and physical-device criteria remain unverified.
- Initial tracker bookkeeping recorded MI-00 authorization, evidence, and
  blockers; completion/build acceptance remained unchecked at that time.
  MI-01–MI-23 and every checkpoint/device gate remained pending. Existing native
  history is untouched.

## MI-00 follow-up — Authorized stage repair and passing host baseline

Run date: **2026-11-15 UTC**; re-stage began at **20:18:02 UTC**. Authorization:
owner requested **“address blocker”** after the proposed local re-stage and
baseline retry were described. This extends MI-00 to that generated-host-stage
repair only; it does not authorize model-import implementation or phone access.

Source/worktree and environment are unchanged from the initial preflight.
Follow-up HEAD: **`8e2fc47f665f64451704d7851ec1cd3b16c01346`**, an evidence-only
commit over the original baseline. Tracked app/scripts/build/native-provenance
inputs still match HEAD with no source changes. Existing dirty planning files
and `app/.cxx/` remain excluded. Their contents were checked unchanged before
follow-up bookkeeping; no stash, reset, or copying into another source tree.

### Commands and actual results

The archive size and SHA-256 were checked again against the committed manifest
before the importer was allowed to mutate the stage. The existing current
importer, not a hand-edited receipt, performed the complete transactional import:

```bash
python scripts/import_llama_release.py .cache/llama-b12020-bin-android-arm64.tar.gz
python scripts/import_llama_release.py --verify-only
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

| Check | Actual result |
|---|---|
| Native import | **Exit 0**; 14 byte-preserved payloads staged from the pinned local archive; no download |
| Native verification | **Exit 0**; exact payload set, ELF headers, versioned receipt, and transaction state pass |
| Gradle baseline | **Exit 0**, **BUILD SUCCESSFUL in 24s**; 80 actionable tasks, 68 executed and 12 up-to-date |
| JVM discovery/execution | **43 tests pass**, 0 failures/errors/skips: `NativeProcessProbeTest` **24**, `NativeServerLauncherTest` **19** |
| Lint | **0 errors / 4 warnings**: `RedundantLabel`, two `GradleDependency`, `ChromeOsAbiSupport`; same categories as historical native evidence; not fixed or suppressed |
| Production debug APK | `:app:assembleDebug` succeeds |
| Android test APK | `:app:assembleDebugAndroidTest` succeeds; **compiled only**, not executed |

These are existing native-runtime JVM tests, not tests of an implemented
model-import flow. The 94 Python tests were **not rerun**: importer/auditor/test
source and their fixture contracts are unchanged since their passing initial
run. Changing the generated real stage does not change those disposable-fixture
tests. No final clean-output build, APK content/manifest audit, device install,
or instrumentation execution is claimed by this follow-up.

### Generated-state and artifact identities

The importer regenerated `.payload_receipt.json` with SHA-256
`fe2b7459b888908415f8b52336f2f541a9c6faf80e06df4ea8198ff536af8d73`.
All **14 staged library SHA-256 values are unchanged** from the initial stage
and match the pinned manifest; archive bytes and provenance are unchanged.
Successful verification confirms no unresolved import marker or backup remains.
The importer's persistent host lock file is generated state, not app payload.

| Built artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,482,905 | `990419bb5d26ed00a19d08ff30bb14e93e23605f5afdc9ddd434697edbe79a99` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Retained follow-up evidence:
`.cache/model-import/mi-00-restage-20261115T201802Z/`, containing preflight
identities, original receipt/tracker snapshots, `native-import.log`,
`native-verify.log`, `gradle-baseline.log`, checked exit codes,
`verification-results.json`, copies of both JUnit XML reports and the lint XML
report, plus the final preservation/bookkeeping check. Reports record nonzero
test discovery and exact artifact/report hashes. Generated stage files, APKs,
and local evidence logs are **not committed**.

### Remaining boundary

The native-stage blocker and blocked host checks are resolved. At the end of
this repair, the tracker still held MI-00 open for a separate clean-worktree
decision. That was unnecessarily restrictive: its reviewed tracked source was
unchanged and unrelated work was explicitly excluded. The subsequent owner
approval of reconciliation and MI-01 selects this existing checkout without
stashing, resetting, or deleting unrelated work. MI-00 is therefore complete;
this is not a claim of a fresh clean-checkout build. No model-import code was
added during stage repair, and no ADB or other phone operation was performed.

## MI-01 — Bounded records and display names

Owner authorization: **“y”** after the proposal to reconcile MI-00 and implement
MI-01 only with failing tests first. Run date: **2026-11-15 UTC**. Worktree,
JDK/SDK/plugins and exclusions are the reviewed MI-00 environment. Starting HEAD
was `20f478f59991fb4bbc332947a8b7c3515647d0d9`; reconciliation is documentation-only
commit `d858794`. Verified implementation commits are **`dc14cd1`** (names) and
**`4e68a8d`** (records/codec). No new dependencies, Android components, UI wiring,
permissions, native payloads, or toolchain changes.

### Deliverables and contract

Exactly the five MI-01 deliverables were added under the approved prefixes:

- `app/src/main/java/io/github/greprohan/llamadroid/modelimport/ModelRecord.kt`:
  empty/active selection with epoch and optional pending nonce, plus the reset-id
  and intended-new-epoch record type. Values contain no source URI/path fields.
- `app/src/main/java/io/github/greprohan/llamadroid/modelimport/ModelRecordCodec.kt`:
  checked encode/decode and bounded stream read. The caller owns the stream;
  short reads are accepted, no `available()`/seek/reopen is used, and reads stop
  at EOF or 2,049 bytes. Invalid data is rejected, never repaired or promoted.
- `app/src/main/java/io/github/greprohan/llamadroid/modelimport/ModelName.kt`:
  scan at most 256 code points, neutralize control/format/lone-surrogate and
  line/paragraph-separator characters to spaces, trim the bounded prefix and
  use `Unnamed model` for an empty result. Valid names are <=256 code points /
  1,024 UTF-8 bytes; large inputs cannot cause whole-input UTF-8 allocation.
- `app/src/test/java/io/github/greprohan/llamadroid/modelimport/ModelRecordCodecTest.kt`:
  **21 tests**, including independent wire fixtures and adversarial input.
- `app/src/test/java/io/github/greprohan/llamadroid/modelimport/ModelNameTest.kt`:
  **8 tests**, including supplementary characters and bounded-prefix fallback.

V1 wire contract: ASCII `LDMR`, unsigned-byte version `1`, unsigned-byte kind
`0` (selection) or `1` (reset). All ids are exactly 32 lower-case ASCII hex bytes.
Selection fields: epoch, strict 0/1 pending flag and optional nonce, strict 0/1
active flag and optional payload id / signed-64-bit byte count / unsigned-16-bit
UTF-8 byte length / UTF-8 name. Reset fields: reset id and new epoch. Integers
are big-endian. No padding or trailing bytes are accepted. Empty selection
contains no payload fields; active size is at least 24. Names are decoded with
strict UTF-8 error reporting and must already satisfy the safe-name contract.
The maximum actual V1 selection is 1,138 bytes, within the 2,048-byte record cap.
No length/count can request an unbounded allocation.

Decoding a reset record is **not** evidence of committed consent or permission to
delete files. Authoritative-path checking, checked file replacement, store
ownership, and reset execution are MI-02/MI-03/MI-13 work, not implemented here.
Malformed-record errors use the fixed `Invalid model record` message without
retaining parser causes or untrusted metadata. External stream I/O errors
propagate to the caller; no diagnostics/logging boundary is implemented here.

### Test-first evidence and exact verification

The name slice was tested, linted/built, regression-tested and committed before
the record slice. The codec was expanded through tested selection, reset/read,
and adversarial-schema increments, then fully verified and committed.
Compiling no-op contracts were used for the initial RED runs; failures were
actual assertions, not missing symbols, missing tests, or compilation failures.

| Retained RED result | Discovery / intended failures | GREEN result |
|---|---|---|
| `name-red.log` / `.xml` | 8 tests, 7 failed | 8 name tests pass (`name-green.xml`) |
| `selection-red.log` / `.xml` | 5 codec tests, 5 failed | 5 codec tests pass (`selection-green.xml`) |
| `reset-read-red.log` / `.xml` | 10 codec tests, 4 failed | 10 codec tests pass (`reset-read-green.xml`) |

Every RED run had zero errors/skips and exit 1. Successful runs had exit 0.
Commands from the repository root, with successive tests/implementations between
RED/GREEN repetitions:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelNameTest'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelNameTest' :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelRecordCodecTest' --tests '*ModelNameTest'
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Final focused discovery: **29 tests** (21 codec + 8 names), all pass.
Final unfiltered JVM regression: **72 tests** (29 new + 24 native probe + 19
native launcher), **0 failures/errors/skips**. Full Gradle verification:
**BUILD SUCCESSFUL in 28s**, 80 actionable tasks (13 executed / 67 up-to-date).
Native-stage verification also passes within that build. Lint: **0 errors / 4
existing warnings** (`RedundantLabel`, two `GradleDependency`,
`ChromeOsAbiSupport`); no suppression or dependency upgrade. Kotlin production,
unit-test and Android-test compilation succeed; Android instrumentation is
**compiled, not executed**. The earlier 94 Python passes remain applicable:
the host scripts and test inputs have not changed, so that suite was not rerun.

Coverage explicitly includes unknown magic/version/kind/flags, every identity
field, all empty/active/reset truncations, trailing/oversized records, invalid
lengths/UTF-8, negative/too-small/64-bit sizes, maximum Unicode names, unsafe or
huge input names, missing names, short/zero-progress stream reads, a guarded
large synthetic stream, caller-owned resource lifetime and I/O failure.
These are pure JVM contract tests, not Android filesystem/provider evidence.

### Artifacts, exclusions, and remaining work

| Built artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,482,963 | `54b046ed2a336f91fd1bb3a7e013f7daa57ef59fd3c11cbcc9558f5a4c9ad251` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Local evidence: `.cache/model-import/mi-01-20261115T202408Z/`. It retains command
logs/exit codes, RED and GREEN XML snapshots, all four final JUnit reports,
lint XML, source/artifact/report hashes in `results.json`, preflight identities,
the original tracker snapshot, and the final preservation/bookkeeping check.

All native-stage contents (including the receipt), `.gitignore`, `app/.cxx/`
and unrelated approved planning content are unchanged. Tracker edits are scoped
to the current gate and MI-00/MI-01 results; MI-02 onward and historical native
records remain unchanged. New source/tests/evidence are committed; tracker
bookkeeping remains in the existing uncommitted planning document so its prior
owner changes are not swept into an implementation commit.

**MI-01 is implemented, JVM-tested and compiled; not device-tested.** It is not
wired to file persistence, a controller, or the Activity, so it adds no user
import/reset behavior yet. No ADB calls or phone writes occurred. Next smallest
task at MI-01 closure was **MI-02 — checked record replacement**, requiring its
own batch approval. That approval and its results follow. Checkpoint F1 remains
pending MI-03 and review.

## MI-02 — Checked record replacement

Authorization: owner requested **“start next”** following the MI-01 completion
report and explicit MI-02 next-task proposal. Scope is MI-02 only; no device
consent. Run date: **2026-11-15 UTC**. Starting HEAD:
`fde2a8df283da0193fa7981381c58d6b2bb082c9`; same reviewed source/exclusions and
MI-00 JDK/SDK/toolchain. Implementation commits: **`bb5fb4f`** (protocol/fakes),
**`13407f3`** (Android adapter/additional cases), **`443bdfe`** (sidecar readback
fault matrix). No dependency, native payload, manifest or Activity changes.

### Five deliverables

Under `app/src/main/java/io/github/greprohan/llamadroid/modelimport/`:

- `ModelFileOps.kt`: small no-follow entry/descriptor I/O boundary with
  device/inode/type/length/link identity, reader/writer ownership, exclusive
  creation and checked namespace operations.
- `CheckedModelRecord.kt`: fixed `selection`/`reset` slots; caller supplies the
  expected old record. Creates `.next` exclusively, checks identity, writes,
  explicitly flushes/syncs/closes, bounded-readbacks the temporary record, then
  verifies the old base again before same-directory rename. Confirms the new
  base's entire record and transferred inode, closed descriptors and absent
  sidecar before success. It never truncates/deletes/renames aside the old base.
- `AndroidModelFileOps.kt`: existing canonical private root only, root inode/type
  rechecks, direct-child names, `lstat`/`fstat`, no-follow/exclusive/nonblocking
  opens with mode 0600, unbuffered checked writes, `fsync`, checked `Os.close`,
  public `Os.rename` and checked `Os.remove` on a validated single-link regular
  file. No public `Os.unlink` assumption. The borrowed input stream does not own
  a second descriptor; its enclosing Reader owns/closes the original fd.

Under `app/src/test/java/io/github/greprohan/llamadroid/modelimport/`:
`FakeModelFiles.kt` and `CheckedModelRecordTest.kt` (**19 JVM methods**, with
per-operation fault matrices). No Android API mocks are counted as platform
proof. All mutation is in-memory fake data; no owner model paths are accessed.

### Outcome/resource contract

- `NEW`: expected replacement and candidate inode confirmed at base, `.next`
  absent, no live descriptor. An exception after successful rename can still
  reconcile to this outcome; its original failure is retained for the caller.
- `OLD`: exact old record and inode (or initial absence) confirmed after the
  attempt/cleanup. Only a known owned sidecar may be removed, after all handles
  stop. Sidecar-removal failure is explicit debt, never permission to delete the
  authoritative base or any payload.
- `AMBIGUOUS`: unknown/unreadable/mismatched namespace or unresolved descriptor;
  no rollback or guessed selection. Debt is conservatively true here, not proof
  that a sidecar physically exists. `quiescent=false` blocks subsequent I/O;
  explicit `close()` retries only the captured handle, never a reused descriptor.

Read-only lookup never promotes or deletes `.next`. Stale regular owned
sidecars are checked before removal, and cleanup false returns are detected by
postinspection. Primary write/sync failures retain secondary close/cleanup
failures. Caller must retain one serialized, exclusively leased store owner;
root lease/recovery/payload ownership are **MI-03**, not implemented here.
The protocol does not establish power-loss/directory-fsync durability.

### Test-first and build evidence

| Stage | Actual evidence |
|---|---|
| Initial publication RED | **1 discovered / 1 assertion failure**, exit 1; compiling no-op protocol |
| Publication GREEN | 1/1 pass, exit 0 |
| Reconciliation RED | **4 discovered / 3 assertion failures**, exit 1; pre-commit faults, false normal returns, post-rename exception |
| Reconciliation/resource GREEN | Passing focused tests after implementation, then expanded contract cases |
| Protocol slice | Focused tests + lint/both APKs pass; full JVM regression passes before commit |
| First Android adapter build | JVM/APK steps pass, **lint fails** with `NewApi` for `OsConstants.O_CLOEXEC` (SDK field since 27, minSdk 26) |
| Corrected adapter build | **BUILD SUCCESSFUL in 21s**, lint 0 errors / 4 existing warnings, both APKs built |
| Final expanded readback matrix | **91 JVM tests pass**, 0 failures/errors/skips; **BUILD SUCCESSFUL in 18s**, 80 actionable tasks (6 executed / 74 up-to-date), lint and APK tasks pass |

Final suite roster: MI-02 **19**, codec **21**, names **8**, native probe **24**,
native launcher **19**. Required malformed/postcondition/resource cases are not
skipped. Tests exercise create/info/write/flush/sync/close/readback/rename faults,
rename-then-throw, silent write/rename/close/delete failures, debt/retry, unknown
base, wrong inode, unsafe entry kinds/hardlinks, stale sidecars, reset/initial
slots, fixed operation ordering, byte preservation until rename, and no further
I/O while a descriptor remains live. No worker threads or Android processes are
created by these tests. Native-stage verification passes in each Gradle build.
The 94 earlier Python results remain applicable; their inputs are unchanged.

Exact repository-root commands, repeated only after relevant source/test edits
or for expanded regression scope:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*CheckedModelRecordTest'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*CheckedModelRecordTest' :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
javap -classpath D:/softwares/android-sdk/platforms/android-35/android.jar android.system.Os android.system.OsConstants
```

### API-26 flag correction provenance

SDK API metadata confirms public `Os.open`, `Os.rename` and `Os.remove` since 21,
`OsConstants.O_CLOEXEC` since 27 and `Os.fcntlInt` since 30. Neither newer Java
API is used in the final adapter. Atomic close-on-exec open is retained via the
verified ARM64 Linux UAPI value **02000000 octal = 0x80000**, not a lint
suppression or minSdk upgrade. This is specific to the already ARM64-only APK;
any ABI expansion requires reviewing native constants again.

Evidence: Android 8.0/API-26 [generic fcntl header](https://android.googlesource.com/platform/bionic/+/android-8.0.0_r1/libc/kernel/uapi/asm-generic/fcntl.h)
and its [ARM64 include/overrides](https://android.googlesource.com/platform/bionic/+/android-8.0.0_r1/libc/kernel/uapi/asm-arm64/asm/fcntl.h).
The installed, unchanged NDK **27.2.12479018** headers agree:
`sysroot/usr/include/asm-generic/fcntl.h` and
`sysroot/usr/include/aarch64-linux-android/asm/fcntl.h`. Header snapshots and
SDK API metadata results are retained locally. This source/API check is not an
API-26 or S25 runtime test of fd flags/closing/linking.

### Artifacts and remaining gate

| Built artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,482,963 | `d949e7d72878b1d93e065e4ecfc9c999f055bae32a0baf69d0f353e9b7adb9ea` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Local evidence: `.cache/model-import/mi-02-20261115T204241Z/`: preflight/tracker
snapshots, RED/GREEN XML and logs, checked exit codes, initial failing lint log,
final five JUnit reports/lint XML, `android-public-api.txt`, NDK header snapshots,
source/artifact/report identities in `results.json` and final preservation check.
No generated files, APKs, logs or native payloads are committed. Existing owner
planning changes remain uncommitted; tracker bookkeeping does not sweep them
into the implementation commits.

**MI-02 is implemented, fake-I/O/JVM-tested and Android-compiled, not
Android/device-tested.** Filesystem syscall/descriptor/rename behavior must be
validated by MI-15/MI-20; source review, JVM fakes and lint are not substitutes.
No ADB, device installation/launch/stop, process-death, backup or owner-data test
occurred. Native stage, `.gitignore`, `app/.cxx/`, toolchain and unrelated files
are unchanged. At MI-02 closure, the next task was MI-03 and required its own
authorization. That authorization/results follow; F1 owner review remains pending.

## MI-03 — Authoritative selection recovery and root ownership

Owner authorization: **“start next”** after the explicit MI-02 completion/MI-03
next-task report. Scope is MI-03 only, followed by F1 evidence preparation, not
MI-04 or device writes. Run date: **2026-11-16 UTC**, starting HEAD
`d1ad976db22fd083ae9512235d23f1c751cf804f`. Same reviewed worktree/source exclusions.
Implemented/tested commits: **`4aba8e4`** (lease), **`6fcbea3`** (store/root/recovery),
**`b1cb474`** (adversarial/resource coverage), **`fcdb9ef`** (committed-empty/root
replacement boundaries). No SDK/dependency/toolchain/native/manifest/UI changes.

### Five approved deliverables

Under `app/src/main/java/io/github/greprohan/llamadroid/modelimport/`:

- `ModelOwnership.kt`: synchronized root-keyed in-process lease; idempotent close
  cannot release a newer owner's lease. Also holds the small root/inventory
  boundary and Android implementation, keeping the existing record-I/O API
  unchanged. The production factory canonicalizes the supplied no-backup parent,
  uses fixed child `models`, checks creation, rejects a linked/non-directory root,
  remembers/rechecks root device/inode, and lazily enumerates direct children
  through a closed `DirectoryStream`. It never materializes the directory list.
- `ModelStore.kt`: one leased, caller-serialized recovery/cleanup owner. It reads
  authoritative reset state first, explicitly initializes empty selection only
  for an empty root or lone owned `selection.next`, validates authoritative
  selection/owned entries, then resolves active payload kind, links, exact
  signed-64-bit length, descriptor identity and readability before exposing its
  generated private path. It validates again after cleanup. Display names never
  become path components; `.next` is never promoted by inspection.

Under `app/src/test/java/io/github/greprohan/llamadroid/modelimport/`:
`ModelStoreTest.kt` (**17 methods**), `ModelRecoveryTest.kt` (**15 methods**), and
the extended `FakeModelFiles.kt` (fake root/inventory, named read/close faults,
and optional long stat length for synthetic tests). Exactly the five planned
files were added/modified. Inventory snapshots in the bounded fake fixtures are
not the production enumeration implementation.

`ModelStore.open(noBackupParent)` expects `Context.noBackupFilesDir` from its
future application-context owner; no Activity/singleton wiring is added here.
The lease excludes a second store instance, not concurrent operations on the
same instance: callers must serialize store methods, as the approved controller
will do. This is not cross-process locking.

### Authority, cleanup and resource outcomes

- Valid selection is the sole model authority. Old/new/explicit-empty file
  images retain exactly their authoritative epoch, pending nonce and active
  id/name/size. Uncommitted records cannot select a promoted payload or resume a
  dead copy. Missing/corrupt metadata with payloads, candidates, reset state or
  unknown entries blocks mutation; it never invents an empty state.
- Committed reset is reported as `RESET_PENDING` **before selection lookup**.
  It is not executed here (MI-13 owns reset recovery). Corrupt reset or any
  uncommitted `reset.next` blocks ordinary recovery without authorizing deletion.
- A complete preflight validates every recognized owned entry before any
  sweeping. Cleanup checks current authoritative selection, root identity,
  no-follow regular type, one link and unchanged entry identity before unlink;
  it confirms absence afterward. Only strict generated candidates, unreferenced
  payloads and owned `selection.next` are eligible. Sources, unknown names,
  unknown links/directories and the active payload are preserved.
- Failed record-sidecar cleanup exposes `recordDebt` and disables metadata-write
  eligibility. Unreferenced candidate/payload debt is separately visible and does
  not block current selection or eligibility when space permits. `canWrite` is a
  protocol eligibility flag, **not** a free-space/permission guarantee or an
  implemented import/replacement command. Recovery retries safe cleanup.
- Payload/record readers and inventory handles are captured before use. Pending
  close returns `RESOURCES_PENDING`, blocks another recovery I/O pass and keeps
  the lease. Explicit disposal retries the captured resources and cannot release
  ownership while any remains live; a new store can recover after successful
  disposal. No replacement worker/thread is started to conceal pending cleanup.

Root/type/containment/syscall behavior is **compiled only** for Android. Pure
root-validation faults are not real Android symlink tests. Payload resolution
opens/checks readability with constant-size reads, not a complete body/hash/GGUF
check, RAM-fit estimate, or inference compatibility claim. Large-length tests
use fake stats; they are **not** real >2 GiB imports or heap/device evidence.

### Test-first evidence and verification

All RED runs compiled and discovered nonzero tests, then failed assertions;
none was a missing-file/symbol/test-discovery failure. Each was followed by GREEN:

| Retained RED XML/log | Discovered in that class | Assertion failures |
|---|---:|---:|
| `lease-red` | 2 | 2 |
| `store-red` | 4 | 2 |
| `recovery-red` | 5 | 3 |
| `reset-order-red` | 7 | 1 |
| `root-guard-red` | 10 | 1 |

The root-guard case found and fixed a real orchestration gap: recovery must
revalidate the root after initial inventory and **before** publishing the empty
record, not merely before/after the whole operation. Existing Android record-I/O
root checks are retained as an additional boundary, not used to waive this test.

Exact repository-root command families, with relevant source/test edits or
expanded regression scope between repetitions:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelStoreTest'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*ModelStoreTest' --tests '*ModelRecoveryTest'
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python --version
java -version
./gradlew --version
```

Focused RED/GREEN runs and the last focused run passed through 30 store/recovery
methods; the final unfiltered run includes the two additional boundary methods,
so **all 32 MI-03 tests pass**. Full final roster: store **17**, recovery **15**,
checked record **19**, codec **21**, names **8**, native probe **24**, native
launcher **19** = **123 tests**, 0 failures/errors/skips. The two synchronized
lease workers use bounded latch handshakes, are joined and asserted terminated
before the race test passes; no worker is left to mutate a later fixture.

The matrix covers old/new/empty file-image recovery, lost initial publication
acknowledgement, initialization faults/retry, stale next records, corrupt/missing
metadata, every owned entry's unsafe kinds/hardlinks, exact/missing/mismatched
lengths, read failure, changed payload/root identity, malformed/unowned names,
source-byte preservation, independent roots/simultaneous lease attempts,
record-versus-payload debt, inventory faults and record/payload/inventory pending
close ownership. File-image reconstruction and injected errors are not process
termination or physical-disk evidence.

Final Gradle result: **BUILD SUCCESSFUL in 23s**, 80 actionable tasks (6 executed /
74 up-to-date). Production compilation and both APK builds had also executed
successfully during the 34-second store/root integration runs; unchanged outputs
are reused in the final test-only expansion. Lint: **0 errors / 4 unchanged
warnings** (`RedundantLabel`, two `GradleDependency`, `ChromeOsAbiSupport`), no
suppression. Native stage verification passes in each Gradle invocation.
Python **3.14.7**, Temurin **17.0.21+9**, Gradle **8.10.2**/project plugins unchanged;
SDK remains the existing `local.properties` path. The prior 94 Python passes
remain applicable because their scripts, fixtures and provenance inputs are
unchanged; that successful suite was not redundantly rerun.

### Artifacts and retained evidence

| Built artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,482,963 | `9a27a9aaccbd4d1acc690ade9c4361673f971aa4a02b0d6228061a1b938258f7` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 927,596 | `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6` |

Production DEX symbol presence was checked for `ModelStore`, `ModelOwnership`
and `AndroidModelRoot`; this is a narrow packaging presence check, not a full
APK audit/manifest review or runtime test. Full clean-output/audit work stays
MI-19. Android instrumentation remains compiled, **not executed**.

Local evidence: `.cache/model-import/mi-03-20261116T095717Z/`. It retains preflight
identities/tracker snapshot, all five RED and subsequent GREEN XML/logs, focused
and unfiltered command output, exit codes, tools log, final seven JUnit reports,
lint XML, source/report/artifact hashes and final preservation check. Source,
tests and evidence documentation are committed. Tracker bookkeeping remains
with the owner's pre-existing uncommitted planning changes; these were not swept
into code commits. Native payloads/receipt, `.gitignore`, `app/.cxx/`, other
planning documents, toolchain and later tasks/native history are unchanged.
No model roots or owner data were accessed on Android: no ADB, installs,
instrumentation, launch/stop/reset, or device writes.

### Checkpoint F1 — Storage foundation ready for owner review

**Host evidence ready; owner review pending, not checkpoint approval.** All
MI-01–MI-03 cases run in the final regression (80 model-import + 43 existing JVM
cases); lint/APK checks pass, with no required preservation/ownership case
skipped or waived. Checkpoint review should examine the committed codecs,
checked replacement and recovery/root boundaries plus the retained fault and
resource results before MI-04. Approval of MI-03 execution does not itself
approve F1 or authorize the next slice.

No platform, actual process-death, power-loss, backup/transfer or physical-phone
result is inferred from this foundation. At MI-03 closure the next action was
F1 review/MI-04 authorization; the subsequent review and standing owner
instruction are recorded below. Model import, server lifecycle, inference and
WebUI milestones remain incomplete.

## F1 technical review — 2026-11-16

The owner requested the next step (F1 review), then explicitly requested
uninterrupted host work until a phone is required. Reviewed implementation:
**`fcdb9efaf08d1ece6a7f7a98440e3110e6aaa3f1`**, unchanged at review HEAD
`836799d4f6dc8733da42b304de1002c5edd1e0a8` (documentation-only difference).
All eight production model-import sources and six JVM test/fixture sources were
read against the P1 contract. No blocking defect was identified within that
contract; this is a bounded review, not a claim that no bugs can exist.

| Review boundary | Source/evidence checked | Finding |
|---|---|---|
| Bounded untrusted metadata | `ModelName`, `ModelRecordCodec`, independent wire fixtures, truncation/UTF-8/length tests | Fixed/bounded allocation, canonical safe names and strict record schema; no source path fields or parser-data diagnostics. |
| Old-record preservation | `CheckedModelRecord`, operation/fault matrix | Base unchanged until same-directory rename; temp readback/identity/closed-handle checks; old/new/ambiguous reconciliation rather than blind rollback. |
| False normal returns / debt | Write/rename/close/remove silent-failure cases | Readback/postinspection detects violated postconditions; unclosed resources block another I/O pass; known-base debt is not guessed publication. |
| Root/type/path ownership | `ModelOwnership`, `AndroidModelRoot`, `AndroidModelFileOps`, owned-kind/link/id/path tests | Canonical trusted parent and fixed child, no-follow single-link regular entries, generated payload names; lazy closed inventory. Actual Android syscall behavior still needs MI-15/MI-20. |
| Old/new/empty recovery | `ModelStore`, reconstructed file images and corruption tests | Only base authority; no `.next` promotion; corrupted/missing metadata does not invent a model or authorize deletion. |
| Cleanup / reset boundary | Full preflight, current-selection checks, source preservation, record/payload debt and reset-order cases | Cleanup remains limited to owned unreferenced entries; reset is reported pending, not executed before MI-13. |
| Resource and concurrency assumptions | Captured reader/inventory cleanup, lease tests, bounded latch worker teardown | Second store excluded; one store remains caller-serialized, not an implicit concurrent queue. No cross-process/hostile-writer guarantee is inferred. |

Fresh review audit compared every implementation/test SHA-256 with the retained
MI-03 results, verified all seven JUnit XML hashes and their nonzero **123/123**
results (80 model-import + 43 regression, no failures/errors/skips), inspected the
4-warning/0-error lint report and checked both APK byte sizes/SHA-256 identities.
No implementation or test inputs changed, so the successful test/build commands
were **not rerun merely for reassurance**. Exact commands/results/source hashes
remain in the MI-01–MI-03 evidence above; current audit is retained at
`.cache/model-import/f1-20261116T111011Z/review-audit.json`.

**F1 technical review is complete.** This does not claim a separate human review,
Android/process-death result or module closure. The latest owner instruction
authorizes incremental host progression, including technical checkpoint reviews,
through **MI-19/H2**; still record each actual review and any fixes before the
next slice. Do not mark a human/device-only checklist item as performed merely
because host progression is authorized. **Stop at MI-20**, before inventory,
installation/test/package-stop/data actions requiring physical-phone consent.
No MI-04 code, phone access or unrelated-work changes were made in this review.

## MI-04 — Fixed header and sequential byte copy

Authorized by the standing host-only instruction. Commits **`40c74d5`** (header)
and **`4d088df`** (ports/engine/tests). Exactly five approved deliverables:
`GgufHeader.kt`, `ModelSource.kt`, `ModelImportEngine.kt` under the production
`modelimport/` package, and `GgufHeaderTest.kt`, `ModelImportEngineTest.kt` under
the matching JVM test package. No prior production/test files were modified.

`GgufHeader` accepts the approved byte-level `GGUF` magic with little-endian v2/v3
and big-endian v3 version bytes; other magic/version/byte-order combinations and
prefixes shorter than 24 bytes fail. The two untrusted unsigned counts are never
used for allocation/traversal. Counts/body integrity and compatibility are not
validated; a header-passing, body-corrupt fixture intentionally passes.

The pure engine retains the 24-byte prefix once, creates a candidate only after
validation, writes prefix then a single 256 KiB buffer through actual source EOF,
and tolerates short/zero-progress bulk reads without seek, reopen or trusting
`available()`. Long counters use checked addition. Source close precedes explicit
candidate flush/sync/close; closed postconditions and a final cancellation check
precede `CopiedCandidate`. The engine cannot publish metadata. Failure retains
its primary cause, candidate ownership and pending-resource flag; cleanup closes
captured resources once, without concealing unresolved close. The source and
candidate/cancellation ports have no MI-05/MI-07 forward dependencies. Candidate
delete/space/reported-size policy is deliberately the next MI-05 slice.

RED evidence: header **5/5 assertions failed**, then GREEN; initial copy **2/2
failed**, then GREEN. An additional test found metadata could be accessed after
copy outside classified cleanup: **1/13 engine tests failed**, then metadata/name
snapshotting was moved inside owned preparation before candidate creation.
All RED runs compiled/discovered nonzero cases, no errors/skips.

Exact commands (RED/GREEN repetitions separated by source/test edits):

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests '*GgufHeaderTest'
./gradlew --no-daemon :app:testDebugUnitTest --tests '*GgufHeaderTest' --tests '*ModelImportEngineTest'
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Final regression **141/141 pass** (5 header + 13 engine + prior 123), zero
failures/errors/skips; **BUILD SUCCESSFUL in 21s**, 80 tasks (17 executed / 63
up-to-date). Lint 0 errors / the same 4 warnings; both APKs pass. Tests cover exact
prefix/body identity across multiple buffers, short/non-seekable unknown-length
sources and actual EOF, early invalid/truncated input before destination creation,
maximal untrusted counts, source/destination read/write/close/flush/sync failures,
silent close, primary-cause privacy/retention, cooperative cancellation and the
header-only integrity limitation. Android adapter/instrumentation behavior is
still unexecuted; no model store/Activity integration or phone access is claimed.

Evidence: `.cache/model-import/mi-04-20261116T111752Z/` retains commands/exit codes,
RED/GREEN XML, final nine JUnit reports/lint XML, all source hashes and artifacts
in `results.json`. Production APK SHA-256:
`57f05a050650002bf8bf07d647d3f6b064741e58706a45bad1f0a68db58c9439`;
instrumentation APK remains
`f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6`.
Prior P1 source/report identities, native stage/receipt, `.gitignore`, `app/.cxx/`
and unrelated documents are unchanged. Next: MI-05 under standing authorization.

## MI-05 — Space, size and stopped-resource cleanup

Host-only implementation, commits `08935dc` and `b3af807`. Exactly the five
planned files: `ImportPolicy.kt`, `ModelImportEngine.kt`, `ModelImportEngineTest.kt`,
`ModelImportFailureTest.kt`, `FakeModelSource.kt`. The engine requires an explicit
injected destination-space policy (no permissive production default).

Known positive sizes require the entire new payload plus **67,108,864 bytes**
before source reads/candidate creation. Every pending write, for known or unknown
sizes, remeasures available space and checks the reserve. Failed/negative
measurements fail conservatively; checks subtract the reserve without overflow.
Unbuffered destination writes are a documented adapter obligation. Over-reported
sizes fail at actual EOF; under-reported sizes fail before an excess write.
Counters use checked addition, and percentage uses bounded exact integer ratio
arithmetic even near signed-Long limits.

Failure/cancellation closes each captured resource once; candidate deletion is
attempted only after both source and destination confirm closed. Pending close
blocks deletion; failure keeps candidate identity, resource/cleanup-pending flags,
the original cause and at most three typed secondary warnings. A discard failure
does not mask a read/write/cancellation cause. No selection/source/old payload
mutation is possible through these ports; publication integration remains MI-09.

RED: policy **5/5 failed**; engine-policy integration **2/20 failed** (one assertion,
one wrong-primary-type cast), then GREEN. Extended fault/boundary runs pass.
Final command:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

**155/155 JVM tests pass**, zero failures/errors/skips; lint **0 errors / 4 existing
warnings**, both APKs pass, **BUILD SUCCESSFUL in 19s** (80 tasks, 14 executed).
The legal sequential synthetic **2,147,483,649-byte** stream returns no more than
requested, uses only the 24-byte prefix / 256 KiB buffer and counts output without
allocating a model. This is not a physical file/provider/device result. Coverage
includes known/unknown/unusable size, exact reserve boundary, mid-copy depletion
and measurement errors, strict size mismatch, near-Long arithmetic, create/read/
write/flush/sync/close/discard errors, pending close, and cancellation at preparation,
read, space, progress and closed handoff. Fake resources are settled by tests.

Evidence `.cache/model-import/mi-05-20261116T113651Z/`: preflight, exact command logs,
exit codes, RED/GREEN XML, final ten reports/lint and source/artifact hashes in
`results.json`. Production APK `76f0f0aac8ca93cc332f60b0c25a003e0ce18b804f0bf5318fedbb284ea00d4c`
(79,482,963 bytes); test APK `f75923a5847f9a5264ed25e4b78411769f3866e1b221ced2ffad6f85a87772e6`
(927,596 bytes). Preservation audit confirms all other preflight app/test sources,
native stage/receipt, `.cxx`, `.gitignore` and unrelated planning/spec files unchanged.
No phone access; next MI-06 and an actual F2 technical review.

## MI-06 — Android provider adapter and isolated fixtures

Commits `59e6a7a`, `632be2e`, `6e6eab6`; exactly five approved deliverables:
`AndroidImportIo.kt`, and test-only `FixtureModelProvider.kt`, `AndroidImportIoTest.kt`,
`ModelImportTestStore.kt`, `app/src/androidTest/AndroidManifest.xml`.

The adapter rejects main-thread/non-content access, queries only name/size with
cancellation, bounds/sanitizes accepted names and treats null/empty/missing/
unparseable sizes as unknown. Cancellation/read/revocation errors do not become
successful metadata fallbacks. Cursor-close failure retains an explicit pending
owner. Read-only `openAssetFileDescriptor` and `createInputStream` retain selected
slice offsets/lengths and pipes; EOF checks reliable-pipe errors. Descriptor
acquisition is published before stream construction, and a source-local lock
serializes construction/close without using a future cancellation/commit lock.
Acquisition failures attempt close once, retaining any pending resource and the
primary cause. `StatFs.availableBytes` measures the supplied destination directory.
Controller registration of the signal **before** metadata/open and immutable
source ownership remain explicit MI-07/MI-09 integration obligations.

The fixture provider is in the **test APK only**, separate `:model_import_fixture`
process, with bounded fixed ids, generated 50-byte documents, UID/signature caller
validation, no owner paths and no target production-class dependency. Slices have
unselected prefix/suffix bytes; pipes include reliable-error delivery. Tiny pipe
contents are populated synchronously, below minimum pipe capacity, with **no
producer thread**. Shared test-store construction uses fresh generated no-backup
containers, root leases and checked no-follow cleanup of known regular files;
it never uses the production `models/` root or recursive/app-data deletion.

Commands: `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passed **in 24s**, 80 tasks / 20 executed, **155 JVM tests, zero failures/errors/skips**,
lint **0 errors / 4 unchanged warnings**. After the fixture-only review correction,
`:app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` passed **in 15s**;
unchanged JVM inputs were not pointlessly rerun. **Seven Android adapter test
methods compile; none was executed.** There is no Android RED/GREEN/runtime claim.

Evidence `.cache/model-import/mi-06-20261116T114504Z/`: preflight/command logs,
exit codes, ten passing JUnit reports, lint, source/artifact hashes, decoded
production/test manifests and apkanalyzer version. Actual SDK tool:
`D:/softwares/android-sdk/cmdline-tools/latest/bin/apkanalyzer.bat` (JDK 17).
Production has **no provider**; test authority is
`io.github.greprohan.llamadroid.test.modelimport.fixture`, separate process as
above. All 14 packaged ARM64 native-library hashes match the unchanged stage.
Production APK `dc2e6ad8103fb56cba978697ef0720fc82428a5e06eaee566cf89b6300d266e3`
(79,482,963 bytes), test APK `3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`
(954,174 bytes). Existing sources/native/excluded owner work unchanged; no phone access.

## F2 technical review — 2026-11-16

Actually reviewed 13 exact source/test/manifest files across MI-04–MI-06, retained
passing reports and decoded APK declarations. Review audit:
`.cache/model-import/f2-20261116T115422Z/review-audit.json` with exact hashes.
Reviewed fixed-prefix/byte-order limits, bounded read/count/space/percentage logic,
size mismatches, original/secondary failure retention and stopped-resource-only
deletion. Reviewed adapter acquisition/close ownership, provider caller/process
isolation and generated-root-only mutation. A fixture pipe EOF/semaphore-release
race was identified and **removed**, not waived (`6e6eab6`); pending cursor-close
ownership was also retained during implementation. No unresolved host-contract
blocker found for MI-07. Android/provider descriptor behavior, process death,
physical >2 GiB import and backup/transfer remain unverified until approved device
batches. This is an assistant technical review, not invented owner/human sign-off.

## MI-07 — Cancellation/commit gate and captured source ownership

Commit `90ba6d1`; four planned files added: `ModelImportState.kt`,
`ImportOperation.kt`, `ImportOperationTest.kt`, `ImportResourceRaceTest.kt`.
The pure operation has immutable identity, monotonic copy/promotion phases,
and one short in-memory gate for accepted Cancel versus one-shot COMMITTING.
No provider/file call executes inside it. Late Cancel after commit does not set
cancellation or dispatch helpers. Acquisition requires a registered provider
signal; cancellation forbids starting a new acquisition. An already-running
acquisition retains the owner until its late resource is disposed/closed.

At most two immutable abort tasks per operation capture its signal and single
source lease. Duplicate Cancel queues nothing. Dispatch rejection retains the
cancel flag, bounded causes and truthful unresolved ownership. A blocked signal
does not block independent source close. Source construction/close handoff is
single-owner: worker/helper race for one close claim, pending close is not retried
blindly or reported closed, and a post-close exception cannot cause raw descriptor
reuse/double-close. Failed close remains pending until separately confirmed closed.
No helper can reference a candidate/selection/reset writer. Completion and commit
require no acquisition/helper/resource owner; finished operations release references.
The dispatcher contract is explicit: enqueue nonblocking, never execute inline;
the bounded production executor is a later MI-09 integration obligation.

Meaningful RED runs: gate **5/5 failed**, source ownership **3/8 failed**, signal-
before-acquisition **1/6 failed**, followed by GREEN. Fifteen new tests include
**100 concurrent Cancel/commit pairs**, explicit both-winner outcomes, queued
helper duplication, late acquisition, immutable old-operation capture, dispatch
rejection, post-close errors, blocked signal and worker-close races. Latches are
released in `finally`; actual worker threads/executor futures are joined and
asserted terminated before passing. These are JVM fakes, not Android FD evidence.

Final command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 25s**, 80 tasks / 16 executed, **170 JVM tests**, zero failures/errors/
skips. Lint **0 errors / 4 existing warnings**, both APKs pass. Evidence:
`.cache/model-import/mi-07-20261116T115656Z/` with RED/GREEN/final logs/XML, preflight,
12 reports, lint and all source/artifact hashes in `results.json`. Production APK
`e8192d6ac2c99b4486362ecee535c85158d64d2f6e8ca88a8b65e426f5ffa3ea`
(79,482,963 bytes); test APK remains
`3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`.
All pre-existing app/test/native/excluded owner inputs unchanged. No phone access,
Activity wiring, Android execution or fabricated checkpoint/human result. MI-08 next.

## MI-08 — Durable picker identity and bounded launch/result routes

Commits `713d6c2`, `df4f406`, `115b005`; five planned files added/modified:
`PickerRequest.kt`, `PickerCoordinator.kt`, `ModelStore.kt`, `PickerRequestTest.kt`,
`ModelStoreTest.kt`. Reservation/consumption uses checked record replacement with
fresh opaque epoch/nonce identity, preserving active id/name/size and old payload
bytes. Only a confirmed matching durable token clears before source opening.
Old/ambiguous/descriptor-pending outcomes cannot authorize launch/import; actual
post-rename errors are reconciled as confirmed new rather than rolled back.
Failures retain their primary cause internally, not in rendered diagnostics.

The coordinator separates RESERVING, RESERVED, LAUNCH_CLAIMED, AWAITING_RESULT,
CONSUMING, restored-uncertain and blocked states. Only a same-process unclaimed
reservation can be passively claimed once. Disk calls run outside its memory gate;
duplicates cannot queue a second reservation/consume. Process restoration never
auto-launches. Explicit retired-route supersession writes a new token; an old A
result cannot consume B before or after B consumption. Dismissal/launch failure
clears only its own token. Worker reconciliation cannot overwrite a live transition.
The saved logical Activity route has one binding and monotonically allocates
`0x0001..0xFFFE`, never wraps/reuses codes, rejects malformed state, and refuses
new launch until its binding returns or the entire route is retired. No URI/path
is persisted. Actual Android picker/lifecycle wiring remains MI-11/MI-12.

RED: store **2/19 failed**, route **3/4 failed**, coordinator **3/7 failed**, then
GREEN (no missing/zero/skipped tests). Final full command:
`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 23s**, 80 tasks / 17 executed. **188 JVM tests**, zero failures/errors/
skips; lint **0 errors / 4 existing warnings**; both APKs pass. Eighteen new tests
cover preserved active/source bytes, single consumption, epoch/stale results,
record write/rename/ambiguous/close failures, bounded restored routes, explicit
abandonment and a latch-blocked reservation proving memory claims remain usable.
The test reservation worker is released/joined with no survivor.

Evidence `.cache/model-import/mi-08-20261116T120826Z/`: preflight, exact logs/exit
codes, RED/GREEN XML, final thirteen reports/lint and source/artifact hashes in
`results.json`. Production APK `d6fafad25f964c5925b83701162db650d0f80694bc2b3e45c8cbd4300851043e`
(79,482,963 bytes); test APK unchanged
`3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`.
Only planned store/test edits plus three additions; native and excluded owner
work unchanged. No Android/device run or phone access. MI-09 controller next.

### MI-09 storage-integration split review

Before controller implementation, reviewed the actual `ModelStore`/file-operation
ports: recovery and picker updates exist, but candidate creation/promotion/active
publication do not. The writer only accepts a whole byte array, which would force
extra production copy buffers for partial chunks. The approved normal-import
protocol therefore needs a small **MI-09S** storage prerequisite, separately limited
to five files (`ModelFileOps`, `AndroidModelFileOps`, `ModelStore`, `FakeModelFiles`,
`ModelStoreTest`). The original five controller deliverables remain separate.
This technical split implements already-approved behavior under standing host
scope; it does not invent owner/human approval or permit device access. Acceptance
requires checked exclusive candidate ownership, no live-candidate cleanup, bounded
subsection writes, closed promotion, checked old/new/ambiguous publication and safe
cleanup debt, with tests/lint/both APKs before the next slice.

## MI-09S — Checked store integration

Reviewed five-file split completed in `e1c2318`, `1c61538`, `93d625e`:
`ModelFileOps.kt`, `AndroidModelFileOps.kt`, `ModelStore.kt`, `FakeModelFiles.kt`,
`ModelStoreTest.kt`. Unbuffered production writes accept array offset/count directly,
without per-chunk copies. Exclusive candidate ownership blocks recovery/second
writers even after close until discard/publication. Acquisition validation captures
handles before fallible checks; unknown identity is preserved for later recovery,
not guessed safe to delete. Unconfirmed close is not blindly retried. Promotion
requires successful flush/sync/close, checked inode/length, unchanged selection and
absent final path; old/new/ambiguous rename outcomes never overwrite another payload.

Checked selection publication confirms new before retiring old; failures preserve
old bytes, unresolved records preserve both payloads, and authoritative-read
reconciliation never promotes `.next`. Source/helper quiescence and the operation's
commit gate are explicit caller obligations. Proven unreferenced cleanup debt is
nonblocking and distinct from a successful new selection; stale candidate handles
cannot delete an active payload or affect a later owner.

RED stages: candidate **2/25 failed**, promotion **3/28**, publication **3/31**,
reconciliation **1/32**, unsynced handoff **1/37**, then GREEN. Fourteen new tests
exercise sequential fake-source/store paths, first import/replacement, exact bytes,
all those transaction outcomes, close/ownership guards, cleanup debt and recovery.
Final full command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 42s**, 80 tasks / 17 executed: **202 JVM tests**, no failures/errors/skips;
lint **0 errors / 4 existing warnings**, both APKs pass. No Android/device run.

Evidence `.cache/model-import/mi-09s-20261116T122004Z/` retains exact command logs,
RED/GREEN/final XML, thirteen reports/lint, source/artifact identities and preservation
review in `results.json`. Production APK
`3d99bbec6b2edbbfb8823d461eb61afb84775ce1da07968fe48ff86e3d4b0ada`;
test APK unchanged `3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`.
Exactly the reviewed five files changed; other source/native/excluded owner work
is preserved. Controller/singleton/notifications and checkpoint A remain next.

## MI-09 — Serialized controller, bounded cancellation and notifications

Commits `87fa7db`, `bb4e390`, `9796e92`; exactly five approved deliverables:
`ModelImportController.kt`, `ModelImports.kt`, `ModelImportState.kt`,
`ImportOperation.kt`, `ModelImportControllerTest.kt`. Lazy singleton ownership is
constrained to **Application**, not an Activity/generic Context. There is one
bounded I/O executor and two bounded abort workers; command reservation precedes
enqueueing. Provider query/open registers cancellation before blocking and publishes
an immutable owned source before stream construction. Resource-completion callbacks
run outside the operation gate; pending cleanup retains the command and checks late
completion at worker exit so it cannot lose a finalization wakeup or launch a writer.

The integrated fake-source flow consumes the matching durable picker first, copies,
promotes while still cancellable, then wins the atomic commit gate before checked
publication. Ambiguity preserves both payloads until authoritative reconciliation;
confirmed old/cancel/error and confirmed new/cleanup-debt remain distinct. Snapshots
contain typed/sanitized issues, never raw exceptions or provider error text.
One replaceable main runnable serves all state kinds. Increasing revisions/current
subscriptions prevent stale delivery; copy progress is eligible within 100 ms and
capped at 10 Hz, with immediate stage/final supersession. Activity binding is not yet
wired. Android provider/Handler behavior is **compiled, not device-executed**.

RED: command/notification **2/2 failed**, pipeline **1/4**, pending finalization
**1/6**, stale classification boundary **1 case** (a separate late-cancel fixture
initially targeted the earlier consumption rename and was corrected), then GREEN.
Seventeen controller tests cover first/replacement exact old-byte preservation,
consumption/stale/cancel failures, late commit, promotion cancellation, transient/
persistent ambiguity, cleanup debt, pending close, notification ordering/rate and
real I/O plus two helper workers with a blocked signal/independent source close.
Latches are released and all executor futures/termination checked; no survivor.

Final current-source command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 39s** (80 tasks / 18 executed): **219 JVM tests**, zero failures/errors/
skips, lint **0 errors / 4 baseline warnings**, both APKs pass. An intermediate
`StaticFieldLeak` warning was fixed by the Application-only type, **not suppressed**.
Evidence `.cache/model-import/mi-09-20261116T124121Z/` retains logs/exit codes,
RED/GREEN XML, fourteen final reports/lint, decoded production manifest and all
source/artifact hashes. Production APK
`4fa65a918691cf010f401a5d318c7f09777f3888bb0f2a10fa8d831803800c2c`;
test APK unchanged `3f80f1b1cc87144d37250b428a45b07f63b577373982d419325dc4ef34c694c3`.
All 14 packaged native hashes and excluded owner inputs preserved. No phone access.

## Checkpoint A technical review — 2026-11-16

Actually reviewed 16 exact production/test files across ownership, picker, controller,
store, streaming and Android acquisition, current reports/APK identities and decoded
manifest. Audit `.cache/model-import/checkpoint-a-20261116T155637Z/review-audit.json`
records exact hashes, checks and resolved findings. No unresolved host-contract blocker
for MI-10. Production declares no new permission/provider/background component;
native bytes are unchanged. Android/runtime/SAF/Handler/lifecycle and physical >2 GiB
results remain unverified. This is **assistant technical review**, not an invented
human owner review. The tracker keeps that distinct human-only checkbox unclaimed;
the owner's standing uninterrupted-host-work instruction authorizes progression.

### MI-10 fixture integration review

The required before-EOF unknown-size screen case cannot be established with the
existing finite/prepopulated 50-byte provider fixtures. Review adds the existing
**test-only** `FixtureModelProvider.kt` as MI-10's fifth file: one validated token-
bound producer, release handshake and bounded join. No production command/provider
or owner-root override is introduced. Original screen/layout/strings/test scope
is unchanged; all Android cases remain compiled-only until MI-20 consent.

## MI-10 — Import Views and gated unknown-size contract

Commit `016cac3`; five reviewed deliverables: `ModelImportScreen.kt`,
`main_activity.xml`, `strings.xml`, `ModelImportScreenTest.kt`, and the explicitly
reviewed test-only `FixtureModelProvider.kt` extension. The screen does not open a
store/singleton or retain a controller: active private selection and pending new
copy are separate, actual Long sizes remain intact, known progress uses checked
percent and unknown progress has copied bytes/indeterminate display without a
fabricated total. Preparing/cancelling/finishing/resource-pending states and typed
recovery/error/cleanup messages remain explicit. Import is not a compatibility
claim. Actions are disabled while unbound, reset hidden until wired, and detach
removes callbacks. Launcher/system picker are still MI-11, not claimed working.

Four Android methods **compile only**: unbound/recovery actions, active-vs-pending/
finishing/detach, cancellation/warnings, and
`unknownSizeProviderRendersCopiedBytesWithoutPercent`. The latter uses the shared
isolated no-backup store helper plus real Android adapter/engine/controller and a
test-provider reliable pipe. A token-bound producer emits 24 bytes, waits for an
observer/render handshake, then releases the remaining 26 bytes/EOF. The test checks
copied bytes/no percent **before EOF**, then 50-byte completion; finite latches,
worker futures and producer join are required, with no sleep timing or production
singleton. This procedure is **not executed/evidence of Android behavior yet**.
Test controls remain solely in the test APK/provider. Provider failure is surfaced
through reliable-pipe error and checked finish status, not concealed as clean EOF.

Incremental provider/layout/binder/state-test/integrated-test build checks pass.
Final full command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 44s**, 80 tasks / 27 executed: **219 JVM tests**, zero failures/errors/
skips; lint **0 errors / 4 baseline warnings**, both APKs pass. New plural/unused-
placeholder warnings were corrected using plural resources and removing the now-
unreferenced placeholder, without suppressions or Long-to-Int display truncation.
The standing Definition of Done was reviewed: Android runtime/visual behavior and
independent human acceptance remain **unverified**; this is host task scope, not
feature/ship completion or a waived device gate.

Evidence `.cache/model-import/mi-10-20261116T160118Z/` retains all exact build logs/
exit codes, fourteen passing JVM reports/lint, source/artifact hashes and reviewed
fixture/DoD limits in `results.json`. Production APK
`27cdde6fc007959249371d1c2e2dc9e5d8e8a3068cf420c25d584c49d3bd6e41`;
test APK `db612a60d6eee9e826a7703a6d04b08fbd1078dfcb97ba9c6aaeca7ead976ed5`
(969,945 bytes). Exactly five reviewed files changed; packaged 14 native hashes,
other sources and excluded owner work are unchanged. No phone access. MI-11 next.

### MI-11 UI-message integration review

Accurate missing-picker, malformed-result, busy-owner and retired/exhausted-route
messages require fixed resource strings, not raw provider exceptions or hardcoded
text. The existing `strings.xml` is explicitly MI-11's fifth file; original Activity,
route/test and manifest scope is unchanged. This is host-only decomposition under
standing authorization, not permission to invoke the real picker or access a phone.

## MI-11 — Launcher and opaque SAF result routing

Commits `ce6a83d`, `b9a6450`; five reviewed files: `MainActivity.kt`,
`AndroidPickerRoute.kt`, `AndroidPickerRouteTest.kt`, production manifest and
`strings.xml`. MainActivity binds only the Views/controller and observes while
started, detaching/clearing screen callbacks on stop/destroy. It reads **no incoming
Activity data/path/model/argument extras**. One memory-only, token-bound result can
wait for serialized store recovery; no URI or dead copy is saved/restored. Additional
same-process configuration-retention/lifecycle cases remain the next MI-12 slice.

A same-process reserved request is claimed once before `ACTION_OPEN_DOCUMENT`,
OPENABLE/`*/*`, single-document mode with temporary read access. Route identity/code/
epoch/nonce are strictly bounded in one <=2 KiB tested Bundle contract; no URI.
Unknown/malformed saved state retires the logical route rather than restarting
codes at 1; exhaustion requires a fresh Activity route, never wrap/rebind. Only a
matching single content URI with read flag reaches controller consumption; null,
multiple, conflicting/non-content/ungranted/stale results cannot open a source.
Picker cancellation/launch failure clears only its matching token. Actual readable
provider access is still checked off-main and can fail safely. No persistent grant,
external import filter or test/path control was added.

Six Android memory/dispatch tests **compile, not run**: SAF flags/opaque saved
bounds, restored matching/single consumption, bad inputs, failed dispatch/code
non-reuse, malformed saved routes and exhaustion/retirement. Dispatch fakes never
invoke the owner's picker/store. Full command
`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 25s** (80 tasks / 18 executed): **219 JVM tests**, zero failures/errors/
skips; lint **0 errors / 4 baseline warnings**, both APKs pass. An intermediate
`InflateParams` warning was fixed using Activity `setContentView`/content root,
not suppressed. No Android runtime/lifecycle/visual/picker or human/ship result.

Decoded **both actual APK manifests** using
`D:/softwares/android-sdk/cmdline-tools/latest/bin/apkanalyzer.bat`, installed
command-line tools **12.0** (`source.properties` retained), existing JDK 17.
Production has exactly one exported **MAIN/LAUNCHER** Activity and no VIEW/SEND,
permissions, provider, service or receiver. The fixture provider remains test-only.
All 14 packaged native hashes match the unchanged stage.
Evidence `.cache/model-import/mi-11-20261116T162132Z/`: exact logs/exit codes,
fourteen JVM reports/lint, decoded declarations/tool provenance and source/artifact
hashes. Production APK `88b7669755b1ac40a0f5ebab373abbb3a669472147b1b92e2d92c1286c4efa51`;
test APK `8a7eb4f0fb49e849abd7d7b48346036ded6a08b69c9083fd13c5a57169a32e11`
(975,121 bytes). Five-file scope and excluded owner work preserved. No phone access.

## MI-12 — Recreation ownership and transient result handoff

Commits `a6f05b9`, `2c401db`; four of the planned five files changed:
`MainActivity.kt`, `AndroidPickerRoute.kt`, `AndroidPickerRouteTest.kt`,
`ModelImportControllerTest.kt`. Existing `PickerCoordinator` phase guards are reused
unchanged. One route/result object is retained only through Android in-process
non-configuration retention, with **no Activity/View/callback** references. It can
hold one token-bound URI while recovery is busy; handoff clears only that exact
result when a controller command reserves ownership. Bundle saving excludes the
URI; process restoration never reopens a dead copy. Destroying a non-configuration
route retires it and drops transient data. Duplicate UI actions cannot create a
second command/launch; stopped/destroyed observers detach.

Four new JVM controller regressions pass: replacing an observer mid-copy without
another writer/old delivery, passive reattachment before/after launch claim, a new
fake file-image/store owner recovering an outstanding request before a delivered
result can start, and old-A/wrong-epoch callbacks before/after B consumption.
These are **not actual Android rotation or process-death tests**. Three additional
Android memory contracts compile: retained-vs-saved transient result, stale handoff/
retirement, and an adversarial malformed Intent getter. F3 review found the last
parser exception could escape, so it now becomes a fixed Invalid result without
raw exception/URI diagnostics. No instrumentation is executed here.

Final current-source command `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`
passes **in 25s**, 80 tasks / 20 executed: **223 JVM tests**, zero failures/errors/
skips; lint **0 errors / 4 baseline warnings**, both APKs pass. Twenty model-import
Android methods compile only. Evidence `.cache/model-import/mi-12-20261116T163622Z/`
retains focused/final logs, fourteen JVM reports/lint, decoded APK manifests, source/
artifact identities and preservation review. Production APK
`caf3826dd85a24e00d634c29bd74aa5c90f4b79dfb99762cf1cfc5e07f562796`;
test APK `0e89543ba3185181f52a05040cf6ebb45760bf285e8e3d9db3aa0cf5a1a908c3`
(976,319 bytes). Native hashes and all excluded owner inputs unchanged. No phone
access or Android runtime/human/ship completion claim.

## F3 technical review — 2026-11-16

Actually reviewed 14 exact Activity/route/screen/controller/provider/test/resource/
manifest files, current reports/APK hashes and declarations. Audit:
`.cache/model-import/f3-20261116T164425Z/review-audit.json`. The malformed-result
getter exception was fixed and recompiled/regression-tested, not waived. Reviewed
observer detachment, in-process-only transient URI ownership, no saved/dead-copy
resume, stale callback/code/epoch rejection, separated active/pending display and
test-only gated producer/isolated-root cleanup. Reset UI is still hidden/unwired;
no owner selection reset/replacement was executed by these host checks. No
unresolved host-contract blocker for MI-13. Actual Android SAF, visual/lifecycle,
provider/process-death and physical fixtures remain unverified until approved
MI-20/MI-21 batches. This is assistant technical review, not invented human approval.

## MI-13 — Explicit authorization and repeatable reset recovery

Implemented in four planned substantive files (`ModelReset`, `ModelStore`, reset/recovery tests); the fake required no change. Commits `952c69f`, `4ccf099`, `dbf0963`. Only a verified authoritative marker permits payload/selection deletion; unknown names, links, hardlinks and failed removals stay blocked and untouched. Preflight precedes sweeping. The marker's recorded empty epoch survives retries, `reset.next` is removed first and `reset` last after verified empty publication. Store descriptors/writers retain their lease while pending; explicit confirmation retires only stopped logical candidate ownership. Declining has no storage operation; UI consent binding is MI-14.

**Reviewed MI-13R prerequisite split:** review reproduced a gap in fresh corrupt-marker repair: deleting the corrupt base before writing its replacement could lose unresolved authorization if writing failed. Added a separate two-file checked-record prerequisite (`CheckedModelRecord`, its tests), commit `a63f203`, rather than silently adding a sixth MI-13 file. `repairReset` requires explicit reset-slot consent and a checked regular single-link base identity, keeps that base until atomic rename, and reconciles OLD/NEW/AMBIGUOUS through the existing checked protocol. No selection repair bypass. The assertion RED is retained; failed repair now preserves original corrupt bytes and cannot unblock ordinary recovery. This is technical decomposition under standing host authorization, not additional human/device approval.

Meaningful RED: initial reset 3/3 assertions failed, integration 3/27 failed, corrupt-marker preservation 1/10 failed (final assertion run; initial missing-key reproduction also retained). GREEN/final command: `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`, **BUILD SUCCESSFUL in 27s**, 80 tasks / 15 executed, **238 JVM passes**, no failures/errors/skips. Lint 0 errors / 4 baseline warnings; both APKs pass. Evidence pointer `.cache/model-import/mi-13-current-path.txt` identifies exact logs/reports/source hashes/`results.json`; production APK `9b532628c89e333c0028a40d84965e3dd716534bda555a1703ad7a524daee08f`, test APK unchanged `0e89543ba3185181f52a05040cf6ebb45760bf285e8e3d9db3aa0cf5a1a908c3`. All 14 packaged native hashes match staging. Twenty Android methods remain compiled only. No phone/store-owner access, recursive deletion, app-data clearing, toolchain/native changes or actual process-death claim. MI-14 next under standing authorization.

## MI-14 — Current in-app consent and checkpoint B

Controller commits `5530c11`, screen/resources/tests `fb56faa`, dialog-identity correction `89e515b`. The five planned files implement revision-bound, single-use recovery-only confirmation and reject healthy/stale/busy/source-helper-pending snapshots. The short in-memory gate reserves the one command before worker I/O. Successful reset exposes the verified empty state, never an inference/import success; failed deletion stays unavailable for a fresh explicit retry. New picker recovery invalidates old-epoch tokens. Four new JVM controller tests pass, with meaningful RED 2/25 assertions retained.

The dialog explains irreversible private-copy/selection removal and source preservation, defaults to no action on decline/back/dismiss, persists no confirmation, and is closed on destruction or revision changes. Review caught a delayed old-dialog callback at the *same* revision: opaque per-dialog identity now rejects both old positive and dismissal callbacks. Two new Android screen tests compile only (22 total model-import Android methods), covering decline/destruction, stale/current/single-use consent, and superseded same-revision callbacks. Real dialog/rotation/visual results remain MI-20/MI-21.

**Reviewed MI-14W integration split:** the approved five-file task does not include the Activity callback; leaving it unwired would not deliver reset. A separate one-file `MainActivity` slice (`9e3f1a4`) binds the existing screen's confirmed revision to the controller. No intent extras, external reset entry point, production test path or sixth-file silent expansion. Tested/committed independently under standing host authorization.

Final current-source full Gradle command passes **in 25s**, 80 tasks / 20 executed, **242 JVM passes**, no failures/errors/skips; lint 0 errors / 4 baseline warnings, both APKs pass. `.cache/model-import/mi-14-current-path.txt` points to retained RED/GREEN/final logs, JUnit/lint, decoded declarations and `results.json`. Production APK `247b5f6f1a1d9613de732871d988049bd3c15124df0bb476edd117487f0b29ec`; test APK `d7c6d6fb86d7ca59cdbb5b4f0a5cb396cf39b24538b3964273a229cbb299fccb`. Fourteen packaged native libraries match staging; no new production permissions/components or external import/reset routes.

**Actual B technical review:** 16 exact source/test/resource/manifest files plus reports/artifacts reviewed; retained `.cache/model-import/b-20261116T170332Z/review-audit.json`. Reviewed authorization ordering/repair, stopped-resource lease retention, scope/isolation, confirmation lifetime/identity, callback wiring and no old-token relaunch. Both findings above were fixed/tested, not waived. No unresolved host-contract blocker for MI-15. This is an assistant technical review under the uninterrupted-host-work instruction, not fabricated human acceptance or Android/device evidence. No phone access; source/owner-state mutation has not been tested on a device.

## MI-15 — Isolated Android contract suite compiled

Commit `7d1e7f1` adds four `AndroidModelStoreTest` methods and the explicit four-class `ModelImportIsolatedTestSuite` (adapter, screen, route, store only). Real-adapter cases cover exclusive creation/sync/rename/descriptor closure, no-follow link refusal without truncating old bytes, authoritative-selection versus sidecar recovery, and committed-versus-uncommitted reset. Every mutation uses `ModelImportTestStore`'s fresh generated no-backup root and root lease; the one explicit fixed test-created link is removed in `finally`, never through production recursive cleanup. No adapter fix or helper change was needed. Native/crash/hang fixtures are excluded from the ordinary suite.

Host command `./gradlew --no-daemon :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL in 18s**, 75 tasks / 8 executed; lint remains 0 errors / 4 baseline warnings. `.cache/model-import/mi-15-current-path.txt` retains the exact build log. **26 model-import Android methods compile, none run.** The 242 JVM results remain applicable (no JVM/production input change); no unchanged JVM rerun. This is not evidence of actual Android filesystem/provider/process-death behavior. No phone access. MI-16 next.

## MI-16 — Two-phase process-death fixtures compiled / H0 review

Four planned instrumentation files only, commits `06e1bb4`, `5d426e1`, `c0e4579`, `fbfdbd8`. Validated 32-hex run ids map exclusively to generated `noBackupFilesDir/model-import-tests/<id>/models`; setup refuses an existing run, verification requires it. Reopening acquires the same root lease. Optional file-operation decoration exists only in the instrumentation helper, never the production store/Activity. Missing/bad orchestration/scenario/checkpoint arguments fail before fixture creation.

Setup uses real Android file operations with test-only wrappers at all 23 first/replace/reset pairs. Synced-temp checkpoints occur after actual descriptor close before rename; committed checkpoints occur immediately after real rename; retired/reset deletion checkpoints occur after real unlink; source/candidate closure precedes the streams-closed marker. Markers use complete custom status frames 1001/1002 and exactly seven bounded fields, including PID/UID/process-start identity. Setup pauses with a finite 120-second failure fallback; it must be externally terminated, NEVER counted as a passing test. Request reservation/consumption is checked before source opening.

Separate verification establishes authoritative old/new/empty selection, full fixture bytes/metadata, pending-request state with no automatic claim/restarted copy, intended reset epoch, no dangling candidate/debt, and then removes its own files/lease *before* emitting verified. Uncommitted `reset.next` preserves complete old metadata/bytes and blocks reset until explicit consent; it is never promoted. Host MI-17A must prove actual old-target termination and a distinct restarted process identity; these test classes alone do not prove process death. Setup/verify are excluded from the fixed ordinary suite. Failed/interrupted setup roots remain isolated and require the host's separately filtered verification/cleanup, never owner-root deletion.

Initial setup compilation found a wrong metadata type name, fixed to the existing `ModelMetadata` without changing production. Final command `./gradlew --no-daemon :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` passes **in 18s**, 75 tasks / 8 executed; lint 0 errors / 4 baseline warnings. 26 ordinary + 2 crash methods compile, **none run**, 23 scenario pairs NOT executed. Unchanged 242 JVM results remain applicable. Evidence `.cache/model-import/mi-16-current-path.txt`, including failed/corrected/final logs and `results.json`. Production APK remains `247b5f6f1a1d9613de732871d988049bd3c15124df0bb476edd117487f0b29ec`; test APK `72124e88ffc35a7b203219211bde1c83aaf704a229421f500061e63bc8ee4ef7`.

**Actual H0 technical review:** seven exact fixture/provider/suite files, source/APK identities and exclusion checks retained via `.cache/model-import/h0-current-path.txt` (`review-audit.json`). Reviewed no-backup namespace/lease/reopen semantics, separate provider package, bounded fixture creation, marker ordering, complete old metadata/length assertions, finite pause fallback and setup-vs-expected-pass exclusions. No unresolved host-contract blocker for MI-17. Assistant technical review only; no human/device result fabricated, no phone inventory or writes. Real platform/process termination, retained-installation and provider behavior remain blocked on MI-20/MI-22 consent.

## MI-17 — Bounded filtered host execution / fake ADB

Three planned Python files only plus this evidence: commits `f951be4`, `a680c67`, `e7da25c`, `c45f4ce`, `b28ae7d`, `c8fd0cf`. Entry script owns fixed packages/runner/two filters and explicit 26-method isolated/one-method native rosters, checked against instrumentation source (including the required provider/controller/view unknown-size case). Generic standard-library helper owns nonblocking pipe polling, subprocess reaping, limits/deadlines, APK/UID identity and independent teardown. No reader threads can be stranded by inherited-open pipes. Output/line limits apply before accumulation; errors/evidence retain only fixed reasons and bounded parsed fields, **no raw diagnostic output/serials/URIs/commands**. No diagnostic tail is retained (zero is below the 64 KiB cap).

`--help` has no ADB calls. Actual execution requires both explicit write flag and private `ANDROID_SERIAL`; no arbitrary class/package/path/root input, installation/uninstall/clear/downgrade or server-kill command. Existing/linked evidence directories are refused and reports use exclusive creation. Preflight verifies both installed base-APK byte streams against fixed local APKs and literal package UIDs (including a reproduced/fixed regex-dot identity gap); initially active test packages are refused before test mutation. Pre/postflight caps are 120 seconds; short controls <=15 seconds. Suite budgets reserve the final 30 seconds for local transport reaping, independently stopping both packages and checking all their UID processes gone; changed APK/UID or failed cleanup cannot become a pass. The baseline is post-install/pre-test, not an old pre-update package hash.

Meaningful RED parser/transport/identity/suite/hardening assertions retained, then GREEN. Final `python -m unittest discover -s scripts/tests -p 'test_model_import_recovery.py' -v`: **22 host tests pass**, zero failures/errors/skips, 0.488s. Python subprocess fixtures/fake ADB only; **no ADB/device contact**. `python scripts/check_model_import_recovery.py --help` passes without device selection. Evidence `.cache/model-import/mi-17-current-path.txt` retains RED/GREEN/final logs/help/source hashes/`results.json`. 242 JVM and latest APK/Android compilation remain applicable (their inputs unchanged, no redundant Gradle rerun). Crash-marker orchestration is still MI-17A; no actual retained-installation or instrumentation result claimed. Git commit succeeded despite a non-fatal auto-maintenance pack-index permission warning; no unrelated Git pack cleanup/config change was attempted.

## MI-17A — Retained process-death orchestration / H1 review

Commits `53272d9`, `edd8464`, `c0e1113`, `ba18f80`, three Python files plus evidence only. CLI now supports exact first/replace/reset pairs or per-scenario/all matrices; suite/checkpoint modes are mutually exclusive. `--scenario all` requires `--checkpoint all` (no silently filtered subset); explicit inapplicable pairs fail before even creating evidence/contacting a device. Fresh generated run ids correlate fixed test-only setup/verification filters; neither arbitrary root nor external run id is accepted.

Ready status 1001 requires a complete <=1,024-byte, exact-key/schema/type/correlation/UID frame after the exact setup method starts. Duplicate/stale/noisy/unframed/wrong-type markers and premature setup completion fail. Review additionally reproduced/fixed incomplete extra output after ready. The setup must still be live and its PID/UID/name match the sole target-UID process. Controlled stop independently stops both packages and proves all their UID processes gone *within work budget*, before separately filtered verification starts. Verification needs exact 1002 plus complete successful nonzero method reporting and a distinct `(pid, startedElapsedMs)` pair (PID reuse alone is allowed, same process is not).

Each case reserves its final 30 seconds within 180 seconds; setup cap30, verification90, control<=15, independent final transport/provider/target teardown, and separate pre/postflight120 caps remain enforced. Every row compares against the original matrix post-install/pre-test APK/UID baseline. Failures stop further mutation and list later requested rows explicitly `not_run`; no skipped pair is represented as success. If interruption/marker/verification fails, `fixture_cleanup=false` stays truthful: process teardown is separately proved, but isolated fixture bytes may remain for reviewed follow-up. There is no owner-root/app-data deletion fallback and no claim that cleanup turned the failed case into a pass.

Final `python -m unittest discover -s scripts/tests -p 'test_model_import_recovery.py' -v`: **33 host fake/transport tests pass**, no failures/errors/skips, 0.610s; all **23 first/replace/reset pairs** pass fake orchestration. Marker RED 16 assertions, pair RED23 assertions and partial-ready regression RED retained. Fault coverage includes each setup/stop/verification/final-teardown transition, missing/stale/wrong UID/start frames, incomplete/skipped verification, transport timeout, retained identity and explicit matrix halt. `--help` passes with no ADB. Evidence `.cache/model-import/mi-17a-current-path.txt`; unchanged Android/APK/JVM evidence applies, no redundant build. **No actual process death, phone contact or retained-installation test has occurred.**

**Actual H1 technical review:** eleven exact Python/fixture/provider/suite/manifest files and fixed-roster/checkpoint correspondence reviewed; `.cache/model-import/h1-current-path.txt` points to source hashes/results/`review-audit.json`. Reviewed bounded nonblocking transport/reaping, source roster exclusions, private output, reset/cancel safety boundaries, original APK/UID baseline, complete framing and separate stop/restart identity. Partial-ready finding fixed/tested, not waived. No unresolved host-contract blocker for MI-18. Assistant technical review only; explicit device-write consent remains absent. The physical-phone gate is still MI-20, not bypassed by fake ADB.

## MI-18 — Non-cooperative cancellation fixtures compiled

Exactly five planned substantive files, commits `80e18d9`, `5e5ae8d`, `23bda5b`, `0bee4b8`, `4a0189c`; generic device helper needed no change. The test-only provider adds token-guarded query/open/read sessions with <=120-second fallback, fixed generated bytes, no owner paths and bounded producer ownership/finish join. Query/open deliberately ignore cancellation while held; open is explicitly released *after accepted Cancel* to exercise late-descriptor publication/closure. Read holds a reliable pipe after the 24-byte prefix. The close case wraps the acquired source with an explicitly **simulated** blocked close (not proof of every OEM close implementation), with a finite release/finally-close path.

Separate `ModelImportCancellationDeviceTest` setup uses an isolated root, actual Android adapter/engine/controller, one I/O worker and <=2 captured helpers. Compiled assertions check main-Looper responsiveness, repeated Cancel/helper bounds, no second command/reset while busy, complete cancellation or truthful pending work, unchanged old bytes, and late open lease closure. A kernel may leave a read syscall in flight after descriptor close; the test requires continued command exclusion rather than inventing source-helper nonquiescence. Separate post-restart verification checks full old selection/bytes, no dead-copy/picker restart, no candidate/debt, and closes fixture storage before verified status. The ordinary four-class/26-method suite excludes both cancellation methods.

Host tables now contain **27 exact pairs** (23 prior + four cancel-hang); the latter use only the separate cancellation setup/verify filters and the same checked stop/restart, provider-UID teardown, retained APK/UID baseline and budgets. Meaningful host RED 1/34 assertion, then final **34 Python fake/transport passes**, zero failures/errors/skips, 0.643s; all 27 pairs simulate successfully and provider-process teardown is explicitly checked. `--help` passes without ADB. Latest Android lint/both APK build **19s**, 75 tasks / 8 executed, 0 errors / 4 baseline warnings. **26 ordinary + 4 crash/cancellation methods compile, none run.** 242 JVM results still apply (production unchanged). Evidence `.cache/model-import/mi-18-current-path.txt` retains logs/help/source/APK identities/`results.json`; production APK unchanged `247b5f6f1a1d9613de732871d988049bd3c15124df0bb476edd117487f0b29ec`, test APK `d4c0c1b94c305470537b12b4c20a42fef92071a12821655dffed672b7feec659`. No ADB/phone contact, no actual provider cancellation/process termination claim. MI-19 next under standing authorization.

## MI-19 — Clean-output host regression and H2 device gate

Verified implementation source HEAD `78a7004b242c5b9edca355e59ca8e0c49a433e5b` in the accepted existing worktree, **not a clean-checkout reproduction**. JDK Adoptium17.0.21.9, Gradle8.10.2, Python3.14.7, SDK `D:/softwares/android-sdk`, command-line tools12.0 and build-tools35.0.0; no toolchain/dependency/native changes. The native packaging subprocess alone received scoped `ANDROID_HOME`/`ANDROID_SDK_ROOT` for its disposable copy (the main checkout uses existing `local.properties`). Exact versions, commands/logs/source identities are retained via `.cache/model-import/mi-19-current-path.txt`.

| Executed host check | Actual result |
|---|---|
| `python scripts/import_llama_release.py --verify-only` | Exact pinned native payload/ELF/receipt/transaction state passes. |
| `python -m unittest discover -s scripts/tests -p 'test_*.py' -v` | **128 tests pass**, 0 failures/errors/skips, 2.603s; includes 34 new driver/protocol/transport tests. |
| `./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` | **BUILD SUCCESSFUL in 38s; all 81 tasks executed**. 242 JVM passes, 0 failures/errors/skips; lint0 errors/4 baseline warnings; both clean-output APKs built. |
| `python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk` | ZIP/native bytes/attribution audit passes; no weights/unrelated native payloads. |
| `python scripts/tests/check_native_packaging.py -v` | **12 integration tests pass**, 0 failures/errors/skips, 103.109s; only its disposable host project is mutated. Previous `.cache/blocker3` logs were copied before overwrite; current logs also retained with MI-19 evidence. |
| SDK12.0 `apkanalyzer.bat manifest print` on both APKs | Decoded declarations independently audited as below; no device contact. |
| build-tools35.0.0 `apksigner.bat verify --verbose --print-certs` on both APKs | Both debug signatures verify and their public signer digests match; no claim that an existing phone installation accepts this key. |

Current clean-output artifacts (these **supersede, and are not byte-identical to,
earlier incremental APKs**; all 14 pinned native members/attribution are unchanged):

| APK | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 79,332,034 | `76284abdf487403266e26114a24246638b4422a1dee3e0d84f6dd7ec2069f291` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 993,738 | `3867922538ccec2a1abe25b9c6496440e69dee7dc41b7e3ee89cce3850abbddc` |

Decoded production manifest: one exported MAIN/LAUNCHER Activity only; no new permission, service, provider, receiver or external import/reset route. Test manifest alone contains the separately processed validated fixture provider and fixed runner/target package. Seven fixture/suite/crash/helper class names and checkpoint/hang controls are absent from production DEX and present in test DEX. Production assets are exactly upstream license/notices; no bundled GGUF. The ordinary suite still has exactly four classes / 26 expected methods, native smoke one separate method; 27 crash/cancel pairs and 30 model-import Android methods are compiled **not run**. All payload/selection/picker/reset files are designed under `noBackupFilesDir/models`; static placement does not prove OEM backup/transfer behavior. Native smoke only uses `--version`, not a model/server/listener.

Preservation audit: **41 excluded owner files** (including all 36 `app/.cxx` files and dirty planning/ignore/spec inputs) and **94 source/stage/tool inputs** match preflight hashes after clean build, full tests and packaging integration. No stash/reset/owner cleanup, generated weights/native commit, production model-root access or phone operation. Tracker bookkeeping remains mixed with owner planning changes and is deliberately not staged wholesale.

### H2 technical review — host-ready, not runtime-ready

`.cache/model-import/h2-current-path.txt` points to `review-audit.json`; MI-19 retains JUnit/lint, full logs, decoded manifests, signature checks, source/tool/artifact identities and `results.json`. Focused source/changes across reset/controller/UI, Android adapters, namespace/provider/cancellation fixtures, Python driver/parser/transport, manifests and native smoke were actually reviewed (27 files); the 48-source inventory was separately rehashed against unchanged foundation evidence, **not described as a fresh full reread of every source**. Reviewed consent lifetime, atomic marker repair/ordering, helper/writer exclusion, genuine stopped-process identity, bounded transport/teardown, privacy, fixed filters and test-only controls. No unresolved host-contract blocker for the phone gate. This is assistant technical review, not human approval or Android behavioral evidence.

**STOP: MI-20 requires a phone and separate explicit approval.** Approval must cover private S25/package/storage inventory; retained updates of the two exact audited APKs (including test package presence/signature handling); isolated 26-method suite and separate native `--version` smoke; bounded force-stop/fixture-provider teardown for **both** fixed packages; and the intended existing-selection/data impact. No uninstall, downgrade, data clear, disk filling, owner-model/source deletion or automatic reset is authorized. Signature/update failure blocks rather than permits uninstall. Establish the **post-install/pre-test** APK/UID baseline before suites; pre-update hashes are intentionally not that baseline.

MI-21 needs separately approved real SAF/lifecycle and >2 GiB test-owned fixtures; MI-22 needs controlled interruption/cancel matrix approval and cannot overlap manual import. Physical results, phone/OEM compatibility, actual disk-full/backup transport/low-memory/power-loss behavior, inference/WebUI and full module/ship readiness remain explicitly unverified. The standing Definition of Done's runtime/human gates are not waived by these host results.

## MI-20 — Consent recorded; device connection blocked — 2026-11-17

The owner replied **“approve”** to the explicit MI-20 request: private inventory, retained updates of both audited APKs, bounded isolated/native tests and bounded stops of both fixed packages/provider. This is new, separate batch consent, not an inference from the old native-runtime/task-list approval. It does **not** authorize MI-21 production-selection/manual/large-file work or MI-22 interruption scenarios, uninstall/downgrade/data clear, owner-model/source deletion, automatic reset or disk filling.

Local debug/test APK hashes still match the MI-19 audited identities. The authorized inventory attempt used bounded captured `adb devices -l`, retaining no serial or raw device output. It could not privately select a connected device; the follow-up redacted diagnostic returned exit0 with **0 authorized, 0 unauthorized, 0 offline, 0 permission-denied devices**, and no preselected `ANDROID_SERIAL`. No device-specific property/package/storage/model-root inventory, installation, instrumentation or package stop was attempted. Package presence, existing selection/data impact and available phone storage are consequently **unknown**, not assumed empty or fresh.

Evidence pointer `.cache/model-import/mi-20-current-path.txt` identifies `.cache/model-import/mi-20-20261117T121237Z/`: `inventory.json` records the consent/safe block, `connection-status.json` the redacted connection counts. MI-20 remains unchecked and no Android/runtime acceptance is claimed. Production/test code and artifacts did not change; unchanged passing host checks were not rerun. Resume after the S25 is connected with USB debugging authorized; inventory existing packages/data/storage **before** any retained update. Initially absent packages need explicit installation consent, and any signature/update failure blocks rather than permits uninstall. Then establish the post-install/pre-test APK/UID baseline and use only the approved bounded wrapper for the isolated26 and separate native1 filters.

## MI-20 — Wireless S25 batch: native pass, isolated failure — 2026-11-17

This supersedes the earlier missing-device blocker. The owner said **“wireless
adb connected”**; the already recorded MI-20 consent was used, not requested again.
Evidence pointer `.cache/model-import/mi-20-current-path.txt` now identifies
`.cache/model-import/mi-20-20261117T141619Z/`. No serial, wireless endpoint, URI,
raw device output or provider document name is retained in evidence.

### Inventory and retained installation

- Exactly one authorized device: `SM-S931B`, `arm64-v8a`, reporting Android 17 /
  API 37. Both fixed packages existed, UIDs **10437 / 10439**. No fresh-install
  assumption or additional installation consent was needed. No package processes
  were active. `/data` had **131,193,332 KiB available** (about 125 GiB).
- Before updates, `no_backup/models`, legacy `files/models`, `files/model.gguf`
  and all four selection/reset record paths were absent. Inventory did not open
  the app, import a document, initialize the production singleton or reset data.
- Three real host-contract blockers were fixed in separate tested commits:
  **`31057d1`** accepts up to 64 KiB of process output, still rejects overflow;
  **`8b03b6e`** accepts names containing spaces without ignoring target-UID
  children or weakening UID checks; **`eaabab2`** selects exactly one literal
  package row while validating every row, rejecting duplicates/malformed output.
  The S25 process sample was **39,718 bytes / 1,140 rows**, with five names
  containing spaces. The production package filter returned both target and test
  rows; UIDs had not actually changed. All these pre-install gates stopped before
  phone writes. RED logs prove each regression before its fix; successive full
  Python results were **129 / 131 / 133 passes**, final **3.490 s**. No Gradle/app
  source or APK bytes changed, so unchanged successful builds were not repeated.
- Both approved updates then succeeded, using bounded captured `install -r` and
  `install -r -t`. No uninstall, downgrade, data clear or signature fallback.
  `post-install-baseline.json` records the installed-byte SHA-256/UID baseline
  **before any suite**, distinct from the deliberately replaced pre-update hashes.
  Production remains **`76284abdf487403266e26114a24246638b4422a1dee3e0d84f6dd7ec2069f291`**;
  test remains **`3867922538ccec2a1abe25b9c6496440e69dee7dc41b7e3ee89cce3850abbddc`**.

### Actual bounded test results

Only the approved fixed `--suite isolated` and `--suite native` filters ran,
through the reviewed wrapper. A private in-memory selector and a baseline-checking
session bound each run to the original post-install identities. No unfiltered
instrumentation, crash/cancel matrix or manual production import ran.

- `isolated/report.json`: **FAIL**, `failed skipped or duplicate test`, 0 fully
  accepted suite tests. This means incomplete reporting, not that no method ran.
- `isolated-diagnostic-run/report.json`: **FAIL**. The observer retained only
  known-roster method/status identities, exception types and code source frames.
  `documentIoRefusesMainThreadAndNonContentInputs` reported start/pass;
  `optionalNullEmptyMissingAndOverflowMetadataNeverInventsASize` reported failure
  `-2`, `java.lang.AssertionError`, **`AndroidImportIoTest.kt:98`**. A subsequent
  test had started before the wrapper stopped the process; it is not counted as
  passed and fixture cleanup is not asserted.
- `native/report.json`: **PASS, 1 test**, complete nonzero JUnit framing,
  `teardown=true`, `retained=true`. This validates this S25's installed pinned
  native payload hashes, `--version` launch/exit and bounded child cleanup only;
  it does not validate inference, a model, server readiness or the WebUI.
- `isolated-numeric-diagnostic/report.json`: **FAIL**. One final diagnostic run of
  the same approved suite retained only the numeric assertion shape: **expected
  50, actual null**. No raw failure text/URI was saved. No more unchanged retries.
- Every run independently verified the same APK/UID baseline afterward and
  stopped both packages/provider. `postflight.json` additionally confirms both
  packages quiet and all three checked production model paths still absent,
  matching pre-update inventory. No owner file/model deletion, production reset,
  disk filling or source access occurred. Interrupted test-fixture cleanup is
  not promoted to a success claim.

### Fixture blocker and next artifact gate

`provider-classpath-audit.json` and host-only DEX listings establish that
`FixtureModelProvider` references `kotlin.collections.SetsKt`,
`kotlin.jvm.internal.Intrinsics` and other Kotlin runtime helpers not defined in
its standalone test APK. The target APK's runtime classes do not make a test-only
provider's separate package/process self-contained. This is a concrete packaging
contract gap consistent with null queries; bounded sanitized logcat checks did
not obtain a device exception, so that exact causal stack remains unverified.

**MI-20 is NOT complete.** Next smallest slice: make the test provider
self-contained without moving it into production or depending on target helper
classes; host-test/build and audit the revised test APK, preserve the current
artifact/baseline evidence, then obtain explicit scope for its retained update and
another bounded isolated run. The old consent covered the two exact old APKs,
not an unreviewed replacement. MI-21 and MI-22 remain separately gated. No new
APK, provider rewrite, backup/disk-full test or manual owner import was performed
in this batch.

## MI-20 — Approved self-contained fixture repair; active-app gate — 2026-11-17

The owner's subsequent **“approve”** explicitly covers fixing the test-only
provider, rebuilding/re-auditing its APK, retained-updating that revised test APK
and a bounded isolated rerun. This is the new artifact scope, not repeated consent
for the old batch. It does not authorize MI-21/MI-22 or owner-data deletion/reset.

Evidence: `.cache/model-import/mi-20-fixture-current-path.txt` identifies
`.cache/model-import/mi-20-fixture-20261117T144639Z/`. Before builds, original
production/test APK copies and original installed-baseline JSON were preserved;
41 owner-file and production-input hashes were captured. The revised APK is
separately preserved as `revised-test.apk`.

### Incremental implementation and host verification

- **`68734fd`**: staged test-only Java metadata/file provider; original manifest
  remained active. Build/lint/unit-test task gate **PASS, 41 s**; new DEX had no
  Kotlin references.
- **`89504d0`**: ported reliable short/error and gated unknown-size pipes with
  existing ownership, finite waits, token checks and joins. Same gate **PASS, 30 s**.
- **`30dcc25`**: ported the existing query/open/read hang fixtures and finite
  cancellation protocols without running them. Same gate **PASS, 33 s**.
- **`d7590df`**: selected `StandaloneFixtureProvider` in the test manifest and
  removed `FixtureModelProvider.kt`. Same gate **PASS, 32 s**; final Python suite
  **133/133 PASS, 3.759 s**; lint **0 errors / 4 baseline warnings**. JVM test task
  was **UP-TO-DATE** in these builds; unchanged prior 242 JVM results are not
  misrepresented as newly executed tests.
- Actual side-by-side source review checked all 15 fixture IDs, caller UID/debug
  signature validation, URI/token validation, 50 generated GGUF bytes, read-only
  descriptors/slices, reliable errors, no owner paths, bounded waits/worker joins
  and cleanup ownership. All existing provider modes remain compiled; this is an
  assistant technical review, not fabricated human/platform acceptance.
- Final decoded test manifest retains the test authority and separate
  `:model_import_fixture` process. DEX audit covers the provider, two state classes,
  two lambda classes and D8's `-IA` synthetic class: only platform classes and
  those six self-contained test classes are referenced. **Zero Kotlin or target
  helper references**, old provider classes absent. An initial audit enumerator
  missed the `-IA` suffix; enumeration was corrected, without a product change or
  repeat build. All six class bodies were actually inspected by the audit.
- APK signature verification passes and matches the original public debug signer.
  Revised test APK: **993,738 bytes**, SHA-256
  **`660379acb43023afdafdbd0b23509f18ab890ffadc88af7244b0f65a4c9eb1fa`**.
  Production remains byte-identical at
  **`76284abdf487403266e26114a24246638b4422a1dee3e0d84f6dd7ec2069f291`**.
  `apk-audit.json` verifies all preflight owner/production-input hashes before
  subsequent intentional tracker bookkeeping. No dependencies/build settings,
  native payloads, production code, suite filters or test rosters changed.

### Actual retry gate and next action

Captured bounded read-only checks privately selected the S25 and matched both
installed APK bytes/UIDs against the **original installed baseline**. However,
`target_main` UID 10437 was active. Additional sanitized read-only inventory found
`no_backup/models` now present; the two checked legacy paths remain absent.
`retained-update.json` records **completed=false, phone_writes=false**;
`active-package-diagnostic.json` records only fixed process roles/path presence.
No revised installation, package stop or test was attempted. Owner data appearing
between batches is not a cleanup target and is not attributed to the host build.

**Paused for owner quiescence, not missing approval:** finish/cancel any manual
import and confirm ready. A cached main process can remain after closing its UI;
once the owner confirms no concurrent manual import, use the already approved
bounded package stops, re-inventory the current model store read-only, preserve
its new baseline, and retained-update **only the audited revised test APK**.
Never demand an absent model directory or delete/reset it to satisfy the old
inventory. Establish a new post-install/pre-test APK/UID baseline, then run the
fixed bounded isolated wrapper with sanitized failure diagnostics if needed.
The original native pass remains attributed to its original test APK and unchanged
production APK; no native run on the revised APK is invented. MI-21/MI-22 remain
separate gates. The earlier provider packaging gap is fixed on host; whether it
explains the device's null metadata is still **unverified until the retry**.

## MI-20 — Approved isolated retry PASS and production-store preservation — 2026-11-17

The owner replied **“ready”**, confirming the requested manual-import quiescence.
Existing approval covered the revised test APK, fixed bounded isolated retry and
bounded stops of both packages/provider; no repeated approval was requested.
Evidence pointer `.cache/model-import/mi-20-retry-current-path.txt` identifies
`.cache/model-import/mi-20-retry-20261117T150701Z/`. Earlier batch/fixture evidence
pointers are preserved, not overwritten. No raw serial, wireless endpoint, source
URI, owner label or raw instrumentation output is retained.

### Inventory, retained update and baseline

- Private S25 identity checks succeeded; installed original APK bytes/UIDs matched
  the preserved original baseline. Both packages were explicitly bounded-stopped,
  with all matching UID processes quiet, before store inventory. This used the
  owner's readiness confirmation rather than assuming a cached main process
  meant an ongoing manual import.
- The now-existing production `no_backup/models` was preserved, **not deleted or
  required to be absent**. Read-only no-follow/type/link checks found **one
  selection record, one payload, zero candidates**. Inventory records root/entry
  inode, mode, link count, size and mtime; selection bytes are SHA-256 hashed, and
  generated payload names are represented only by hashes. No payload body was
  read/hashed and no claim of full GGUF validity/content integrity is made.
  Both legacy model paths remain absent. `/data` had **129,378,608 KiB available**.
- Only pinned audited `revised-test.apk` was installed using bounded captured
  **`install -r -t`**, with success acknowledgement. Production was not reinstalled
  or changed. No uninstall, downgrade, data clear, reset, signature fallback,
  document grant/source access or production selection replacement occurred.
- Actual installed-byte hashing verified production
  **`76284abdf487403266e26114a24246638b4422a1dee3e0d84f6dd7ec2069f291`**
  and revised test
  **`660379acb43023afdafdbd0b23509f18ab890ffadc88af7244b0f65a4c9eb1fa`**;
  UIDs **10437 / 10439** are unchanged. `post-install-baseline.json` was established
  before tests. Production snapshot after installation exactly matched the new
  pre-update snapshot, not the obsolete earlier absent-store inventory.

### Actual test result and postflight

`isolated/report.json`: **PASS, 26 tests, errors=[]**, complete nonzero fixed-roster
JUnit reporting, **teardown=true, retained=true**. The in-memory baseline-checking
session used the pinned APKs and compared every identity read to the new baseline.
Only the reviewed fixed `--suite isolated` filter ran. Sanitized diagnostic events
independently contain all 26 start/pass pairs, no negative statuses/exceptions.
The previously failing `optionalNullEmptyMissingAndOverflowMetadataNeverInventsASize`
now passes, as do regular/sliced/reliable-error I/O, checked filesystem/store/reset
fixtures, picker routing and unknown-size ContentResolver/controller/view cases.
This validates those **isolated** paths, not the real manual picker/lifecycle matrix.

`postflight.json` confirms both packages quiet and production inventory equal to
`production-before.json`. Selection-record SHA-256, payload identity/stat metadata
and model-root identity/stat metadata match before update, after update and after
tests. This is scoped preservation evidence, **not a full payload-body hash**.
No owner-data deletion or automatic cleanup/reset was used to make tests pass.

### Native provenance and task boundary

The separately approved original batch's `native/report.json` remains **PASS 1/1**
on original test APK `38679225…` and the exact same production APK. Host APK
inspection in this retry proves `NativeVersionSmokeTest` disassembly is **identical**
in original/revised test APKs (SHA-256 of disassembly
`ca171d10ab2002b66c69ad2577370bfc6ee8b2d45dccf575ad1599c6e8cd9b13`), and native
provenance asset bytes are identical. `native-provenance-audit.json` records this.
No new native instrumentation ran outside the isolated-retry scope, and no claim
is made that native executed under the revised test APK. Each successful suite
has its own correct retained post-install baseline; the two test APK identities
are intentionally not conflated. There was no app/native implementation change
that would invalidate the earlier native launch/exit evidence.

**MI-20's bounded isolated/provider and native-smoke acceptance is now complete
across these separately authorized batches.** No unchanged JVM/Python/build/lint
suite was rerun for reassurance; prior host evidence remains unchanged. MI-21 real
SAF, lifecycle, manual selection replacements and >2 GiB test-owned fixtures still
need separate approval for each production-selection impact. MI-22's 27
interruption/cancel pairs and MI-23/Checkpoint C/module/ship acceptance remain
unexecuted or unapproved. The existing owner model copy must not be replaced or
deleted as a side effect of entering MI-21 without explicit data-impact consent.

## MI-21 — Non-replacing UI entry; keyguard blocker — 2026-11-17

Owner request **“start next”** starts MI-21, not a silent approval to remove the
current private model. The stated first slice is real app launch and real SAF
open/cancel without selecting any document. Successful replacement imports,
>2 GiB generated sources/private copies, settings changes, reset, new installations
and MI-22 interruption work remain outside this slice.

Evidence `.cache/model-import/mi-21-current-path.txt` identifies
`.cache/model-import/mi-21-ui-20261117T153006Z/`. Read-only checks privately selected
the S25, verified both installed APK bytes/UIDs against the MI-20 revised baseline
and captured the current production store before launch. No unfinished candidate
was found. Captured bounded `am start -W` with MAIN/LAUNCHER and the fixed
`.MainActivity` component was accepted. This verifies intent entry only, **not a
human OEM launcher-icon tap or visible app-screen acceptance**.

### UI observation and privacy

- Non-PTY `uiautomator dump /dev/tty` and `/proc/self/fd/1` returned only short
  completion output, no XML in the captured stream. The capture method was
  corrected to bounded **`adb shell -tt uiautomator dump /dev/tty`**; the PTY
  capture returned a real hierarchy. No persistent phone XML file or screenshot
  was created, and no raw hierarchy, owner text, URI or endpoint was retained.
- The actual hierarchy showed **target UI absent, system UI present, keyguard
  resource indicators present**. Other package identities/text were not stored.
  No button coordinates were guessed, no input was sent into an unverified
  foreground window, and no unlock/PIN action was attempted.
- `production-before.json`, `production-after-launch.json` and
  `production-latest.json` match exactly, including selection SHA-256 and payload
  stat metadata. Full payload bodies were not read/hashed. No picker opened,
  document was selected/copied, model was replaced/deleted, fixture created,
  settings changed, package installed or forced stop performed in this slice.
- App/host source did not change; no unchanged host tests/builds or MI-20 suites
  were rerun. No manual visual/human sign-off is invented from hierarchy output.

**Paused for physical unlock:** owner must unlock the S25, leave it awake and
confirm. Then resume the already stated non-replacing UI slice using PTY hierarchy
capture, verified target/picker resource identities and bounded actions. Picker
reservation/cancellation may rewrite routing/selection metadata legitimately;
verify the active-model identity/bytes/stat rather than mistake an expected
metadata inode change for a model replacement. Stop again before selecting any
source or successful replacement. Full MI-21, including real offline import,
rotation/copy/Cancel/lifecycle/>2 GiB evidence, remains unchecked; MI-20's isolated
unknown-size provider result is not relabelled a real-picker/manual import result.

### Unlock-confirmed resume — still asleep/keyguard

After owner **“unlocked”**, evidence pointer
`.cache/model-import/mi-21-resume-current-path.txt` identifies
`.cache/model-import/mi-21-resume-20261117T154616Z/`. Fresh private selection and
installed APK byte/UID verification matched the pinned revised MI-20 baseline.
The production snapshot matched the previous UI batch exactly. In-memory PTY
hierarchy again showed keyguard and no target UI; a bounded power-state query
reported not awake. This does not establish when or why the phone locked.

`production-before.json` and `production-after.json` match exactly; all 41
current owner-file hashes also match. `batch-audit.json` records the blocked
result. No tap, Back, unlock, launch retry, picker opening, document selection,
package stop/install, settings change or production metadata write occurred.
No raw hierarchy, unrelated package text, selector, source URI or screenshot was
retained. The target app was not force-stopped.

Ignored one-batch helpers now prepare the same bounded open/cancel flow, checking
keyguard before APK transfer, then installed identity, active-record/payload
identity, idle target controls, actual resolved picker focus before Back, and
post-return nonce/active-model preservation. Ten host-only generated-metadata/UI
guard checks pass; those are **helper checks, not product or device acceptance**.
The actual helper invocation stopped at its keyguard guard without input; its
picker actions and post-return checks remain unexecuted. No app/tracked host
implementation changed, so prior successful product builds/tests were not rerun.
Unlock again and keep the screen awake; do not select a source, change display
settings or replace/reset the existing private model for this slice.

### Real picker open/cancel PASS — 2026-11-17

After the next owner **“unlocked”**, the prepared bounded helper was invoked
immediately. Its first invocation stopped before input because exactly one
authorized device was not available. A bounded private enumeration then found
one authorized device, zero offline/unauthorized devices; a new invocation of
`python .cache/model-import/mi21_picker_slice.py` completed **PASS**. No selector
or endpoint was saved. Evidence pointer `.cache/model-import/mi-21-resume-current-path.txt`
now identifies `.cache/model-import/mi-21-resume-20261117T182520Z/`.

- Fresh installed production/test APK bytes and UIDs matched the pinned baseline.
  The initial saved setup hierarchy still showed keyguard; the later pre-input
  hierarchy/focus and idle controls were verified independently. No unlock input
  was sent or bypass attempted. Fixed MAIN/LAUNCHER entry was used when the target
  was not focused; this is not a human launcher-icon tap claim.
- Exactly one actual verified target Import-button tap opened the resolved
  DocumentsUI SAF picker. Its actual foreground window/package matched the
  resolver before exactly one Back action. No document entry/confirmation was
  tapped, no source was opened/read and no import/copy/Cancel-during-copy ran.
- The returned app window was focused, showed the known Ready status and enabled
  Import button, with pending/cancel/reset controls absent. The displayed active
  model-name hash matched the private bounded selection-record decoder.
- `active-before.json` and `active-after.json` match: epoch and active-record
  fingerprints, active name/size, payload identity/inode/size/mtime/mode/link
  metadata, and no pending nonce. Exactly one selection and one payload remain,
  with no candidates. Selection SHA also matches. **Full filesystem snapshots do
  not match:** the selection inode changed through legitimate picker reservation
  and cancellation. This is not reported as unchanged selection inode or as
  payload replacement. No full payload-body hash or GGUF/inference claim is made.
- `batch-audit.json` records these checks and hashes of both ignored one-batch
  helpers; all 41 inventoried owner files matched before own tracker bookkeeping.
  No app/tracked host implementation changed; unchanged successful product
  builds/tests and MI-20 suites were not rerun. No install, package stop, reset,
  settings change, large fixture or MI-22 action occurred. Raw UI/source names,
  URIs, screenshots and unrelated package text were not retained.

This proves **only real picker open/cancel and active-model preservation** under
the stated non-replacing scope. Offline local import, copy progress/Cancel,
rotation/multiple Activities/lock-return/restart, >2 GiB actual copied length and
bounded heap remain unverified. Before selecting any source, obtain separate
current-private-copy replacement-impact consent; even an intended Cancel can
race successful commit and normal old-copy cleanup. Original external sources
must remain untouched; generated/large fixtures need their own explicit scope.

### Replacement approved; source identity ambiguous — 2026-11-17

The owner answered the explicit replacement-impact question with **“yes, use LMF3
in Downloads dir”**. This authorizes the next real replacement using the approved
source, not source deletion/modification, extra replacements, new large fixtures,
settings changes or MI-22. Do not request that same impact approval again.

Bounded read-only filesystem lookup under the fixed Downloads directory (maximum
three levels, 65,536 output bytes / 256 entries) found no literal LMF3 GGUF match
and two LFM3 candidates. Only candidate-name hashes, stat metadata and generic
quantization labels were retained: **Q8_0, 1,846,240,768 bytes**, and **F16,
949,841,248 bytes**. No raw names, source URI, unrelated filenames, device selector
or source body was retained/read. Evidence pointer
`.cache/model-import/mi-21-source-choice-current-path.txt` identifies
`.cache/model-import/mi-21-source-choice-20261117T183306Z/source-choice.json`.

The latest hierarchy showed the target focused with no keyguard; an earlier
observation showed keyguard. No input, picker opening, import or copy occurred in
this lookup. Clarify **which file** before selecting any document; permission to
replace is granted, source disambiguation is not. No product implementation
changed and successful unchanged product builds/tests were not repeated. The
real-picker open/cancel pass remains valid; full MI-21 is still incomplete.

### Corrected model/mmproj classification; real replacement PASS — 2026-11-17

The assistant's preceding ambiguity was a **classification error**: the F16 GGUF
was the mmproj, not an alternative language model. The owner clarified this after
confirming the location was the phone's Downloads. All earlier filesystem queries
were already over private ADB under `/sdcard/Download`, never the PC's Downloads.
Read-only filename-role checks confirmed exactly one model and one mmproj. The
existing explicit replacement approval therefore identified the model without
another consent request. No mmproj body was opened or selected.

Evidence pointer `.cache/model-import/mi-21-import-current-path.txt` identifies
`.cache/model-import/mi-21-import-20261117T183950Z/`:

- Before UI action, both installed APK bytes/UIDs matched the pinned baseline;
  the old active-record/epoch/payload stat matched the prior picker batch.
  Source model/mmproj stats and destination headroom were captured. No APK was
  rebuilt/installed, package stopped, settings changed or source fixture created.
- The verified idle Import button opened the actual system SAF picker in
  Downloads. The approved file was not in the initial visible hierarchy; no
  wrong row was tapped.
  A guarded continuation opened the actual picker Search control. Diagnosis
  identified its real `AutoCompleteTextView` rather than assuming an EditText;
  the continuation verified that focused field and the fixed `LFM3` query before
  submitting it. The exact approved filename matched one result; the mmproj was
  explicitly excluded. One model-row tap delivered the real document to the app.
  All continuations checked the still-pending route, unchanged old active state
  and source stats; no second import/picker reservation was started.
- The first post-selection hierarchy already showed the known **Imported** status,
  enabled Import button, correct active-name hash, and no pending/cancel/reset
  controls. `observations.json` contains one Imported observation, **not observed
  in-flight copy progress**. No copy Cancel, rotation, lock/leave or restart was
  attempted. The source-selection guard and helper hashes are retained; raw UI,
  filenames, source URI and device selector are not.
- Final bounded record/inventory checks show a **new active identity and payload**
  of **1,846,240,768 bytes**, same model name and store epoch, cleared nonce,
  exactly one selection record/one payload/no candidates, and old private payload
  absent after normal authorized replacement cleanup. Source model and mmproj
  mode/link/inode/size/mtime metadata match before/after. This is metadata and
  actual copied-length evidence, not full source/payload hashing or full GGUF
  structural/compatibility validation.
- All 41 owner-file hashes matched before own tracker bookkeeping; five generated
  host-memory helper guards passed (not product/device-suite acceptance). No
  product implementation changed, so unchanged successful product builds/tests
  and MI-20 suites were not repeated. No inference/WebUI was started. The app was
  left at its successful return, not force-stopped.

`result.json` and `batch-audit.json` PASS only this one real replacement. Its
length is **below 2 GiB**, no heap bound was measured, and Wi-Fi/network settings
were untouched; offline operation/network independence is not claimed. Full
MI-21 and MI-22/MI-23 remain incomplete. This consumes the approved single real
replacement; extra imports, interruption actions or new large fixtures need
separate scope, without altering/deleting either Downloads source.

### Next progress/Cancel attempt approved; paused before input — 2026-11-17

Owner **“do next”** follows the explicit proposal to test copy progress/Cancel,
with fast-copy completion/replacement risk stated. Scope is one additional attempt
with the same approved phone Downloads model, never the mmproj; external sources
must stay untouched. It does not authorize automatic retries, extra replacements,
fixtures, settings changes, package stops or MI-22.

The prior real import finished before the first idle-waiting accessibility dump.
Read-only diagnosis confirmed `dumpsys activity top` exposes the actual target view
hierarchy, current bounds, visibility and enabled/clickable flags. The prepared
one-batch helper requires accessibility/live-view anchor agreement before opening
the picker, and awake/unlocked/current-target policy/focus, a non-scrolling fully
visible layout, a live visible enabled Cancel control and a strictly partial
candidate size before its single Cancel tap. Raw dumps stay in memory. A successful
Cancel must return the known Cancelled status and preserve the exact active
record/epoch/payload stat, with nonce cleared and no candidates. Fast completion
is explicitly incomplete Cancel evidence, not a pass; no retry is automatic.

Twenty-one generated host-memory helper guards pass, including live-coordinate
composition, hidden ancestors, disabled/clipped/wrong-anchor controls, candidate
metadata, awake/keyguard policy and no-retry intent markers. These are **private
helper checks, not product/device acceptance**. Actual accessibility-anchor and
Cancel-path acceptance remain unexecuted. No product source changed, so unchanged
successful product builds/tests were not repeated.

Actual invocation `python .cache/model-import/mi21_cancel_attempt.py` stopped at
its initial keyguard gate, before installed-identity/current-store rechecks or any
new input/selection/copy. No current snapshot or Cancel pass is invented. Evidence
pointer `.cache/model-import/mi-21-cancel-gate-current-path.txt` identifies
`.cache/model-import/mi-21-cancel-gate-20261117T190008Z/gate.json`. The earlier model
replacement remains PASS. Resume the same prepared command immediately after
owner unlock/keep-awake confirmation, without bypassing keyguard or changing
screen/network settings. Full MI-21 remains incomplete.

### Approved Cancel attempt — fast completion, Cancel not proven — 2026-11-17

After owner **“unlocked”**, immediately invoked
`python .cache/model-import/mi21_cancel_attempt.py`. Evidence pointer
`.cache/model-import/mi-21-cancel-current-path.txt` identifies
`.cache/model-import/mi-21-cancel-20261117T190523Z/`.

- Fresh preflight matched both pinned installed APK byte/UID identities and the
  prior real-import active record/payload baseline. Model/mmproj source stats and
  headroom were checked. Actual accessibility/live-view anchor bounds agreed;
  the awake/unlocked/current-MainActivity and non-scrolling layout gates passed.
  The actual Downloads SAF picker/search selected the exact approved model once,
  never the mmproj. Intent markers prevent automatic re-opening/re-selection.
- `fast-observations.json` records a first partial candidate size of
  **38,273,048 bytes**, sampled before its subsequent foreground-policy/live-view
  queries. Those later view snapshots already showed Import enabled, with
  pending/progress/Cancel controls not visible and Cancel disabled. These are
  **sequential observations**, not proof that Cancel was hidden while the partial
  copy existed. No live Cancel control met all gates, so **zero Cancel taps** were
  sent. Numeric UI progress and a visible in-flight progress widget were not
  captured. The candidate size independently proves copying began.
- `ui-observations.json` then records the known **Imported** status. Postflight
  checks show a new active identity/new private payload of 1,846,240,768 bytes,
  same model name/store epoch, cleared nonce, old private payload absent after
  normal authorized cleanup, and exactly one selection/one payload/no candidates.
  The old private identity was **not preserved**: this was the explicitly warned
  fast-completion replacement outcome, not a completed Cancel test.
- Source model and mmproj stat metadata match before/after; all 41 owner-file
  hashes matched before own tracker bookkeeping. `batch-audit.json` verifies the
  executed helper hashes match the prepared gate's hashes. No source was modified
  or deleted; no product source, package install/stop, settings, large fixture,
  inference, MI-22 action or second attempt occurred. No payload-body hash or
  unchanged private payload identity is claimed.

`result.json` is **INCOMPLETE_CANCEL_NOT_PROVEN**. This is not a product Cancel
failure: cancellation was never requested. This attempt consumes its one-copy
approval; the no-retry guard remains active. Existing successful product tests
were not rerun on unchanged inputs. Full MI-21 remains unchecked. The next
possible slice is an explicitly approved larger test-owned source and controlled
Cancel attempt, with source cleanup and restoration of the real model if that
copy commits. No fixture creation/restoration/extra copy was authorized at that
historical point; the subsequently approved batch is recorded below.

### Approved 4 GiB fixture — import/restoration/cleanup PASS — 2026-11-17

Owner **“yes”** explicitly approved creating/deleting one temporary 4 GiB source,
one Cancel test, and restoring the real model if the fixture committed. Batch
`.cache/model-import/mi-21-fixture-20261117T191721Z/`, pointer
`.cache/model-import/mi-21-fixture-current-path.txt`:

- Checked pinned installed APK bytes/UIDs, current active baseline, original
  model/mmproj source stats and at least 8 GiB additional-space budget plus
  64 MiB reserve. Created a unique generated source exclusively (no overwrites)
  under the phone's Downloads, with a little-endian v3 24-byte GGUF header and
  a 40-byte ownership marker. Wrote zero-filled data to a verified logical length
  of **4,294,967,296 bytes**, retaining the prefix across interrupted writes.
  This is a **synthetic header-only test source, not an inference model**; no
  weights/generated source entered Git. Before/after creation snapshots showed
  the app's production store unchanged and original source stats preserved.
- Initial UI-stage enumeration found two authorized ADB transports. Read-only
  filtering found only one physical S25/ARM64 match with this batch's exact
  fixture inode/stat/prefix. A bounded fixture-scoped selector then privately
  chose that approved phone. The other device received only a read-only model
  property query, with no UI input, file/app-state access or writes. Duplicate routes would require the same
  private hardware identity; distinct matching phones are rejected. No selector,
  endpoint, hardware ID or unrelated device model was retained.
- Fresh UI-stage APK/active/source/fixture checks and actual accessibility/live
  view anchor/foreground gates passed. Actual SAF Downloads selected the exact
  generated source once, explicitly excluding the mmproj. The first candidate
  sample was **27,263,000 bytes**; later sequential guarded view samples already
  showed the idle post-copy layout. **No eligible Cancel control, zero Cancel
  taps, no repeated fixture copy.** This is still an observation-timing limit,
  not a measured Cancel-handler failure or proof that Cancel was hidden during
  the earlier partial candidate sample.
- The app reported Imported with a **new active private identity/payload of
  4,294,967,296 bytes**, unchanged store epoch, cleared nonce and no candidate
  debris. This verifies completed copied length beyond 2 GiB, not bounded heap,
  full GGUF compatibility or inference. The automated Cancel result remains
  **INCOMPLETE_CANCEL_NOT_PROVEN**.
- Conditional restoration then used another verified real SAF Downloads selection
  of the original model, never the mmproj. The app reported Imported and selected
  a new **1,846,240,768-byte** real-model copy. The private fixture payload was
  normally removed. `restoration/result.json` PASS; original private identity
  was not preserved because restoration legitimately created a new one.
- After resources were released, cleanup verified the generated source's exact
  nonce prefix/inode/mode/link/size metadata and removed **only that owned source**.
  `fixture-cleanup.json` and an independent absence check confirm removal. Final
  inventory has one selection/one real-model payload/no candidates, clear nonce,
  same epoch and original source model/mmproj stats. The source was deleted
  **after restoration**, so selected-fixture source revocation/independence was
  not exercised. No whole payload-body hash is claimed.
- All 41 owner-file hashes matched before own tracker bookkeeping. Executed helper
  hashes match; **50 private host-helper guard cases across separate groups**
  passed, not a product/device suite. An initial cleanup fake-test used an invalid
  host `out` argument and was corrected before its six guards passed; no phone
  action was involved in that harness error. No product source/build input,
  package install/stop, settings, inference or MI-22 changed; unchanged successful
  product builds/tests were not repeated.

### Subsequent owner manual Cancel confirmation

Owner then reported **“i tested `cancel` manually, it works”**. This is accepted as
**basic Cancel manually confirmed by the owner** (`owner-manual-cancel.json`),
separately from the automated timing misses. Do not repeat the Cancel test or
misrepresent the report as agent-observed UI, automated cancellation, a recorded
specific source/phase, or independently checked private-store preservation.
The agent sent no further phone commands after that report; the automated batch's
final snapshots remain pre-report evidence, not invented post-manual snapshots.
Remaining MI-21 work is numeric progress/lifecycle/offline/heap and scoped source
independence evidence; the grouped acceptance items and MI-22 remain open.

## Emulator-only non-importing lifecycle batch — 2026-11-17

### Scope, platform and initial install

Owner requested **“start next, and use the running emulator instead of live s25”**,
explicitly naming `emulator-5554`. All batch ADB commands used only
`-s emulator-5554`; the helper rejects any changed selector and checks qemu=1.
There was no device enumeration/selection or contact with the S25. The emulator
reports Android 17/API 37, primary x86_64, supported ABI list x86_64/arm64-v8a and
`libndk_translation.so`. This supports this APK's Java UI testing, not a native
server/ELF startup or ARM64 inference claim.

Both installed and retained (`package list -u`) target/test package checks were
empty. A new, non-replacing production-only installation used the pinned existing
79,332,034-byte APK SHA-256
`76284abdf487403266e26114a24246638b4422a1dee3e0d84f6dd7ec2069f291`.
No rebuild, test APK, uninstall, `-r`, app-data clear, model import or fixture was
needed. Installed byte/UID verification passed, emulator target UID **10234**.
The first fixed MAIN/LAUNCHER entry initialized one empty selection record, no
payloads/candidates, and displayed No model imported / Ready.

Evidence pointer `.cache/model-import/mi-21-emulator-current-path.txt` identifies
`.cache/model-import/mi-21-emulator-20261117T194524Z/`. Initial and final package,
UI, store and rotation records plus ignored one-batch helper hashes are retained.
Raw hierarchies, unrelated app text/accounts/files, screenshots and native output
were not retained. Fifteen generated host-memory helper guards passed across two
groups; these are not extra product-suite or physical-device acceptance results.

### Cases actually executed

1. **Idle rotation — PASS:** changed actual display rotation 0 → 1; the ready UI
   persisted, the client MainActivity identity changed while PID/UID stayed the
   same, and the empty selection record/epoch stayed identical. Restored the
   baseline `wm user-rotation free`, accelerometer rotation 1 / user rotation 0.
2. **Picker rotation — PASS:** one verified Import tap opened the actual resolved
   SAF picker. With **no file selected**, rotation changed 0 → 1, the same pending
   nonce/epoch survived, and the actual picker stayed foreground. Back was sent
   only from that verified picker with its unchanged reservation. The target
   returned Ready, client Activity recreated in the same process, nonce cleared,
   and original empty selection SHA restored. Original rotation settings restored.
   This is picker dismissal, **not a repeat of the owner's copy Cancel test**.
3. **Screen-off/wake return — PASS, genuine lock unverified:** from an idle app,
   KEYCODE_SLEEP produced a non-Awake power state without changing the selection.
   KEYCODE_WAKEUP returned directly to the ready app with no stale busy controls.
   The emulator did not show keyguard; no PIN/security settings changed and normal
   keyguard dismissal was not needed. An initial read-only `wm help` query exposed
   Android 17's exit 255 despite valid usage output; the private helper was adapted
   before the sleep case started. This was not an app failure or a failed Cancel.
4. **Idle process restart — PASS:** after verifying no import/pending route,
   force-stopped only the emulator target package and observed its process absent.
   Fixed MAIN/LAUNCHER relaunch produced a new PID/client Activity with the same
   UID, empty selection SHA/epoch, Ready UI and no pending/cancel/reset controls.
   No app data was cleared. This is **idle restart**, not an MI-22 copy-interruption
   scenario or active-model restoration test.

### Postflight and limits

`batch-audit.json` is **PASS_BOUNDED_EMPTY_STORE_EMULATOR_CHECKS**. Final installed
APK SHA/UID match, test APK remains absent, one empty selection/no payload/no
candidate remain, original rotation mode/settings match, and all 41 owner-file
hashes matched before own tracker bookkeeping. The app is left installed and
Ready. No product implementation changed, so unchanged successful product builds,
JVM/packaging suites and MI-20 device tests were not rerun.

These emulator results do not prove active-model selection preservation, rotation
or leave/lock during a copy, multiple Activities, genuine credential lock/unlock,
OEM background behavior, numeric progress, offline operation, heap bounds, source
revocation or native startup/inference. Full MI-21 remains unchecked; physical
S25 lifecycle acceptance is not invented or required as permission to touch that
phone. Further work must honor the owner's emulator-only direction. The owner's
basic manual Cancel confirmation remains accepted and is not repeated.

## Emulator active-import attempt — observer handshake blocker — 2026-11-17

Owner **“continue doing next until any blocker is encountered”** authorized the
proposed emulator-only active-import work with a test-owned source. Fixed
`emulator-5554`/qemu=1, installed APK/UID and the empty selection baseline matched.
Both source/private headroom checks reported 15,171,129,344 bytes available before
creation, enough for a 4 GiB source plus 4 GiB destination and 64 MiB reserve.
No owner GGUF, new APK, source from the S25 or app-data reset was involved.

Evidence pointer `.cache/model-import/mi-21-emulator-active-current-path.txt`
identifies `.cache/model-import/mi-21-emulator-active-20261117T204053Z/`:

- Created one exclusive test-owned **4,294,967,296-byte** source using the existing
  nonce/prefix/inode/regular-single-link guards. The synthetic GGUF fixed header
  is not a usable inference model. Before/after creation app-store snapshots match.
- Verified the actual SAF picker, navigated its real Downloads root and located
  the exact generated source. Its pending nonce/epoch and source metadata were
  checked again. There was no basic app Cancel action or repeat of passed cases.
- To avoid the previous sequential-query timing miss, prepared an observer armed
  **before** the source tap. It uses a remote 20-second timeout and would rotate
  when a partial candidate appears, then sample client Activity/configuration and
  the same candidate identity/size. Nine generated protocol/selector host guards
  and five generated source-row guards passed before the attempt. These are
  private helper checks, not an app/device pass.
- The observer's LF-only `LD_ARMED\n` predicate did not recognize the emulator's
  `LD_ARMED\r\n` response and timed out at arming. No source-selected marker,
  candidate or payload was found; the app remained behind the actual picker with
  the same pending nonce. No in-flight rotation or import is claimed. Later test
  stages were stopped rather than retrying the import.
- The initial generic cleanup treated a source-selection **intent** as an
  already-started copy and deferred deletion because the app was not foreground.
  Read-only diagnosis resolved that ambiguity: the known picker was still focused
  with our exact unchanged empty-store reservation. Cleanup then sent Back only
  from that verified route, required Ready/empty original selection, verified the
  owned source's nonce/prefix/inode/stat and removed only it. Original rotation
  settings were restored; final inventory has no payload/candidate, original
  empty selection SHA/epoch and matching installed APK/UID. The initial deferred
  cleanup is not misreported as a remaining fixture or an app failure.
- A subsequent **read-only** `echo` probe in the same nested timeout/shell form
  returned exit 0 and exactly ten CRLF-framed bytes, identifying the transport
  mismatch without starting another app test. The helper predicate was corrected
  to accept one complete LF/CRLF marker and reject partial, prefixed or duplicate
  markers. Eight new host cases pass, including both full protocol line endings;
  the old predicate failure is reproduced. **The fix is not runtime-verified.**

`batch-audit.json`: **BLOCKED_OBSERVER_HANDSHAKE_WITH_CLEANUP_PASS**. All 41 owner
files matched before own tracker bookkeeping; no product code/build input,
physical-phone access, test APK, app clear/stop, inference or further import/test
was performed. Successful unchanged product builds/tests were not rerun. The
emulator is left Ready with no imported model and the temporary source absent.

Next smallest action is a **fresh, separately recorded attempt** using the fixed
observer, not overwriting/reusing the failed intent ledger or its deleted source.
Further runtime work stopped at the encountered blocker; active-copy lifecycle,
numeric progress, heap and offline/source-independence evidence remain open.

## Observer blockers addressed — emulator active-import rotation PASS — 2026-11-17

Owner **“address blocker”** continued the fixed-emulator scope. No old failure
marker/source was reset or overwritten. Two fresh recorded attempts explain the
resolution; neither is a S25/OEM/native inference acceptance claim.

### CRLF runtime verification and 4 GiB comparison diagnosis

`.cache/model-import/mi-21-emulator-active-20261117T211454Z/` preserves the parent
204053Z ledger hashes and first verifies the actual corrected `Watch.arm/finish`
over a harmless echo-only emulator command. CRLF was accepted. A new 4 GiB source
was then selected once; the observer armed but timed out without a partial-candidate
marker. The import committed, the owned source was removed, and the resulting
private synthetic copy was retained. No basic Cancel was requested.

Read-only diagnosis isolated another **harness** error: the emulator shell's
built-in `[` returned false for both `1 < 4294967296` and `4294967296 > 0`, while
`toybox test` correctly returned true. The partial-copy predicate now uses
`toybox test "$bytes" -gt 0 && toybox test "$bytes" -lt 4294967296`.
Seven host cases and seven actual read-only emulator boundary comparisons passed,
including 2 GiB/4 GiB edges; no app import/rotation was started by those probes.
Fresh-parent/no-retry and phase-aware cleanup guards passed 7+4 host cases.

### Fresh model-present attempt and actual in-flight proof

Latest `.cache/model-import/mi-21-emulator-active-20261117T212906Z/` verified that
only the known previous **test-owned** 4 GiB private copy was selected: exact record,
source-prefix nonce, inode/stat, APK/UID and idle UI checks. No owner weight was
read/replaced, no app reset forced an empty baseline. A new exclusive source and
fresh case ledger retained the previous attempt's complete byte hashes. The new
source/private-copy reserve was checked before generation.

The actual armed observer captured:

- Before rotation: candidate **14,417,944 bytes**.
- First sample: same candidate at **43,778,072 bytes**, old portrait Activity still
  present, correctly **not** accepted as recreated.
- Next samples: **new client Activity, landscape configuration**, same candidate
  identity/inode at **86,507,544** and **136,577,048 bytes** — both strictly below
  the 4 GiB source length. This proves actual recreation during the in-flight copy,
  not merely a rotation request followed by a completed import.
- Final persisted new selection refers to that **same inode**, now a full
  **4,294,967,296-byte** private payload; same PID/UID/epoch, nonce clear, old test
  payload absent, one selected payload and no candidate/extra payload. Only one
  source tap/import occurred in this attempt. Numeric UI progress was not captured.

The wrapper also gained a guarded consumed-route Activity-refresh path, with four
host cases rejecting unexpected second taps; it was not needed in this actual
attempt. No URI/extras or production import hooks were injected.

### Postflight observation fix, cleanup and retained state

The first idle-waiting UI dump was unavailable while progress was updating. The
initial verifier retained its BLOCKED result; its subsequent read-only observation
found **Ready**, not transient Imported, with the correct new persisted copy.
Initialization can reset the transient outcome; status text alone is not a commit
proof. The verifier now permits Ready **only** when source-name/size, new active
identity/new payload, same epoch and cleared nonce independently prove replacement.
It retries only bounded known busy-idle-dump misses without selecting another file.
Fourteen host guards cover these postconditions and read-only retries, including
rejecting unchanged/wrong records, unknown foreground and UI/record mismatch.

`--verify-existing` resolved that SAME job, without another import/rotation. It
required the captured live proof, same PID, committed-candidate inode and exact new
record. Original rotation settings were already restored. Cleanup verified and
removed **only the new owned external source**; the selected private test-copy
metadata stayed identical after removal. This is warm-state source-removal
preservation evidence, not permission revocation/cold restart or inference.

`resolved-result.json` and `rotation/result-resolved.json` PASS;
`batch-audit.json` is **PASS_ACTIVE_IMPORT_ROTATION_BLOCKERS_RESOLVED**. The original
failed postflight report remains for provenance. Both older attempt directories
are byte-unchanged; the critical executed observer hash matches. Host groups total
36 new helper cases (7+4+7+4+14), plus seven read-only emulator numeric probes —
not a product-suite count. Final APK SHA/UID retained, all 41 owner-file hashes
matched before own tracker bookkeeping. No product implementation/build input
changed, so unchanged successful product builds/tests were not repeated.

**Final emulator state:** app Ready with the selected **synthetic 4 GiB private
copy**, one selection/one payload/no candidates, no external generated source,
original rotation restored. No app-data clear, S25 contact, basic Cancel retest,
new test APK or native inference. This resolves the observer blocker and the
bounded active-import rotation case, not full MI-21. Further cases require fresh
current model-aware baselines, not the old empty-store helper or deleted sources.
