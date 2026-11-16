package io.github.greprohan.llamadroid.modelimport

import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Externally controlled cases only; NEVER add this class to the ordinary suite. */
@RunWith(AndroidJUnit4::class)
class ModelImportCancellationDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private class Session(val protocol: ModelImportCrashProtocol) : AutoCloseable {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val f = ModelImportTestStore(instrumentation.targetContext, protocol.runId)
        val handler = Handler(Looper.getMainLooper())
        private val notification = AtomicReference<(() -> Unit)?>(null)
        private val delivery = Runnable { notification.get()?.invoke() }
        val io = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(1))
        val helpers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(2))
        val futures = ConcurrentLinkedQueue<Future<*>>()
        val aborts = AtomicInteger()
        val finished = CountDownLatch(1); val copied = CountDownLatch(1)
        val closeEntered = CountDownLatch(1); val closeRelease = CountDownLatch(1)
        val published = CountDownLatch(1)
        val lease = AtomicReference<ModelSource?>(null)
        val uri: Uri = Uri.parse("content://${instrumentation.context.packageName}.modelimport.fixture/fixture/" +
            when (protocol.checkpoint) { "query-hung" -> "hang-query"; "open-hung" -> "hang-open"; else -> "hang-read" })
        val token = checkNotNull(resolver.call(uri, "prepare-hang", uri.lastPathSegment, null)?.getString("token"))
        val controller = ModelImportController({ f.store }, { ModelImportEngine(AndroidImportIo(resolver).policy(f.path)) },
            { task -> futures += io.submit { task() } }, { task -> aborts.incrementAndGet(); futures += helpers.submit { task() } },
            { delay, action -> synchronized(notification) {
                notification.set(action); handler.removeCallbacks(delivery); check(handler.postDelayed(delivery, delay))
            } }, android.os.SystemClock::elapsedRealtime)
        private var detach: (() -> Unit)? = null
        fun <T> main(action: () -> T): T = FutureTask<T> { action() }.let {
            check(handler.post(it)); it.get(2, TimeUnit.SECONDS)
        }
        fun awaitWork() { futures.toList().forEach { it.get(5, TimeUnit.SECONDS) } }
        fun seed() {
            f.root.files.createExclusive("model-${ModelImportCrashProtocol.oldId}.gguf").use {
                it.write(ModelImportCrashProtocol.bytes(true)); it.flush(); it.sync()
            }
            CheckedModelRecord(f.root.files).use {
                check(it.replace(null, ModelRecord.Selection(ModelImportCrashProtocol.epoch,
                    active = ActiveModel(ModelImportCrashProtocol.oldId, 50, "old"))).state == CheckedModelRecord.State.NEW)
            }
            main { detach = controller.observe { state ->
                if (state.bytes >= 24) copied.countDown()
                if (!state.busy && state.outcome != null) finished.countDown()
            } }
        }
        fun acquire(operation: ImportOperation): ModelSource {
            val signal = CancellationSignal()
            operation.registerSignal { signal.cancel() }; operation.beginAcquisition()
            var owned: ModelSource? = null
            try {
                val adapter = AndroidImportIo(resolver)
                val metadata = adapter.metadata(uri, signal); adapter.policy(f.path).preflight(metadata.size)
                adapter.open(uri, metadata, signal) { source ->
                    val captured = if (protocol.checkpoint != "close-hung") source else object : ModelSource by source {
                        // SIMULATED blocked close, not evidence of every OEM's descriptor-close behavior.
                        override fun close() {
                            closeEntered.countDown()
                            try { check(closeRelease.await(120, TimeUnit.SECONDS)) }
                            finally { source.close() }
                        }
                    }
                    owned = operation.publish(captured); lease.set(owned); published.countDown(); operation.check()
                }
                return checkNotNull(owned)
            } catch (failure: AndroidImportIo.AcquisitionFailure) {
                if (owned == null && failure.pending != null) owned = operation.publish(failure.pending)
                throw failure
            } finally { operation.endAcquisition(); operation.refreshResource() }
        }
        override fun close() {
            closeRelease.countDown()
            try {
                resolver.call(uri, "release-hang", token, null)
                awaitWork()
                helpers.shutdown(); check(helpers.awaitTermination(5, TimeUnit.SECONDS))
                awaitWork() // Source-helper completion may enqueue one I/O continuation.
                io.shutdown(); check(io.awaitTermination(5, TimeUnit.SECONDS))
                resolver.call(uri, "finish-hang", token, null)
                f.close() // Only after workers/producers stop. Never touches the production owner.
            } finally { main { detach?.invoke(); notification.set(null); handler.removeCallbacks(delivery) } }
        }
    }

    @Test fun stageUntilCheckpoint() {
        val protocol = ModelImportCrashProtocol()
        require(protocol.scenario == "cancel-hang")
        Session(protocol).use { s ->
            s.seed(); assertTrue(s.controller.initialize()); s.awaitWork()
            assertTrue(s.controller.beginPicker()); s.awaitWork()
            val picker = checkNotNull(s.controller.claimLaunch()); assertTrue(s.controller.markLaunched(picker))
            assertTrue(s.controller.importResult(picker, s::acquire))
            s.resolver.call(s.uri, "await-hang", s.token, null)
            if (protocol.checkpoint in setOf("read-hung", "close-hung")) assertTrue(s.copied.await(5, TimeUnit.SECONDS))
            s.main {
                assertEquals(ImportOperation.CancelResult.ACCEPTED, s.controller.requestCancel())
                repeat(20) { s.controller.requestCancel() }
                val state = s.controller.snapshot()
                if (state.busy) assertFalse(s.controller.beginPicker())
                assertFalse(s.controller.confirmReset(state.revision))
            } // Completion of this future proves the main Looper is responsive while I/O is blocked.
            if (protocol.checkpoint == "close-hung") {
                assertTrue(s.closeEntered.await(5, TimeUnit.SECONDS))
                assertTrue(s.controller.snapshot().busy)
            }
            if (protocol.checkpoint == "open-hung") {
                s.resolver.call(s.uri, "release-hang", s.token, null) // Deliver the late descriptor after accepted Cancel.
                assertTrue(s.finished.await(8, TimeUnit.SECONDS)); s.awaitWork()
                assertEquals(0L, s.published.count); assertTrue(checkNotNull(s.lease.get()).closed)
                assertEquals(ImportOutcome.CANCELLED, s.controller.snapshot().outcome)
            }
            val state = s.controller.snapshot()
            assertNotEquals(ImportOutcome.IMPORTED, state.outcome)
            assertEquals(ModelImportCrashProtocol.oldId, state.store.active!!.model.id)
            assertTrue(s.aborts.get() in 1..2); assertTrue(s.helpers.largestPoolSize <= 2)
            if (state.busy) {
                val op = checkNotNull(state.operation)
                assertTrue(op.cancelAccepted); assertFalse(op.quiescent)
            } else {
                assertEquals(ImportOutcome.CANCELLED, state.outcome); assertTrue(checkNotNull(state.operation).quiescent)
            }
            s.f.root.files.openRead("model-${ModelImportCrashProtocol.oldId}.gguf").use {
                assertArrayEquals(ModelImportCrashProtocol.bytes(true), it.input.readBytes())
            }
            protocol.armed = true; protocol.hit(protocol.checkpoint)
            fail("Controlled cancellation checkpoint was not paused")
        }
    }
}
