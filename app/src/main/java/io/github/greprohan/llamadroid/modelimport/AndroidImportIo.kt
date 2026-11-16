package io.github.greprohan.llamadroid.modelimport

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Provider calls belong to the serialized I/O worker, never the UI thread. */
internal class AndroidImportIo(private val resolver: ContentResolver) {
    class AcquisitionFailure(val pending: ModelSource?, cause: Exception) : IOException("Cannot open model document", cause)
    private fun worker() { check(Looper.myLooper() != Looper.getMainLooper()) { "Document I/O on main thread" } }
    private fun document(uri: Uri) { require(uri.scheme == "content") { "Expected document URI" } }

    fun metadata(uri: Uri, signal: CancellationSignal): ModelMetadata {
        worker(); document(uri); signal.throwIfCanceled()
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = resolver.query(uri, projection, null, null, null, signal) ?: run {
            signal.throwIfCanceled()
            return ModelMetadata(null, null)
        }
        val owner = CursorOwner(cursor)
        var primary: Exception? = null
        var value = ModelMetadata(null, null)
        try {
            signal.throwIfCanceled()
            value = if (!cursor.moveToFirst()) ModelMetadata(null, null) else {
                val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                ModelMetadata(if (name < 0 || cursor.isNull(name)) null else ModelName.sanitize(cursor.getString(name)),
                    if (size < 0 || cursor.isNull(size)) null else cursor.getString(size)?.toLongOrNull()?.takeIf { it > 0 })
            }
        } catch (failure: Exception) { primary = failure }
        try { owner.close() } catch (close: Exception) {
            if (primary == null) primary = close else if (primary !== close) primary.addSuppressed(close)
        }
        primary?.let { if (!owner.closed) throw AcquisitionFailure(owner, it) else throw it }
        signal.throwIfCanceled()
        return value
    }

    fun policy(destination: File): ImportPolicy = ImportPolicy {
        worker()
        StatFs(destination.path).availableBytes
    }

    /** publish registers immutable ownership before any descriptor-to-stream transition. */
    fun open(uri: Uri, metadata: ModelMetadata, signal: CancellationSignal,
             publish: (ModelSource) -> Unit = {}): ModelSource {
        worker(); document(uri); signal.throwIfCanceled()
        val asset = resolver.openAssetFileDescriptor(uri, "r", signal) ?: throw IOException("No model document stream")
        val source = AssetSource(asset, metadata)
        try {
            publish(source)
            signal.throwIfCanceled()
            source.start()
            signal.throwIfCanceled()
            return source
        } catch (failure: Exception) {
            try { source.close() } catch (close: Exception) { if (close !== failure) failure.addSuppressed(close) }
            throw AcquisitionFailure(source.takeUnless { it.closed }, failure)
        }
    }

    /** Retains ownership if a cursor close fails; it can never be mistaken for document input. */
    private class CursorOwner(private val cursor: Cursor) : ModelSource {
        override val metadata = ModelMetadata(null, null)
        override val input: InputStream get() = error("Metadata cursor is not document input")
        override val closed: Boolean get() = cursor.isClosed
        private var attempted = false
        @Synchronized override fun close() {
            if (closed) return
            if (!attempted) { attempted = true; cursor.close() }
            check(closed) { "Metadata close remains pending" }
        }
    }

    private class AssetSource(private val asset: AssetFileDescriptor, override val metadata: ModelMetadata) : ModelSource {
        private val descriptor = asset.parcelFileDescriptor
        private val fd = descriptor.fileDescriptor
        private var stream: InputStream? = null
        private var closeAttempted = false
        @Volatile override var closed = false
            private set
        override val input: InputStream get() = checkNotNull(stream) { "Source stream is not acquired" }
        // A source-local lock serializes construction/close; never the cancellation/commit gate.
        @Synchronized fun start() {
            check(!closeAttempted)
            val delivered = asset.createInputStream()
            stream = object : InputStream() {
                private fun checked(count: Int): Int { if (count == -1) descriptor.checkError(); return count }
                override fun read(): Int = checked(delivered.read())
                override fun read(bytes: ByteArray, offset: Int, count: Int): Int = checked(delivered.read(bytes, offset, count))
                // Borrowed stream; only AssetSource may close the descriptor/stream.
            }
            ownedStream = delivered
        }
        private var ownedStream: InputStream? = null
        @Synchronized override fun close() {
            if (closed) return
            if (!closeAttempted) {
                closeAttempted = true
                try { ownedStream?.close() ?: asset.close() } finally { closed = !fd.valid() }
            }
            check(closed) { "Document close remains pending" }
        }
    }
}
