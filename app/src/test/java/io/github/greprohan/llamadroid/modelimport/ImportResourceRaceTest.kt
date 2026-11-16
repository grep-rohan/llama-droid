package io.github.greprohan.llamadroid.modelimport

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class ImportResourceRaceTest {
    private class Pool : AutoCloseable {
        private val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        private val futures = java.util.Collections.synchronizedList(mutableListOf<java.util.concurrent.Future<*>>())
        fun execute(task: () -> Unit) { futures += executor.submit { task() } }
        override fun close() {
            executor.shutdown()
            assertTrue("No surviving abort helpers", executor.awaitTermination(5, TimeUnit.SECONDS))
            futures.forEach { it.get(1, TimeUnit.SECONDS) }
        }
    }

    @Test fun blockedSignalCannotHoldGateOrPreventIndependentSourceClose() {
        val signalEntered = CountDownLatch(1); val releaseSignal = CountDownLatch(1)
        val sourceClosed = CountDownLatch(1); val source = FakeModelSource(24)
        lateinit var op: ImportOperation
        Pool().use { pool ->
            op = ImportOperation("blocked-signal", pool::execute)
            op.registerSignal { signalEntered.countDown(); check(releaseSignal.await(5, TimeUnit.SECONDS)) }
            op.beginAcquisition(); val lease = op.publish(source); op.endAcquisition()
            source.fault = { if (it == "close") sourceClosed.countDown() }
            try {
                assertEquals(ImportOperation.CancelResult.ACCEPTED, op.requestCancel())
                assertTrue(signalEntered.await(3, TimeUnit.SECONDS)); assertTrue(sourceClosed.await(3, TimeUnit.SECONDS))
                repeat(50) { op.requestCancel() }
                assertFalse(op.tryCommit()); assertFalse(op.finish())
                assertEquals(ImportPhase.CANCELLING, op.state().phase)
            } finally { releaseSignal.countDown() }
            // Pool.close joins both independently dispatched operations.
        }
        assertTrue(source.closed); assertEquals(1, source.closes); assertTrue(op.finish())
    }

    @Test fun helperCannotDoubleCloseOrReleaseAWorkersPendingNormalClose() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val source = FakeModelSource(24); val workerFailure = AtomicReference<Throwable?>()
        lateinit var op: ImportOperation
        Pool().use { pool ->
            op = ImportOperation("normal-close", pool::execute); op.registerSignal {}
            op.beginAcquisition(); val lease = op.publish(source); op.endAcquisition()
            source.fault = { if (it == "close") { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) } }
            val worker = Thread({ try { lease.close() } catch (failure: Throwable) { workerFailure.set(failure) } }, "test-close-owner")
            worker.start()
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                op.requestCancel()
                assertFalse(lease.closed); assertFalse(op.finish()); assertTrue(op.state().resourcePending)
                assertThrows(IllegalStateException::class.java) { lease.close() }
                assertEquals(1, source.closes)
            } finally { release.countDown(); worker.join(5000); assertFalse("No surviving worker", worker.isAlive) }
        }
        assertNull(workerFailure.get()); assertTrue(source.closed); assertEquals(1, source.closes)
        assertTrue(op.finish()); assertTrue(op.state().quiescent)
    }

    @Test fun simultaneousCancelAndCommitHaveExactlyOneWinner() {
        Pool().use { pool -> repeat(100) {
            val op = ImportOperation("race-$it", pool::execute)
            val start = CountDownLatch(1); val done = CountDownLatch(2)
            val commit = AtomicReference<Boolean>(); val cancel = AtomicReference<ImportOperation.CancelResult>()
            pool.execute { try { check(start.await(3, TimeUnit.SECONDS)); commit.set(op.tryCommit()) } finally { done.countDown() } }
            pool.execute { try { check(start.await(3, TimeUnit.SECONDS)); cancel.set(op.requestCancel()) } finally { done.countDown() } }
            start.countDown(); assertTrue(done.await(3, TimeUnit.SECONDS))
            if (commit.get()) {
                assertEquals(ImportOperation.CancelResult.FINISHING, cancel.get()); assertFalse(op.state().cancelAccepted)
            } else {
                assertEquals(ImportOperation.CancelResult.ACCEPTED, cancel.get()); assertTrue(op.state().cancelAccepted)
            }
            assertTrue(op.finish())
        } }
    }

    @Test fun oldCapturedAbortNeverTouchesAnotherOperationsResource() {
        val tasks = mutableListOf<() -> Unit>(); val old = ImportOperation("old", tasks::add)
        val next = ImportOperation("next") { error("No next cancellation") }
        old.registerSignal {}; next.registerSignal {}
        val a = FakeModelSource(24); val b = FakeModelSource(24)
        old.beginAcquisition(); old.publish(a); old.endAcquisition(); old.requestCancel()
        next.beginAcquisition(); val nextLease = next.publish(b); next.endAcquisition()
        tasks.forEach { it() }
        assertEquals(1, a.closes); assertEquals(0, b.closes); assertFalse(b.closed)
        nextLease.close(); assertEquals(1, b.closes); assertTrue(old.finish() && next.finish())
    }

    @Test fun rejectedAbortDispatchCannotLoseCancellationOrPretendAResourceStopped() {
        val op = ImportOperation("rejected") { throw java.util.concurrent.RejectedExecutionException("bounded executor") }
        op.registerSignal {}
        val source = FakeModelSource(24)
        op.beginAcquisition(); val lease = op.publish(source); op.endAcquisition()
        assertEquals(ImportOperation.CancelResult.ACCEPTED, op.requestCancel())
        repeat(50) { op.requestCancel() }
        assertEquals(2, op.abortFailures().size); assertEquals(0, op.state().helpers)
        assertEquals(0, source.closes); assertFalse(op.tryCommit()); assertFalse(op.finish())
        lease.close(); assertTrue(op.finish()); assertEquals(1, source.closes)
    }

    @Test fun exceptionAfterConfirmedCloseDoesNotAllowRawDescriptorRetry() {
        val op = ImportOperation("closed-error") { error("No helper") }; op.registerSignal {}
        val source = FakeModelSource(24); val primary = IOException("close returned an error")
        source.fault = { if (it == "close") { source.closed = true; throw primary } }
        op.beginAcquisition(); val lease = op.publish(source); op.endAcquisition()
        assertSame(primary, assertThrows(IOException::class.java) { lease.close() })
        lease.close(); assertEquals(1, source.closes); assertTrue(lease.closed); assertTrue(op.finish())
    }

    @Test fun workerAndHelperShareOneImmutableCloseOwner() {
        val tasks = mutableListOf<() -> Unit>(); val op = ImportOperation("first", tasks::add)
        var signals = 0; op.registerSignal { signals++ }
        val source = FakeModelSource(24)
        op.beginAcquisition(); val lease = op.publish(source); op.endAcquisition()
        assertFalse(op.tryCommit())
        op.requestCancel(); assertEquals(2, tasks.size)
        lease.close() // Worker wins; captured helper must not close the raw handle twice.
        tasks.forEach { it() }; tasks.forEach { it() }
        assertEquals(1, source.closes); assertEquals(1, signals)
        assertTrue(lease.closed); assertTrue(op.finish()); assertTrue(op.state().quiescent)
    }

    @Test fun cancelledAcquisitionClosesItsLateHandleAndCannotReleaseOwnerEarly() {
        val tasks = mutableListOf<() -> Unit>(); val op = ImportOperation("late", tasks::add)
        op.registerSignal {}; op.beginAcquisition(); op.requestCancel()
        tasks.single()()
        assertFalse(op.finish())
        val source = FakeModelSource(24); val lease = op.publish(source)
        assertEquals(2, tasks.size); assertFalse(op.finish())
        tasks.last()(); assertTrue(lease.closed); assertEquals(1, source.closes)
        assertFalse(op.finish()); op.endAcquisition(); assertTrue(op.finish())
    }

    @Test fun unresolvedCloseIsNotRetriedOrReportedAsQuiescent() {
        val op = ImportOperation("pending") { error("No cancel") }
        op.registerSignal {}
        val source = FakeModelSource(24).also { it.ignoreClose = true }
        op.beginAcquisition(); val lease = op.publish(source); op.endAcquisition()
        try {
            assertThrows(IllegalStateException::class.java) { lease.close() }
            assertThrows(IllegalStateException::class.java) { lease.close() }
            assertEquals(1, source.closes); assertFalse(lease.closed)
            assertTrue(op.state().resourcePending); assertFalse(op.finish()); assertFalse(op.tryCommit())
        } finally {
            source.ignoreClose = false; source.close(); op.refreshResource()
            assertTrue(lease.closed); assertTrue(op.finish())
        }
    }
}
