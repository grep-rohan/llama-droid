package io.github.greprohan.llamadroid

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Same pure-JVM tests are compiled into androidTest. No native executable is mocked or run. */
class NativeProcessProbeTest {
    private open class FakeProcess(
        aliveInitially: Boolean = false,
        private val forceOnly: Boolean = false,
        private val unkillable: Boolean = false,
        private val code: Int = 0,
        val out: InputStream = ByteArrayInputStream("stdout fixture".toByteArray()),
        val err: InputStream = ByteArrayInputStream("stderr fixture".toByteArray()),
        val input: OutputStream = object : OutputStream() { override fun write(value: Int) = error("No stdin writes") },
    ) : Process() {
        private val alive = AtomicBoolean(aliveInitially)
        private val exited = CountDownLatch(if (aliveInitially) 1 else 0)
        val destroys = AtomicInteger()
        val forced = AtomicInteger()
        override fun getInputStream(): InputStream = out
        override fun getErrorStream(): InputStream = err
        override fun getOutputStream(): OutputStream = input
        override fun waitFor(): Int = error("Unbounded waitFor must never be used")
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exited.await(timeout, unit)
        override fun exitValue(): Int {
            if (alive.get()) throw IllegalThreadStateException("Still alive")
            return code
        }
        override fun isAlive(): Boolean = alive.get()
        fun exit() { alive.set(false); exited.countDown() }
        override fun destroy() {
            destroys.incrementAndGet()
            if (!forceOnly && !unkillable) exit()
        }
        override fun destroyForcibly(): Process {
            forced.incrementAndGet()
            if (!unkillable) exit()
            return this
        }
    }

    private fun probe() = NativeProcessProbe(timeoutMillis = 500, cleanupMillis = 1_000, tailBytes = 128)
    private fun timeoutProbe() = NativeProcessProbe(timeoutMillis = 40, cleanupMillis = 300, tailBytes = 128)
    private fun bounded(action: () -> NativeProcessProbe.Result): NativeProcessProbe.Result {
        val start = System.nanoTime()
        val result = action()
        assertTrue("Probe exceeded wall bound", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3_000)
        return result
    }
    private fun awaitIgnoringInterrupts(latch: CountDownLatch) {
        while (latch.count > 0) {
            try { latch.await() } catch (_: InterruptedException) { /* Simulate a broken blocking API. */ }
        }
    }

    @Test fun normalExitDrainsBothStreamsAndReaps() {
        val process = FakeProcess()
        val result = probe().run(process)
        assertTrue(result.toString(), result.successful)
        assertEquals(0, result.exitCode)
        assertFalse(result.timedOut)
        assertTrue(result.processReaped && result.readersStopped && result.operationsStopped)
        assertEquals("stdout fixture", result.stdoutTail)
        assertEquals("stderr fixture", result.stderrTail)
        assertEquals(0, process.destroys.get())
    }

    @Test fun abnormalExitIsNotSuccess() {
        val result = probe().run(FakeProcess(code = 7))
        assertEquals(7, result.exitCode)
        assertFalse(result.successful)
        assertFalse(result.timedOut)
        assertTrue(result.processReaped)
    }

    @Test fun timeoutDestroysAndReapsEvenIfCleanupExitIsZero() {
        val process = FakeProcess(aliveInitially = true)
        val result = bounded { timeoutProbe().run(process) }
        assertTrue(result.timedOut)
        assertFalse(result.successful)
        assertTrue(result.processReaped)
        assertFalse(process.isAlive)
        assertTrue(process.destroys.get() > 0)
    }

    @Test fun forceTerminationIsUsedWhenGracefulStopFails() {
        val process = FakeProcess(aliveInitially = true, forceOnly = true)
        val result = bounded { timeoutProbe().run(process) }
        assertTrue(result.timedOut)
        assertTrue(result.processReaped)
        assertTrue(process.forced.get() > 0)
        assertFalse(process.isAlive)
    }

    @Test fun survivingProcessIsReportedNotPretendedClean() {
        val process = FakeProcess(aliveInitially = true, unkillable = true)
        val result = bounded { timeoutProbe().run(process) }
        assertFalse(result.processReaped)
        assertFalse(result.successful)
        assertTrue(result.failures.any { it.contains("not reaped") })
        assertTrue(process.forced.get() > 0)
        process.exit()
    }

