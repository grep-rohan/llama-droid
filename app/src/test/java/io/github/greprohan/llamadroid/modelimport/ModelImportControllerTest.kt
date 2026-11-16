package io.github.greprohan.llamadroid.modelimport

import org.junit.Assert.*
import org.junit.Test

class ModelImportControllerTest {
    private class Queue : ModelImportController.Notifications {
        var slot: Pair<Long, () -> Unit>? = null
        override fun replace(delayMillis: Long, action: () -> Unit) { slot = delayMillis to action }
        fun deliver() { val next = slot; slot = null; next?.second?.invoke() }
    }
    private class Fixture : AutoCloseable {
        val root = FakeModelRoot(); val work = mutableListOf<() -> Unit>(); val aborts = mutableListOf<() -> Unit>()
        val main = Queue(); var now = 0L; var constructions = 0
        var store: ModelStore? = null
        val controller = ModelImportController({ constructions++; ModelStore(root).also { store = it } },
            { ModelImportEngine(ImportPolicy { Long.MAX_VALUE }) }, work::add, aborts::add, main) { now }
        fun runWork() { work.removeAt(0)() }
        fun pick(): PickerToken {
            check(controller.beginPicker()); runWork()
            return checkNotNull(controller.claimLaunch()).also { check(controller.markLaunched(it)) }
        }
        fun request(source: ModelSource) = ModelImportController.SourceRequest { operation ->
            operation.registerSignal {}
            operation.beginAcquisition()
            try { operation.publish(source).also { operation.check() } } finally { operation.endAcquisition() }
        }
        override fun close() { store?.close() }
    }

    @Test fun firstImportAndReplacementUseOneSourceAndPreserveOldBytesUntilConfirmed() {
        Fixture().use { f ->
            val first = FakeModelSource(50)
            assertTrue(f.controller.importResult(f.pick(), f.request(first))); f.runWork()
            assertEquals(ImportOutcome.IMPORTED, f.controller.snapshot().outcome)
            val old = f.controller.snapshot().store.active!!.model
            val oldBytes = f.root.files.nodes.getValue("model-${old.id}.gguf").bytes.copyOf()
            val second = FakeModelSource(60)
            second.fault = { if (it == "read") {
                assertArrayEquals(oldBytes, f.root.files.nodes.getValue("model-${old.id}.gguf").bytes)
            } }
            assertTrue(f.controller.importResult(f.pick(), f.request(second)))
            assertFalse(f.controller.importResult(PickerToken("a".repeat(32), "b".repeat(32)), f.request(second)))
            f.runWork()
            val state = f.controller.snapshot()
            assertEquals(ImportOutcome.IMPORTED, state.outcome); assertFalse(state.busy)
            assertEquals(60L, state.store.active!!.model.sizeBytes)
            assertNotEquals(old.id, state.store.active.model.id)
            assertFalse(f.root.files.nodes.containsKey("model-${old.id}.gguf"))
            assertEquals(1, first.closes); assertEquals(1, second.closes)
        }
    }

    @Test fun acceptedCancelKeepsCommandReservedUntilCapturedAbortHelpersFinish() {
        Fixture().use { f ->
            f.controller.importResult(f.pick(), f.request(FakeModelSource(50))); f.runWork()
            val old = f.controller.snapshot().store.active!!.model
            val oldBytes = f.root.files.nodes.getValue("model-${old.id}.gguf").bytes.copyOf()
            val source = FakeModelSource(60)
            source.fault = { if (it == "read" && source.consumed >= 24) f.controller.requestCancel() }
            f.controller.importResult(f.pick(), f.request(source)); f.runWork()
            assertTrue(f.controller.snapshot().busy && f.controller.snapshot().resourcesPending)
            assertFalse(f.controller.beginPicker()); assertEquals(2, f.aborts.size)
            repeat(20) { f.controller.requestCancel() }; assertEquals(2, f.aborts.size)
            f.aborts.toList().forEach { it() }
            assertEquals(1, f.work.size); f.runWork()
            assertEquals(ImportOutcome.CANCELLED, f.controller.snapshot().outcome)
            assertFalse(f.controller.snapshot().busy); assertTrue(f.work.isEmpty())
            assertEquals(old.id, f.controller.snapshot().store.active!!.model.id)
            assertArrayEquals(oldBytes, f.root.files.nodes.getValue("model-${old.id}.gguf").bytes)
            assertEquals(1, source.closes)
        }
    }

