package io.github.greprohan.llamadroid.modelimport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Intentionally interrupted setup, excluded from ordinary expected-pass suites. */
@RunWith(AndroidJUnit4::class)
class ModelImportCrashSetupTest {
    @Test fun stageUntilCheckpoint() {
        val protocol = ModelImportCrashProtocol() // Validate before opening any mutable root.
        val f = ModelImportTestStore(InstrumentationRegistry.getInstrumentation().targetContext,
            protocol.runId, decorate = protocol::files)
        try {
            if (protocol.scenario != "first") {
                val old = ModelRecord.Selection(ModelImportCrashProtocol.epoch,
                    active = ActiveModel(ModelImportCrashProtocol.oldId, 50, "old"))
                f.root.files.createExclusive("model-${ModelImportCrashProtocol.oldId}.gguf").use {
                    it.write(ModelImportCrashProtocol.bytes(true)); it.flush(); it.sync()
                }
                CheckedModelRecord(f.root.files).use {
                    assertEquals(CheckedModelRecord.State.NEW, it.replace(null, old).state)
                }
            }
            protocol.armed = true
            if (protocol.scenario == "reset") {
                ModelReset(f.root).use {
                    it.confirm(ModelRecord.ResetAuthorization(ModelImportCrashProtocol.oldId, ModelImportCrashProtocol.resetEpoch))
                }
            } else {
                assertTrue(f.store.recover().canWrite)
                val reserved = f.store.reservePicker(ModelImportCrashProtocol.nonce)
                assertTrue(reserved.accepted); protocol.hit("request-reserved")
                assertTrue(f.store.consumePicker(checkNotNull(reserved.token)).accepted); protocol.hit("request-consumed")
                val source = object : ModelSource {
                    override val metadata = ModelMetadata("new", 50)
                    override val input = ByteArrayInputStream(ModelImportCrashProtocol.bytes(false))
                    override var closed = false
                    override fun close() { input.close(); closed = true }
                }
                val copied = ModelImportEngine(ImportPolicy { android.os.StatFs(f.path.path).availableBytes })
                    .copy(source, { f.store.createCandidate(ModelImportCrashProtocol.newId) })
                assertTrue(source.closed && copied.candidate.closed); protocol.hit("streams-closed")
                f.store.promote(copied)
                assertEquals(CheckedModelRecord.State.NEW, f.store.publishCandidate(copied).outcome)
            }
            fail("Requested checkpoint was not reached")
        } finally { f.store.close() } // Keep namespace for separate verification; never claim a setup pass.
    }
}