    @Test fun bothStreamsAreDrainedConcurrentlyNotSequentially() {
        val entered = CountDownLatch(2)
        fun stream(value: String) = object : InputStream() {
            private val delegate = ByteArrayInputStream(value.toByteArray())
            override fun read(): Int = error("Use bounded block reads")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                entered.countDown()
                if (!entered.await(2, TimeUnit.SECONDS)) throw IOException("Other reader never started")
                return delegate.read(buffer, offset, length)
            }
        }
        val result = probe().run(FakeProcess(out = stream("out"), err = stream("err")))
        assertTrue(result.toString(), result.successful)
        assertEquals("out", result.stdoutTail)
        assertEquals("err", result.stderrTail)
    }

    @Test fun largeSingleLinesDoNotLimitDrainingOrRetainedMemory() {
        val out = ByteArray(2_000_000) { 'x'.code.toByte() } + "OUT-END".toByteArray()
        val err = ByteArray(2_000_000) { 'y'.code.toByte() } + "ERR-END".toByteArray()
        val result = probe().run(FakeProcess(out = ByteArrayInputStream(out), err = ByteArrayInputStream(err)))
        assertTrue(result.toString(), result.successful)
        assertEquals(out.size.toLong(), result.stdoutBytes)
        assertEquals(err.size.toLong(), result.stderrBytes)
        assertEquals(128, result.stdoutRetainedBytes)
        assertEquals(128, result.stderrRetainedBytes)
        assertTrue(result.stdoutTail.endsWith("OUT-END"))
        assertTrue(result.stderrTail.endsWith("ERR-END"))
    }

    @Test fun smallTailHandlesUtf8WithoutUnboundedStrings() {
        val bytes = "\u03bb\u03bb\u03bb-final".toByteArray(Charsets.UTF_8)
        val result = NativeProcessProbe(500, 1_000, 8).run(FakeProcess(out = ByteArrayInputStream(bytes)))
        assertTrue(result.toString(), result.successful)
        assertEquals(8, result.stdoutRetainedBytes)
        assertTrue(result.stdoutTail.endsWith("-final"))
        assertTrue(result.stdoutTail.length <= 8)
    }

    @Test fun stdinAndBothOutputStreamsAreClosed() {
        val closed = AtomicInteger()
        fun stream() = object : ByteArrayInputStream(byteArrayOf()) {
            override fun close() { closed.incrementAndGet(); super.close() }
        }
        val input = object : OutputStream() {
            override fun write(value: Int) = error("No input")
            override fun close() { closed.incrementAndGet() }
        }
        val result = probe().run(FakeProcess(out = stream(), err = stream(), input = input))
        assertTrue(result.toString(), result.successful)
        assertEquals(3, closed.get())
    }

    @Test fun readFailureCannotBecomeSuccess() {
        val stream = object : InputStream() { override fun read(): Int = throw IOException("read fixture failure") }
        val result = probe().run(FakeProcess(out = stream))
        assertFalse(result.successful)
        assertTrue(result.failures.any { it.contains("read fixture failure") })
        assertTrue(result.processReaped)
    }

    @Test fun readerErrorCannotSilentlyBecomeSuccess() {
        val stream = object : InputStream() { override fun read(): Int = throw AssertionError("reader fatal fixture") }
        val result = probe().run(FakeProcess(out = stream))
        assertFalse(result.successful)
        assertTrue(result.failures.any { it.contains("reader fatal fixture") })
    }

    @Test fun errorDiagnosticsAreBoundedToo() {
        val stream = object : InputStream() { override fun read(): Int = throw IOException("x".repeat(20_000)) }
        val result = probe().run(FakeProcess(out = stream))
        assertFalse(result.successful)
        assertTrue(result.failures.isNotEmpty())
        assertTrue(result.failures.all { it.length <= 512 })
    }

    @Test fun streamGetterFailureStillCleansChild() {
        val process = object : FakeProcess(aliveInitially = true) {
            override fun getInputStream(): InputStream = throw IOException("getter fixture failure")
        }
        val result = bounded { timeoutProbe().run(process) }
        assertFalse(result.successful)
        assertTrue(result.failures.any { it.contains("getter fixture failure") })
        assertFalse(process.isAlive)
        assertTrue(result.processReaped)
    }

    @Test fun closeFailureIsExplicitFailure() {
        val stream = object : ByteArrayInputStream(byteArrayOf()) {
            override fun close() = throw IOException("close fixture failure")
        }
        val result = probe().run(FakeProcess(err = stream))
        assertFalse(result.successful)
        assertTrue(result.failures.any { it.contains("close fixture failure") })
    }

    @Test fun stuckReaderAndCloseHaveBoundedFailure() {
        val release = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int { awaitIgnoringInterrupts(release); return -1 }
            override fun close() { awaitIgnoringInterrupts(release) }
        }
        try {
            val result = bounded { timeoutProbe().run(FakeProcess(out = stream)) }
            assertFalse(result.successful)
            assertFalse(result.readersStopped)
            assertFalse(result.operationsStopped)
            assertTrue(result.failures.any { it.contains("stdout") })
        } finally { release.countDown() }
    }

    @Test fun forcedStreamCloseMustNotBeCalledCompleteDraining() {
        val closed = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int { awaitIgnoringInterrupts(closed); return -1 }
            override fun close() { closed.countDown() }
        }
        try {
            val result = bounded { timeoutProbe().run(FakeProcess(out = stream)) }
            assertFalse(result.successful)
            assertTrue(result.readersStopped)
            assertTrue(result.failures.any { it.contains("EOF") })
        } finally { closed.countDown() }
    }

    @Test fun brokenTimedWaitCannotHangControllerOrSkipForceKill() {
        val release = CountDownLatch(1)
        val process = object : FakeProcess(aliveInitially = true, forceOnly = true) {
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
                awaitIgnoringInterrupts(release)
                return !isAlive
            }
        }
        try {
            val result = bounded { timeoutProbe().run(process) }
            assertFalse(result.successful)
            assertFalse(result.operationsStopped)
            assertTrue(process.forced.get() > 0)
            assertFalse(process.isAlive)
        } finally { release.countDown(); process.exit() }
    }

    @Test fun brokenDestroyCannotPreventIndependentForceKill() {
        val release = CountDownLatch(1)
        val process = object : FakeProcess(aliveInitially = true, forceOnly = true) {
            override fun destroy() { awaitIgnoringInterrupts(release) }
        }
        try {
            val result = bounded { timeoutProbe().run(process) }
            assertFalse(result.successful)
            assertFalse(result.operationsStopped)
            assertTrue(process.forced.get() > 0)
            assertFalse(process.isAlive)
        } finally { release.countDown(); process.exit() }
    }

    @Test fun waitExceptionStillTriggersCleanup() {
        val process = object : FakeProcess(aliveInitially = true) {
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = throw IllegalStateException("wait failed")
        }
        val result = bounded { timeoutProbe().run(process) }
        assertFalse(result.successful)
        assertTrue(result.failures.any { it.contains("wait failed") })
        assertFalse(process.isAlive)
    }

    @Test fun interruptedCallerCleansChildAndRestoresInterruptFlag() {
        val process = FakeProcess(aliveInitially = true)
        try {
            Thread.currentThread().interrupt()
            val result = bounded { timeoutProbe().run(process) }
            assertFalse(result.successful)
            assertFalse(process.isAlive)
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(result.failures.any { it.contains("interrupted") })
        } finally { Thread.interrupted(); process.exit() }
    }

    @Test fun deadlineIncludesTimeSpentStartingLauncher() {
        val process = FakeProcess(aliveInitially = true)
        val oldStart = System.nanoTime() - TimeUnit.SECONDS.toNanos(1)
        val result = bounded { timeoutProbe().run(process, oldStart) }
        assertTrue(result.timedOut)
        assertFalse(process.isAlive)
        assertFalse(result.successful)
    }

    @Test fun invalidBudgetsAreRejectedBeforeAnyProcessIsStarted() {
        assertThrows(IllegalArgumentException::class.java) { NativeProcessProbe(0, 100, 128) }
        assertThrows(IllegalArgumentException::class.java) { NativeProcessProbe(30_001, 100, 128) }
        assertThrows(IllegalArgumentException::class.java) { NativeProcessProbe(100, 0, 128) }
        assertThrows(IllegalArgumentException::class.java) { NativeProcessProbe(100, 100, 0) }
    }

    @Test fun pinnedBuildEvidenceRequiresActualBuildAndCommitNotExecutableName() {
        val commit = "0ec4027a2a0183de87c007069adddc06e665f7de"
        assertTrue(PinnedVersionEvidence.matches("version: 0.0.1 (build 12020, commit 0ec4027a2)\n", 12020, commit))
        for (output in listOf("llama-server b12020", "version: 0.0.1 (build 12021, commit 0ec4027a2)",
            "version: 0.0.1 (build 12020, commit deadbeef0)", "version: 0.0.1 (build 12020, commit 0ec4027a)")) {
            assertFalse(output, PinnedVersionEvidence.matches(output, 12020, commit))
        }
    }
}
