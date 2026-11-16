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
}
