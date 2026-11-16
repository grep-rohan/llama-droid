#!/usr/bin/env python3
"""Explicitly consented retained-installation tests. --help never contacts ADB."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
import time
import uuid

from model_import_device import Budget, Device, Failure, Instrumentation

TARGET = "io.github.greprohan.llamadroid"
TEST = TARGET + ".test"
MODEL = TARGET + ".modelimport."
RUNNER = TEST + "/androidx.test.runner.AndroidJUnitRunner"
ROOT = Path(__file__).resolve().parents[1]
APKS = {TARGET: ROOT / "app/build/outputs/apk/debug/app-debug.apk",
        TEST: ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"}
FILTERS = {"isolated": MODEL + "ModelImportIsolatedTestSuite", "native": TARGET + ".NativeVersionSmokeTest"}
METHODS = {
    "AndroidImportIoTest": "regularSlicesAndReliableShortPipesDeliverOnlySelectedBytes reliablePipeErrorsAreReadFailuresNotCleanEof cancellationAndRevocationAreNotOptionalMetadataFallback acquisitionFailureAndLateClosedPublicationDoNotLeakDescriptors androidMutationHelperHasDistinctLeasedNonBackupRootsAndSafeCleanup documentIoRefusesMainThreadAndNonContentInputs optionalNullEmptyMissingAndOverflowMetadataNeverInventsASize".split(),
    "ModelImportScreenTest": "resetDeclineDismissAndRecreationNeverPersistConsent resetConfirmationIsSingleUseAndBoundToCurrentRecoveryRevision unknownSizeProviderRendersCopiedBytesWithoutPercent unboundActionsAndUnwiredRecoveryAreDisabledWithoutOpeningAnyStore replacementRemainsSeparateFromActiveAndFinishingOrDetachDisablesActions cancellationAndCleanupWarningsUseExplicitSanitizedStates".split(),
    "AndroidPickerRouteTest": "dispatchUsesOnlySingleReadOnlySafAndSavesBoundedOpaqueIdentity restoredBindingMatchesOneResultWithoutRebindingAnOldCodeToANewerToken failedDispatchConsumesItsCodeWithoutClearingAnotherBindingOrReusingTheRoute invalidOrMissingSavedRouteNeverRestartsAtOneInsideTheSameLogicalActivity exhaustedSavedCodesAndRetiredRoutesCannotWrapOrAcceptOldCallbacks retainedTransientResultSurvivesInProcessButIsNeverRestoredFromBundle oldActivityHandoffCannotConsumeANewerResultAndRetirementDropsTransientUri malformedResultGetterIsInvalidWithoutLeakingItsExceptionTextOrRetainingBinding malformedMultipleNonContentAndUngrantedResultsNeverBecomeDocuments".split(),
    "AndroidModelStoreTest": "checkedRecordUsesRealExclusiveSyncRenameAndClosedDescriptors exclusiveAndNoFollowRefuseExistingAndLinkedFixtureEntries authoritativeSelectionWinsOverSidecarAndRecoveryRetiresOnlyUnselectedBytes onlyCommittedResetResumesToRecordedEmptyEpochOnRealFilesystem".split(),
}
ROSTERS = {"isolated": {(MODEL + cls, method) for cls, methods in METHODS.items() for method in methods},
           "native": {(FILTERS["native"], "installedPinnedServerExitsWithinDeadlineAndLeavesNoChild")}}


PAIRS = {
    "first": ("initial-selection-next", "request-reserved", "request-consumed", "candidate-created", "mid-copy", "streams-closed", "payload-renamed", "selection-next-synced", "selection-committed"),
    "replace": ("request-reserved", "request-consumed", "candidate-created", "mid-copy", "streams-closed", "payload-renamed", "selection-next-synced", "selection-committed", "old-payload-delete"),
    "reset": ("reset-next-synced", "reset-committed", "reset-delete", "reset-empty-committed", "reset-cleared"),
}
CRASH_SETUP = MODEL + "ModelImportCrashSetupTest"
CRASH_VERIFY = MODEL + "ModelImportCrashVerifyTest"


def expand_pairs(scenario, checkpoint):
    if scenario == "all":
        if checkpoint != "all": raise Failure("all scenarios require all checkpoints")
        return [(name, point) for name, points in PAIRS.items() for point in points]
    if scenario not in PAIRS: raise Failure("invalid scenario")
    if checkpoint == "all": return [(scenario, point) for point in PAIRS[scenario]]
    if checkpoint not in PAIRS[scenario]: raise Failure("inapplicable checkpoint pair")
    return [(scenario, checkpoint)]


def run_case(session, scenario, checkpoint, baseline=None):
    if expand_pairs(scenario, checkpoint) != [(scenario, checkpoint)]: raise Failure("one explicit pair required")
    correlation = {"runId": uuid.uuid4().hex, "scenario": scenario, "checkpoint": checkpoint}
    report = {**correlation, "passed": False, "tests": 0, "fixture_cleanup": False, "errors": []}
    try:
        flight = Budget(120); installed = session.identity(flight)
        if baseline is not None and installed != baseline: raise Failure("installed package identity changed")
        baseline = installed; report["baseline"] = baseline
        if session.processes(baseline, flight): raise Failure("test packages are already active")
    except Failure as error:
        report["errors"].append(str(error)); return report
    budget = Budget(180, reserve=30); live = []
    arguments = {**correlation, "orchestrated": "true"}
    try:
        parser = Instrumentation({(CRASH_SETUP, "stageUntilCheckpoint")}, correlation, 1001, baseline[TARGET]["uid"])
        setup, deadline = session.instrument_command(RUNNER, CRASH_SETUP + "#stageUntilCheckpoint", arguments, budget, 30)
        live.append(setup)
        result = setup.run(deadline, parser.feed, until=lambda: 1001 in parser.markers)
        if result is not None or setup.proc.poll() is not None or 1001 not in parser.markers:
            raise Failure("setup did not remain paused")
        ready = parser.markers[1001]; report["ready"] = ready
        processes = session.processes(baseline, budget)
        if (ready["pid"], ready["uid"], TARGET) not in processes or sum(uid == ready["uid"] for pid, uid, name in processes) != 1:
            raise Failure("ready target process mismatch")
        # Controlled stop is work, not reserved final teardown. No verification until all UIDs are quiet.
        session.teardown(baseline, budget, teardown=False)
        setup.close(budget.deadline(2)); live.remove(setup)
        parser = Instrumentation({(CRASH_VERIFY, "verifyAfterRestart")}, correlation, 1002, baseline[TARGET]["uid"])
        verify, deadline = session.instrument_command(RUNNER, CRASH_VERIFY + "#verifyAfterRestart", arguments, budget, 90)
        live.append(verify)
        if verify.run(deadline, parser.feed) != 0: raise Failure("verification transport failed")
        report.update(parser.complete())
        verified = parser.markers[1002]
        if (ready["pid"], ready["startedElapsedMs"]) == (verified["pid"], verified["startedElapsedMs"]):
            raise Failure("verification did not restart the target")
        report["verified"] = verified; report["fixture_cleanup"] = True
    except Failure as error: report["errors"].append(str(error))
    finally:
        for cmd in live:
            try: cmd.close(min(budget.end, time.monotonic() + 2))
            except Failure: report["errors"].append("instrumentation transport cleanup failed")
        try:
            session.teardown(baseline, budget); report["teardown"] = True
        except Failure: report["errors"].append("device teardown incomplete")
        try:
            report["retained"] = session.identity(Budget(120)) == baseline
            if not report["retained"]: report["errors"].append("installed package identity changed")
        except Failure: report["errors"].append("postflight identity unavailable")
    report["passed"] = not report["errors"] and report["fixture_cleanup"]
    return report


def run_matrix(session, pairs):
    if not pairs or len(pairs) != len(set(pairs)):
        raise Failure("invalid checkpoint matrix")
    for scenario, checkpoint in pairs:
        if expand_pairs(scenario, checkpoint) != [(scenario, checkpoint)]: raise Failure("invalid checkpoint matrix")
    baseline = None; cases = []; halted = False
    for scenario, checkpoint in pairs:
        if halted:
            cases.append({"scenario": scenario, "checkpoint": checkpoint, "passed": False, "not_run": "prior case failed"})
            continue
        result = run_case(session, scenario, checkpoint, baseline)
        baseline = result.pop("baseline", baseline)
        cases.append(result); halted = not result["passed"]
    return {"mode": "recovery", "passed": not halted, "baseline": baseline, "cases": cases,
            "requested": len(pairs), "tests": sum(case.get("tests", 0) for case in cases)}


class Parser(argparse.ArgumentParser):
    def error(self, message):
        raise Failure("invalid command-line arguments")


def run_suite(session, mode):
    report = {"mode": mode, "passed": False, "tests": 0, "errors": []}
    try:
        flight = Budget(120)
        baseline = session.identity(flight)
        report["baseline"] = baseline
        if session.processes(baseline, flight): raise Failure("test packages are already active")
    except Failure as error:
        report["errors"].append(str(error)); return report
    budget = Budget(300 if mode == "isolated" else 150, reserve=30)
    cmd = None
    try:
        parser = Instrumentation(ROSTERS[mode])
        cmd, deadline = session.instrument_command(RUNNER, FILTERS[mode], {}, budget, 300)
        if cmd.run(deadline, parser.feed) != 0: raise Failure("instrumentation transport failed")
        report.update(parser.complete())
    except Failure as error: report["errors"].append(str(error))
    finally:
        if cmd is not None:
            try: cmd.close(min(budget.end, time.monotonic() + 2))
            except Failure: report["errors"].append("instrumentation transport cleanup failed")
        try:
            session.teardown(baseline, budget); report["teardown"] = True
        except Failure: report["errors"].append("device teardown incomplete")
        try:
            report["retained"] = session.identity(Budget(120)) == baseline
            if not report["retained"]: report["errors"].append("installed package identity changed")
        except Failure: report["errors"].append("postflight identity unavailable")
    report["passed"] = not report["errors"]
    return report


def main(argv=None, environ=None, device_factory=Device):
    parser = Parser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--suite", choices=tuple(FILTERS))
    mode.add_argument("--scenario", choices=(*PAIRS, "all"))
    parser.add_argument("--checkpoint", choices=sorted({p for points in PAIRS.values() for p in points} | {"all"}))
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--allow-device-writes", action="store_true")
    try:
        args = parser.parse_args(argv)
        if args.suite and args.checkpoint is not None: raise Failure("suite mode rejects checkpoints")
        if args.scenario and args.checkpoint is None: raise Failure("scenario checkpoint required")
        pairs = expand_pairs(args.scenario, args.checkpoint) if args.scenario else None
        env = os.environ if environ is None else environ
        if not args.allow_device_writes or not env.get("ANDROID_SERIAL"):
            raise Failure("separate owner consent and private ANDROID_SERIAL required")
        if any(path.is_symlink() for path in (args.evidence_dir, *args.evidence_dir.parents)):
            raise Failure("linked evidence directory refused")
        session = device_factory(env["ANDROID_SERIAL"], APKS)
        args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
        report = run_suite(session, args.suite) if args.suite else run_matrix(session, pairs)
        encoded = json.dumps(report, indent=2)
        if len(encoded.encode("utf-8")) > 65536: raise Failure("evidence size limit")
        with (args.evidence_dir / "report.json").open("x", encoding="utf-8") as output:
            output.write(encoded)
        print("PASS" if report["passed"] else "FAIL: reviewed evidence required")
        return 0 if report["passed"] else 1
    except Failure as error:
        print(str(error), file=sys.stderr); return 1
    except (Exception, KeyboardInterrupt):
        print("host execution failed or interrupted", file=sys.stderr); return 1


if __name__ == "__main__":
    raise SystemExit(main())