    @Test fun cancellationBeforeWorkerClearsOnlyItsPickerWithoutOpeningSource() {
        Fixture().use { f ->
            var opens = 0; val token = f.pick()
            f.controller.importResult(token) { opens++; error("No source after accepted cancel") }
            assertEquals(ImportOperation.CancelResult.ACCEPTED, f.controller.requestCancel())
            f.runWork()
            assertEquals(0, opens); assertTrue(f.aborts.isEmpty())
            assertEquals(ImportOutcome.CANCELLED, f.controller.snapshot().outcome)
            assertNull(f.controller.snapshot().store.selection!!.pendingPickerNonce)
        }
    }

    @Test fun progressIsEligibleWithin100msAtMost10HzAndFinalReplacesDelayedProgress() {
        Fixture().use { f ->
            val seen = mutableListOf<Pair<Long, ModelImportSnapshot>>()
            f.controller.observe { seen += f.now to it }; f.main.deliver()
            val source = FakeModelSource(ModelImportEngine.BUFFER_BYTES * 5L + 24)
            val token = f.pick(); f.main.deliver()
            source.fault = { if (it == "read") {
                if (source.consumed == 0L) f.main.deliver() else {
                    assertTrue(checkNotNull(f.main.slot).first in 0..100)
                    f.now += 50
                    if (f.now % 100 == 0L) f.main.deliver()
                }
            } }
            f.controller.importResult(token, f.request(source)); f.runWork()
            assertEquals(0L, checkNotNull(f.main.slot).first)
            f.main.deliver()
            val progress = seen.filter { it.second.operation?.phase == ImportPhase.COPYING && it.second.bytes > 0 }
            assertTrue(progress.size >= 2)
            assertTrue(progress.zipWithNext().all { (a, b) -> b.first - a.first >= 100 })
            assertTrue(seen.zipWithNext().all { (a, b) -> b.second.revision > a.second.revision })
            assertEquals(ImportOutcome.IMPORTED, seen.last().second.outcome)
            assertEquals(source.length, seen.last().second.bytes)
        }
    }

    @Test fun staleCallbackDoesNotOpenAnySourceOrClearTheActualPendingToken() {
        Fixture().use { f ->
            val token = f.pick(); var opens = 0
            f.controller.importResult(token.copy(nonce = "0".repeat(32))) { opens++; error("Stale source") }
            f.runWork()
            assertEquals(0, opens); assertEquals(ImportIssue.STALE_RESULT, f.controller.snapshot().issue)
            assertEquals(token.nonce, f.controller.snapshot().store.selection!!.pendingPickerNonce)
        }
    }

    @Test fun lateCancelAtMetadataCommitIsFinishingAndCannotDispatchAnyAbort() {
        Fixture().use { f ->
            val token = f.pick(); var late: ImportOperation.CancelResult? = null
            f.root.files.hook = { if (it == "rename" && f.root.files.nodes.containsKey("selection.next") &&
                f.controller.snapshot().operation?.phase == ImportPhase.COMMITTING) {
                late = f.controller.requestCancel()
            } }
            f.controller.importResult(token, f.request(FakeModelSource(50))); f.runWork()
            assertEquals(ImportOperation.CancelResult.FINISHING, late)
            assertTrue(f.aborts.isEmpty()); assertEquals(ImportOutcome.IMPORTED, f.controller.snapshot().outcome)
        }
    }

    @Test fun engineFailuresRenderOnlyTypedIssuesAndPreserveThePreviousModel() {
        Fixture().use { f ->
            f.controller.importResult(f.pick(), f.request(FakeModelSource(50))); f.runWork()
            val old = f.controller.snapshot().store.active!!.model
            val source = FakeModelSource(60)
            source.fault = { if (it == "read" && source.consumed >= 24) throw java.io.IOException("content://private/source") }
            f.controller.importResult(f.pick(), f.request(source)); f.runWork()
            val state = f.controller.snapshot()
            assertEquals(ImportOutcome.FAILED, state.outcome); assertEquals(ImportIssue.READ_WRITE, state.issue)
            assertEquals(old, state.store.active!!.model); assertFalse(state.busy)
            assertFalse(state.toString().contains("content://")); assertEquals(1, source.closes)
        }
    }

