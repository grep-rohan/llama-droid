package io.github.greprohan.llamadroid.modelimport

internal enum class ModelRecordFile(val base: String) {
    SELECTION("selection"), RESET("reset");
    val next: String get() = "$base.next"
}

/** Serialized owner only. A decoded sidecar never becomes authoritative by inspection. */
internal class CheckedModelRecord(private val files: ModelFileOps, private val slot: ModelRecordFile = ModelRecordFile.SELECTION) : AutoCloseable {
    enum class State { OLD, NEW, AMBIGUOUS }
    data class Result(val state: State, val failure: Exception? = null, val sidecarDebt: Boolean = false)

    private data class Snapshot(val entry: ModelFileOps.Entry, val record: ModelRecord)
    private var live: ModelFileOps.Handle? = null
    val quiescent: Boolean get() = live?.closed != false

    override fun close() {
        live?.let { handle ->
            if (!handle.closed) handle.close()
            check(handle.closed) { "Model record descriptor remains open" }
            live = null
        }
    }

    private fun <T> using(handle: ModelFileOps.Handle, action: () -> T): T {
        check(quiescent)
        live = handle
        var failure: Throwable? = null
        try {
            return action()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try { close() } catch (error: Throwable) {
                if (failure == null) throw error
                if (failure !== error) failure.addSuppressed(error)
            }
        }
    }

    private fun regular(entry: ModelFileOps.Entry) {
        check(entry.kind == ModelFileOps.Kind.REGULAR && entry.links == 1L) { "Unsafe model record entry" }
    }

    private fun typed(record: ModelRecord) {
        require((slot == ModelRecordFile.SELECTION && record is ModelRecord.Selection) ||
            (slot == ModelRecordFile.RESET && record is ModelRecord.ResetAuthorization)) { "Wrong model record kind" }
    }

    private fun snapshot(name: String): Snapshot? {
        check(quiescent) { "Model record descriptor remains open" }
        val entry = files.stat(name) ?: return null
        regular(entry)
        check(entry.size in 0..ModelRecordCodec.MAX_RECORD_BYTES.toLong()) { "Oversized model record" }
        val reader = files.openRead(name)
        val record = using(reader) {
            check(reader.info == entry) { "Model record identity changed" }
            ModelRecordCodec.read(reader.input).also { typed(it) }
        }
        check(files.stat(name) == entry) { "Model record identity changed" }
        return Snapshot(entry, record)
    }

    fun read(): ModelRecord? = snapshot(slot.base)?.record

    fun replace(expected: ModelRecord?, replacement: ModelRecord): Result {
        typed(replacement)
        expected?.let { typed(it) }
        val bytes = ModelRecordCodec.encode(replacement)
        val old = try {
            snapshot(slot.base).also { check(it?.record == expected) { "Model record expectation changed" } }
        } catch (failure: Exception) { return Result(State.AMBIGUOUS, failure, true) }
        var identity: ModelFileOps.Entry? = null
        var problem: Exception? = null
        try {
            files.stat(slot.next)?.let { removeNext(it) }
            val writer = files.createExclusive(slot.next)
            using(writer) {
                val entry = writer.info
                identity = entry
                regular(entry)
                check(files.stat(slot.next) == entry)
                writer.write(bytes)
                writer.flush()
                writer.sync()
            }
            val prepared = snapshot(slot.next)
            check(prepared?.record == replacement && prepared.entry.sameFile(checkNotNull(identity)))
            check(snapshot(slot.base) == old)
            files.rename(slot.next, slot.base)
        } catch (failure: Exception) { problem = failure }
        if (!quiescent) return Result(State.AMBIGUOUS, problem, true)
        try {
            val current = snapshot(slot.base)
            if (current?.record == replacement && identity?.let { current.entry.sameFile(it) } == true &&
                files.stat(slot.next) == null
            ) return Result(State.NEW, problem)
            if (current == old) {
                try {
                    identity?.let { owned ->
                        files.stat(slot.next)?.let { remaining ->
                            check(remaining.sameFile(owned)) { "Model record sidecar identity changed" }
                            removeNext(remaining)
                        }
                    }
                } catch (cleanup: Exception) {
                    if (problem == null) problem = cleanup
                    else if (problem !== cleanup) problem.addSuppressed(cleanup)
                }
                check(snapshot(slot.base) == old) { "Model record changed during cleanup" }
                val debt = try { files.stat(slot.next) != null } catch (_: Exception) { true }
                return Result(State.OLD, problem ?: IllegalStateException("Model record replacement not confirmed"), debt)
            }
        } catch (failure: Exception) {
            if (problem == null) problem = failure
            else if (problem !== failure) problem.addSuppressed(failure)
        }
        return Result(State.AMBIGUOUS, problem ?: IllegalStateException("Model record outcome unavailable"), true)
    }

    private fun removeNext(entry: ModelFileOps.Entry) {
        check(quiescent)
        regular(entry)
        check(files.stat(slot.next) == entry) { "Model record sidecar identity changed" }
        files.remove(slot.next)
        check(files.stat(slot.next) == null) { "Model record sidecar removal failed" }
    }
}
