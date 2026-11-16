#!/usr/bin/env python3
"""Explicitly consented retained-installation tests. --help never contacts ADB."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
import time

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


class Parser(argparse.ArgumentParser):
    def error(self, message):
        raise Failure("invalid command-line arguments")


def run_suite(session, mode):
    report = {"mode": mode, "passed": False, "tests": 0, "errors": []}
    try:
        baseline = session.identity(Budget(120))
        report["baseline"] = baseline
        if session.processes(baseline, Budget(15)): raise Failure("test packages are already active")
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
    parser.add_argument("--suite", choices=tuple(FILTERS), required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--allow-device-writes", action="store_true")
    try:
        args = parser.parse_args(argv)
        env = os.environ if environ is None else environ
        if not args.allow_device_writes or not env.get("ANDROID_SERIAL"):
            raise Failure("separate owner consent and private ANDROID_SERIAL required")
        if any(path.is_symlink() for path in (args.evidence_dir, *args.evidence_dir.parents)):
            raise Failure("linked evidence directory refused")
        session = device_factory(env["ANDROID_SERIAL"], APKS)
        args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
        report = run_suite(session, args.suite)
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
