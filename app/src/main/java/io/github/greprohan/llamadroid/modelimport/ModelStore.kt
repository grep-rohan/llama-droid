package io.github.greprohan.llamadroid.modelimport

import java.io.File
import java.util.UUID

/** Recovery/cleanup owner, not an importer, reset executor, or process-wide singleton. */
internal class ModelStore(private val root: ModelRoot,
                         private val newEpoch: () -> String = { UUID.randomUUID().toString().replace("-", "") }) : AutoCloseable {
    companion object {
        fun open(noBackupParent: File): ModelStore = ModelStore(AndroidModelRoot(noBackupParent))
    }
    enum class Problem { RECOVERY_REQUIRED, RESET_PENDING, RESOURCES_PENDING }
    data class Active(val model: ActiveModel, val path: String)
    data class State(val selection: ModelRecord.Selection? = null, val active: Active? = null,
                     val problem: Problem? = null, val recordDebt: Boolean = false, val payloadDebt: Boolean = false) {
        val available: Boolean get() = problem == null && selection != null
        val canWrite: Boolean get() = available && !recordDebt
    }
    private val lease = ModelOwnership.acquire(root.key)
    private var closed = false
    private var selection: CheckedModelRecord? = null
    private var reset: CheckedModelRecord? = null
    private var reader: ModelFileOps.Reader? = null
    private var directory: ModelRoot.Inventory? = null
    private var candidate: OwnedCandidate? = null
    private val quiescent: Boolean get() = selection?.quiescent != false && reset?.quiescent != false &&
        reader?.closed != false && directory?.closed != false && candidate?.closed != false

    private fun closeReader() {
        reader?.let { if (!it.closed) it.close(); check(it.closed); reader = null }
    }
    private fun closeDirectory() {
        directory?.let { if (!it.closed) it.close(); check(it.closed); directory = null }
    }
    private fun <T> listing(action: (Iterator<String>) -> T): T {
        val current = root.entries()
        directory = current
        try { return action(current.iterator()) } finally { closeDirectory() }
    }

    private fun resolve(model: ActiveModel): Active {
        val name = "model-${model.id}.gguf"
        val entry = checkNotNull(root.files.stat(name))
        check(entry.kind == ModelFileOps.Kind.REGULAR && entry.links == 1L && entry.size == model.sizeBytes)
        val handle = root.files.openRead(name)
        reader = handle
        try {
            check(handle.info == entry)
            check(handle.input.read() != -1)
        } finally { closeReader() }
        check(root.files.stat(name) == entry)
        return Active(model, root.payloadPath(name))
    }

    private data class Contents(var nonNext: Boolean = false, var next: Boolean = false,
                                var reset: Boolean = false, var orphans: Boolean = false)
    private fun scan(activeName: String?): Contents = listing { names ->
        val contents = Contents()
        while (names.hasNext()) {
            val name = names.next()
            if (name != "selection.next") contents.nonNext = true
            if (ModelOwnership.isRecord(name) || ModelOwnership.isPayload(name) || ModelOwnership.isCandidate(name)) {
                val entry = checkNotNull(root.files.stat(name))
                check(entry.kind == ModelFileOps.Kind.REGULAR && entry.links == 1L && entry.size >= 0)
            }
            if (name == "selection.next") contents.next = true
            if (name == "reset" || name == "reset.next") contents.reset = true
            if (ModelOwnership.isCandidate(name) || (ModelOwnership.isPayload(name) && name != activeName)) contents.orphans = true
        }
        contents
    }

    private fun cleanup(selected: ModelRecord.Selection, activeName: String?): Pair<Boolean, Boolean> {
        var recordDebt = false
        var payloadDebt = false
        listing { names ->
            while (names.hasNext()) {
                val name = names.next()
                val sidecar = name == "selection.next"
                if (!sidecar && !ModelOwnership.isCandidate(name) &&
                    (!ModelOwnership.isPayload(name) || name == activeName)) continue
                root.validate()
                check(selection!!.read() == selected) { "Selection changed during cleanup" }
                val entry = root.files.stat(name) ?: continue
                check(entry.kind == ModelFileOps.Kind.REGULAR && entry.links == 1L)
                try {
                    check(root.files.stat(name) == entry) { "Cleanup entry changed" }
                    root.files.remove(name)
                    check(root.files.stat(name) == null) { "Cleanup not confirmed" }
                } catch (_: Exception) {
                    if (sidecar) recordDebt = true else payloadDebt = true
                }
            }
        }
        return recordDebt to payloadDebt
    }

    fun recover(): State {
        check(!closed) { "Model store is closed" }
        if (!quiescent || candidate != null) return State(problem = Problem.RESOURCES_PENDING)
        return try {
            if (selection == null) {
                root.prepare()
                selection = CheckedModelRecord(root.files)
                reset = CheckedModelRecord(root.files, ModelRecordFile.RESET)
            }
            root.validate()
            if (checkNotNull(reset).read() != null) return State(problem = Problem.RESET_PENDING)
            val record = checkNotNull(selection)
            var current = record.read() as ModelRecord.Selection?
            val before = scan(current?.active?.let { "model-${it.id}.gguf" })
            check(!before.reset) { "Reset requires recovery" }
            if (current == null) {
                check(!before.nonNext) { "Missing authoritative selection" }
                val empty = ModelRecord.Selection(newEpoch())
                root.validate()
                check(record.replace(null, empty).state == CheckedModelRecord.State.NEW)
                current = record.read() as ModelRecord.Selection?
                check(current == empty)
            }
            val selected = checkNotNull(current)
            val activeName = selected.active?.let { "model-${it.id}.gguf" }
            selected.active?.let { resolve(it) } // Validate before any destructive cleanup.
            val debt = cleanup(selected, activeName)
            val after = scan(activeName)
            check(!after.reset && record.read() == selected) { "Selection changed during recovery" }
            val active = selected.active?.let { resolve(it) }
            root.validate()
            State(selected, active, recordDebt = after.next || debt.first, payloadDebt = after.orphans || debt.second)
        } catch (_: Exception) {
            State(problem = if (quiescent) Problem.RECOVERY_REQUIRED else Problem.RESOURCES_PENDING)
        }
    }

    fun createCandidate(id: String): ModelCandidate {
        require(PickerToken.validId(id)) { "Invalid candidate identity" }
        val state = recover()
        check(state.canWrite && state.selection?.pendingPickerNonce == null) { "Store is not ready for a candidate" }
        root.validate()
        check(root.files.stat("model-$id.gguf") == null) { "Payload identity collision" }
        val owned = OwnedCandidate(id, checkNotNull(state.selection), root.files.createExclusive("candidate-$id.part"))
        candidate = owned // Capture ownership before any fallible descriptor/path validation.
        try {
            owned.identity = owned.writer.info.also { entry ->
                check(entry.kind == ModelFileOps.Kind.REGULAR && entry.links == 1L && entry.size == 0L)
                check(root.files.stat(owned.fileName) == entry)
            }
            return owned
        } catch (failure: Exception) {
            try { owned.close() } catch (close: Exception) { if (close !== failure) failure.addSuppressed(close) }
            if (owned.closed) try { owned.discard() } catch (cleanup: Exception) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    private fun candidateFor(copied: CopiedCandidate): OwnedCandidate {
        check(!closed && quiescent)
        val owned = checkNotNull(candidate)
        check(copied.candidate === owned && owned.closed && !owned.ambiguous && copied.bytes >= GgufHeader.SIZE)
        root.validate()
        check(checkNotNull(selection).read() == owned.base) { "Selection changed during copy" }
        check(checkNotNull(reset).read() == null && root.files.stat("reset.next") == null)
        val entry = checkNotNull(root.files.stat(owned.fileName))
        check(owned.sameOwned(entry) && entry.size == copied.bytes) { "Candidate changed during copy" }
        return owned
    }

    /** Still cancellable: the promoted payload is not selected until publishCandidate. */
    fun promote(copied: CopiedCandidate) {
        val owned = candidateFor(copied)
        val from = "candidate-${owned.id}.part"
        val to = "model-${owned.id}.gguf"
        check(owned.fileName == from)
        check(root.files.stat(to) == null) { "Payload identity collision" }
        var primary: Exception? = null
        try { root.files.rename(from, to) } catch (failure: Exception) { primary = failure }
        try {
            val remaining = root.files.stat(from)
            val promoted = root.files.stat(to)
            when {
                remaining == null && promoted != null && owned.sameOwned(promoted) && promoted.size == copied.bytes -> owned.fileName = to
                remaining != null && owned.sameOwned(remaining) && promoted == null -> Unit // Confirmed not promoted.
                else -> { owned.ambiguous = true; error("Promotion outcome unavailable") }
            }
        } catch (failure: Exception) {
            owned.ambiguous = true
            if (primary == null) primary = failure else if (primary !== failure) primary.addSuppressed(failure)
        }
        primary?.let { throw it }
        check(owned.fileName == to) { "Promotion not confirmed" }
    }

    /** Caller first proves source/helpers stopped; this never closes an active writer. */
    fun discardPendingCandidate() { candidate?.discard() }

    private inner class OwnedCandidate(override val id: String, val base: ModelRecord.Selection,
                                       val writer: ModelFileOps.Writer) : ModelCandidate {
        var identity: ModelFileOps.Entry? = null
        var fileName = "candidate-$id.part"
        var ambiguous = false
        private var closeAttempted = false
        override val closed: Boolean get() = writer.closed
        private fun owned() { check(!this@ModelStore.closed && candidate === this) { "Candidate ownership expired" } }
        override fun write(bytes: ByteArray, offset: Int, count: Int) {
            owned(); check(!closed && !ambiguous && fileName.startsWith("candidate-"))
            writer.write(bytes, offset, count)
        }
        override fun flush() { owned(); check(!closed); writer.flush() }
        override fun sync() { owned(); check(!closed); writer.sync() }
        override fun close() {
            if (closed) return
            if (!closeAttempted) { closeAttempted = true; writer.close() }
            check(closed) { "Candidate descriptor remains pending" }
        }
        fun sameOwned(entry: ModelFileOps.Entry): Boolean = identity?.let {
            entry.sameFile(it) && entry.kind == ModelFileOps.Kind.REGULAR && entry.links == 1L
        } == true
        override fun discard() {
            owned(); check(closed && !ambiguous && quiescent) { "Candidate is not safe to discard" }
            root.validate()
            val selected = checkNotNull(selection).read() as ModelRecord.Selection?
            check(selected != null && selected.active?.id != id) { "Candidate may be referenced" }
            val before = root.files.stat(fileName)
            if (before == null) { candidate = null; return }
            check(sameOwned(before)) { "Candidate identity changed" }
            try {
                root.files.remove(fileName)
                check(root.files.stat(fileName) == null) { "Candidate removal not confirmed" }
                candidate = null
            } catch (failure: Exception) {
                try {
                    val remaining = root.files.stat(fileName)
                    // A known, closed, unreferenced regular file is nonblocking cleanup debt.
                    if (remaining == null || sameOwned(remaining)) candidate = null else ambiguous = true
                } catch (secondary: Exception) { if (secondary !== failure) failure.addSuppressed(secondary) }
                throw failure
            }
        }
    }

    data class PickerUpdate(val state: State, val accepted: Boolean = false,
                            val token: PickerToken? = null, val outcome: CheckedModelRecord.State? = null,
                            val failure: Exception? = null)

    /** Worker-serialized, explicit user action; supersession is controlled by PickerCoordinator. */
    fun reservePicker(nonce: String): PickerUpdate {
        require(PickerToken.validId(nonce)) { "Invalid picker identity" }
        val state = recover()
        val current = state.selection
        if (!state.canWrite || current == null || current.pendingPickerNonce == nonce) return PickerUpdate(state)
        val token = PickerToken(current.epoch, nonce)
        return pickerChange(current, current.copy(pendingPickerNonce = nonce), token)
    }

    /** Matching durable token is cleared and confirmed before any provider open. */
    fun consumePicker(token: PickerToken): PickerUpdate {
        val state = recover()
        val current = state.selection
        if (!state.canWrite || current == null || current.epoch != token.epoch ||
            current.pendingPickerNonce != token.nonce) return PickerUpdate(state)
        return pickerChange(current, current.copy(pendingPickerNonce = null), token)
    }

    private fun pickerChange(expected: ModelRecord.Selection, next: ModelRecord.Selection, token: PickerToken): PickerUpdate {
        return try {
            root.validate()
            val result = checkNotNull(selection).replace(expected, next)
            val state = if (result.state == CheckedModelRecord.State.AMBIGUOUS) {
                State(problem = if (quiescent) Problem.RECOVERY_REQUIRED else Problem.RESOURCES_PENDING)
            } else recover()
            val accepted = result.state == CheckedModelRecord.State.NEW && state.canWrite && state.selection == next
            PickerUpdate(state, accepted, token.takeIf { accepted }, result.state, result.failure)
        } catch (failure: Exception) {
            PickerUpdate(State(problem = if (quiescent) Problem.RECOVERY_REQUIRED else Problem.RESOURCES_PENDING), failure = failure)
        }
    }

    override fun close() {
        if (closed) return
        var failure: Exception? = null
        fun attempt(action: () -> Unit) {
            try { action() } catch (error: Exception) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        attempt { candidate?.close() }
        attempt { selection?.close() }
        attempt { reset?.close() }
        attempt { closeReader() }
        attempt { closeDirectory() }
        if (quiescent) { lease.close(); closed = true }
        failure?.let { throw it }
        check(closed) { "Model store resources remain open" }
    }
}
