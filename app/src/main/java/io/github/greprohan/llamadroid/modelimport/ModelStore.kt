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
    private val quiescent: Boolean get() = selection?.quiescent != false && reset?.quiescent != false &&
        reader?.closed != false && directory?.closed != false

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
        if (!quiescent) return State(problem = Problem.RESOURCES_PENDING)
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

    override fun close() {
        if (closed) return
        var failure: Exception? = null
        fun attempt(action: () -> Unit) {
            try { action() } catch (error: Exception) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        attempt { selection?.close() }
        attempt { reset?.close() }
        attempt { closeReader() }
        attempt { closeDirectory() }
        if (quiescent) { lease.close(); closed = true }
        failure?.let { throw it }
        check(closed) { "Model store resources remain open" }
    }
}