    @Test fun failedPickerConsumptionNeverOpensSourceAndIsASelectionError() {
        Fixture().use { f ->
            val token = f.pick(); var opens = 0
            f.root.files.hook = { if (it == "sync") throw java.io.IOException("consume sync") }
            f.controller.importResult(token) { opens++; error("No source") }; f.runWork()
            assertEquals(0, opens); assertEquals(ImportIssue.SELECTION_SAVE, f.controller.snapshot().issue)
            assertEquals(token.nonce, f.controller.snapshot().store.selection!!.pendingPickerNonce)
            assertFalse(f.controller.snapshot().busy)
        }
    }

    @Test fun cancellationDuringPromotionStillPreservesTheOldModelAndDropsUnselectedFinal() {
        Fixture().use { f ->
            f.controller.importResult(f.pick(), f.request(FakeModelSource(50))); f.runWork()
            val old = f.controller.snapshot().store.active!!.model
            val token = f.pick()
            f.root.files.hook = { if (it == "renamed" && f.controller.snapshot().operation?.phase == ImportPhase.PROMOTING) {
                assertEquals(ImportOperation.CancelResult.ACCEPTED, f.controller.requestCancel())
            } }
            f.controller.importResult(token, f.request(FakeModelSource(60))); f.runWork()
            assertTrue(f.controller.snapshot().busy); f.aborts.toList().forEach { it() }; f.runWork()
            assertEquals(ImportOutcome.CANCELLED, f.controller.snapshot().outcome)
            assertEquals(old, f.controller.snapshot().store.active!!.model)
            assertEquals(listOf("model-${old.id}.gguf"), f.root.files.nodes.keys.filter { ModelOwnership.isPayload(it) })
        }
    }

    @Test fun transientAndPersistentCommitAmbiguityNeverPretendThatOldWasRestored() {
        for (persistent in listOf(false, true)) Fixture().use { f ->
            f.controller.importResult(f.pick(), f.request(FakeModelSource(50))); f.runWork()
            val old = f.controller.snapshot().store.active!!.model; val token = f.pick()
            var switched = false; var failures = 0
            f.root.files.hook = {
                if (it == "renamed" && f.controller.snapshot().operation?.phase == ImportPhase.COMMITTING) switched = true
                if (switched && it == "read:selection" && (persistent || failures++ == 0)) throw java.io.IOException("temporarily unreadable")
            }
            f.controller.importResult(token, f.request(FakeModelSource(60))); f.runWork()
            val state = f.controller.snapshot(); assertFalse(state.busy)
            if (persistent) {
                assertEquals(ImportIssue.RECOVERY_REQUIRED, state.issue); assertNull(state.store.active)
                assertEquals(2, f.root.files.nodes.keys.count { ModelOwnership.isPayload(it) })
                assertTrue(f.root.files.nodes.containsKey("model-${old.id}.gguf"))
            } else {
                assertEquals(ImportOutcome.IMPORTED, state.outcome); assertNotEquals(old.id, state.store.active!!.model.id)
                assertFalse(f.root.files.nodes.containsKey("model-${old.id}.gguf"))
            }
            assertTrue(f.root.files.handles.all { it.closed })
        }
    }

