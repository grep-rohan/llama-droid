package io.github.greprohan.llamadroid.modelimport

import java.util.UUID

/** Only reserve/consume touch disk (serialized worker); claim/mark/snapshot are memory-only. */
internal class PickerCoordinator(private val store: ModelStore,
    private val nonce: () -> String = { UUID.randomUUID().toString().replace("-", "") }) {
    enum class Phase { IDLE, RESERVING, RESERVED, LAUNCH_CLAIMED, AWAITING_RESULT, RESTORED_UNCERTAIN, CONSUMING, BLOCKED }
    data class Snapshot(val phase: Phase, val token: PickerToken? = null)
    private val gate = Any()
    private var current = Snapshot(Phase.IDLE)
    private var initialized = false
    fun snapshot(): Snapshot = synchronized(gate) { current }
    private fun recovered(state: ModelStore.State): Snapshot {
        if (!state.canWrite) return Snapshot(Phase.BLOCKED)
        val selection = checkNotNull(state.selection)
        val token = selection.pendingPickerNonce?.let { PickerToken(selection.epoch, it) }
        return Snapshot(if (token == null) Phase.IDLE else Phase.RESTORED_UNCERTAIN, token)
    }
    fun restore(state: ModelStore.State) = synchronized(gate) {
        check(!initialized)
        initialized = true
        current = recovered(state) // A restored reservation can NEVER become auto-launchable.
    }
    /** Worker recovery/reset may replace unavailable state, never a live picker transition. */
    fun reconcile(state: ModelStore.State) = synchronized(gate) {
        check(current.phase == Phase.IDLE || current.phase == Phase.RESTORED_UNCERTAIN || current.phase == Phase.BLOCKED)
        initialized = true
        current = recovered(state)
    }
    fun reserve(): ModelStore.PickerUpdate? {
        synchronized(gate) {
            if (current.phase != Phase.IDLE && current.phase != Phase.RESTORED_UNCERTAIN) return null
            initialized = true
            current = Snapshot(Phase.RESERVING)
        }
        val result = try { store.reservePicker(nonce()) } catch (failure: Exception) {
            ModelStore.PickerUpdate(ModelStore.State(problem = ModelStore.Problem.RECOVERY_REQUIRED), failure = failure)
        }
        synchronized(gate) {
            current = if (result.accepted) Snapshot(Phase.RESERVED, checkNotNull(result.token)) else recovered(result.state)
        }
        return result
    }
    fun claimLaunch(): PickerToken? = synchronized(gate) {
        if (current.phase != Phase.RESERVED) null else {
            current = current.copy(phase = Phase.LAUNCH_CLAIMED)
            current.token
        }
    }
    fun markLaunched(token: PickerToken): Boolean = synchronized(gate) {
        if (current.phase != Phase.LAUNCH_CLAIMED || current.token != token) false
        else { current = current.copy(phase = Phase.AWAITING_RESULT); true }
    }
    /** Only after its logical Activity result route is explicitly retired. */
    fun abandonRoute(token: PickerToken) = synchronized(gate) {
        if (current.token == token && (current.phase == Phase.LAUNCH_CLAIMED || current.phase == Phase.AWAITING_RESULT)) {
            current = current.copy(phase = Phase.RESTORED_UNCERTAIN)
        }
    }
    fun consumeResult(token: PickerToken): ModelStore.PickerUpdate? = consume(token, false)
    fun dismiss(token: PickerToken): ModelStore.PickerUpdate? = consume(token, true)
    private fun consume(token: PickerToken, dismiss: Boolean): ModelStore.PickerUpdate? {
        synchronized(gate) {
            val eligible = current.phase == Phase.LAUNCH_CLAIMED || current.phase == Phase.AWAITING_RESULT ||
                current.phase == Phase.RESTORED_UNCERTAIN || (dismiss && current.phase == Phase.RESERVED)
            if (!eligible || current.token != token) return null
            current = current.copy(phase = Phase.CONSUMING)
        }
        val result = try { store.consumePicker(token) } catch (failure: Exception) {
            ModelStore.PickerUpdate(ModelStore.State(problem = ModelStore.Problem.RECOVERY_REQUIRED), failure = failure)
        }
        synchronized(gate) { current = recovered(result.state) }
        return result
    }
}
