package io.github.greprohan.llamadroid.modelimport

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Standalone test-APK process: no target classes, owner paths or production controls. */
class FixtureModelProvider : ContentProvider() {
    private val ids = setOf("regular", "slice", "pipe", "pipe-error", "null-open", "missing", "empty", "null-query", "overflow", "unknown", "denied")
    private fun fixture(uri: Uri): String {
        val ctx = checkNotNull(context)
        val caller = Binder.getCallingUid()
        val targetUid = ctx.packageManager.getPackageUid("io.github.greprohan.llamadroid", 0)
        check(caller == ctx.applicationInfo.uid || (caller == targetUid &&
            ctx.packageManager.checkSignatures(caller, ctx.applicationInfo.uid) == PackageManager.SIGNATURE_MATCH)) { "Fixture caller denied" }
        require(uri.scheme == "content" && uri.authority == ctx.packageName + ".modelimport.fixture" &&
            uri.query == null && uri.fragment == null && uri.pathSegments.size == 2 && uri.pathSegments[0] == "fixture")
        return uri.pathSegments[1].also { require(it in ids); if (it == "denied") throw SecurityException("Fixture denied") }
    }
    private fun payload() = ByteArray(50) { (it * 17).toByte() }.also {
        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).put("GGUF".toByteArray()).putInt(3).putLong(0).putLong(0)
    }
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val id = fixture(uri)
        if (id == "null-query") return null
        val columns = if (id == "missing") arrayOf("unused") else projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        if (id != "empty") cursor.addRow(columns.map { column -> when (column) {
            OpenableColumns.DISPLAY_NAME -> "fixture.gguf"
            OpenableColumns.SIZE -> when (id) { "unknown" -> null; "overflow" -> "9223372036854775808"; else -> 50L }
            else -> null
        } })
        return cursor
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?, cancellationSignal: CancellationSignal?): Cursor? {
        cancellationSignal?.throwIfCanceled()
        return query(uri, projection, selection, selectionArgs, sortOrder)
    }
    override fun openAssetFile(uri: Uri, mode: String, signal: CancellationSignal?): AssetFileDescriptor? {
        signal?.throwIfCanceled()
        return openAssetFile(uri, mode)
    }
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val id = fixture(uri); require(mode == "r")
        if (id == "null-open") return null
        if (id == "pipe" || id == "pipe-error") {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            try {
                // Fifty bytes fit below even POSIX's minimum pipe capacity. Populate before
                // returning: no producer thread, EOF/release race, queue or surviving helper.
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    val bytes = payload()
                    for (offset in bytes.indices step 3) output.write(bytes, offset, minOf(3, bytes.size - offset))
                    if (id == "pipe-error") pipe[1].closeWithError("Generated fixture error")
                }
                return AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)
            } catch (failure: Exception) {
                for (descriptor in pipe) try { descriptor.close() } catch (close: Exception) { if (close !== failure) failure.addSuppressed(close) }
                throw failure
            }
        }
        val file = File.createTempFile("model-import-fixture-", ".bin", checkNotNull(context).cacheDir)
        var descriptor: ParcelFileDescriptor? = null
        try {
            val bytes = payload()
            file.outputStream().use { output -> if (id == "slice") output.write(byteArrayOf(1, 2, 3, 4, 5)); output.write(bytes); output.write(byteArrayOf(6, 7, 8)) }
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            check(file.delete())
            return AssetFileDescriptor(descriptor, if (id == "slice") 5L else 0L, 50L)
        } catch (failure: Exception) { descriptor?.close(); throw failure }
        finally { if (file.exists()) check(file.delete()) }
    }
    override fun getType(uri: Uri): String { fixture(uri); return "application/octet-stream" }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