    @Test fun successfulReplacementWithCleanupDebtRemainsSuccessfulAndNextActionRetriesDebt() {
        Fixture().use { f ->
            f.controller.importResult(f.pick(), f.request(FakeModelSource(50))); f.runWork()
            val old = f.controller.snapshot().store.active!!.model; val token = f.pick()
            f.root.files.hook = { if (it == "remove:model-${old.id}.gguf") throw java.io.IOException("cleanup") }
            f.controller.importResult(token, f.request(FakeModelSource(60))); f.runWork()
            val state = f.controller.snapshot()
            assertEquals(ImportOutcome.IMPORTED, state.outcome); assertTrue(state.store.canWrite)
            assertTrue(state.warnings.contains(ImportWarning.CLEANUP_DEBT)); assertTrue(state.store.payloadDebt)
            assertNotEquals(old.id, state.store.active!!.model.id)
            f.root.files.hook = {}; f.controller.beginPicker(); f.runWork()
            assertFalse(f.controller.snapshot().store.payloadDebt)
            assertFalse(f.root.files.nodes.containsKey("model-${old.id}.gguf"))
        }
    }

    @Test fun unresolvedSourceCloseCannotDeleteCandidateOrReleaseCommandUntilSeparatelyConfirmed() {
        Fixture().use { f ->
            val source = FakeModelSource(50).also { it.ignoreClose = true }
            var captured: ImportOperation? = null
            val request = f.request(source)
            try {
                f.controller.importResult(f.pick()) { captured = it; request.acquire(it) }; f.runWork()
                assertTrue(f.controller.snapshot().busy && f.controller.snapshot().resourcesPending)
                assertEquals(1, f.root.files.nodes.keys.count { ModelOwnership.isCandidate(it) })
                assertFalse(f.controller.beginPicker()); assertEquals(1, source.closes)
                f.controller.requestCancel(); f.aborts.toList().forEach { it() }
                assertTrue(f.work.isEmpty()); assertTrue(f.controller.snapshot().busy); assertEquals(1, source.closes)
                source.closed = true // Fake external confirmation, not a second raw close.
                captured!!.refreshResource(); assertEquals(1, f.work.size); f.runWork()
                assertFalse(f.controller.snapshot().busy); assertEquals(ImportOutcome.CANCELLED, f.controller.snapshot().outcome)
                assertFalse(f.root.files.nodes.keys.any { ModelOwnership.isCandidate(it) })
            } finally {
                source.closed = true; captured?.refreshResource()
                f.aborts.toList().forEach { it() }; while (f.work.isNotEmpty()) f.runWork()
            }
        }
    }

    @Test fun cancelDuringPickerConsumptionDoesNotAbortThatRecordWriterOrOpenSource() {
        Fixture().use { f ->
            val token = f.pick(); var opens = 0
            f.root.files.hook = { if (it == "rename" && f.root.files.nodes.containsKey("selection.next")) {
                assertEquals(ImportOperation.CancelResult.ACCEPTED, f.controller.requestCancel())
            } }
            f.controller.importResult(token) { opens++; error("Cancelled before provider") }; f.runWork()
            assertEquals(0, opens); assertTrue(f.aborts.isEmpty())
            assertEquals(ImportOutcome.CANCELLED, f.controller.snapshot().outcome)
            assertNull(f.controller.snapshot().store.selection!!.pendingPickerNonce)
        }
    }

