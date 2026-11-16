package io.github.greprohan.llamadroid.modelimport

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual filesystem contracts, compiled on host; only isolated generated roots are mutated. */
@RunWith(AndroidJUnit4::class)
class AndroidModelStoreTest {
    private val a = "a".repeat(32)
    private val b = "b".repeat(32)
    private fun fixture() = ModelImportTestStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private fun write(files: ModelFileOps, name: String, bytes: ByteArray) {
        files.createExclusive(name).use { it.write(bytes); it.flush(); it.sync() }
    }

    @Test fun checkedRecordUsesRealExclusiveSyncRenameAndClosedDescriptors() {
        fixture().use { f ->
            val files = f.root.files
            val old = ModelRecord.Selection(a)
            val next = old.copy(pendingPickerNonce = b)
            CheckedModelRecord(files).use { record ->
                assertEquals(CheckedModelRecord.State.NEW, record.replace(null, old).state)
                val before = files.stat("selection")!!
                assertEquals(CheckedModelRecord.State.NEW, record.replace(old, next).state)
                assertEquals(next, record.read()); assertTrue(record.quiescent)
                assertFalse(before.sameFile(files.stat("selection")!!))
                assertNull(files.stat("selection.next"))
            }
            assertEquals(next, f.store.recover().selection)
        }
    }

    @Test fun exclusiveAndNoFollowRefuseExistingAndLinkedFixtureEntries() {
        fixture().use { f ->
            val files = f.root.files; val payload = "model-$a.gguf"; val candidate = "candidate-$b.part"
            write(files, payload, ByteArray(24) { 7 })
            try { files.createExclusive(payload).close(); fail("Must not truncate existing file") } catch (_: android.system.ErrnoException) { }
            val link = File(f.path, candidate)
            Os.symlink(File(f.path, payload).path, link.path)
            try {
                assertEquals(ModelFileOps.Kind.LINK, files.stat(candidate)!!.kind)
                try { files.openRead(candidate).close(); fail("No-follow reader") } catch (_: IllegalStateException) { }
                try { files.remove(candidate); fail("No link unlink in production adapter") } catch (_: IllegalStateException) { }
            } finally { Os.remove(link.path) } // Fixed test-created link, never recursive cleanup.
            files.openRead(payload).use { assertArrayEquals(ByteArray(24) { 7 }, it.input.readBytes()) }
        }
    }

    @Test fun authoritativeSelectionWinsOverSidecarAndRecoveryRetiresOnlyUnselectedBytes() {
        fixture().use { f ->
            val old = ModelRecord.Selection(a, active = ActiveModel(a, 24, "old"))
            val next = old.copy(active = ActiveModel(b, 24, "new"))
            write(f.root.files, "model-$a.gguf", ByteArray(24) { 7 })
            write(f.root.files, "model-$b.gguf", ByteArray(24) { 9 })
            CheckedModelRecord(f.root.files).use { assertEquals(CheckedModelRecord.State.NEW, it.replace(null, old).state) }
            write(f.root.files, "selection.next", ModelRecordCodec.encode(next))
            val state = f.store.recover()
            assertTrue(state.canWrite); assertEquals(old, state.selection)
            f.root.files.openRead("model-$a.gguf").use { assertArrayEquals(ByteArray(24) { 7 }, it.input.readBytes()) }
            assertNull(f.root.files.stat("model-$b.gguf")); assertNull(f.root.files.stat("selection.next"))
        }
    }

    @Test fun onlyCommittedResetResumesToRecordedEmptyEpochOnRealFilesystem() {
        fixture().use { f ->
            assertTrue(f.store.recover().canWrite)
            write(f.root.files, "model-$a.gguf", ByteArray(24) { 7 })
            val auth = ModelRecord.ResetAuthorization(a, b)
            write(f.root.files, "reset.next", ModelRecordCodec.encode(auth))
            assertFalse(f.store.recover().available)
            assertNotNull(f.root.files.stat("model-$a.gguf"))
            f.root.files.remove("reset.next")
            CheckedModelRecord(f.root.files, ModelRecordFile.RESET).use {
                assertEquals(CheckedModelRecord.State.NEW, it.replace(null, auth).state)
            }
            assertEquals(ModelRecord.Selection(b), f.store.recover().selection)
            assertNull(f.root.files.stat("model-$a.gguf")); assertNull(f.root.files.stat("reset"))
        }
    }
}
