package io.github.greprohan.llamadroid.modelimport

import java.util.concurrent.CancellationException

/** dispatch must enqueue without blocking/running inline; at most two abort tasks per operation. */
internal class ImportOperation(val id: String, private val dispatch: (() -> Unit) -> Unit) : ModelCancellation {
    enum class CancelResult { ACCEPTED, ALREADY_CANCELLED, FINISHING, FINISHED }
    private val gate = Any()
    private var current = ImportPhase.PREPARING
    private var cancelled = false
    private var acquiring = false
    private var signal: (() -> Unit)? = null
    private var signalSent = false
    private var source: SourceLease? = null
    private var sourceSent = false
    private var helpers = 0
    private val errors = mutableListOf<Exception>()
    private class Abort(val action: () -> Unit) { var started = false; var done = false }
    fun state() = synchronized(gate) { ImportOperationState(id, current, cancelled, helpers, acquiring, source?.settled == false) }
    private fun checkLocked() { if (cancelled) throw CancellationException("Model import cancelled") }
    override fun check() = synchronized(gate) { checkLocked() }
    private fun complete(task: Abort, failure: Exception? = null) = synchronized(gate) {
        if (!task.done) {
            task.done = true; helpers--
            if (failure != null && errors.size < 2) errors += failure
        }
    }
    private fun submit(tasks: List<Abort>) {
        for (task in tasks) try {
            dispatch {
                val run = synchronized(gate) { if (task.done || task.started) false else { task.started = true; true } }
                if (run) {
                    var failure: Exception? = null
                    try { task.action() } catch (error: Exception) { failure = error }
                    finally { complete(task, failure) }
                }
            }
        } catch (failure: Exception) {
            synchronized(gate) {
                if (!task.started) complete(task, failure)
                else if (errors.size < 2) errors += failure // A started task still owns its slot until its finally.
            }
        }
    }
    private fun abortsLocked(): List<Abort> {
        if (!cancelled) return emptyList()
        val tasks = mutableListOf<Abort>()
        signal?.let { action -> if (!signalSent) { signalSent = true; tasks += Abort(action) } }
        source?.let { captured -> if (!sourceSent) { sourceSent = true; tasks += Abort { captured.close() } } }
        helpers += tasks.size
        return tasks
    }
    fun registerSignal(cancel: () -> Unit) {
        val tasks = synchronized(gate) {
            check(signal == null && current != ImportPhase.COMMITTING && current != ImportPhase.FINISHED)
            signal = cancel; abortsLocked()
        }
        submit(tasks)
    }
    fun beginAcquisition() = synchronized(gate) {
        checkLocked()
        check(signal != null && source == null && current != ImportPhase.COMMITTING && current != ImportPhase.FINISHED && !acquiring)
        acquiring = true
    }
    fun endAcquisition() = synchronized(gate) { check(acquiring); acquiring = false }
    /** Caller owns an acquisition until this returns; this operation never reuses a resource slot. */
    fun publish(source: ModelSource): ModelSource {
        val lease = SourceLease(source)
        val tasks = synchronized(gate) {
            check(acquiring && this.source == null && current != ImportPhase.COMMITTING && current != ImportPhase.FINISHED)
            this.source = lease
            abortsLocked()
        }
        submit(tasks)
        return lease
    }
    fun phase(next: ImportPhase) = synchronized(gate) {
        checkLocked()
        require(next == ImportPhase.COPYING || next == ImportPhase.PROMOTING)
        check((next == ImportPhase.COPYING && current == ImportPhase.PREPARING) ||
            (next == ImportPhase.PROMOTING && current == ImportPhase.COPYING))
        current = next
    }
    fun requestCancel(): CancelResult {
        val (result, tasks) = synchronized(gate) {
            when {
                current == ImportPhase.FINISHED -> CancelResult.FINISHED to emptyList()
                current == ImportPhase.COMMITTING -> CancelResult.FINISHING to emptyList()
                cancelled -> CancelResult.ALREADY_CANCELLED to emptyList()
                else -> {
                    cancelled = true; current = ImportPhase.CANCELLING
                    CancelResult.ACCEPTED to abortsLocked()
                }
            }
        }
        submit(tasks)
        return result
    }
    fun tryCommit() = synchronized(gate) {
        if (cancelled || acquiring || helpers != 0 || source?.settled == false ||
            current == ImportPhase.FINISHED || current == ImportPhase.COMMITTING) false
        else { current = ImportPhase.COMMITTING; true }
    }
    fun finish() = synchronized(gate) {
        if (acquiring || helpers != 0 || source?.settled == false) false else {
            current = ImportPhase.FINISHED; signal = null; source = null; true
        }
    }
    fun refreshResource() { synchronized(gate) { source }?.refresh() }

    private enum class CloseState { OPEN, CLOSING, CLOSED, FAILED }
    private inner class SourceLease(private val captured: ModelSource) : ModelSource {
        private var closing = CloseState.OPEN // Access only under gate; no foreign I/O inside gate.
        val settled: Boolean get() = closing == CloseState.CLOSED
        override val metadata get() = captured.metadata
        override val input get() = captured.input
        override val closed: Boolean get() = synchronized(gate) { settled }
        fun refresh() {
            val confirmed = captured.closed
            synchronized(gate) { if (confirmed && closing != CloseState.CLOSING) closing = CloseState.CLOSED }
        }
        override fun close() {
            val own = synchronized(gate) {
                when (closing) {
                    CloseState.CLOSED -> false
                    CloseState.OPEN -> { closing = CloseState.CLOSING; true }
                    else -> error("Source close remains pending")
                }
            }
            if (!own) return
            var confirmed = false
            try {
                if (!captured.closed) captured.close()
                confirmed = captured.closed
                check(confirmed) { "Source close not confirmed" }
            } finally {
                // Even an exception after a real close must not permit a duplicate raw close.
                if (!confirmed) confirmed = captured.closed
                synchronized(gate) { closing = if (confirmed) CloseState.CLOSED else CloseState.FAILED }
            }
        }
    }
    fun abortFailures(): List<Exception> = synchronized(gate) { errors.toList() }
}