    @Test fun realWorkersKeepOneWriterWhileSignalBlocksAndIndependentCloseUnblocksRead() {
        val io = java.util.concurrent.Executors.newSingleThreadExecutor()
        val helpers = java.util.concurrent.Executors.newFixedThreadPool(2)
        val ioFutures = java.util.concurrent.ConcurrentLinkedQueue<java.util.concurrent.Future<*>>()
        val helperFutures = java.util.concurrent.ConcurrentLinkedQueue<java.util.concurrent.Future<*>>()
        val readEntered = java.util.concurrent.CountDownLatch(1); val releaseRead = java.util.concurrent.CountDownLatch(1)
        val signalEntered = java.util.concurrent.CountDownLatch(1); val releaseSignal = java.util.concurrent.CountDownLatch(1)
        val closeEntered = java.util.concurrent.CountDownLatch(1); val terminal = java.util.concurrent.CountDownLatch(1)
        val stored = java.util.concurrent.atomic.AtomicReference<ModelStore?>()
        val root = FakeModelRoot(); val source = FakeModelSource(50)
        lateinit var controller: ModelImportController
        controller = ModelImportController({ ModelStore(root).also { stored.set(it) } },
            { ModelImportEngine(ImportPolicy { Long.MAX_VALUE }) },
            { task -> ioFutures += io.submit { try { task() } finally {
                val state = controller.snapshot()
                if (!state.busy && state.outcome != null) terminal.countDown()
            } } }, { task -> helperFutures += helpers.submit { task() } }, Queue(), { 0L })
        source.fault = {
            if (it == "read" && source.consumed >= 24) {
                readEntered.countDown(); check(releaseRead.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
            if (it == "close") { closeEntered.countDown(); releaseRead.countDown() }
        }
        try {
            assertTrue(controller.beginPicker()); io.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            val token = checkNotNull(controller.claimLaunch()); controller.markLaunched(token)
            assertTrue(controller.importResult(token) { op ->
                op.registerSignal { signalEntered.countDown(); check(releaseSignal.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
                op.beginAcquisition()
                try { op.publish(source) } finally { op.endAcquisition() }
            })
            assertTrue(readEntered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(ImportOperation.CancelResult.ACCEPTED, controller.requestCancel())
            assertTrue(signalEntered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(closeEntered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            io.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(controller.snapshot().busy && controller.snapshot().resourcesPending)
            assertFalse(controller.beginPicker()); assertEquals(1L, terminal.count)
            releaseSignal.countDown(); assertTrue(terminal.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(ImportOutcome.CANCELLED, controller.snapshot().outcome)
            assertFalse(controller.snapshot().busy); assertEquals(1, source.closes)
        } finally {
            releaseRead.countDown(); releaseSignal.countDown()
            helpers.shutdown(); assertTrue("No surviving abort workers", helpers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
            io.shutdown(); assertTrue("No surviving I/O worker", io.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
            helperFutures.forEach { it.get(1, java.util.concurrent.TimeUnit.SECONDS) }
            ioFutures.forEach { it.get(1, java.util.concurrent.TimeUnit.SECONDS) }
            stored.get()?.close()
        }
    }

    @Test fun pickerLaunchClaimIsMemoryOnlyAndMatchingDismissIsSerialized() {
        Fixture().use { f ->
            assertTrue(f.controller.beginPicker()); f.runWork()
            val events = f.root.files.events.size
            val token = checkNotNull(f.controller.claimLaunch())
            assertNull(f.controller.claimLaunch()); assertTrue(f.controller.markLaunched(token))
            assertEquals(events, f.root.files.events.size)
            assertEquals(token.nonce, f.controller.snapshot().store.selection!!.pendingPickerNonce)
            assertTrue(f.controller.dismissPicker(token)); assertFalse(f.controller.dismissPicker(token))
            f.runWork(); assertNull(f.controller.snapshot().store.selection!!.pendingPickerNonce)
            assertEquals(PickerCoordinator.Phase.IDLE, f.controller.snapshot().picker.phase)
        }
    }

    @Test fun constructionDoesNoIoAndDuplicateCommandsCannotQueueASecondOwner() {
        Fixture().use { f ->
            assertEquals(0, f.constructions); assertTrue(f.root.files.events.isEmpty())
            assertTrue(f.controller.initialize()); assertFalse(f.controller.initialize())
            assertEquals(1, f.work.size); assertTrue(f.controller.snapshot().busy)
            f.runWork()
            assertEquals(1, f.constructions); assertTrue(f.controller.snapshot().store.canWrite)
            assertFalse(f.controller.snapshot().busy); assertTrue(f.work.isEmpty())
        }
    }

    @Test fun allSnapshotTypesShareOneNewestNotificationAndUnsubscribeRemovesReferences() {
        Fixture().use { f ->
            val first = mutableListOf<ModelImportSnapshot>(); val second = mutableListOf<ModelImportSnapshot>()
            val detach = f.controller.observe(first::add)
            assertNotNull(f.main.slot); f.controller.initialize(); f.runWork()
            val detachSecond = f.controller.observe(second::add)
            detach(); f.main.deliver()
            assertTrue(first.isEmpty()); assertEquals(1, second.size)
            assertEquals(f.controller.snapshot(), second.single())
            assertTrue(second.single().store.canWrite)
            detachSecond(); f.main.deliver(); assertEquals(1, second.size)
        }
    }
}
