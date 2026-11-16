package io.github.greprohan.llamadroid.modelimport

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class GgufHeaderTest {
    private fun prefix(version: Int, order: ByteOrder): ByteArray =
        ByteBuffer.allocate(24).order(order).put("GGUF".toByteArray()).putInt(version)
            .putLong(-1).putLong(Long.MIN_VALUE).array()

    @Test fun acceptsLittleEndianV2AndV3WithoutInterpretingCounts() {
        for (version in listOf(2, 3)) {
            assertEquals(GgufHeader.Info(version, ByteOrder.LITTLE_ENDIAN),
                GgufHeader.parse(prefix(version, ByteOrder.LITTLE_ENDIAN)))
        }
    }
    @Test fun acceptsBigEndianV3FromItsVersionBytes() {
        assertEquals(GgufHeader.Info(3, ByteOrder.BIG_ENDIAN), GgufHeader.parse(prefix(3, ByteOrder.BIG_ENDIAN)))
    }
    @Test fun rejectsBigEndianV2AndUnsupportedVersions() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            for (version in listOf(0, 1, 4, Int.MAX_VALUE, -1)) {
                assertEquals(GgufHeader.Reason.VERSION, assertThrows(GgufHeader.Invalid::class.java) {
                    GgufHeader.parse(prefix(version, order))
                }.reason)
            }
        }
        assertThrows(GgufHeader.Invalid::class.java) { GgufHeader.parse(prefix(2, ByteOrder.BIG_ENDIAN)) }
    }
    @Test fun rejectsEveryTruncatedFixedHeader() {
        for (length in 0 until 24) {
            assertEquals(GgufHeader.Reason.TRUNCATED, assertThrows(GgufHeader.Invalid::class.java) {
                GgufHeader.parse(prefix(3, ByteOrder.LITTLE_ENDIAN).copyOf(length))
            }.reason)
        }
    }
    @Test fun magicIsByteLevelNotNativeEndianOrCaseInsensitive() {
        for (magic in listOf("FUGG", "gguf", "GGU0", "NOPE")) {
            val bytes = prefix(3, ByteOrder.BIG_ENDIAN)
            magic.toByteArray().copyInto(bytes)
            assertEquals(GgufHeader.Reason.MAGIC, assertThrows(GgufHeader.Invalid::class.java) {
                GgufHeader.parse(bytes)
            }.reason)
        }
    }
}
