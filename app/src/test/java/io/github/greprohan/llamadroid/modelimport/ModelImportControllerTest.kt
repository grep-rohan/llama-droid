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
        override fun close() { store?.close() }
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
