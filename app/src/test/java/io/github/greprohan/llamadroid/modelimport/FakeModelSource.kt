package io.github.greprohan.llamadroid.modelimport

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Legal sequential stream, including >2 GiB, with no model-sized allocations. */
internal class FakeModelSource(val length: Long, reported: Long? = length) : ModelSource {
    override val metadata = ModelMetadata("fixture", reported)
    override var closed = false
    var closes = 0
    var consumed = 0L
    var maxRequest = 0
    var fault: (String) -> Unit = {}
    var ignoreClose = false
    private val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        .put("GGUF".toByteArray()).putInt(3).putLong(0).putLong(0).array()
    override val input = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 255
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            require(off >= 0 && len >= 0 && off <= b.size - len)
            check(!closed); fault("read")
            maxRequest = maxOf(maxRequest, len)
            if (len == 0) return 0
            if (consumed == length) return -1
            val n = minOf(len.toLong(), length - consumed).toInt()
            // Generated body is all zeroes; fill only the fixed prefix.
            b.fill(0, off, off + n)
            if (consumed < header.size) {
                val prefix = minOf(n.toLong(), header.size - consumed).toInt()
                header.copyInto(b, off, consumed.toInt(), consumed.toInt() + prefix)
            }
            consumed += n
            return n
        }
        override fun available(): Int = error("No size/seek capability")
    }
    override fun close() { closes++; fault("close"); if (!ignoreClose) closed = true }
}

internal class CountingModelCandidate(private val source: ModelSource) : ModelCandidate {
    override val id = "b".repeat(32)
    override var closed = false
    var written = 0L
    var closes = 0
    var discards = 0
    val events = mutableListOf<String>()
    var fault: (String) -> Unit = {}
    var ignoreClose = false
    override fun write(bytes: ByteArray, offset: Int, count: Int) {
        check(!closed); require(count in 1..bytes.size && offset in 0..bytes.size - count)
        fault("write"); written = Math.addExact(written, count.toLong())
    }
    override fun flush() { events += "flush"; fault("flush") }
    override fun sync() { events += "sync"; fault("sync") }
    override fun close() { closes++; events += "close"; fault("close"); if (!ignoreClose) closed = true }
    override fun discard() { check(closed && source.closed); discards++; events += "discard"; fault("discard") }
}
