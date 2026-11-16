package io.github.greprohan.llamadroid.modelimport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ModelImportEngineTest {
    private fun ModelImportEngine() = ModelImportEngine(ImportPolicy { Long.MAX_VALUE })
    private fun bytes(size: Int = 24) = ByteArray(size) { (it * 17).toByte() }.also {
        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).put("GGUF".toByteArray()).putInt(3).putLong(-1).putLong(-1)
    }
    private class Source(data: ByteArray, chunk: Int = Int.MAX_VALUE) : ModelSource {
        override val metadata = ModelMetadata("model", null)
        override var closed = false
        var closes = 0
        var consumed = 0
        var fault: (String) -> Unit = {}
        var ignoreClose = false
        override val input = object : ByteArrayInputStream(data) {
            override fun read(): Int { fault("read-one"); return super.read().also { if (it != -1) consumed++ } }
            override fun read(b: ByteArray, offset: Int, length: Int): Int {
                fault("read")
                return super.read(b, offset, minOf(length, chunk)).also { if (it > 0) consumed += it }
            }
            override fun available(): Int = error("Non-seekable source")
            override fun reset() = error("Must never reopen or seek")
        }
        override fun close() { closes++; fault("close"); if (!ignoreClose) closed = true }
    }
    private class Candidate : ModelCandidate {
        override val id = "a".repeat(32)
        override var closed = false
        val output = ByteArrayOutputStream()
        val events = mutableListOf<String>()
        var closes = 0
        var fault: (String) -> Unit = {}
        var ignoreClose = false
        override fun write(bytes: ByteArray, offset: Int, count: Int) { check(!closed); fault("write"); output.write(bytes, offset, count) }
        override fun flush() { fault("flush"); events += "flush" }
        override fun sync() { fault("sync"); events += "sync" }
        override fun close() { events += "close"; closes++; fault("close"); if (!ignoreClose) closed = true }
        override fun discard() { check(closed) }
    }

    private fun settle(source: Source, candidate: Candidate) {
        source.fault = {}; candidate.fault = {}; source.ignoreClose = false; candidate.ignoreClose = false
        if (!source.closed) source.close()
        if (!candidate.closed) candidate.close()
        assertTrue(source.closed && candidate.closed)
    }

    @Test fun rejectsAllEarlyEofsBeforeCreatingAnyCandidateAndClosesSourceOnce() {
        for (length in 0 until 24) {
            val source = Source(bytes().copyOf(length), 2)
            val failure = assertThrows(ModelImportEngine.Failure::class.java) {
                ModelImportEngine().copy(source, { error("Invalid header must precede candidate creation") })
            }
            assertEquals(GgufHeader.Reason.TRUNCATED, (failure.primary as GgufHeader.Invalid).reason)
            assertTrue(source.closed); assertEquals(1, source.closes)
        }
    }

    @Test fun badMagicStopsAtThePrefixWithoutReadingTheBody() {
        val source = Source(bytes(4096).also { it[0] = 0 })
        val failure = assertThrows(ModelImportEngine.Failure::class.java) {
            ModelImportEngine().copy(source, { error("No candidate for bad magic") })
        }
        assertEquals(GgufHeader.Reason.MAGIC, (failure.primary as GgufHeader.Invalid).reason)
        assertEquals(24, source.consumed); assertTrue(source.closed)
    }

    @Test fun headerPassingBodyCorruptionMayPassThisDeliberatelyLimitedCheck() {
        val data = bytes(29); val candidate = Candidate()
        assertEquals(29L, ModelImportEngine().copy(Source(data), { candidate }).bytes)
        assertArrayEquals(data, candidate.output.toByteArray()) // Counts claim tensors the body cannot contain.
    }

    @Test fun sourceCloseFailureCannotHandOffEvenWhenEveryByteWasCopied() {
        val source = Source(bytes(40)); val candidate = Candidate(); val primary = IOException("source close")
        source.fault = { if (it == "close") throw primary }
        try {
            val failure = assertThrows(ModelImportEngine.Failure::class.java) { ModelImportEngine().copy(source, { candidate }) }
            assertSame(primary, failure.primary)
            assertEquals(ModelImportEngine.Phase.SOURCE_CLOSE, failure.phase)
            assertTrue(failure.resourcesPending); assertTrue(candidate.closed)
            assertEquals(1, source.closes); assertEquals(listOf("close"), candidate.events)
        } finally { settle(source, candidate) }
    }

    @Test fun destinationFaultsCannotProduceAHandoffAndPreserveThePrimaryCause() {
        for (event in listOf("write", "flush", "sync", "close")) {
            val source = Source(bytes(50)); val candidate = Candidate(); val primary = IOException("private content://source")
            candidate.fault = { if (it == event) throw primary }
            try {
                val failure = assertThrows(ModelImportEngine.Failure::class.java) { ModelImportEngine().copy(source, { candidate }) }
                assertSame(primary, failure.primary); assertSame(candidate, failure.candidate)
                assertFalse(failure.message!!.contains("content://")); assertTrue(source.closed)
                assertEquals(event == "close", failure.resourcesPending)
            } finally { settle(source, candidate) }
        }
    }

    @Test fun readFailureAfterPrefixStillClosesBothOwnedResources() {
        val source = Source(bytes(50)); val candidate = Candidate()
        val primary = IOException("read failed")
        source.fault = { if (it == "read" && source.consumed >= 24) throw primary }
        val failure = assertThrows(ModelImportEngine.Failure::class.java) { ModelImportEngine().copy(source, { candidate }) }
        assertSame(primary, failure.primary); assertEquals(ModelImportEngine.Phase.READ, failure.phase)
        assertTrue(source.closed && candidate.closed); assertEquals(24, candidate.output.size())
    }

    @Test fun silentCloseIsPendingNotSuccessAndIsNotRetriedByTheEngine() {
        for (sourcePending in listOf(true, false)) {
            val source = Source(bytes()); val candidate = Candidate()
            source.ignoreClose = sourcePending; candidate.ignoreClose = !sourcePending
            try {
                val failure = assertThrows(ModelImportEngine.Failure::class.java) { ModelImportEngine().copy(source, { candidate }) }
                assertTrue(failure.resourcesPending)
                assertEquals(1, source.closes); assertEquals(1, candidate.closes)
            } finally { settle(source, candidate) }
        }
    }

    @Test fun cancellationBeforeCopyAndAtClosedCandidateHandoffNeverReturnsSuccess() {
        for (late in listOf(false, true)) {
            val source = Source(bytes(40)); val candidate = Candidate(); var cancelled = !late
            candidate.fault = { if (it == "close") cancelled = true }
            try {
                val failure = assertThrows(ModelImportEngine.Failure::class.java) {
                    ModelImportEngine().copy(source, { candidate }, ModelCancellation {
                        if (cancelled) throw java.util.concurrent.CancellationException("Cancelled")
                    })
                }
                assertTrue(failure.primary is java.util.concurrent.CancellationException)
                assertTrue(source.closed); if (late) assertTrue(candidate.closed)
            } finally { settle(source, candidate) }
        }
    }

    @Test fun unexpectedMetadataFailureIsOwnedAndClassifiedBeforeCreatingACandidate() {
        val source = Source(bytes()); val primary = IOException("metadata failed")
        val wrapper = object : ModelSource by source { override val metadata: ModelMetadata get() = throw primary }
        var created = false
        val failure = assertThrows(ModelImportEngine.Failure::class.java) {
            ModelImportEngine().copy(wrapper, { created = true; Candidate() })
        }
        assertSame(primary, failure.primary); assertTrue(source.closed); assertFalse(created)
    }

    @Test fun zeroLengthBulkReadsUseBoundedSingleByteFallback() {
        val data = bytes(51); val source = Source(data, 0); val candidate = Candidate()
        ModelImportEngine().copy(source, { candidate })
        assertArrayEquals(data, candidate.output.toByteArray())
    }

    @Test fun sourceIsClosedBeforeFlushAndProgressCountsDeliveredBytes() {
        val source = Source(bytes(55), 7); val candidate = Candidate(); val progress = mutableListOf<Long>()
        candidate.fault = { if (it == "flush") assertTrue(source.closed) }
        ModelImportEngine().copy(source, { candidate }, progress = { progress += it })
        assertEquals(24L, progress.first()); assertEquals(55L, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun copiesShortNonSeekableUnknownLengthStreamExactlyOnceThroughEof() {
        val data = bytes(4097); val source = Source(data, 3); val candidate = Candidate()
        val result = ModelImportEngine().copy(source, { candidate })
        assertArrayEquals(data, candidate.output.toByteArray())
        assertEquals(data.size.toLong(), result.bytes)
        assertEquals(1, source.closes); assertEquals(1, candidate.closes)
        assertEquals(listOf("flush", "sync", "close"), candidate.events)
    }
    @Test fun copiesBeyondTheFixedBufferWithoutDuplicatingThePrefix() {
        val data = bytes(ModelImportEngine.BUFFER_BYTES * 2 + 7); val candidate = Candidate()
        assertEquals(data.size.toLong(), ModelImportEngine().copy(Source(data), { candidate }).bytes)
        assertArrayEquals(data, candidate.output.toByteArray())
    }
}
