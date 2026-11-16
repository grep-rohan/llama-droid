package io.github.greprohan.llamadroid.modelimport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Separately filtered post-restart assertions. A new store object alone is NOT process-death proof. */
@RunWith(AndroidJUnit4::class)
class ModelImportCrashVerifyTest {
    @Test fun verifyAfterRestart() {
        val protocol = ModelImportCrashProtocol()
        ModelImportTestStore(InstrumentationRegistry.getInstrumentation().targetContext, protocol.runId, existing = true).use { f ->
            val state = f.store.recover()
            if (protocol.scenario == "reset" && protocol.checkpoint == "reset-next-synced") {
                assertFalse(state.available) // Uncommitted authorization cannot delete even the old model.
                CheckedModelRecord(f.root.files).use {
                    val selected = it.read() as ModelRecord.Selection
                    assertEquals(ModelRecord.Selection(ModelImportCrashProtocol.epoch,
                        active = ActiveModel(ModelImportCrashProtocol.oldId, 50, "old")), selected)
                }
                assertEquals(50L, f.root.files.stat("model-${ModelImportCrashProtocol.oldId}.gguf")!!.size)
                f.root.files.openRead("model-${ModelImportCrashProtocol.oldId}.gguf").use {
                    assertArrayEquals(ModelImportCrashProtocol.bytes(true), it.input.readBytes())
                }
                assertNull(f.root.files.stat("reset")); assertNotNull(f.root.files.stat("reset.next"))
            } else {
                assertTrue(state.canWrite)
                val selection = checkNotNull(state.selection)
                val committedNew = protocol.checkpoint in setOf("selection-committed", "old-payload-delete")
                val expected = when {
                    protocol.scenario == "reset" -> null
                    committedNew -> ModelImportCrashProtocol.newId
                    protocol.scenario == "replace" -> ModelImportCrashProtocol.oldId
                    else -> null
                }
                assertEquals(expected, state.active?.model?.id)
                assertEquals(if (protocol.checkpoint == "request-reserved") ModelImportCrashProtocol.nonce else null,
                    selection.pendingPickerNonce)
                if (expected != null) {
                    assertEquals(50L, state.active!!.model.sizeBytes)
                    assertEquals(if (committedNew) "new" else "old", state.active.model.name)
                    f.root.files.openRead("model-$expected.gguf").use {
                        assertArrayEquals(ModelImportCrashProtocol.bytes(!committedNew), it.input.readBytes())
                    }
                }
                if (protocol.scenario == "reset") assertEquals(ModelImportCrashProtocol.resetEpoch, selection.epoch)
                val coordinator = PickerCoordinator(f.store).also { it.restore(state) }
                assertEquals(if (protocol.checkpoint == "request-reserved") PickerCoordinator.Phase.RESTORED_UNCERTAIN
                    else PickerCoordinator.Phase.IDLE, coordinator.snapshot().phase)
                assertNull(coordinator.claimLaunch()) // Never restart a dead picker/copy.
                f.root.entries().use { inventory ->
                    val expectedNames = setOfNotNull("selection", expected?.let { "model-$it.gguf" })
                    assertEquals(expectedNames, inventory.toSet())
                }
                assertFalse(state.recordDebt || state.payloadDebt)
            }
        } // All fixture files/leases are removed BEFORE the verified frame.
        protocol.verified()
    }
}
