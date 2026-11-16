package io.github.greprohan.llamadroid.modelimport

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.greprohan.llamadroid.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Host compilation only; mutations use isolated test stores, never the production singleton. */
@RunWith(AndroidJUnit4::class)
class ModelImportScreenTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun screen(confirmation: ModelImportScreen.Confirmation? = null): Pair<View, ModelImportScreen> {
        lateinit var result: Pair<View, ModelImportScreen>
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, android.R.style.Theme_Material_Light_NoActionBar)
            val root = LayoutInflater.from(context).inflate(R.layout.main_activity, null)
            result = root to if (confirmation == null) ModelImportScreen(root) else ModelImportScreen(root, confirmation)
        }
        return result
    }
    private class MainQueue : ModelImportController.Notifications, AutoCloseable {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private val lock = Any()
        private var latest: (() -> Unit)? = null
        private val delivery = Runnable { synchronized(lock) { latest }?.invoke() }
        override fun replace(delayMillis: Long, action: () -> Unit) = synchronized(lock) {
            latest = action; handler.removeCallbacks(delivery); check(handler.postDelayed(delivery, delayMillis))
        }
        override fun close() = synchronized(lock) { handler.removeCallbacks(delivery); latest = null }
    }

    private class Confirmation : ModelImportScreen.Confirmation {
        var yes: () -> Unit = {}; var dismissed: () -> Unit = {}; var shown = 0; var closed = 0
        override fun show(yes: () -> Unit, dismissed: () -> Unit): AutoCloseable {
            shown++; this.yes = yes; this.dismissed = dismissed
            return AutoCloseable { closed++; dismissed() }
        }
    }

    @Test fun resetDeclineDismissAndRecreationNeverPersistConsent() {
        val prompt = Confirmation(); val (root, screen) = screen(prompt)
        instrumentation.runOnMainSync { try {
            var confirmations = 0
            screen.bind(ModelImportScreen.Actions(reset = { confirmations++ }))
            val failed = ModelImportSnapshot(revision = 9, store = ModelStore.State(problem = ModelStore.Problem.RECOVERY_REQUIRED))
            screen.render(failed)
            root.findViewById<Button>(R.id.import_reset).performClick()
            assertEquals(1, prompt.shown); assertEquals(0, confirmations)
            prompt.dismissed() // Decline/back/outside; no command.
            root.findViewById<Button>(R.id.import_reset).performClick()
            val staleYes = prompt.yes
            screen.close() // Rotation destroys the old dialog; no restore/autoconfirm state.
            staleYes(); assertEquals(0, confirmations); assertEquals(1, prompt.closed)
        } finally { screen.close() } }
    }

    @Test fun resetConfirmationIsSingleUseAndBoundToCurrentRecoveryRevision() {
        val prompt = Confirmation(); val (root, screen) = screen(prompt)
        instrumentation.runOnMainSync { try {
            val confirmations = mutableListOf<Long>()
            screen.bind(ModelImportScreen.Actions(reset = { confirmations += it }))
            val failed = ModelImportSnapshot(revision = 9, store = ModelStore.State(problem = ModelStore.Problem.RESET_PENDING))
            screen.render(failed); root.findViewById<Button>(R.id.import_reset).performClick()
            val stale = prompt.yes
            screen.render(failed.copy(revision = 10)); stale(); assertTrue(confirmations.isEmpty())
            root.findViewById<Button>(R.id.import_reset).performClick()
            val declinedYes = prompt.yes; val declinedDismiss = prompt.dismissed
            declinedDismiss()
            root.findViewById<Button>(R.id.import_reset).performClick()
            val current = prompt.yes
            declinedDismiss(); declinedYes() // Delayed callbacks from the prior dialog, same revision.
            assertTrue(confirmations.isEmpty())
            current(); current()
            assertEquals(listOf(10L), confirmations)
            screen.render(failed.copy(revision = 11, resourcesPending = true))
            assertEquals(View.GONE, root.findViewById<View>(R.id.import_reset).visibility)
        } finally { screen.close() } }
    }

    @Test fun unknownSizeProviderRendersCopiedBytesWithoutPercent() {
        val resolver = instrumentation.targetContext.contentResolver
        val uri = android.net.Uri.parse("content://${instrumentation.context.packageName}.modelimport.fixture/fixture/gated-unknown")
        val token = checkNotNull(resolver.call(uri, "prepare-gated", null, null)?.getString("token"))
        val testStore = ModelImportTestStore(instrumentation.targetContext)
        val io = java.util.concurrent.Executors.newSingleThreadExecutor()
        val helpers = java.util.concurrent.Executors.newFixedThreadPool(2)
        val futures = java.util.concurrent.ConcurrentLinkedQueue<java.util.concurrent.Future<*>>()
        val queue = MainQueue(); val (root, screen) = screen()
        val progressSeen = java.util.concurrent.CountDownLatch(1); val terminalSeen = java.util.concurrent.CountDownLatch(1)
        val controller = ModelImportController({ testStore.store },
            { ModelImportEngine(AndroidImportIo(resolver).policy(testStore.path)) },
            { task -> futures += io.submit { task() } }, { task -> futures += helpers.submit { task() } }, queue,
            android.os.SystemClock::elapsedRealtime)
        var detach: (() -> Unit)? = null
        var submitted = false
        try {
            instrumentation.runOnMainSync { detach = controller.observe { state ->
                screen.render(state)
                if (state.operation?.phase == ImportPhase.COPYING && state.bytes > 0 && state.total == null) progressSeen.countDown()
                if (!state.busy && state.outcome != null) terminalSeen.countDown()
            } }
            assertTrue(controller.beginPicker()); io.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            val picker = checkNotNull(controller.claimLaunch()); controller.markLaunched(picker)
            submitted = controller.importResult(picker) { operation ->
                val signal = android.os.CancellationSignal()
                operation.registerSignal { signal.cancel() }; operation.beginAcquisition()
                var owned: ModelSource? = null
                try {
                    val adapter = AndroidImportIo(resolver)
                    val metadata = adapter.metadata(uri, signal); assertNull(metadata.size)
                    adapter.policy(testStore.path).preflight(metadata.size)
                    adapter.open(uri, metadata, signal) { source -> owned = operation.publish(source); operation.check() }
                    checkNotNull(owned)
                } catch (failure: AndroidImportIo.AcquisitionFailure) {
                    if (owned == null && failure.pending != null) owned = operation.publish(failure.pending)
                    throw failure
                } finally { operation.endAcquisition(); operation.refreshResource() }
            }
            assertTrue(submitted); assertTrue(progressSeen.await(5, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(root.context.resources.getQuantityString(R.plurals.import_progress_unknown, 2, 24L), root.findViewById<TextView>(R.id.import_progress_text).text.toString())
                assertFalse(root.findViewById<TextView>(R.id.import_progress_text).text.contains("%"))
                assertTrue(root.findViewById<ProgressBar>(R.id.import_progress).isIndeterminate)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.import_pending).visibility)
            }
            assertTrue(controller.snapshot().busy); assertEquals(1L, terminalSeen.count)
            resolver.call(uri, "release-gated", token, null)
            assertTrue(terminalSeen.await(8, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(ImportOutcome.IMPORTED, controller.snapshot().outcome)
            assertEquals(50L, controller.snapshot().store.active!!.model.sizeBytes)
            instrumentation.runOnMainSync {
                assertEquals(root.context.resources.getQuantityString(R.plurals.import_size, 2, 50L), root.findViewById<TextView>(R.id.active_model_size).text.toString())
                assertEquals(View.GONE, root.findViewById<View>(R.id.import_pending).visibility)
            }
        } finally {
            try {
                resolver.call(uri, "release-gated", token, null)
                if (submitted) terminalSeen.await(10, java.util.concurrent.TimeUnit.SECONDS)
                helpers.shutdown(); check(helpers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                io.shutdown(); check(io.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                futures.forEach { it.get(1, java.util.concurrent.TimeUnit.SECONDS) }
                val finish = checkNotNull(resolver.call(uri, "finish-gated", token, null))
                assertFalse("Generated producer completed without error", finish.getBoolean("failed"))
                testStore.close() // Only AFTER every consumer/producer worker is joined.
            } finally {
                instrumentation.runOnMainSync { detach?.invoke(); screen.close(); queue.close() }
            }
        }
    }

    private fun activeState(): ModelStore.State {
        val model = ActiveModel("b".repeat(32), 2_147_483_649L, "Old private model")
        return ModelStore.State(ModelRecord.Selection("a".repeat(32), active = model),
            ModelStore.Active(model, "/test-only/not-accessed"))
    }

    @Test fun unboundActionsAndUnwiredRecoveryAreDisabledWithoutOpeningAnyStore() {
        val (root, screen) = screen()
        instrumentation.runOnMainSync { try {
            screen.render(ModelImportSnapshot(store = activeState()))
            assertFalse(root.findViewById<Button>(R.id.import_button).isEnabled)
            assertEquals(View.GONE, root.findViewById<View>(R.id.import_reset).visibility)
            screen.render(ModelImportSnapshot(store = ModelStore.State(problem = ModelStore.Problem.RECOVERY_REQUIRED)))
            assertEquals(View.GONE, root.findViewById<View>(R.id.import_reset).visibility)
            assertEquals(root.context.getString(R.string.import_unavailable_model), root.findViewById<TextView>(R.id.active_model_name).text.toString())
        } finally { screen.close() } }
    }

    @Test fun replacementRemainsSeparateFromActiveAndFinishingOrDetachDisablesActions() {
        val (root, screen) = screen()
        instrumentation.runOnMainSync { try {
            var chooses = 0; var cancels = 0
            screen.bind(ModelImportScreen.Actions({ chooses++ }, { cancels++ }))
            val ready = ModelImportSnapshot(store = activeState())
            screen.render(ready); root.findViewById<Button>(R.id.import_button).performClick(); assertEquals(1, chooses)
            val copy = ready.copy(busy = true, name = "New candidate", bytes = 30, total = 50,
                operation = ImportOperationState("operation", ImportPhase.COPYING, false, 0, false, true))
            screen.render(copy)
            assertEquals("Old private model", root.findViewById<TextView>(R.id.active_model_name).text.toString())
            assertEquals(root.context.resources.getQuantityString(R.plurals.import_size, 2, 2_147_483_649L), root.findViewById<TextView>(R.id.active_model_size).text.toString())
            assertEquals("New candidate", root.findViewById<TextView>(R.id.import_pending_name).text.toString())
            assertEquals(60, root.findViewById<ProgressBar>(R.id.import_progress).progress)
            assertTrue(root.findViewById<TextView>(R.id.import_progress_text).text.contains("%"))
            root.findViewById<Button>(R.id.import_cancel).performClick(); assertEquals(1, cancels)
            screen.render(copy.copy(operation = copy.operation!!.copy(phase = ImportPhase.COMMITTING)))
            assertFalse(root.findViewById<Button>(R.id.import_cancel).isEnabled)
            screen.close(); root.findViewById<Button>(R.id.import_button).performClick()
            assertEquals(1, chooses); assertFalse(root.findViewById<Button>(R.id.import_button).isEnabled)
        } finally { screen.close() } }
    }

    @Test fun cancellationAndCleanupWarningsUseExplicitSanitizedStates() {
        val (root, screen) = screen()
        instrumentation.runOnMainSync { try {
            screen.bind(ModelImportScreen.Actions(cancel = {}))
            screen.render(ModelImportSnapshot(store = activeState(), busy = true, resourcesPending = true,
                operation = ImportOperationState("operation", ImportPhase.CANCELLING, true, 2, false, true)))
            assertFalse(root.findViewById<Button>(R.id.import_cancel).isEnabled)
            assertEquals(root.context.getString(R.string.import_cancelling), root.findViewById<TextView>(R.id.import_status).text.toString())
            screen.render(ModelImportSnapshot(store = activeState(), outcome = ImportOutcome.IMPORTED,
                warnings = setOf(ImportWarning.CLEANUP_DEBT)))
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.import_warnings).visibility)
            assertEquals(View.GONE, root.findViewById<View>(R.id.import_pending).visibility)
            assertEquals(root.context.getString(R.string.import_complete), root.findViewById<TextView>(R.id.import_status).text.toString())
        } finally { screen.close() } }
    }
}
