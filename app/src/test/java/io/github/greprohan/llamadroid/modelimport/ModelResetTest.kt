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
