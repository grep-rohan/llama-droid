package io.github.greprohan.llamadroid

import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Test-only observer. No shell, launch hooks, model, listener or production lifecycle code. */
internal class NativeProcessProbe(
    private val timeoutMillis: Long = 30_000,
    private val cleanupMillis: Long = 2_000,
    private val tailBytes: Int = 16 * 1024,
) {
    init {
        require(timeoutMillis in 1..30_000)
        require(cleanupMillis in 1..2_000)
        require(tailBytes in 1..64 * 1024)
    }

    data class Result(
        val exitCode: Int?,
        val timedOut: Boolean,
        val processReaped: Boolean,
        val readersStopped: Boolean,
        val streamsDrained: Boolean,
        val operationsStopped: Boolean,
        val stdoutTail: String,
        val stderrTail: String,
        val stdoutBytes: Long,
        val stderrBytes: Long,
        val stdoutRetainedBytes: Int,
        val stderrRetainedBytes: Int,
        val exitElapsedMillis: Long?,
        val elapsedMillis: Long,
        val failures: List<String>,
    ) {
        val successful: Boolean get() = exitCode == 0 && !timedOut && processReaped &&
            readersStopped && streamsDrained && operationsStopped && failures.isEmpty()
    }

    /** Construct the probe before launching. startNanos may include time spent in launcher.start. */
    fun run(process: Process, startNanos: Long = System.nanoTime()): Result {
        val session = Session(process, startNanos)
        try {
            session.startReaders()
            session.observeExit()
        } finally {
            try {
                session.cleanup()
            } finally {
                if (session.interrupted) Thread.currentThread().interrupt()
            }
        }
        return session.result()
    }

    private class Tail(private val capacity: Int) {
        private val bytes = ByteArray(capacity)
        private var start = 0
        private var size = 0
        private var total = 0L
        @Synchronized fun append(buffer: ByteArray, count: Int) {
            total += count
            if (count >= capacity) {
                System.arraycopy(buffer, count - capacity, bytes, 0, capacity)
                start = 0
                size = capacity
            } else {
                for (index in 0 until count) {
                    bytes[(start + size) % capacity] = buffer[index]
                    if (size == capacity) start = (start + 1) % capacity else size++
                }
            }
        }
        data class Snapshot(val text: String, val retained: Int, val total: Long)
        @Synchronized fun snapshot(): Snapshot {
            val retained = ByteArray(size) { bytes[(start + it) % capacity] }
            return Snapshot(String(retained, Charsets.UTF_8), size, total)
        }
    }

    private data class Pending<T>(val label: String, val future: FutureTask<T>, val thread: Thread)
    private data class Outcome<T>(val completed: Boolean, val value: T? = null, val timedOut: Boolean = false)

    private inner class Session(private val process: Process, private val started: Long) {
        private val out = Tail(tailBytes)
        private val err = Tail(tailBytes)
        private val stop = AtomicBoolean(false)
        private val errors = ConcurrentLinkedQueue<String>()
        private val operations = mutableListOf<Thread>() // Controller-thread confined.
        private val outStream = AtomicReference<InputStream?>(null)
        private val errStream = AtomicReference<InputStream?>(null)
        private val outEof = AtomicBoolean(false)
        private val errEof = AtomicBoolean(false)
        private val readers = mutableListOf<Thread>()
        private val budget = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        private val entered = System.nanoTime()
        private val deadline = entered + (budget - (entered - started).coerceIn(0, budget))
        private var code: Int? = null
        private var timedOut = false
        private var reaped = false
        private var readersStopped = false
        private var streamsDrained = false
        private var operationsStopped = false
        private var exitElapsed: Long? = null
        var interrupted = false
            private set

        private fun failure(label: String, cause: Throwable? = null) {
            val detail = if (cause == null) "" else
                ": ${cause.javaClass.simpleName.take(80)}: ${cause.message.orEmpty().take(384)}"
            errors.add((label.take(128) + detail).take(512))
        }
        private fun remaining(end: Long): Long = (end - System.nanoTime()).coerceAtLeast(0)
        private fun elapsed(): Long = TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - started).coerceAtLeast(0))
        private fun thread(label: String, action: () -> Unit): Thread = Thread(action, "native-probe-$label").apply {
            isDaemon = true // A broken API must be reported, never hold the test VM alive.
        }
        private fun <T> begin(label: String, action: () -> T): Pending<T> {
            val future = FutureTask<T> { action() }
            val worker = thread(label) { future.run() }
            operations.add(worker)
            worker.start()
            return Pending(label, future, worker)
        }
        private fun <T> await(pending: Pending<T>, end: Long): Outcome<T> {
            return try {
                Outcome(true, pending.future.get(remaining(end), TimeUnit.NANOSECONDS))
            } catch (_: TimeoutException) {
                pending.future.cancel(true)
                pending.thread.interrupt()
                failure("${pending.label} deadline exceeded")
                Outcome(false, timedOut = true)
            } catch (error: ExecutionException) {
                failure(pending.label, error.cause ?: error)
                Outcome(false)
            } catch (error: InterruptedException) {
                interrupted = true // get/join clears the flag; restore only after cleanup.
                pending.future.cancel(true)
                failure("${pending.label} interrupted", error)
                Outcome(false)
            }
        }
        private fun <T> call(label: String, end: Long, action: () -> T): Outcome<T> {
            if (remaining(end) == 0L) {
                failure("$label deadline exceeded")
                return Outcome(false, timedOut = true)
            }
            return await(begin(label, action), end)
        }
        private fun waitForExit(label: String, end: Long): Outcome<Int?> = call(label, end) {
            val millis = TimeUnit.NANOSECONDS.toMillis(remaining(end)).coerceAtLeast(1)
            if (process.waitFor(millis, TimeUnit.MILLISECONDS)) process.exitValue() else null
        }
        fun startReaders() {
            fun reader(label: String, tail: Tail, reference: AtomicReference<InputStream?>,
                       eof: AtomicBoolean, source: () -> InputStream) {
                val worker = thread(label) {
                    try {
                        val stream = source()
                        reference.set(stream)
                        val buffer = ByteArray(4096)
                        while (!stop.get() && !Thread.currentThread().isInterrupted) {
                            val count = stream.read(buffer)
                            if (count < 0) { eof.set(true); break }
                            if (count > buffer.size) error("Invalid stream read count")
                            if (count == 0) Thread.yield() else tail.append(buffer, count)
                        }
                    } catch (error: Throwable) {
                        // A dead reader (including an Error) must never look like a
                        // successfully drained pipe. Ignore only deliberate closure IO.
                        if (!stop.get() || error !is java.io.IOException) failure("$label read", error)
                    }
                    // Stream closing is a separate bounded operation: close can block too.
                }
                readers.add(worker)
                worker.start()
            }
            reader("stdout", out, outStream, outEof) { process.inputStream }
            reader("stderr", err, errStream, errEof) { process.errorStream }
        }
        fun observeExit() {
            val result = waitForExit("waitFor", deadline)
            timedOut = result.timedOut || (result.completed && result.value == null)
            if (result.completed && result.value != null) {
                code = result.value
                reaped = true
                exitElapsed = elapsed()
                timedOut = timedOut || exitElapsed!! > timeoutMillis
            }
        }
        private fun joinUntil(worker: Thread, end: Long) {
            while (worker.isAlive && remaining(end) > 0) {
                val nanos = remaining(end)
                if (nanos == 0L) break
                try {
                    worker.join(nanos / 1_000_000, (nanos % 1_000_000).toInt())
                } catch (error: InterruptedException) {
                    interrupted = true
                    failure("${worker.name} join interrupted", error)
                }
            }
        }
        fun cleanup() {
            val cleanupBudget = TimeUnit.MILLISECONDS.toNanos(cleanupMillis)
            val end = System.nanoTime() + cleanupBudget
            fun slice(fraction: Int) = minOf(end, System.nanoTime() + cleanupBudget / fraction)
            if (!reaped) {
                call("destroy", slice(6)) { process.destroy() }
                val graceful = waitForExit("reap after destroy", slice(6))
                if (graceful.completed && graceful.value != null) {
                    code = graceful.value
                    reaped = true
                }
                if (!reaped) {
                    call("destroyForcibly", slice(6)) { process.destroyForcibly(); Unit }
                    val forced = waitForExit("reap after force", slice(4))
                    if (forced.completed && forced.value != null) {
                        code = forced.value
                        reaped = true
                    }
                }
            }
            // Let readers drain EOF after exit before forcing closure; never readLine().
            val drainEnd = minOf(end, System.nanoTime() + cleanupBudget / 4)
            readers.forEach { joinUntil(it, drainEnd) }
            // EOF induced only by forced closure cannot prove the pipe was drained.
            streamsDrained = outEof.get() && errEof.get()
            if (!outEof.get()) failure("stdout did not reach EOF before forced closure")
            if (!errEof.get()) failure("stderr did not reach EOF before forced closure")
            stop.set(true)
            val closes = listOf(
                begin("close stdout") { (outStream.get() ?: process.inputStream).close() },
                begin("close stderr") { (errStream.get() ?: process.errorStream).close() },
                begin("close stdin") { process.outputStream.close() },
            )
            readers.filter { it.isAlive }.forEach { it.interrupt() }
            closes.forEach { await(it, end) }
            readers.forEach { joinUntil(it, end) }
            operations.forEach { joinUntil(it, end) }
            readersStopped = readers.none { it.isAlive }
            operationsStopped = operations.none { it.isAlive }
            if (!reaped) failure("Child process not reaped")
            readers.filter { it.isAlive }.forEach { failure("${it.name} reader did not stop") }
            operations.filter { it.isAlive }.forEach { failure("${it.name} operation did not stop") }
        }
        fun result(): Result {
            val stdout = out.snapshot()
            val stderr = err.snapshot()
            return Result(
                exitCode = code, timedOut = timedOut, processReaped = reaped,
                readersStopped = readersStopped, streamsDrained = streamsDrained, operationsStopped = operationsStopped,
                stdoutTail = stdout.text, stderrTail = stderr.text,
                stdoutBytes = stdout.total, stderrBytes = stderr.total,
                stdoutRetainedBytes = stdout.retained, stderrRetainedBytes = stderr.retained,
                exitElapsedMillis = exitElapsed, elapsedMillis = elapsed(), failures = errors.toList(),
            )
        }
    }
}

/** Pinned upstream build-info.cpp.in prints this header to stderr; generic names are insufficient. */
internal object PinnedVersionEvidence {
    fun matches(output: String, build: Int, fullCommit: String): Boolean {
        require(build > 0 && fullCommit.matches(Regex("[0-9a-f]{40}")))
        val commit = Regex.escape(fullCommit.take(9)) // Actual b12020 binary embeds a 9-character commit.
        return Regex("(?m)^version: [^\\r\\n]+ \\(build $build, commit $commit\\)\\r?$")
            .containsMatchIn(output)
    }
}
