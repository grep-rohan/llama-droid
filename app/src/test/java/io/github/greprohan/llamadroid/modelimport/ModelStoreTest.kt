package io.github.greprohan.llamadroid.modelimport

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ModelStoreTest {
    private val epoch = "a".repeat(32)
    private val id = "b".repeat(32)

    @Test fun emptyRootPublishesExplicitEmptySelectionAndKeepsItsEpochOnReopen() {
        val root = FakeModelRoot()
        ModelStore(root) { epoch }.use { store ->
            val state = store.recover()
            assertTrue(state.available)
            assertTrue(state.canWrite)
            assertEquals(ModelRecord.Selection(epoch), state.selection)
            assertNull(state.active)
            assertEquals(state.selection, ModelRecordCodec.decode(root.files.nodes.getValue("selection").bytes))
        }
        ModelStore(root) { error("Existing epoch must not be replaced") }.use {
            assertEquals(epoch, it.recover().selection!!.epoch)
        }
    }

    @Test fun validatedActiveSelectionExposesOnlyItsGeneratedPrivatePath() {
        val root = FakeModelRoot()
        val model = ActiveModel(id, 24, "model")
        root.files.put("selection", ModelRecord.Selection(epoch, active = model))
        root.files.putBytes("model-$id.gguf", ByteArray(24) { it.toByte() })
        ModelStore(root).use {
            val state = it.recover()
            assertTrue(state.available)
            assertEquals(model, state.active!!.model)
            assertEquals("/isolated/${root.key}/models/model-$id.gguf", state.active.path)
        }
        assertTrue(root.files.handles.all { it.closed })
        assertTrue(root.inventories.all { it.closed })
    }

    @Test fun rootInvalidatedDuringInitialInventoryCannotPublishSelection() {
        val root = FakeModelRoot()
        root.files.hook = { if (it == "list-close") root.valid = false }
        ModelStore(root) { epoch }.use { assertFalse(it.recover().available) }
        assertFalse(root.files.events.any { it.startsWith("create:") })
    }

    @Test fun storesShareTheRootLeaseBeforeAnyRootIo() {
        val root = FakeModelRoot()
        ModelStore(root).use {
            assertThrows(IllegalStateException::class.java) { ModelStore(FakeModelRoot(root.key, root.files)) }
            assertTrue(root.files.events.isEmpty())
        }
        ModelStore(root).close()
    }

    @Test fun pendingInventoryCloseBlocksIoAndPreventsLeaseReleaseUntilRetry() {
        val root = FakeModelRoot().apply { files.silent += "list-close" }
        val store = ModelStore(root) { epoch }
        try {
            assertEquals(ModelStore.Problem.RESOURCES_PENDING, store.recover().problem)
            val count = root.files.events.size
            assertEquals(ModelStore.Problem.RESOURCES_PENDING, store.recover().problem)
            assertEquals(count, root.files.events.size)
            assertThrows(IllegalStateException::class.java) { store.close() }
            assertThrows(IllegalStateException::class.java) { ModelStore(FakeModelRoot(root.key, root.files)) }
        } finally { root.files.silent.clear(); store.close() }
        assertTrue(root.inventories.all { it.closed })
        ModelStore(root).close()
    }

    @Test fun missingOrMismatchedActivePayloadNeverTriggersCleanup() {
        for (length in listOf(-1, 0, 23, 25)) {
            val root = FakeModelRoot().apply {
                files.put("selection", ModelRecord.Selection(epoch, active = ActiveModel(id, 24, "model")))
                if (length >= 0) files.putBytes("model-$id.gguf", ByteArray(length))
                files.putBytes("candidate-${"c".repeat(32)}.part", byteArrayOf(1))
            }
            val bytes = root.files.nodes.getValue("selection").bytes.copyOf()
            ModelStore(root).use { assertFalse(it.recover().available) }
            assertArrayEquals(bytes, root.files.nodes.getValue("selection").bytes)
            assertFalse(root.files.events.any { it.startsWith("remove:") })
            assertTrue(root.files.handles.all { it.closed })
        }
    }

    @Test fun invalidEpochFactoryCannotCreateOrReplaceRecords() {
        val root = FakeModelRoot()
        ModelStore(root) { "../not-an-id" }.use { assertFalse(it.recover().available) }
        assertFalse(root.files.events.any { it.startsWith("create:") })
    }

    @Test fun closedStoreCannotRecoverOrReleaseANewerOwnersLease() {
        val root = FakeModelRoot()
        val first = ModelStore(root)
        first.close()
        ModelStore(root).use {
            first.close()
            assertThrows(IllegalStateException::class.java) { first.recover() }
            assertThrows(IllegalStateException::class.java) { ModelStore(root) }
        }
    }

    @Test fun oneRootCannotHaveTwoLiveOwners() {
        val key = UUID.randomUUID().toString()
        ModelOwnership.acquire(key).use {
            assertThrows(IllegalStateException::class.java) { ModelOwnership.acquire(key) }
        }
        ModelOwnership.acquire(key).close()
    }

    @Test fun independentRootsAndIdempotentCloseDoNotReleaseAnotherOwner() {
        val key = UUID.randomUUID().toString()
        val first = ModelOwnership.acquire(key)
        val other = ModelOwnership.acquire("$key-other")
        first.close()
        ModelOwnership.acquire(key).use {
            first.close()
            assertThrows(IllegalStateException::class.java) { ModelOwnership.acquire(key) }
        }
        other.close()
    }
}
