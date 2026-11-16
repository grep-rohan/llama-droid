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

    @Test fun committedResetWithOwnedNamespaceResumesBeforeCorruptSelectionIsRead() {
        val targetEpoch = "d".repeat(32)
        val root = FakeModelRoot().apply {
            files.put("reset", ModelRecord.ResetAuthorization(a, targetEpoch))
            files.putBytes("selection", byteArrayOf(1))
            files.putBytes("model-$a.gguf", ByteArray(24) { 7 })
        }
        ModelStore(root) { error("Must use authorized epoch") }.use {
            assertEquals(ModelRecord.Selection(targetEpoch), it.recover().selection)
            assertTrue(it.recover().canWrite)
        }
        assertEquals(setOf("selection"), root.files.nodes.keys)
    }

    @Test fun freshConfirmationRejectsLiveCandidateAndInvalidatesStoppedCandidateOwnership() {
        val root = FakeModelRoot()
        ModelStore(root) { epoch }.use { store ->
            assertTrue(store.recover().canWrite)
            val candidate = store.createCandidate(a)
            assertEquals(ModelStore.Problem.RESOURCES_PENDING, store.confirmReset().problem)
            assertFalse(root.files.nodes.containsKey("reset"))
            candidate.close()
            assertTrue(store.confirmReset().canWrite)
            assertEquals(setOf("selection"), root.files.nodes.keys)
            try { candidate.discard(); fail("Expired ownership") } catch (_: IllegalStateException) { }
        }
    }

    @Test fun freshConfirmationCanRecoverCorruptSelectionWithoutRecursiveOrSourceDeletion() {
        val root = FakeModelRoot().apply {
            files.putBytes("selection", byteArrayOf(1)); files.putBytes("model-$a.gguf", ByteArray(24))
        }
        ModelStore(root) { epoch }.use {
            assertFalse(it.recover().available)
            assertEquals(ModelRecord.Selection(epoch), it.confirmReset().selection)
        }
        assertEquals(setOf("selection"), root.files.nodes.keys)
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

    @Test fun unsafeOwnedTypesAndHardlinksBlockAllCleanupBeforeAnyUnlink() {
        for (name in listOf("selection", "selection.next", "reset", "reset.next", "model-$a.gguf",
                            "model-$b.gguf", "candidate-$b.part")) {
            for (kind in ModelFileOps.Kind.entries) {
                val root = root().apply {
                    files.putBytes("candidate-${"d".repeat(32)}.part", byteArrayOf(1))
                    if (name != "selection" && name != "model-$a.gguf") files.putBytes(name, byteArrayOf(1))
                }
                root.files.nodes.getValue(name).apply { this.kind = kind; if (kind == ModelFileOps.Kind.REGULAR) links = 2 }
                val before = root.files.nodes.mapValues { it.value.bytes.copyOf() }
                ModelStore(root).use { assertFalse(it.recover().available) }
                assertEquals(before.keys, root.files.nodes.keys)
                before.forEach { (path, bytes) -> assertArrayEquals(bytes, root.files.nodes.getValue(path).bytes) }
                assertFalse(root.files.events.any { it.startsWith("remove:") || it.startsWith("create:") })
            }
        }
    }

    @Test fun unknownNamesLinksAndDirectoriesArePreservedWithoutBeingOpened() {
        val names = listOf("unrelated-link", "unrelated-dir", "model-${"B".repeat(32)}.gguf", "candidate-invalid.part")
        val root = root().apply {
            names.forEach { files.putBytes(it, byteArrayOf(5)) }
            files.nodes.getValue(names[0]).kind = ModelFileOps.Kind.LINK
            files.nodes.getValue(names[1]).kind = ModelFileOps.Kind.DIRECTORY
        }
        ModelStore(root).use { assertTrue(it.recover().canWrite) }
        names.forEach {
            assertTrue(root.files.nodes.containsKey(it))
            assertFalse(root.files.events.contains("open:$it"))
            assertFalse(root.files.events.contains("remove:$it"))
        }
        assertArrayEquals(source, root.files.nodes.getValue("source.gguf").bytes)
    }

    @Test fun corruptSelectionNeverPromotesNextOrDeletesEitherPayload() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1), ByteArray(2049),
                             ModelRecordCodec.encode(ModelRecord.ResetAuthorization(a, b)))) {
            val root = root().apply {
                files.putBytes("selection", bytes); files.put("selection.next", new)
                files.putBytes("model-$b.gguf", ByteArray(24) { 9 })
            }
            val before = root.files.nodes.mapValues { it.value.bytes.copyOf() }
            ModelStore(root).use { assertFalse(it.recover().available) }
            assertEquals(before.keys, root.files.nodes.keys)
            before.forEach { (name, value) -> assertArrayEquals(value, root.files.nodes.getValue(name).bytes) }
            assertFalse(root.files.events.any { it.startsWith("remove:") || it.startsWith("create:") })
        }
    }

    @Test fun inventoryOpenAndIterationFailuresCannotTriggerPartialSweeping() {
        for (event in listOf("list-open", "list-iterator", "list-next")) {
            val root = root().apply { files.putBytes("model-$b.gguf", ByteArray(24)) }
            root.files.hook = { if (it == event) throw java.io.IOException("fixture inventory failure") }
            ModelStore(root).use { assertFalse(it.recover().available) }
            assertFalse(root.files.events.any { it.startsWith("remove:") })
            assertTrue(root.inventories.all { it.closed })
        }
    }

    @Test fun initialPublicationFailuresAreRetryableWithoutGuessingASelection() {
        for (event in listOf("create:selection.next", "info-write", "write", "flush", "sync", "closed-write", "rename")) {
            val root = FakeModelRoot()
            root.files.hook = { if (it == event) throw java.io.IOException(event) }
            ModelStore(root) { epoch }.use { assertFalse(it.recover().available) }
            assertFalse(root.files.nodes.containsKey("selection"))
            assertTrue(root.files.handles.all { it.closed })
            root.files.hook = {}
            ModelStore(root) { epoch }.use { assertEquals(ModelRecord.Selection(epoch), it.recover().selection) }
        }
    }

    @Test fun initializationAfterRenameExceptionRetainsTheConfirmedEpoch() {
        val root = FakeModelRoot()
        var generated = 0
        root.files.hook = { if (it == "renamed") throw java.io.IOException("ack lost") }
        ModelStore(root) { generated++; epoch }.use { store ->
            assertEquals(epoch, store.recover().selection!!.epoch)
            assertEquals(epoch, store.recover().selection!!.epoch)
            assertEquals(1, generated)
        }
    }

    @Test fun committedEmptySelectionSurvivesDeadCopyAndUnselectedPayloadWithoutPromotion() {
        for (pending in listOf(null, "c".repeat(32))) {
            val empty = ModelRecord.Selection(epoch, pending)
            val root = FakeModelRoot().apply {
                files.put("selection", empty); files.put("selection.next", new)
                files.putBytes("model-$b.gguf", ByteArray(24))
                files.putBytes("candidate-$a.part", byteArrayOf(1))
                files.putBytes("source.gguf", source)
            }
            ModelStore(root) { error("Committed epoch must survive") }.use {
                val state = it.recover()
                assertTrue(state.canWrite)
                assertEquals(empty, state.selection)
                assertNull(state.active)
            }
            assertEquals(setOf("selection", "source.gguf"), root.files.nodes.keys)
            assertArrayEquals(source, root.files.nodes.getValue("source.gguf").bytes)
        }
    }

    @Test fun rootReplacementAfterRecoveryCannotTriggerCleanupOnTheNextCall() {
        val root = root()
        ModelStore(root).use { store ->
            assertTrue(store.recover().available)
            root.files.putBytes("candidate-$b.part", byteArrayOf(1))
            root.valid = false
            assertFalse(store.recover().available)
            assertTrue(root.files.nodes.containsKey("candidate-$b.part"))
            assertFalse(root.files.events.any { it.startsWith("remove:") })
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
