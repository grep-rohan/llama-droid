package io.github.greprohan.llamadroid.modelimport

internal class ModelImportController(private val createStore: () -> ModelStore,
    private val createEngine: () -> ModelImportEngine, private val execute: (() -> Unit) -> Unit,
    private val abort: (() -> Unit) -> Unit, private val notifications: Notifications,
    private val nowMillis: () -> Long) {
    /** Atomically replace ONE queued notification; nonblocking, no inline execution. */
    fun interface Notifications { fun replace(delayMillis: Long, action: () -> Unit) }
    private val gate = Any()
    private var latest = ModelImportSnapshot()
    private class Subscription(val listener: (ModelImportSnapshot) -> Unit, var revision: Long = -1)
    private val observers = linkedMapOf<Any, Subscription>()
    private var urgent = false
    private var deliveredAt: Long? = null
    private val deliver: () -> Unit = { deliverNewest() }
    private var store: ModelStore? = null // I/O-worker-only.
    @Volatile private var picker: PickerCoordinator? = null
    private var operation: ImportOperation? = null // Guarded by gate; immutable capture per command.
    private var ioRunning = false
    private class Pending(val operation: ImportOperation, val primary: Exception?,
                          var publication: ModelStore.Publication? = null, var cleanupFailed: Boolean = false) {
        @Volatile var waitingForSource = false
    }
    @Volatile private var pending: Pending? = null
    fun snapshot(): ModelImportSnapshot = synchronized(gate) { latest }
    fun observe(listener: (ModelImportSnapshot) -> Unit): () -> Unit {
        val key = Any()
        synchronized(gate) { observers[key] = Subscription(listener); urgent = true; scheduleLocked() }
        return { synchronized(gate) { observers.remove(key) }; Unit }
    }
    private fun scheduleLocked() {
        if (observers.isEmpty()) return
        val elapsed = deliveredAt?.let { (nowMillis() - it).coerceIn(0, 100) } ?: 100L
        notifications.replace(if (urgent) 0 else 100 - elapsed, deliver)
    }
    private fun change(progress: Boolean = false, transform: (ModelImportSnapshot) -> ModelImportSnapshot) = synchronized(gate) {
        latest = transform(latest).copy(revision = Math.addExact(latest.revision, 1))
        urgent = urgent || !progress
        scheduleLocked()
    }
    private fun deliverNewest() {
        val keys = synchronized(gate) { urgent = false; deliveredAt = nowMillis(); observers.keys.toList() }
        for (key in keys) {
            val delivery = synchronized(gate) {
                observers[key]?.takeIf { it.revision < latest.revision }?.let { subscription ->
                    subscription.revision = latest.revision
                    subscription.listener to latest
                }
            }
            delivery?.let { (listener, state) -> listener(state) }
        }
    }
    private fun readyStore(): ModelStore {
        store?.let { return it }
        val opened = createStore()
        store = opened
        val state = opened.recover()
        val coordinator = PickerCoordinator(opened)
        coordinator.restore(state)
        picker = coordinator
        change { it.copy(store = state, picker = coordinator.snapshot()) }
        return opened
    }
    private fun command(op: ImportOperation? = null, action: () -> Unit): Boolean {
        synchronized(gate) {
            if (latest.busy) return false
            operation = op; ioRunning = true
            change { it.copy(busy = true, issue = null, outcome = null, operation = op?.state(),
                resourcesPending = false, bytes = 0, total = null, name = null, warnings = emptySet()) }
        }
        try {
            execute {
                try { action() } catch (_: Exception) {
                    change { it.copy(issue = ImportIssue.RECOVERY_REQUIRED, outcome = ImportOutcome.FAILED) }
                } finally { finishTurn() }
            }
        } catch (_: Exception) {
            op?.finish()
            synchronized(gate) {
                operation = null; ioRunning = false
                change { it.copy(busy = false, operation = op?.state(), issue = ImportIssue.RECOVERY_REQUIRED, outcome = ImportOutcome.FAILED) }
            }
            return false
        }
        return true
    }
    private fun resumeReadyLocked(): Boolean {
        val current = pending ?: return false
        if (ioRunning || !current.waitingForSource || !current.operation.state().quiescent) return false
        ioRunning = true
        return true
    }
    private fun resourcesChanged(op: ImportOperation) {
        val resume = synchronized(gate) { operation === op && resumeReadyLocked() }
        if (resume) resumePending()
    }
    private fun finishTurn() {
        val resume = synchronized(gate) {
            ioRunning = false
            if (pending == null) {
                operation = null
                change { it.copy(busy = false) }
                false
            } else resumeReadyLocked()
        }
        if (resume) resumePending()
    }
    private fun resumePending() {
        try {
            execute {
                try { settlePending() } catch (_: Exception) {
                    pending?.waitingForSource = false
                    change { it.copy(issue = ImportIssue.RECOVERY_REQUIRED, resourcesPending = true) }
                } finally { finishTurn() }
            }
        } catch (_: Exception) {
            synchronized(gate) { ioRunning = false; pending?.waitingForSource = false }
            change { it.copy(issue = ImportIssue.RECOVERY_REQUIRED, resourcesPending = true) }
        }
    }
    private class StalePickerResult : Exception("Picker result no longer matches")
    private class SelectionFailure(cause: Exception?) : Exception("Picker consumption not confirmed", cause)
    fun interface SourceRequest { fun acquire(operation: ImportOperation): ModelSource }
    fun importResult(token: PickerToken, request: SourceRequest): Boolean {
        val op = ImportOperation(java.util.UUID.randomUUID().toString(), abort)
        op.onResourceChange { resourcesChanged(op) }
        return command(op) {
            var publication: ModelStore.Publication? = null
            var primary: Exception? = null
            try {
                readyStore()
                val coordinator = checkNotNull(picker)
                val consumed = coordinator.consumeResult(token)
                change { it.copy(store = consumed?.state ?: it.store, picker = coordinator.snapshot()) }
                if (consumed == null) throw StalePickerResult()
                if (!consumed.accepted) throw SelectionFailure(consumed.failure)
                op.check()
                val source = request.acquire(op)
                val engine = createEngine()
                op.phase(ImportPhase.COPYING)
                change { it.copy(operation = op.state()) }
                val copied = engine.copy(source, { checkNotNull(store).createCandidate(newPayloadId()) }, op) { count ->
                    val metadata = source.metadata
                    change(progress = true) { it.copy(bytes = count, total = metadata.size?.takeIf { n -> n > 0 },
                        name = ModelName.sanitize(metadata.name), operation = op.state()) }
                }
                op.check(); op.phase(ImportPhase.PROMOTING)
                change { it.copy(operation = op.state()) }
                checkNotNull(store).promote(copied)
                op.check()
                check(op.tryCommit()) { "Commit gate unavailable" }
                change { it.copy(operation = op.state()) }
                publication = checkNotNull(store).publishCandidate(copied)
            } catch (failure: Exception) { primary = failure }
            var cleanupFailed = (primary as? ModelImportEngine.Failure)?.cleanupPending == true
            try { op.closeSource() } catch (_: Exception) { cleanupFailed = true }
            pending = Pending(op, primary, publication, cleanupFailed)
            settlePending()
        }
    }
    private fun newPayloadId() = java.util.UUID.randomUUID().toString().replace("-", "")
    fun requestCancel(): ImportOperation.CancelResult {
        val op = synchronized(gate) { operation } ?: return ImportOperation.CancelResult.FINISHED
        val result = op.requestCancel()
        synchronized(gate) { if (operation === op) change { it.copy(operation = op.state()) } }
        return result
    }
    private fun issue(error: Exception?): ImportIssue {
        val cause = if (error is ModelImportEngine.Failure) error.primary else error
        return when (cause) {
            is StalePickerResult -> ImportIssue.STALE_RESULT
            is SelectionFailure -> ImportIssue.SELECTION_SAVE
            is GgufHeader.Invalid -> if (cause.reason == GgufHeader.Reason.VERSION) ImportIssue.UNSUPPORTED_HEADER else ImportIssue.INVALID_HEADER
            is ImportPolicy.Rejected -> when (cause.reason) {
                ImportPolicy.Reason.SPACE_CHECK -> ImportIssue.SPACE_CHECK
                ImportPolicy.Reason.INSUFFICIENT_SPACE -> ImportIssue.INSUFFICIENT_SPACE
                else -> ImportIssue.INCONSISTENT_SIZE
            }
            is SecurityException -> ImportIssue.SOURCE_UNREADABLE
            else -> if (error is ModelImportEngine.Failure) ImportIssue.READ_WRITE else ImportIssue.SOURCE_UNREADABLE
        }
    }
    private fun settlePending() {
        val finished = pending ?: return
        val op = finished.operation
        op.refreshResource()
        if (!op.state().quiescent) {
            finished.waitingForSource = true
            change { it.copy(operation = op.state(), resourcesPending = true) }; return
        }
        finished.waitingForSource = false
        var publication = finished.publication
        if (publication?.outcome == CheckedModelRecord.State.AMBIGUOUS) {
            publication = checkNotNull(store).reconcilePublication()
            finished.publication = publication
        }
        val state = if (publication?.outcome == CheckedModelRecord.State.NEW || publication?.outcome == CheckedModelRecord.State.AMBIGUOUS) {
            publication.state
        } else {
            try { store?.discardPendingCandidate() } catch (_: Exception) { finished.cleanupFailed = true }
            store?.recover() ?: ModelStore.State(problem = ModelStore.Problem.RECOVERY_REQUIRED)
        }
        if (state.problem == ModelStore.Problem.RESOURCES_PENDING) {
            change { it.copy(store = state, operation = op.state(), resourcesPending = true) }; return
        }
        val imported = publication?.outcome == CheckedModelRecord.State.NEW && state.available
        val cancelled = op.state().cancelAccepted && state.available && publication?.outcome != CheckedModelRecord.State.NEW
        check(op.finish())
        pending = null
        val warnings = mutableSetOf<ImportWarning>()
        if (state.payloadDebt || state.recordDebt) warnings += ImportWarning.CLEANUP_DEBT
        if (finished.cleanupFailed) warnings += ImportWarning.CLEANUP_FAILED
        change { it.copy(store = state, operation = op.state(), resourcesPending = false,
            outcome = if (imported) ImportOutcome.IMPORTED else if (cancelled) ImportOutcome.CANCELLED else ImportOutcome.FAILED,
            issue = if (imported || cancelled) null else if (!state.available) ImportIssue.RECOVERY_REQUIRED
                else if (publication != null) ImportIssue.SELECTION_SAVE else issue(finished.primary), warnings = warnings) }
    }

    /** Consent is single-use and bound to the exact rendered recovery snapshot. */
    fun confirmReset(revision: Long): Boolean = synchronized(gate) {
        if (latest.revision != revision || latest.busy || latest.resourcesPending || pending != null || operation != null ||
            latest.store.problem !in setOf(ModelStore.Problem.RECOVERY_REQUIRED, ModelStore.Problem.RESET_PENDING)) return false
        command {
            val state = readyStore().confirmReset()
            val recovered = PickerCoordinator(checkNotNull(store)).also { it.restore(state) }
            picker = recovered // Reset epoch invalidates all pre-reset tokens, never relaunches one.
            change { it.copy(store = state, picker = recovered.snapshot(),
                issue = if (state.available) null else ImportIssue.RECOVERY_REQUIRED,
                outcome = if (state.available) null else ImportOutcome.FAILED,
                resourcesPending = state.problem == ModelStore.Problem.RESOURCES_PENDING,
                warnings = if (state.payloadDebt || state.recordDebt) setOf(ImportWarning.CLEANUP_DEBT) else emptySet()) }
        }
    }

    fun initialize(): Boolean = command { readyStore() }
    fun beginPicker(): Boolean = command {
        readyStore()
        val coordinator = checkNotNull(picker)
        val result = coordinator.reserve()
        change { it.copy(store = result?.state ?: it.store, picker = coordinator.snapshot(),
            issue = if (result?.accepted == true) null else ImportIssue.SELECTION_SAVE) }
    }
    fun claimLaunch(): PickerToken? {
        val token = picker?.claimLaunch()
        if (token != null) change { it.copy(picker = checkNotNull(picker).snapshot()) }
        return token
    }
    fun markLaunched(token: PickerToken): Boolean {
        val marked = picker?.markLaunched(token) == true
        if (marked) change { it.copy(picker = checkNotNull(picker).snapshot()) }
        return marked
    }
    fun abandonRoute(token: PickerToken) {
        picker?.abandonRoute(token)
        change { it.copy(picker = picker?.snapshot() ?: it.picker) }
    }
    fun dismissPicker(token: PickerToken): Boolean = command {
        readyStore()
        val coordinator = checkNotNull(picker)
        coordinator.dismiss(token)?.let { result -> change { it.copy(store = result.state,
            picker = coordinator.snapshot(), issue = if (result.accepted) null else ImportIssue.SELECTION_SAVE) } }
    }
}
