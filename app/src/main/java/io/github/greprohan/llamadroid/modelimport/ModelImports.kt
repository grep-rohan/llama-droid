package io.github.greprohan.llamadroid.modelimport

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One application-context owner; construction does not open a store/provider or retain an Activity. */
internal class ModelImports private constructor(context: Context) {
    companion object {
        @Volatile private var instance: ModelImports? = null
        fun get(context: Context): ModelImports = instance ?: synchronized(this) {
            instance ?: ModelImports(context.applicationContext).also { instance = it }
        }
    }
    private val app = context.applicationContext
    private fun executor(count: Int, queue: Int, name: String) = ThreadPoolExecutor(count, count, 0L,
        TimeUnit.MILLISECONDS, ArrayBlockingQueue(queue), { task -> Thread(task, name) })
    private val worker = executor(1, 1, "model-import-io")
    private val helpers = executor(2, 2, "model-import-abort")
    private fun modelsDirectory() = File(app.noBackupFilesDir.canonicalFile, "models")
    private fun engine() = ModelImportEngine(AndroidImportIo(app.contentResolver).policy(modelsDirectory()))
    val controller = ModelImportController({ ModelStore.open(app.noBackupFilesDir) }, ::engine,
        { worker.execute(it) }, { helpers.execute(it) }, MainNotifications(), SystemClock::elapsedRealtime)

    /** Only a matched in-memory picker result supplies this URI; never persisted/restored as a copy. */
    fun importResult(token: PickerToken, uri: Uri): Boolean {
        require(uri.scheme == "content") { "Expected document URI" }
        return controller.importResult(token) { operation ->
            val signal = CancellationSignal()
            operation.registerSignal { signal.cancel() } // Before query/open may block.
            operation.beginAcquisition()
            var owned: ModelSource? = null
            try {
                operation.check()
                val io = AndroidImportIo(app.contentResolver)
                val metadata = io.metadata(uri, signal)
                operation.check()
                io.policy(modelsDirectory()).preflight(metadata.size)
                operation.check()
                io.open(uri, metadata, signal) { source ->
                    owned = operation.publish(source) // Immutable descriptor generation, before stream construction.
                    operation.check()
                }
                operation.check()
                checkNotNull(owned)
            } catch (failure: AndroidImportIo.AcquisitionFailure) {
                if (owned == null && failure.pending != null) owned = operation.publish(failure.pending)
                throw failure
            } finally {
                operation.endAcquisition()
                operation.refreshResource()
            }
        }
    }

    private class MainNotifications : ModelImportController.Notifications {
        private val handler = Handler(Looper.getMainLooper())
        private val lock = Any()
        private var latest: (() -> Unit)? = null
        private val delivery = Runnable {
            val action = synchronized(lock) { latest }
            action?.invoke() // Never execute controller/Activity callbacks under this lock.
        }
        override fun replace(delayMillis: Long, action: () -> Unit) = synchronized(lock) {
            latest = action
            handler.removeCallbacks(delivery)
            check(handler.postDelayed(delivery, delayMillis)) { "Main notification queue unavailable" }
        }
    }
}
