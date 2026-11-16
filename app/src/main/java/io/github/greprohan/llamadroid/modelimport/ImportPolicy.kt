package io.github.greprohan.llamadroid.modelimport

import java.io.IOException
import java.math.BigInteger

/** Space is measured on the destination filesystem; output must not hide buffered writes. */
internal class ImportPolicy(private val availableBytes: () -> Long) {
    companion object {
        const val RESERVE_BYTES = 67_108_864L
        fun percent(copied: Long, total: Long): Int? {
            if (total <= 0) return null
            // At most two signed-Long operands, not model-sized allocation or Double rounding.
            return BigInteger.valueOf(copied.coerceIn(0, total)).multiply(BigInteger.valueOf(100))
                .divide(BigInteger.valueOf(total)).toInt()
        }
    }
    enum class Reason { SPACE_CHECK, INSUFFICIENT_SPACE, SIZE_MISMATCH, COUNTER_OVERFLOW }
    class Rejected(val reason: Reason, cause: Exception? = null) : IOException("Import policy: ${reason.name}", cause)

    private fun requireSpace(bytes: Long) {
        val available = try { availableBytes() } catch (failure: Exception) {
            throw Rejected(Reason.SPACE_CHECK, failure)
        }
        if (available < 0) throw Rejected(Reason.SPACE_CHECK)
        // Subtract the reserve first: a known near-Long.MAX_VALUE size cannot wrap.
        if (available < RESERVE_BYTES || bytes > available - RESERVE_BYTES) {
            throw Rejected(Reason.INSUFFICIENT_SPACE)
        }
    }

    fun preflight(reported: Long?) = requireSpace(reported?.takeIf { it > 0 } ?: 0)

    fun beforeWrite(copied: Long, count: Int, reported: Long?): Long {
        require(copied >= 0 && count > 0)
        val next = try { Math.addExact(copied, count.toLong()) } catch (failure: ArithmeticException) {
            throw Rejected(Reason.COUNTER_OVERFLOW, failure)
        }
        if (reported != null && reported > 0 && next > reported) throw Rejected(Reason.SIZE_MISMATCH)
        requireSpace(count.toLong())
        return next
    }

    fun atEof(copied: Long, reported: Long?) {
        if (reported != null && reported > 0 && copied != reported) throw Rejected(Reason.SIZE_MISMATCH)
    }
}
