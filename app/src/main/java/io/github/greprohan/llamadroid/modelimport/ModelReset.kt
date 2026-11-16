package io.github.greprohan.llamadroid.modelimport

/** Worker-serialized by ModelStore's exclusive root lease; never promotes a sidecar. */
internal class ModelReset(private val root: ModelRoot) : AutoCloseable {
    enum class Result { NONE, COMPLETE, BLOCKED, RESOURCES_PENDING }
    private val marker = CheckedModelRecord(root.files, ModelRecordFile.RESET)
    private val selection = CheckedModelRecord(root.files)
    private var inventory: ModelRoot.Inventory? = null
    val quiescent: Boolean get() = marker.quiescent && selection.quiescent && inventory?.closed != false
    private fun attempt(action: () -> Result): Result {
        if (!quiescent) return Result.RESOURCES_PENDING
        return try { root.validate(); action() } catch (_: Exception) {
            if (quiescent) Result.BLOCKED else Result.RESOURCES_PENDING
        }
    }
    private fun listing(action: (String) -> Unit) {
        val opened = root.entries().also { inventory = it }
        try { opened.forEach(action) } finally { opened.close(); check(opened.closed); inventory = null }
    }
    private fun regular(name: String): ModelFileOps.Entry? = root.files.stat(name)?.also {
        check(it.kind == ModelFileOps.Kind.REGULAR && it.links == 1L && it.size >= 0)
    }
    private fun remove(name: String) {
        root.validate()
        val entry = regular(name) ?: return
        check(root.files.stat(name) == entry)
        root.files.remove(name)
        check(root.files.stat(name) == null)
    }
    private fun authorized(auth: ModelRecord.ResetAuthorization) {
        root.validate(); check(marker.read() == auth)
    }
    fun resume(): Result = attempt {
        val auth = marker.read() as ModelRecord.ResetAuthorization?
        if (auth == null) {
            if (root.files.stat("reset.next") == null) Result.NONE else Result.BLOCKED
        } else finish(auth)
    }
    /** Explicit fresh consent only. No model file is touched until the committed marker reads back. */
    fun confirm(authorization: ModelRecord.ResetAuthorization): Result = attempt {
        val previous = try { marker.read() as ModelRecord.ResetAuthorization? } catch (failure: Exception) {
            check(quiescent); null // Fresh consent may replace a corrupt/unreadable regular marker.
        }
        if (previous != null) return@attempt finish(previous)
        remove("reset.next")
        remove("reset")
        val result = marker.replace(null, authorization)
        check(result.state == CheckedModelRecord.State.NEW && marker.read() == authorization)
        finish(authorization)
    }
    private fun finish(auth: ModelRecord.ResetAuthorization): Result {
        authorized(auth)
        // Preflight every entry before deletion; unknown names/types are never swept.
        listing { name ->
            check(ModelOwnership.isRecord(name) || ModelOwnership.isPayload(name) || ModelOwnership.isCandidate(name))
            checkNotNull(regular(name))
        }
        authorized(auth); remove("reset.next")
        listing { name ->
            if (name == "selection.next" || ModelOwnership.isPayload(name) || ModelOwnership.isCandidate(name)) {
                authorized(auth); remove(name)
            }
        }
        val empty = ModelRecord.Selection(auth.newEpoch)
        val current = try { selection.read() } catch (failure: Exception) { check(quiescent); null }
        if (current != empty) {
            authorized(auth); remove("selection")
            check(selection.replace(null, empty).state == CheckedModelRecord.State.NEW)
        }
        check(selection.read() == empty)
        authorized(auth); check(root.files.stat("reset.next") == null)
        remove("reset") // Last: the intended empty epoch is already authoritative.
        check(root.files.stat("reset") == null && root.files.stat("reset.next") == null)
        return Result.COMPLETE
    }
    override fun close() {
        try { marker.close() } finally {
            try { selection.close() } finally { inventory?.let { it.close(); check(it.closed); inventory = null } }
        }
    }
}
