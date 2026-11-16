package io.github.greprohan.llamadroid.modelimport

import org.junit.Assert.*
import org.junit.Test

class ModelRecoveryTest {
    private val epoch = "e".repeat(32)
    private val a = "a".repeat(32)
    private val b = "b".repeat(32)
    private val old = ModelRecord.Selection(epoch, "c".repeat(32), ActiveModel(a, 24, "old"))
    private val new = ModelRecord.Selection(epoch, active = ActiveModel(b, 24, "new"))
    private val source = ByteArray(32) { 3 }
    private fun root(record: ModelRecord.Selection = old) = FakeModelRoot().apply {
        files.put("selection", record)
        files.putBytes("model-${record.active!!.id}.gguf", ByteArray(24) { 7 })
        files.putBytes("source.gguf", source)
    }

    @Test fun committedResetIsReportedBeforeReadingOrMutatingTheSelection() {
        val root = root().apply {
            files.put("reset", ModelRecord.ResetAuthorization(a, "d".repeat(32)))
            files.putBytes("selection", byteArrayOf(1))
        }
        val before = root.files.nodes.toMap()
        ModelStore(root).use {
            assertEquals(ModelStore.Problem.RESET_PENDING, it.recover().problem)
        }
        assertEquals(before, root.files.nodes)
        assertFalse(root.files.events.contains("open:selection"))
        assertFalse(root.files.events.any { it.startsWith("remove:") || it.startsWith("create:") })
    }

    @Test fun uncommittedOrCorruptResetStateNeverAuthorizesDeletion() {
        for (name in listOf("reset", "reset.next")) {
            for (bytes in listOf(byteArrayOf(1), ModelRecordCodec.encode(ModelRecord.ResetAuthorization(a, b)))) {
                val root = root().apply { files.putBytes(name, bytes) }
                val before = root.files.nodes.toMap()
                ModelStore(root).use { assertFalse(it.recover().available) }
                assertEquals(before, root.files.nodes)
                assertFalse(root.files.events.any { it.startsWith("remove:") || it.startsWith("create:") })
            }
        }
    }

    @Test fun interruptedInitializationDiscardsNextInsteadOfPromotingItsEpochOrPayload() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1), ModelRecordCodec.encode(new))) {
            val root = FakeModelRoot().apply { files.putBytes("selection.next", bytes) }
            ModelStore(root) { epoch }.use {
                assertEquals(ModelRecord.Selection(epoch), it.recover().selection)
                assertFalse(root.files.nodes.containsKey("selection.next"))
            }
        }
    }

    @Test fun missingSelectionWithPayloadCandidateResetOrUnknownEntriesNeverInitializes() {
        for (name in listOf("model-$a.gguf", "candidate-$a.part", "reset", "reset.next", "unrelated")) {
            val root = FakeModelRoot().apply { files.putBytes(name, byteArrayOf(42)) }
            val before = root.files.nodes.toMap()
            ModelStore(root) { error("Must not invent an epoch") }.use { assertFalse(it.recover().available) }
            assertEquals(before, root.files.nodes)
            assertFalse(root.files.events.any { it.startsWith("create:") || it.startsWith("remove:") })
        }
    }

    @Test fun crashBeforeAndAfterSelectionCommitRetainsOnlyTheAuthoritativeModel() {
        for (committed in listOf(old, new)) {
            val root = root(committed)
            val retained = "model-${committed.active!!.id}.gguf"
            val retired = "model-${if (committed == old) b else a}.gguf"
            val bytes = root.files.nodes.getValue(retained).bytes.copyOf()
            root.files.putBytes(retired, ByteArray(24) { 9 })
            root.files.putBytes("candidate-$b.part", byteArrayOf(1, 2))
            if (committed == old) root.files.put("selection.next", new)
            ModelStore(root).use { store ->
                val state = store.recover()
                assertTrue(state.available)
                assertEquals(committed, state.selection)
                assertEquals(committed.active, state.active!!.model)
                assertArrayEquals(bytes, root.files.nodes.getValue(retained).bytes)
                assertFalse(root.files.nodes.containsKey(retired))
                assertFalse(root.files.nodes.containsKey("candidate-$b.part"))
                assertArrayEquals(source, root.files.nodes.getValue("source.gguf").bytes)
            }
        }
    }

    @Test fun unreferencedPayloadDebtDoesNotBlockReadingOrAnotherMetadataWrite() {
        val root = root().apply {
            files.putBytes("model-$b.gguf", ByteArray(24))
            files.silent += "remove:model-$b.gguf"
        }
        ModelStore(root).use { store ->
            val state = store.recover()
            assertTrue(state.available && state.canWrite && state.payloadDebt)
            assertEquals(old, state.selection)
            root.files.silent.clear()
            assertFalse(store.recover().payloadDebt)
        }
    }

    @Test fun recordSidecarDebtAllowsCurrentModelButBlocksMetadataWritesUntilRetry() {
        val root = root().apply { files.put("selection.next", new); files.silent += "remove:selection.next" }
        ModelStore(root).use { store ->
            val state = store.recover()
            assertTrue(state.available && state.recordDebt)
            assertFalse(state.canWrite)
            root.files.silent.clear()
            assertTrue(store.recover().canWrite)
            assertEquals(old, store.recover().selection)
        }
    }
}
