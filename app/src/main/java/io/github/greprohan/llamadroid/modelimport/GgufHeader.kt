package io.github.greprohan.llamadroid.modelimport

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fixed-header check only; counts/body/tensors are never parsed or allocated from. */
internal object GgufHeader {
    const val SIZE = 24
    data class Info(val version: Int, val order: ByteOrder)
    enum class Reason { TRUNCATED, MAGIC, VERSION }
    class Invalid(val reason: Reason) : IOException("Invalid GGUF header: ${reason.name}")
    fun parse(prefix: ByteArray): Info {
        if (prefix.size < SIZE) throw Invalid(Reason.TRUNCATED)
        require(prefix.size == SIZE) { "Expected fixed GGUF prefix" }
        if (prefix[0] != 71.toByte() || prefix[1] != 71.toByte() ||
            prefix[2] != 85.toByte() || prefix[3] != 70.toByte()) throw Invalid(Reason.MAGIC)
        val bytes = ByteBuffer.wrap(prefix)
        val little = bytes.order(ByteOrder.LITTLE_ENDIAN).getInt(4)
        if (little == 2 || little == 3) return Info(little, ByteOrder.LITTLE_ENDIAN)
        if (bytes.order(ByteOrder.BIG_ENDIAN).getInt(4) == 3) return Info(3, ByteOrder.BIG_ENDIAN)
        throw Invalid(Reason.VERSION)
    }
}
