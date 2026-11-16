package io.github.greprohan.llamadroid.modelimport

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ModelImportFailureTest {
    private val reserve = ImportPolicy.RESERVE_BYTES
    private fun rejects(reason: ImportPolicy.Reason, action: () -> Unit) {
        assertEquals(reason, assertThrows(ImportPolicy.Rejected::class.java, action).reason)
    }

    @Test fun enginePreflightRejectsBeforeAnySourceReadOrCandidateCreation() {
        val source = FakeModelSource(50)
        val failure = assertThrows(ModelImportEngine.Failure::class.java) {
            ModelImportEngine(ImportPolicy { reserve + 49 }).copy(source, { error("No candidate") })
        }
        assertEquals(ImportPolicy.Reason.INSUFFICIENT_SPACE, (failure.primary as ImportPolicy.Rejected).reason)
        assertEquals(0L, source.consumed); assertTrue(source.closed)
    }

    @Test fun engineStrictMismatchNeverWritesBeyondTotalOrHandsOffPrematureEof() {
        for (reported in listOf(23L, 24L, 49L, 51L)) {
            val source = FakeModelSource(50, reported); val candidate = CountingModelCandidate(source)
            val failure = assertThrows(ModelImportEngine.Failure::class.java) {
                ModelImportEngine(ImportPolicy { Long.MAX_VALUE }).copy(source, { candidate })
            }
            assertEquals(ImportPolicy.Reason.SIZE_MISMATCH, (failure.primary as ImportPolicy.Rejected).reason)
            assertTrue(candidate.written <= reported); assertTrue(source.closed && candidate.closed)
            assertEquals(1, candidate.discards)
        }
    }

    @Test fun realSequentialCountersCrossTwoGiBWithOnlyTheFixedBuffer() {
        val length = 2_147_483_649L
        val source = FakeModelSource(length); val candidate = CountingModelCandidate(source)
        var previous = 0L
        val result = ModelImportEngine(ImportPolicy { Long.MAX_VALUE }).copy(source, { candidate }, progress = {
            assertTrue(it > previous); previous = it
        })
        assertEquals(length, result.bytes); assertEquals(length, candidate.written)
        assertEquals(length, source.consumed); assertEquals(length, previous)
        assertEquals(ModelImportEngine.BUFFER_BYTES, source.maxRequest)
        assertEquals(0, candidate.discards); assertTrue(source.closed && candidate.closed)
    }

    @Test fun knownAndUnknownCopiesRecheckSpaceBeforeEveryWrite() {
        for (reported in listOf(null, 0L, -1L, 50L)) for (measurementFails in listOf(false, true)) {
            val source = FakeModelSource(50, reported); val candidate = CountingModelCandidate(source)
            var calls = 0
            val failure = assertThrows(ModelImportEngine.Failure::class.java) {
                ModelImportEngine(ImportPolicy {
                    calls++
                    if (calls < 3) Long.MAX_VALUE
                    else if (measurementFails) throw IOException("space query") else reserve + 25
                }).copy(source, { candidate })
            }
            assertEquals(if (measurementFails) ImportPolicy.Reason.SPACE_CHECK else ImportPolicy.Reason.INSUFFICIENT_SPACE,
                (failure.primary as ImportPolicy.Rejected).reason)
            assertEquals(24L, candidate.written); assertEquals(3, calls)
            assertEquals(1, candidate.discards); assertFalse(failure.resourcesPending || failure.cleanupPending)
        }
    }

    @Test fun createReadWriteFlushSyncAndCloseFaultsKeepTheirPrimaryAndDoNotHandOff() {
        for (event in listOf("create", "read", "write", "flush", "sync", "close")) {
            val source = FakeModelSource(50); val candidate = CountingModelCandidate(source)
            val primary = IOException("private content://fixture")
            source.fault = { if (event == "read" && it == "read" && source.consumed >= 24) throw primary }
            candidate.fault = { if (it == event) throw primary }
            try {
                val failure = assertThrows(ModelImportEngine.Failure::class.java) {
                    ModelImportEngine(ImportPolicy { Long.MAX_VALUE }).copy(source, {
                        if (event == "create") throw primary else candidate
                    })
                }
                assertSame(primary, failure.primary); assertFalse(failure.message!!.contains("content://"))
                assertTrue(source.closed); assertEquals(1, source.closes)
                assertEquals(if (event == "close") 0 else if (event == "create") 0 else 1, candidate.discards)
                assertEquals(event == "close", failure.resourcesPending)
                assertEquals(event == "close", failure.cleanupPending)
            } finally { candidate.fault = {}; if (!candidate.closed) candidate.close() }
        }
    }

    @Test fun cleanupKeepsPrimaryAndAtMostThreeTypedSecondaryWarnings() {
        val source = FakeModelSource(50); val candidate = CountingModelCandidate(source)
        val primary = IOException("read"); val closeSource = IOException("source close")
        val closeCandidate = IOException("candidate close"); val discard = IOException("discard")
        source.fault = {
            if (it == "read" && source.consumed >= 24) throw primary
            if (it == "close") { source.closed = true; throw closeSource }
        }
        candidate.fault = {
            if (it == "close") { candidate.closed = true; throw closeCandidate }
            if (it == "discard") throw discard
        }
        val failure = assertThrows(ModelImportEngine.Failure::class.java) {
            ModelImportEngine(ImportPolicy { Long.MAX_VALUE }).copy(source, { candidate })
        }
        assertSame(primary, failure.primary)
        assertEquals(listOf(closeSource, closeCandidate, discard), failure.secondary.map { it.cause })
        assertEquals(listOf(ModelImportEngine.Phase.SOURCE_CLOSE, ModelImportEngine.Phase.CANDIDATE_CLOSE,
            ModelImportEngine.Phase.DISCARD), failure.secondary.map { it.phase })
        assertEquals(ModelImportEngine.MAX_SECONDARY, failure.secondary.size)
        assertFalse(failure.resourcesPending); assertTrue(failure.cleanupPending)
        assertEquals(listOf("close", "discard"), candidate.events)
    }

    @Test fun cancellationAtPreflightReadSpaceProgressAndClosedHandoffStopsBeforeDiscard() {
        for (point in listOf("preflight", "read", "space", "progress", "handoff")) {
            val source = FakeModelSource(50); val candidate = CountingModelCandidate(source)
            var cancelled = point == "preflight"; var queries = 0
            source.fault = { if (point == "read" && it == "read" && source.consumed >= 24) cancelled = true }
            candidate.fault = { if (point == "handoff" && it == "close") cancelled = true }
            val failure = assertThrows(ModelImportEngine.Failure::class.java) {
                ModelImportEngine(ImportPolicy {
                    queries++; if (point == "space" && queries == 2) cancelled = true
                    Long.MAX_VALUE
                }).copy(source, { candidate }, ModelCancellation {
                    if (cancelled) throw java.util.concurrent.CancellationException("cancel")
                }, progress = { if (point == "progress") cancelled = true })
            }
            assertTrue(failure.primary is java.util.concurrent.CancellationException)
            assertTrue(source.closed); assertEquals(1, source.closes)
            assertEquals(if (point == "preflight") 0 else 1, candidate.discards)
            assertFalse(failure.resourcesPending || failure.cleanupPending)
            if (point == "space" || point == "preflight") assertEquals(0L, candidate.written)
        }
    }

    @Test fun pendingSourceOrCandidateCloseNeverDeletesAndIsNotBlindlyRetried() {
        for (pendingSource in listOf(true, false)) {
            val source = FakeModelSource(50); val candidate = CountingModelCandidate(source)
            val primary = IOException("read")
            source.fault = { if (it == "read" && source.consumed >= 24) throw primary }
            source.ignoreClose = pendingSource; candidate.ignoreClose = !pendingSource
            try {
                val failure = assertThrows(ModelImportEngine.Failure::class.java) {
                    ModelImportEngine(ImportPolicy { Long.MAX_VALUE }).copy(source, { candidate })
                }
                assertSame(primary, failure.primary); assertTrue(failure.resourcesPending && failure.cleanupPending)
                assertEquals(0, candidate.discards); assertEquals(1, source.closes); assertEquals(1, candidate.closes)
                assertEquals(1, failure.secondary.size)
            } finally {
                source.fault = {}; source.ignoreClose = false; candidate.ignoreClose = false
                if (!source.closed) source.close()
                if (!candidate.closed) candidate.close()
                assertTrue(source.closed && candidate.closed)
            }
        }
    }

    @Test fun matchingPositiveAndUnusableSizesCopyTheActualEofAndNeverDiscardSuccess() {
        for (reported in listOf(50L, 0L, -1L, Long.MIN_VALUE, null)) {
            val source = FakeModelSource(50, reported); val candidate = CountingModelCandidate(source)
            val result = ModelImportEngine(ImportPolicy { reserve + 50 }).copy(source, { candidate })
            assertEquals(50L, result.bytes); assertEquals(50L, candidate.written)
            assertEquals(0, candidate.discards); assertTrue(source.closed && candidate.closed)
            assertEquals(listOf("flush", "sync", "close"), candidate.events)
        }
    }

    @Test fun knownSizeRequiresEntireNewCopyPlusReserveWithoutOverflow() {
        ImportPolicy { reserve + 24 }.preflight(24)
        rejects(ImportPolicy.Reason.INSUFFICIENT_SPACE) { ImportPolicy { reserve + 23 }.preflight(24) }
        rejects(ImportPolicy.Reason.INSUFFICIENT_SPACE) { ImportPolicy { Long.MAX_VALUE }.preflight(Long.MAX_VALUE) }
        ImportPolicy { Long.MAX_VALUE }.preflight(Long.MAX_VALUE - reserve)
    }

    @Test fun failedNegativeAndThrowingSpaceMeasurementsAreNotUnlimitedRoom() {
        for (measure in listOf<() -> Long>({ -1L }, { throw IOException("private path") })) {
            rejects(ImportPolicy.Reason.SPACE_CHECK) { ImportPolicy(measure).preflight(null) }
        }
    }

    @Test fun unknownUnusableSizesCheckReserveAndEveryPendingWrite() {
        for (reported in listOf(null, 0L, -1L, Long.MIN_VALUE)) {
            var free = reserve
            val policy = ImportPolicy { free }
            policy.preflight(reported)
            rejects(ImportPolicy.Reason.INSUFFICIENT_SPACE) { policy.beforeWrite(0, 24, reported) }
            free += 24
            assertEquals(24L, policy.beforeWrite(0, 24, reported))
        }
    }

    @Test fun strictSizeRejectsUnderestimatesBeforeWriteAndOverestimatesAtEof() {
        val policy = ImportPolicy { Long.MAX_VALUE }
        rejects(ImportPolicy.Reason.SIZE_MISMATCH) { policy.beforeWrite(24, 1, 24) }
        rejects(ImportPolicy.Reason.SIZE_MISMATCH) { policy.atEof(24, 25) }
        policy.atEof(24, 24)
        policy.atEof(24, null)
        rejects(ImportPolicy.Reason.COUNTER_OVERFLOW) { policy.beforeWrite(Long.MAX_VALUE - 2, 3, null) }
        assertEquals(Long.MAX_VALUE, policy.beforeWrite(Long.MAX_VALUE - 2, 2, null))
    }

    @Test fun percentUsesBoundedExactArithmeticAtSignedLongAndTwoGiBBoundaries() {
        assertNull(ImportPolicy.percent(24, 0))
        assertNull(ImportPolicy.percent(24, -1))
        assertEquals(50, ImportPolicy.percent(2_147_483_649L, 4_294_967_298L))
        assertEquals(99, ImportPolicy.percent(Long.MAX_VALUE - 1, Long.MAX_VALUE))
        assertEquals(100, ImportPolicy.percent(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0, ImportPolicy.percent(-1, Long.MAX_VALUE))
        assertEquals(100, ImportPolicy.percent(Long.MAX_VALUE, 24))
    }
}
