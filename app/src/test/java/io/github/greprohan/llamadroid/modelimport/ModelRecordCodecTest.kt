package io.github.greprohan.llamadroid.modelimport

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class ModelRecordCodecTest {
    private val epoch = "0123456789abcdef0123456789abcdef"
    private val nonce = "b".repeat(32)
    private val payload = "a".repeat(32)
    private val emptyWire = "LDMR".toByteArray() + byteArrayOf(1, 0) +
        epoch.toByteArray() + byteArrayOf(0, 0)

    @Test fun emptySelectionHasStableVersionedWireFormat() {
        assertArrayEquals(emptyWire, ModelRecordCodec.encode(ModelRecord.Selection(epoch)))
        assertEquals(ModelRecord.Selection(epoch), ModelRecordCodec.decode(emptyWire))
    }

    @Test fun pendingPickerSurvivesWithoutAPayload() {
        val wire = "LDMR".toByteArray() + byteArrayOf(1, 0) + epoch.toByteArray() +
            byteArrayOf(1) + nonce.toByteArray() + byteArrayOf(0)
        val record = ModelRecord.Selection(epoch, nonce)
        assertArrayEquals(wire, ModelRecordCodec.encode(record))
        assertEquals(record, ModelRecordCodec.decode(wire))
    }

    @Test fun activeSelectionPreservesIdentityNameAnd64BitSize() {
        val record = ModelRecord.Selection(epoch, nonce, ActiveModel(payload, Long.MAX_VALUE, "模型 🌍"))
        assertEquals(record, ModelRecordCodec.decode(ModelRecordCodec.encode(record)))
    }

    @Test fun everyTruncationOfEmptyRecordIsRejected() {
        for (end in emptyWire.indices) {
            assertThrows(IllegalArgumentException::class.java) {
                ModelRecordCodec.decode(emptyWire.copyOf(end))
            }
        }
    }

    @Test fun resetHasItsOwnCompleteVersionedSchema() {
        val wire = "LDMR".toByteArray() + byteArrayOf(1, 1) + nonce.toByteArray() + epoch.toByteArray()
        val record = ModelRecord.ResetAuthorization(nonce, epoch)
        assertArrayEquals(wire, ModelRecordCodec.encode(record))
        assertEquals(record, ModelRecordCodec.decode(wire))
        assertThrows(IllegalArgumentException::class.java) { ModelRecordCodec.decode(wire + 0) }
        for (end in wire.indices) {
            assertThrows(IllegalArgumentException::class.java) { ModelRecordCodec.decode(wire.copyOf(end)) }
        }
    }

    @Test fun resetRejectsInvalidAuthorizationIdentityOrEpoch() {
        listOf(
            ModelRecord.ResetAuthorization("", epoch),
            ModelRecord.ResetAuthorization(nonce, "../owner"),
            ModelRecord.ResetAuthorization("A".repeat(32), epoch),
        ).forEach { record ->
            assertThrows(IllegalArgumentException::class.java) { ModelRecordCodec.encode(record) }
        }
    }

    @Test fun readsShortChunksThroughEofWithoutClosingCallerStream() {
        val source = object : ByteArrayInputStream(emptyWire) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                super.read(bytes, offset, minOf(length, 3))
            override fun available(): Int = error("Must not trust available()")
            override fun close() = error("Caller owns the stream")
        }
        assertEquals(ModelRecord.Selection(epoch), ModelRecordCodec.read(source))
        assertEquals(-1, source.read())
    }

    @Test fun oversizedStreamsStopAfterOneOverflowProbeByte() {
        for (length in listOf(2048, 2049, 1_000_000)) {
            var consumed = 0
            val source = object : InputStream() {
                override fun read(): Int {
                    if (consumed == length) return -1
                    check(consumed < 2049) { "Read exceeded the fixed bound" }
                    consumed++
                    return 0
                }
                override fun close() = error("Caller owns the stream")
            }
            assertThrows(IllegalArgumentException::class.java) { ModelRecordCodec.read(source) }
            assertEquals(minOf(length, 2049), consumed)
        }
    }

    @Test fun zeroProgressBulkReadsDoNotSpinOrLoseBytes() {
        val source = object : ByteArrayInputStream(emptyWire) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertEquals(ModelRecord.Selection(epoch), ModelRecordCodec.read(source))
    }

    private fun activeWire(size: Long = 24, name: ByteArray = "model".toByteArray(),
                           declaredLength: Int = name.size): ByteArray =
        emptyWire.copyOf(emptyWire.size - 1) + byteArrayOf(1) + payload.toByteArray() +
            ByteBuffer.allocate(8).putLong(size).array() +
            byteArrayOf((declaredLength ushr 8).toByte(), declaredLength.toByte()) + name

    private fun rejects(bytes: ByteArray) {
        val failure = assertThrows(IllegalArgumentException::class.java) { ModelRecordCodec.decode(bytes) }
        assertEquals("Invalid model record", failure.message)
        assertNull(failure.cause)
    }

    @Test fun envelopeAndBothBooleanFlagsAreStrict() {
        for (offset in listOf(0, 1, 2, 3, 4, 5, 38, 39)) {
            for (value in listOf(2, 127, 255)) {
                val bytes = emptyWire.copyOf()
                bytes[offset] = value.toByte()
                rejects(bytes)
            }
        }
    }

    @Test fun encoderRejectsBadIdsInEveryIdentityField() {
        for (bad in listOf("", "a".repeat(31), "a".repeat(33), "A".repeat(32),
                           "g".repeat(32), "/".repeat(32), "é".repeat(32), "a".repeat(100_000))) {
            val records = listOf(
                ModelRecord.Selection(bad), ModelRecord.Selection(epoch, bad),
                ModelRecord.Selection(epoch, active = ActiveModel(bad, 24, "model")),
                ModelRecord.ResetAuthorization(bad, epoch), ModelRecord.ResetAuthorization(nonce, bad),
            )
            for (record in records) {
                val failure = assertThrows(IllegalArgumentException::class.java) { ModelRecordCodec.encode(record) }
                assertEquals("Invalid model record", failure.message)
                assertNull(failure.cause)
            }
        }
    }

    @Test fun decoderRejectsInvalidBytesInEveryIdentityField() {
        val selection = ModelRecordCodec.encode(ModelRecord.Selection(epoch, nonce, ActiveModel(payload, 24, "model")))
        val reset = ModelRecordCodec.encode(ModelRecord.ResetAuthorization(nonce, epoch))
        for ((wire, offsets) in listOf(selection to listOf(6, 39, 72), reset to listOf(6, 38))) {
            for (offset in offsets) {
                for (value in listOf(0, 47, 65, 103, 255)) {
                    val bytes = wire.copyOf()
                    bytes[offset] = value.toByte()
                    rejects(bytes)
                }
            }
        }
    }

    @Test fun activeSizeRequires24BytesAndPreservesSigned64BitValues() {
        for (size in listOf(Long.MIN_VALUE, -1, 0, 23)) {
            rejects(activeWire(size))
            assertThrows(IllegalArgumentException::class.java) {
                ModelRecordCodec.encode(ModelRecord.Selection(epoch, active = ActiveModel(payload, size, "model")))
            }
        }
        for (size in listOf(24L, 2_147_483_649L, Long.MAX_VALUE)) {
            assertEquals(ModelRecord.Selection(epoch, active = ActiveModel(payload, size, "model")),
                ModelRecordCodec.decode(activeWire(size)))
        }
    }

    @Test fun everyActiveRecordTruncationAndTrailingByteIsRejected() {
        val wire = activeWire()
        for (end in wire.indices) rejects(wire.copyOf(end))
        rejects(wire + 0)
        rejects(ByteArray(2048))
        rejects(ByteArray(2049))
    }

    @Test fun malformedUtf8IsNeverSilentlyReplaced() {
        for (bytes in listOf(byteArrayOf(0x80.toByte()), byteArrayOf(0xc0.toByte(), 0xaf.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xe2.toByte(), 0x82.toByte()))) {
            rejects(activeWire(name = bytes))
        }
    }

    @Test fun declaredNameLengthMustBeBoundedAndMatchTheDeliveredBytes() {
        for (length in listOf(0, 4, 6, 1025, 65535)) {
            rejects(activeWire(declaredLength = length))
        }
    }

    @Test fun unsafeOrOverlongNamesAreRejectedInBothDirectionsWithoutLeakingThem() {
        for (name in listOf("", " ", " leading", "trailing ", "secret\u0000name",
            "content://private/source\n", "secret\u202Ename", "a".repeat(257), "🌍".repeat(257))) {
            rejects(activeWire(name = name.toByteArray(Charsets.UTF_8)))
            val failure = assertThrows(IllegalArgumentException::class.java) {
                ModelRecordCodec.encode(ModelRecord.Selection(epoch, active = ActiveModel(payload, 24, name)))
            }
            assertEquals("Invalid model record", failure.message)
            assertNull(failure.cause)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelRecordCodec.encode(ModelRecord.Selection(epoch, active = ActiveModel(payload, 24, "bad\uD800name")))
        }
    }

    @Test fun maximumUnicodeNameRoundTripsWithAndWithoutPendingPicker() {
        for (pending in listOf(null, nonce)) {
            for (name in listOf("a".repeat(256), "界".repeat(256), "🌍".repeat(256))) {
                val record = ModelRecord.Selection(epoch, pending, ActiveModel(payload, 24, name))
                val wire = ModelRecordCodec.encode(record)
                assertTrue(wire.size <= ModelRecordCodec.MAX_RECORD_BYTES)
                assertEquals(record, ModelRecordCodec.decode(wire))
                assertEquals(record, ModelRecordCodec.read(ByteArrayInputStream(wire)))
            }
        }
    }

    @Test fun recordStreamIoFailurePropagatesWithoutClosingOrInventingARecord() {
        val failure = IOException("test I/O failure")
        val source = object : InputStream() {
            override fun read(): Int = throw failure
            override fun close() = error("Caller owns the stream")
        }
        assertSame(failure, assertThrows(IOException::class.java) { ModelRecordCodec.read(source) })
    }

    @Test fun versionZeroIsNotAnImplicitLegacyOrResetFormat() {
        rejects(emptyWire.copyOf().also { it[4] = 0 })
        rejects(ModelRecordCodec.encode(ModelRecord.ResetAuthorization(nonce, epoch)).also { it[4] = 0 })
    }

    @Test fun emptySelectionCannotCarryPayloadBytes() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelRecordCodec.decode(emptyWire + payload.toByteArray())
        }
    }
}
