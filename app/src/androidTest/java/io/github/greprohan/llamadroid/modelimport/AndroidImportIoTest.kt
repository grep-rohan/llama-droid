package io.github.greprohan.llamadroid.modelimport

import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Compiled on host; execute only in the reviewed isolated MI-20 batch. */
@RunWith(AndroidJUnit4::class)
class AndroidImportIoTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val io get() = AndroidImportIo(instrumentation.targetContext.contentResolver)
    private fun uri(id: String) = Uri.parse("content://${instrumentation.context.packageName}.modelimport.fixture/fixture/$id")
    private fun expected() = ByteArray(50) { (it * 17).toByte() }.also {
        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).put("GGUF".toByteArray()).putInt(3).putLong(0).putLong(0)
    }
    private fun delivered(id: String): ByteArray {
        val signal = CancellationSignal()
        val source = io.open(uri(id), io.metadata(uri(id), signal), signal)
        return source.use {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(7)
            while (true) {
                val count = it.input.read(buffer)
                if (count == -1) break
                check(count > 0)
                bytes.write(buffer, 0, count)
            }
            bytes.toByteArray()
        }.also { assertTrue(source.closed) }
    }

    @Test fun regularSlicesAndReliableShortPipesDeliverOnlySelectedBytes() {
        for (id in listOf("regular", "slice", "pipe")) assertArrayEquals(expected(), delivered(id))
    }

    @Test fun reliablePipeErrorsAreReadFailuresNotCleanEof() {
        assertThrows(IOException::class.java) { delivered("pipe-error") }
    }

    @Test fun cancellationAndRevocationAreNotOptionalMetadataFallback() {
        val signal = CancellationSignal().also { it.cancel() }
        assertThrows(OperationCanceledException::class.java) { io.metadata(uri("regular"), signal) }
        assertThrows(OperationCanceledException::class.java) { io.open(uri("regular"), ModelMetadata(null, null), signal) }
        assertThrows(SecurityException::class.java) { io.metadata(uri("denied"), CancellationSignal()) }
        assertThrows(SecurityException::class.java) { io.open(uri("denied"), ModelMetadata(null, null), CancellationSignal()) }
        assertThrows(IOException::class.java) { io.open(uri("null-open"), ModelMetadata(null, null), CancellationSignal()) }
    }

    @Test fun acquisitionFailureAndLateClosedPublicationDoNotLeakDescriptors() {
        for (closeFirst in listOf(false, true)) {
            var captured: ModelSource? = null
            val failure = assertThrows(AndroidImportIo.AcquisitionFailure::class.java) {
                io.open(uri("regular"), ModelMetadata(null, null), CancellationSignal()) {
                    captured = it
                    if (closeFirst) it.close() else throw IOException("Publication rejected")
                }
            }
            assertTrue(checkNotNull(captured).closed)
            assertNull(failure.pending)
            captured!!.close() // Idempotent; no second descriptor close.
        }
    }

    @Test fun androidMutationHelperHasDistinctLeasedNonBackupRootsAndSafeCleanup() {
        ModelImportTestStore(instrumentation.targetContext).use { first ->
            ModelImportTestStore(instrumentation.targetContext).use { second ->
                assertNotEquals(first.root.key, second.root.key)
                assertTrue(first.path.path.startsWith(instrumentation.targetContext.noBackupFilesDir.canonicalPath))
                assertNotEquals(java.io.File(instrumentation.targetContext.noBackupFilesDir, "models"), first.path)
                assertThrows(IllegalStateException::class.java) { ModelStore(first.root) }
                assertTrue(first.store.recover().canWrite)
                assertTrue(second.store.recover().canWrite)
                io.policy(first.path).preflight(null)
            }
        }
    }

    @Test fun documentIoRefusesMainThreadAndNonContentInputs() {
        instrumentation.runOnMainSync {
            assertThrows(IllegalStateException::class.java) { io.metadata(uri("regular"), CancellationSignal()) }
        }
        assertThrows(IllegalArgumentException::class.java) { io.metadata(Uri.parse("file:///private"), CancellationSignal()) }
    }

    @Test fun optionalNullEmptyMissingAndOverflowMetadataNeverInventsASize() {
        for (id in listOf("unknown", "overflow", "missing", "empty", "null-query")) {
            assertNull(io.metadata(uri(id), CancellationSignal()).size)
        }
        assertEquals(50L, io.metadata(uri("regular"), CancellationSignal()).size)
    }
}
