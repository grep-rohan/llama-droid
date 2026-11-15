package io.github.greprohan.llamadroid.modelimport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * V1: ASCII LDMR, u8 version, u8 kind (0 selection / 1 reset).
 * IDs are 32 ASCII lower-hex bytes. Selection: epoch, bool+optional nonce,
 * bool+optional (id, i64 byte count, u16 UTF-8 byte length, name).
 * Reset: reset id, new epoch. Integers are big-endian; no padding/trailing data.
 */
internal object ModelRecordCodec {
    const val MAX_RECORD_BYTES = 2048
    private const val MAGIC = 0x4c444d52

    fun encode(record: ModelRecord): ByteArray {
        validate(record)
        val bytes = ByteArrayOutputStream(MAX_RECORD_BYTES)
        val output = DataOutputStream(bytes)
        output.writeInt(MAGIC)
        output.writeByte(1)
        when (record) {
            is ModelRecord.Selection -> {
                output.writeByte(0)
                output.write(record.epoch.toByteArray(Charsets.US_ASCII))
                output.writeByte(if (record.pendingPickerNonce == null) 0 else 1)
                record.pendingPickerNonce?.let { output.write(it.toByteArray(Charsets.US_ASCII)) }
                output.writeByte(if (record.active == null) 0 else 1)
                record.active?.let {
                    output.write(it.id.toByteArray(Charsets.US_ASCII))
                    output.writeLong(it.sizeBytes)
                    val name = it.name.toByteArray(Charsets.UTF_8)
                    output.writeShort(name.size)
                    output.write(name)
                }
            }
            is ModelRecord.ResetAuthorization -> {
                output.writeByte(1)
                output.write(record.resetId.toByteArray(Charsets.US_ASCII))
                output.write(record.newEpoch.toByteArray(Charsets.US_ASCII))
            }
        }
        check(bytes.size() <= MAX_RECORD_BYTES)
        return bytes.toByteArray()
    }

    /** Read through EOF or one overflow probe. Stream lifetime belongs to the caller. */
    fun read(input: InputStream): ModelRecord {
        val bytes = ByteArray(MAX_RECORD_BYTES + 1)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read == -1) break
            if (read == 0) {
                val single = input.read()
                if (single == -1) break
                bytes[count++] = single.toByte()
            } else {
                count += read
            }
        }
        ensure(count <= MAX_RECORD_BYTES)
        return decode(bytes.copyOf(count))
    }

    fun decode(bytes: ByteArray): ModelRecord {
        ensure(bytes.size <= MAX_RECORD_BYTES)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        try {
            ensure(input.readInt() == MAGIC && input.readUnsignedByte() == 1)
            val kind = input.readUnsignedByte()
            val record = when (kind) {
                0 -> readSelection(input)
                1 -> ModelRecord.ResetAuthorization(readId(input), readId(input))
                else -> throw IllegalArgumentException("Invalid model record")
            }
            ensure(input.read() == -1)
            validate(record)
            return record
        } catch (_: IOException) {
            throw IllegalArgumentException("Invalid model record")
        }
    }

    private fun readSelection(input: DataInputStream): ModelRecord.Selection {
        val epoch = readId(input)
        val pending = if (readFlag(input)) readId(input) else null
        val active = if (readFlag(input)) {
            val id = readId(input)
            val size = input.readLong()
            val length = input.readUnsignedShort()
            ensure(length in 1..ModelName.MAX_UTF8_BYTES)
            val nameBytes = ByteArray(length)
            input.readFully(nameBytes)
            val name = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(nameBytes)).toString()
            ActiveModel(id, size, name)
        } else null
        return ModelRecord.Selection(epoch, pending, active)
    }

    private fun readFlag(input: DataInputStream): Boolean {
        val value = input.readUnsignedByte()
        ensure(value == 0 || value == 1)
        return value == 1
    }

    private fun readId(input: DataInputStream): String {
        val bytes = ByteArray(32)
        input.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun validId(value: String): Boolean =
        value.length == 32 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun validate(record: ModelRecord) {
        when (record) {
            is ModelRecord.Selection -> {
                ensure(validId(record.epoch))
                ensure(record.pendingPickerNonce == null || validId(record.pendingPickerNonce))
                record.active?.let { ensure(validId(it.id) && it.sizeBytes >= 24 && ModelName.isValid(it.name)) }
            }
            is ModelRecord.ResetAuthorization -> ensure(validId(record.resetId) && validId(record.newEpoch))
        }
    }

    private fun ensure(valid: Boolean) = require(valid) { "Invalid model record" }
}
