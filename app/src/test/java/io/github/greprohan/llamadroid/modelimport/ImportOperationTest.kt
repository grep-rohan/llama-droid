package io.github.greprohan.llamadroid.modelimport

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ImportOperationTest {
    @Test fun acceptedCancellationWinsCommitAndRepeatedCancelQueuesNothingExtra() {
        val tasks = mutableListOf<() -> Unit>(); var signals = 0
        val op = ImportOperation("op", tasks::add)
        op.registerSignal { signals++ }
        assertEquals(ImportOperation.CancelResult.ACCEPTED, op.requestCancel())
        assertThrows(CancellationException::class.java) { op.check() }
        assertFalse(op.tryCommit()); assertFalse(op.finish())
        repeat(100) { assertEquals(ImportOperation.CancelResult.ALREADY_CANCELLED, op.requestCancel()) }
        assertEquals(1, tasks.size); tasks.single()()
        assertEquals(1, signals); assertTrue(op.finish()); assertTrue(op.state().quiescent)
    }

    @Test fun commitWinsWithoutDispatchingAnyLateAbortOrAcceptingCancellation() {
        val tasks = mutableListOf<() -> Unit>(); val op = ImportOperation("op", tasks::add)
        op.registerSignal { error("Never abort a metadata write") }
        assertTrue(op.tryCommit())
        assertEquals(ImportPhase.COMMITTING, op.state().phase)
        assertFalse(op.tryCommit())
        assertEquals(ImportOperation.CancelResult.FINISHING, op.requestCancel())
        op.check(); assertFalse(op.state().cancelAccepted); assertTrue(tasks.isEmpty())
        assertTrue(op.finish()); assertEquals(ImportOperation.CancelResult.FINISHED, op.requestCancel())
    }

    @Test fun acquisitionRequiresARegisteredSignalAndCannotStartAfterAcceptedCancel() {
        val tasks = mutableListOf<() -> Unit>(); val op = ImportOperation("op", tasks::add)
        assertThrows(IllegalStateException::class.java) { op.beginAcquisition() }
        op.registerSignal {}; op.requestCancel()
        assertThrows(CancellationException::class.java) { op.beginAcquisition() }
        tasks.single()(); assertTrue(op.finish())
    }

    @Test fun acquisitionMustCompleteBeforeCommitOrOwnerRelease() {
        val op = ImportOperation("op") { error("No abort") }
        op.registerSignal {}
        op.beginAcquisition()
        assertFalse(op.tryCommit()); assertFalse(op.finish()); assertTrue(op.state().acquiring)
        op.endAcquisition(); assertTrue(op.tryCommit()); assertTrue(op.finish())
    }

    @Test fun lateSignalRegistrationAfterCancelIsCapturedAndDispatchedOnce() {
        val tasks = mutableListOf<() -> Unit>(); val op = ImportOperation("op", tasks::add); var calls = 0
        op.requestCancel(); op.registerSignal { calls++ }
        assertEquals(1, tasks.size); assertFalse(op.finish())
        tasks.single()(); assertEquals(1, calls); assertTrue(op.finish())
        assertThrows(IllegalStateException::class.java) { op.registerSignal {} }
    }

    @Test fun promotionCanStillBeCancelledButCannotBecomeCommitAfterwards() {
        val op = ImportOperation("op") { error("No resources") }
        op.phase(ImportPhase.COPYING); op.phase(ImportPhase.PROMOTING)
        assertEquals(ImportPhase.PROMOTING, op.state().phase)
        op.requestCancel(); assertFalse(op.tryCommit()); assertEquals(ImportPhase.CANCELLING, op.state().phase)
        assertThrows(CancellationException::class.java) { op.phase(ImportPhase.COPYING) }
    }
}
