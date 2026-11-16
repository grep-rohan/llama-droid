package io.github.greprohan.llamadroid.modelimport

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Standalone test-APK process: no target classes, owner paths or production controls. */
class FixtureModelProvider : ContentProvider() {
    private val ids = setOf("regular", "slice", "pipe", "pipe-error", "null-open", "missing", "empty", "null-query", "overflow", "unknown", "denied", "gated-unknown")
    private val gate = Any()
    private class Gated {
        val token = UUID.randomUUID().toString().replace("-", "")
        val release = CountDownLatch(1)
        val ready = CountDownLatch(1)
        var opened = false
        var closed = false
        @Volatile var worker: Thread? = null
        @Volatile var failed = false
    }
    private var gated: Gated? = null
    private fun caller(): android.content.Context {
        val ctx = checkNotNull(context)
        val caller = Binder.getCallingUid()
        val targetUid = ctx.packageManager.getPackageUid("io.github.greprohan.llamadroid", 0)
        check(caller == ctx.applicationInfo.uid || (caller == targetUid &&
            ctx.packageManager.checkSignatures(caller, ctx.applicationInfo.uid) == PackageManager.SIGNATURE_MATCH)) { "Fixture caller denied" }
        return ctx
    }
    private fun fixture(uri: Uri): String {
        val ctx = caller()
        require(uri.scheme == "content" && uri.authority == ctx.packageName + ".modelimport.fixture" &&
            uri.query == null && uri.fragment == null && uri.pathSegments.size == 2 && uri.pathSegments[0] == "fixture")
        return uri.pathSegments[1].also { require(it in ids); if (it == "denied") throw SecurityException("Fixture denied") }
    }
    private fun payload() = ByteArray(50) { (it * 17).toByte() }.also {
        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).put("GGUF".toByteArray()).putInt(3).putLong(0).putLong(0)
    }
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        caller(); require(extras == null || extras.isEmpty)
        if (method == "prepare-gated") {
            require(arg == null)
            val state = synchronized(gate) { check(gated == null); Gated().also { gated = it } }
            return Bundle().apply { putString("token", state.token) }
        }
        require(method == "release-gated" || method == "finish-gated")
        val state = synchronized(gate) { checkNotNull(gated).also { require(arg == it.token) } }
        if (method == "finish-gated") synchronized(gate) { state.closed = true }
        state.release.countDown()
        if (method == "finish-gated") {
            if (state.opened) check(state.ready.await(5, TimeUnit.SECONDS))
            state.worker?.let { it.join(5000); check(!it.isAlive) { "Fixture producer still alive" } }
            synchronized(gate) { check(gated === state); gated = null }
        }
        return Bundle().apply { putBoolean("failed", state.failed) }
    }
    private fun gatedPipe(): AssetFileDescriptor {
        val state = synchronized(gate) { checkNotNull(gated).also { check(!it.opened && !it.closed); it.opened = true } }
        var pipe: Array<ParcelFileDescriptor>? = null
        try {
            val ends = ParcelFileDescriptor.createReliablePipe().also { pipe = it }
            val worker = Thread({
                try {
                    val output = ParcelFileDescriptor.AutoCloseOutputStream(ends[1])
                    try {
                        val bytes = payload()
                        output.write(bytes, 0, 24)
                        check(state.release.await(15, TimeUnit.SECONDS)) { "Fixture release timed out" }
                        output.write(bytes, 24, bytes.size - 24)
                    } catch (failure: Exception) {
                        state.failed = true
                        ends[1].closeWithError("Generated gated fixture error")
                        throw failure
                    } finally { output.close() }
                } catch (_: Exception) {
                    state.failed = true
                    try { ends[1].close() } catch (_: Exception) { }
                }
            }, "gated-model-fixture")
            synchronized(gate) { check(gated === state && !state.closed); state.worker = worker; worker.start() }
            return AssetFileDescriptor(ends[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        } catch (failure: Exception) {
            pipe?.forEach { descriptor -> try { descriptor.close() } catch (close: Exception) { if (close !== failure) failure.addSuppressed(close) } }
            throw failure
        } finally { state.ready.countDown() }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val id = fixture(uri)
        if (id == "null-query") return null
        val columns = if (id == "missing") arrayOf("unused") else projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        if (id != "empty") cursor.addRow(columns.map { column -> when (column) {
            OpenableColumns.DISPLAY_NAME -> "fixture.gguf"
            OpenableColumns.SIZE -> when (id) { "unknown", "gated-unknown" -> null; "overflow" -> "9223372036854775808"; else -> 50L }
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
        if (id == "gated-unknown") return gatedPipe()
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
