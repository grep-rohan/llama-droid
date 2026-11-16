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
    private fun command(action: () -> Unit): Boolean {
        synchronized(gate) {
            if (latest.busy) return false
            change { it.copy(busy = true, issue = null, outcome = null) }
        }
        try {
            execute {
                try { action() } catch (_: Exception) {
                    change { it.copy(issue = ImportIssue.RECOVERY_REQUIRED, outcome = ImportOutcome.FAILED) }
                } finally { change { it.copy(busy = false) } }
            }
        } catch (_: Exception) {
            change { it.copy(busy = false, issue = ImportIssue.RECOVERY_REQUIRED, outcome = ImportOutcome.FAILED) }
            return false
        }
        return true
    }
    fun initialize(): Boolean = command {
        val state = readyStore().recover()
        change { it.copy(store = state, picker = checkNotNull(picker).snapshot()) }
    }
}
