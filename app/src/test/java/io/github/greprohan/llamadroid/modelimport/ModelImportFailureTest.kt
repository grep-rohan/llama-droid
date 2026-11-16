package io.github.greprohan.llamadroid.modelimport

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ModelImportFailureTest {
    private val reserve = ImportPolicy.RESERVE_BYTES
    private fun rejects(reason: ImportPolicy.Reason, action: () -> Unit) {
        assertEquals(reason, assertThrows(ImportPolicy.Rejected::class.java, action).reason)
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
