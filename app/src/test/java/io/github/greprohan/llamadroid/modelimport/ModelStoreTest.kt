package io.github.greprohan.llamadroid.modelimport

import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ModelStoreTest {
    private val epoch = "a".repeat(32)
    private val id = "b".repeat(32)

    @Test fun pickerReservationAndSingleConsumptionPreserveActivePayloadBytesAndEpoch() {
        val root = activeRoot(); val sourceBytes = byteArrayOf(9, 8, 7)
        root.files.putBytes("source-unrelated.gguf", sourceBytes)
        val oldPayload = root.files.nodes.getValue("model-$id.gguf").bytes.copyOf()
        ModelStore(root).use { store ->
            val reserved = store.reservePicker("c".repeat(32))
            assertTrue(reserved.accepted); val token = checkNotNull(reserved.token)
            assertEquals(PickerToken(epoch, "c".repeat(32)), token)
            assertEquals(token.nonce, reserved.state.selection!!.pendingPickerNonce)
            assertTrue(store.consumePicker(token).accepted)
            assertNull(store.recover().selection!!.pendingPickerNonce)
            val before = root.files.events.count { it.startsWith("create:") }
            assertFalse(store.consumePicker(token).accepted)
            assertEquals(before, root.files.events.count { it.startsWith("create:") })
            assertEquals(epoch, store.recover().selection!!.epoch)
            assertEquals(id, store.recover().active!!.model.id)
        }
        assertArrayEquals(oldPayload, root.files.nodes.getValue("model-$id.gguf").bytes)
        assertArrayEquals(sourceBytes, root.files.nodes.getValue("source-unrelated.gguf").bytes)
    }

    @Test fun pickerOldResultCannotConsumeNewerOutstandingRequestOrAnotherEpoch() {
        val root = FakeModelRoot()
        ModelStore(root) { epoch }.use { store ->
            val first = checkNotNull(store.reservePicker("c".repeat(32)).token)
            val second = checkNotNull(store.reservePicker("d".repeat(32)).token)
            assertFalse(store.consumePicker(first).accepted)
            assertEquals(second.nonce, store.recover().selection!!.pendingPickerNonce)
            assertFalse(store.consumePicker(second.copy(epoch = id)).accepted)
            assertTrue(store.consumePicker(second).accepted)
            assertFalse(store.consumePicker(first).accepted)
        }
    }

    @Test fun pickerWriteFailuresPreserveOldSelectionAndPayloadOnReserveAndConsume() {
        for (consume in listOf(false, true)) for (event in listOf("flush", "sync", "rename")) {
            val root = activeRoot(); val token = PickerToken(epoch, "c".repeat(32))
            ModelStore(root).use { store ->
                if (consume) assertTrue(store.reservePicker(token.nonce).accepted) else store.recover()
                val oldRecord = root.files.nodes.getValue("selection").bytes.copyOf()
                val oldPayload = root.files.nodes.getValue("model-$id.gguf").bytes.copyOf()
                val primary = IOException("private record fault")
                root.files.hook = { if (it == event) throw primary }
                val result = if (consume) store.consumePicker(token) else store.reservePicker(token.nonce)
                assertFalse(result.accepted); assertNull(result.token)
                assertEquals(CheckedModelRecord.State.OLD, result.outcome); assertSame(primary, result.failure)
                assertArrayEquals(oldRecord, root.files.nodes.getValue("selection").bytes)
                assertArrayEquals(oldPayload, root.files.nodes.getValue("model-$id.gguf").bytes)
                assertTrue(root.files.handles.all { it.closed })
            }
        }
    }

    @Test fun pickerExceptionAfterRealRenameIsConfirmedRatherThanBlindlyRolledBack() {
        val root = activeRoot(); val primary = IOException("after rename")
        ModelStore(root).use { store ->
            root.files.hook = { if (it == "renamed") throw primary }
            val reserved = store.reservePicker("c".repeat(32))
            assertTrue(reserved.accepted); assertSame(primary, reserved.failure)
            assertTrue(store.consumePicker(checkNotNull(reserved.token)).accepted)
            assertEquals(id, store.recover().active!!.model.id)
            assertNull(store.recover().selection!!.pendingPickerNonce)
        }
    }

    @Test fun ambiguousPickerWriteBlocksLaunchAndNeverDeletesPossiblyReferencedPayload() {
        val root = activeRoot()
        ModelStore(root).use { store ->
            root.files.hook = { if (it == "renamed") { root.files.putBytes("selection", byteArrayOf(1)); throw IOException("ambiguous") } }
            val update = store.reservePicker("c".repeat(32))
            assertFalse(update.accepted); assertNull(update.token); assertFalse(update.state.canWrite)
            assertEquals(CheckedModelRecord.State.AMBIGUOUS, update.outcome)
            assertTrue(root.files.nodes.containsKey("model-$id.gguf"))
            assertFalse(root.files.events.any { it == "remove:model-$id.gguf" })
        }
    }

    @Test fun pickerPendingWriterCloseRetainsLeaseAndInvalidNonceNeverStartsIo() {
        val root = activeRoot(); val store = ModelStore(root)
        try {
            assertTrue(store.recover().canWrite)
            val events = root.files.events.size
            assertThrows(IllegalArgumentException::class.java) { store.reservePicker("../bad") }
            assertEquals(events, root.files.events.size)
            root.files.silent += "close-write"
            val update = store.reservePicker("c".repeat(32))
            assertFalse(update.accepted); assertEquals(ModelStore.Problem.RESOURCES_PENDING, update.state.problem)
            assertThrows(IllegalStateException::class.java) { store.close() }
            assertThrows(IllegalStateException::class.java) { ModelStore(root) }
            assertTrue(root.files.nodes.containsKey("model-$id.gguf"))
        } finally { root.files.silent.clear(); store.close() }
        assertTrue(root.files.handles.all { it.closed })
    }

    @Test fun candidateSubsectionWritesAreExclusiveAndLiveRecoveryCannotSweepThem() {
        val root = activeRoot(); val candidateId = "c".repeat(32)
        val old = root.files.nodes.getValue("model-$id.gguf").bytes.copyOf()
        ModelStore(root).use { store ->
            val candidate = store.createCandidate(candidateId)
            val data = byteArrayOf(9, 1, 2, 3, 8)
            candidate.write(data, 1, 3); candidate.write(data, 2, 2)
            assertArrayEquals(byteArrayOf(1, 2, 3, 2, 3), root.files.nodes.getValue("candidate-$candidateId.part").bytes)
            val events = root.files.events.size
            assertEquals(ModelStore.Problem.RESOURCES_PENDING, store.recover().problem)
            assertEquals(events, root.files.events.size)
            assertThrows(IllegalStateException::class.java) { store.createCandidate("d".repeat(32)) }
            candidate.flush(); candidate.sync(); candidate.close(); candidate.close()
            assertEquals(ModelStore.Problem.RESOURCES_PENDING, store.recover().problem)
            candidate.discard()
            assertFalse(root.files.nodes.containsKey("candidate-$candidateId.part"))
            assertTrue(store.recover().canWrite)
        }
        assertArrayEquals(old, root.files.nodes.getValue("model-$id.gguf").bytes)
        assertTrue(root.files.handles.all { it.closed })
    }

    @Test fun candidateCreationRefusesOutstandingPickerAndSelectedIdCollision() {
        val root = activeRoot()
        ModelStore(root).use { store ->
            assertThrows(IllegalStateException::class.java) { store.createCandidate(id) }
            val token = checkNotNull(store.reservePicker("c".repeat(32)).token)
            assertThrows(IllegalStateException::class.java) { store.createCandidate("d".repeat(32)) }
            assertTrue(store.consumePicker(token).accepted)
            val candidate = store.createCandidate("d".repeat(32)); candidate.close(); candidate.discard()
        }
    }

    private fun copyCandidate(store: ModelStore, candidateId: String, size: Long = 50): CopiedCandidate =
        ModelImportEngine(ImportPolicy { Long.MAX_VALUE }).copy(FakeModelSource(size), { store.createCandidate(candidateId) })

    @Test fun closedCandidatePromotionPreservesOldSelectionUntilPublicationAndMayStillBeDiscarded() {
        val root = activeRoot(); val nextId = "c".repeat(32)
        val oldSelection = root.files.nodes.getValue("selection").bytes.copyOf()
        ModelStore(root).use { store ->
            val copied = copyCandidate(store, nextId)
            assertEquals(50L, root.files.stat("candidate-$nextId.part")!!.size)
            store.promote(copied)
            assertFalse(root.files.nodes.containsKey("candidate-$nextId.part"))
            assertEquals(50L, root.files.stat("model-$nextId.gguf")!!.size)
            assertArrayEquals(oldSelection, root.files.nodes.getValue("selection").bytes)
            assertTrue(root.files.nodes.containsKey("model-$id.gguf"))
            copied.candidate.discard() // Accepted cancellation during promotion must remain safe.
            assertFalse(root.files.nodes.containsKey("model-$nextId.gguf"))
            assertEquals(id, store.recover().active!!.model.id)
        }
    }

    @Test fun promotionReconcilesErrorsBeforeAndAfterRenameWithoutBlindRollback() {
        for (point in listOf("rename", "renamed")) {
            val root = activeRoot(); val nextId = "c".repeat(32)
            ModelStore(root).use { store ->
                val copied = copyCandidate(store, nextId); val primary = IOException(point)
                root.files.hook = { if (it == point) throw primary }
                assertSame(primary, assertThrows(IOException::class.java) { store.promote(copied) })
                root.files.hook = {}
                copied.candidate.discard()
                assertFalse(root.files.nodes.containsKey("model-$nextId.gguf"))
                assertFalse(root.files.nodes.containsKey("candidate-$nextId.part"))
                assertEquals(id, store.recover().active!!.model.id)
            }
        }
    }

    @Test fun finalNameCollisionAndChangedCandidateLengthNeverOverwriteAnyPayload() {
        for (collision in listOf(true, false)) {
            val root = activeRoot(); val nextId = "c".repeat(32)
            ModelStore(root).use { store ->
                val copied = copyCandidate(store, nextId)
                if (collision) root.files.putBytes("model-$nextId.gguf", byteArrayOf(9))
                else root.files.nodes.getValue("candidate-$nextId.part").bytes = byteArrayOf(8)
                assertThrows(IllegalStateException::class.java) { store.promote(copied) }
                copied.candidate.discard()
                if (collision) assertArrayEquals(byteArrayOf(9), root.files.nodes.getValue("model-$nextId.gguf").bytes)
                assertTrue(root.files.nodes.containsKey("model-$id.gguf"))
            }
        }
    }

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

    private fun activeRoot(size: Long = 24) = FakeModelRoot().apply {
        files.put("selection", ModelRecord.Selection(epoch, active = ActiveModel(id, size, "model")))
        files.putBytes("model-$id.gguf", ByteArray(24))
        files.nodes.getValue("model-$id.gguf").reportedSize = size
    }

    @Test fun payloadReadFailureDoesNotDeletePossiblyReferencedFiles() {
        val root = activeRoot()
        root.files.hook = { if (it == "read:model-$id.gguf") throw IOException("fixture read failure") }
        ModelStore(root).use { assertFalse(it.recover().available) }
        assertFalse(root.files.events.any { it.startsWith("remove:") })
        assertTrue(root.files.handles.all { it.closed })
    }

    @Test fun pendingPayloadCloseRetainsOwnershipUntilExplicitDisposal() {
        val root = activeRoot().apply { files.silent += "close-read:model-$id.gguf" }
        val store = ModelStore(root)
        try {
            assertEquals(ModelStore.Problem.RESOURCES_PENDING, store.recover().problem)
            assertThrows(IllegalStateException::class.java) { store.close() }
            assertThrows(IllegalStateException::class.java) { ModelStore(root) }
            assertFalse(root.files.events.any { it.startsWith("remove:") })
        } finally { root.files.silent.clear(); store.close() }
        assertTrue(root.files.handles.all { it.closed })
        ModelStore(root).close()
    }

    @Test fun longStatLengthsAreComparedWithoutAllocatingPayloadSizedBuffers() {
        for (size in listOf(2_147_483_649L, Long.MAX_VALUE)) {
            val root = activeRoot(size) // A fake stat contract, not a real multi-GB fixture.
            ModelStore(root).use { assertEquals(size, it.recover().active!!.model.sizeBytes) }
        }
    }

    @Test fun simultaneousLeaseAttemptsHaveOneWinnerAndNoSurvivingWorkers() {
        val key = UUID.randomUUID().toString()
        val start = CountDownLatch(1)
        val attempted = CountDownLatch(2)
        val release = CountDownLatch(1)
        val wins = AtomicInteger()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val workers = List(2) {
            Thread {
                var owner: ModelOwnership? = null
                try {
                    check(start.await(5, TimeUnit.SECONDS))
                    owner = try { ModelOwnership.acquire(key) } catch (_: IllegalStateException) { null }
                    if (owner != null) wins.incrementAndGet()
                } catch (error: Throwable) { failures += error }
                finally { attempted.countDown() }
                try {
                    if (owner != null) check(release.await(5, TimeUnit.SECONDS))
                } catch (error: Throwable) { failures += error }
                finally { owner?.close() }
            }.apply { start() }
        }
        start.countDown()
        try {
            assertTrue(attempted.await(2, TimeUnit.SECONDS))
            assertEquals(1, wins.get())
        } finally {
            release.countDown()
            workers.forEach { it.join(5000); if (it.isAlive) { it.interrupt(); it.join(2000) }; assertFalse(it.isAlive) }
        }
        assertTrue(failures.toString(), failures.isEmpty())
        ModelOwnership.acquire(key).close()
    }

    @Test fun pendingRecordWriterCloseCannotReleaseTheRootLease() {
        val root = FakeModelRoot().apply { files.silent += "close-write" }
        val store = ModelStore(root) { epoch }
        try {
            assertEquals(ModelStore.Problem.RESOURCES_PENDING, store.recover().problem)
            assertThrows(IllegalStateException::class.java) { store.close() }
            assertThrows(IllegalStateException::class.java) { ModelStore(root) }
            assertFalse(root.files.nodes.containsKey("selection"))
        } finally { root.files.silent.clear(); store.close() }
        assertTrue(root.files.handles.all { it.closed })
        ModelStore(root) { id }.use { assertEquals(id, it.recover().selection!!.epoch) }
    }

    @Test fun payloadIdentityChangesBetweenStatAndOpenFailClosed() {
        val root = activeRoot()
        root.files.hook = { if (it == "open:model-$id.gguf") root.files.putBytes("model-$id.gguf", ByteArray(24)) }
        ModelStore(root).use { assertFalse(it.recover().available) }
        assertTrue(root.files.handles.all { it.closed })
        assertFalse(root.files.events.any { it.startsWith("remove:") })
    }

    @Test fun displayMetadataNeverBecomesAFilePath() {
        val root = activeRoot()
        val model = ActiveModel(id, 24, "../../provider-source.gguf")
        root.files.put("selection", ModelRecord.Selection(epoch, active = model))
        ModelStore(root).use {
            val active = it.recover().active!!
            assertEquals(model.name, active.model.name)
            assertEquals("/isolated/${root.key}/models/model-$id.gguf", active.path)
        }
        assertThrows(IllegalArgumentException::class.java) { root.payloadPath("../model-$id.gguf") }
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
