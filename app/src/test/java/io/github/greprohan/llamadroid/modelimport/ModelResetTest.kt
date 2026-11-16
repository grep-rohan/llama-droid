package io.github.greprohan.llamadroid.modelimport

import org.junit.Assert.*
import org.junit.Test

class ModelResetTest {
    private val a = "a".repeat(32)
    private val b = "b".repeat(32)
    private val authorization = ModelRecord.ResetAuthorization(b, "e".repeat(32))
    private val old = ModelRecord.Selection(a, b, ActiveModel(a, 24, "old"))
    private fun root() = FakeModelRoot().apply {
        files.put("selection", old)
        files.putBytes("model-$a.gguf", ByteArray(24) { 7 })
    }
    private fun read(root: FakeModelRoot, name: String) = ModelRecordCodec.decode(root.files.nodes.getValue(name).bytes)

    @Test fun noMarkerAndUncommittedSidecarNeverAuthorizeDeletion() {
        for (sidecar in listOf(false, true)) {
            val root = root()
            if (sidecar) root.files.put("reset.next", authorization)
            val before = root.files.nodes.mapValues { it.value.bytes.copyOf() }
            ModelReset(root).use { assertEquals(if (sidecar) ModelReset.Result.BLOCKED else ModelReset.Result.NONE, it.resume()) }
            assertEquals(before.keys, root.files.nodes.keys)
            before.forEach { (name, bytes) -> assertArrayEquals(bytes, root.files.nodes.getValue(name).bytes) }
            assertFalse(root.files.events.any { it.startsWith("remove:") })
        }
    }

    @Test fun committedAuthorizationResumesToItsRecordedEmptyEpochAndRemovesMarkerLast() {
        val root = root().apply {
            files.put("reset", authorization)
            files.put("reset.next", authorization.copy(newEpoch = a))
            files.putBytes("candidate-$b.part", byteArrayOf(9))
        }
        ModelReset(root).use { assertEquals(ModelReset.Result.COMPLETE, it.resume()) }
        assertEquals(setOf("selection"), root.files.nodes.keys)
        assertEquals(ModelRecord.Selection(authorization.newEpoch), read(root, "selection"))
        val removes = root.files.events.filter { it.startsWith("remove:") }
        assertEquals("remove:reset.next", removes.first())
        assertEquals("remove:reset", removes.last())
        assertTrue(root.files.handles.all { it.closed })
        assertTrue(root.inventories.all { it.closed })
    }

    @Test fun failedFirstAuthorizationNeverDeletesExistingModelBytes() {
        for (event in listOf("create:reset.next", "write", "flush", "sync", "closed-write", "rename")) {
            val root = root()
            val bytes = root.files.nodes.getValue("model-$a.gguf").bytes.copyOf()
            root.files.hook = { if (it == event) throw java.io.IOException(event) }
            ModelReset(root).use { assertEquals(event, ModelReset.Result.BLOCKED, it.confirm(authorization)) }
            assertEquals(old, read(root, "selection"))
            assertArrayEquals(bytes, root.files.nodes.getValue("model-$a.gguf").bytes)
            assertFalse(root.files.events.contains("remove:model-$a.gguf"))
            assertTrue(root.files.handles.all { it.closed })
        }
    }

    @Test fun interruptionsAfterCommitAreRepeatableWithTheRecordedEpoch() {
        for (event in listOf("remove:reset.next", "remove:model-$a.gguf", "remove:selection", "create:selection.next",
                             "remove:reset")) {
            val root = root().apply { files.put("reset", authorization); files.put("reset.next", authorization) }
            root.files.hook = { if (it == event) throw java.io.IOException(event) }
            ModelReset(root).use { assertEquals(event, ModelReset.Result.BLOCKED, it.resume()) }
            assertEquals(authorization, read(root, "reset"))
            root.files.hook = {}
            ModelReset(root).use { assertEquals(event, ModelReset.Result.COMPLETE, it.resume()) }
            assertEquals(setOf("selection"), root.files.nodes.keys)
            assertEquals(ModelRecord.Selection(authorization.newEpoch), read(root, "selection"))
        }
    }

    @Test fun corruptAndUncommittedMarkersNeedFreshConsentInsteadOfSidecarPromotion() {
        for (name in listOf("reset", "reset.next")) {
            val root = root().apply { files.putBytes(name, byteArrayOf(1)) }
            ModelReset(root).use {
                assertEquals(ModelReset.Result.BLOCKED, it.resume())
                assertEquals(old, read(root, "selection"))
                assertEquals(ModelReset.Result.COMPLETE, it.confirm(authorization))
            }
            assertEquals(ModelRecord.Selection(authorization.newEpoch), read(root, "selection"))
        }
    }

    @Test fun unknownEntriesAndUnsafeTypesPreventAllResetSweeping() {
        for (name in listOf("source.gguf", "candidate-invalid.part", "model-$b.gguf")) {
            val root = root().apply { files.put("reset", authorization); files.putBytes(name, byteArrayOf(3)) }
            if (name.startsWith("model-")) root.files.nodes.getValue(name).links = 2
            ModelReset(root).use { assertEquals(ModelReset.Result.BLOCKED, it.resume()) }
            assertArrayEquals(byteArrayOf(3), root.files.nodes.getValue(name).bytes)
            assertEquals(old, read(root, "selection"))
            assertFalse(root.files.events.any { it.startsWith("remove:") })
        }
    }

    @Test fun falseRemovalAndPendingDescriptorsStayBlockedWithoutClaimingEmptyState() {
        val root = root().apply { files.put("reset", authorization); files.silent += "remove:model-$a.gguf" }
        ModelReset(root).use {
            assertEquals(ModelReset.Result.BLOCKED, it.resume())
            assertEquals(old, read(root, "selection"))
            root.files.silent.clear()
            root.files.silent += "close-read:reset"
            assertEquals(ModelReset.Result.RESOURCES_PENDING, it.resume())
            val count = root.files.events.size
            assertEquals(ModelReset.Result.RESOURCES_PENDING, it.confirm(authorization))
            assertEquals(count, root.files.events.size)
            root.files.silent.clear()
        }
        ModelReset(root).use { assertEquals(ModelReset.Result.COMPLETE, it.resume()) }
    }

    @Test fun alreadyEmptyRecordedEpochIsKeptBeforeMarkerRemoval() {
        val root = FakeModelRoot().apply {
            files.put("selection", ModelRecord.Selection(authorization.newEpoch)); files.put("reset", authorization)
        }
        val inode = root.files.nodes.getValue("selection").inode
        ModelReset(root).use { assertEquals(ModelReset.Result.COMPLETE, it.resume()) }
        assertEquals(inode, root.files.nodes.getValue("selection").inode)
        assertFalse(root.files.events.contains("remove:selection"))
    }

    @Test fun freshConfirmationCommitsAndVerifiesBeforeAnyPayloadDeletion() {
        val root = root()
        root.files.hook = { event ->
            if (event == "remove:model-$a.gguf") assertEquals(authorization, read(root, "reset"))
            if (event == "remove:reset") assertEquals(ModelRecord.Selection(authorization.newEpoch), read(root, "selection"))
        }
        ModelReset(root).use { assertEquals(ModelReset.Result.COMPLETE, it.confirm(authorization)) }
        assertEquals(ModelRecord.Selection(authorization.newEpoch), read(root, "selection"))
    }
}
