package io.github.greprohan.llamadroid.modelimport

import java.io.IOException

/** Sequential byte copy only; never publishes a selection or parses GGUF bodies. */
internal class ModelImportEngine(private val policy: ImportPolicy) {
    companion object { const val BUFFER_BYTES = 256 * 1024; const val MAX_SECONDARY = 3 }
    enum class Phase { PREFLIGHT, HEADER, CREATE, READ, SPACE, WRITE, SIZE, SOURCE_CLOSE, FLUSH, SYNC, CANDIDATE_CLOSE, HANDOFF, DISCARD }
    data class Secondary(val phase: Phase, val cause: Exception)
    class Failure(val phase: Phase, val primary: Exception, val candidate: ModelCandidate?,
                  val resourcesPending: Boolean, val cleanupPending: Boolean,
                  val secondary: List<Secondary>) : IOException("Model import failed at ${phase.name}", primary)

    fun copy(source: ModelSource, create: () -> ModelCandidate,
             cancel: ModelCancellation = ModelCancellation {}, progress: (Long) -> Unit = {}): CopiedCandidate {
        var candidate: ModelCandidate? = null
        var name = ModelName.FALLBACK
        var copied = 0L
        var phase = Phase.PREFLIGHT
        var failedAt = phase
        var primary: Exception? = null
        var sourceCloseAttempted = false
        var candidateCloseAttempted = false
        try {
            val metadata = source.metadata
            name = ModelName.sanitize(metadata.name)
            val reported = metadata.size
            cancel.check()
            policy.preflight(reported)
            phase = Phase.HEADER
            val input = source.input
            fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                val count = input.read(bytes, offset, length)
                if (count < -1 || count > length) throw IOException("Source stream returned invalid count")
                if (count != 0) return count
                val single = input.read()
                if (single == -1) return -1
                bytes[offset] = single.toByte()
                return 1
            }
            val prefix = ByteArray(GgufHeader.SIZE)
            var offset = 0
            while (offset < prefix.size) {
                cancel.check()
                val count = read(prefix, offset, prefix.size - offset)
                if (count == -1) throw GgufHeader.Invalid(GgufHeader.Reason.TRUNCATED)
                offset += count
            }
            GgufHeader.parse(prefix)
            cancel.check()
            phase = Phase.CREATE
            val output = create()
            candidate = output
            cancel.check()
            phase = Phase.SPACE
            val prefixTotal = policy.beforeWrite(0, prefix.size, reported)
            cancel.check()
            phase = Phase.WRITE
            output.write(prefix, 0, prefix.size)
            copied = prefixTotal
            progress(copied)
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                cancel.check()
                phase = Phase.READ
                val count = read(buffer, 0, buffer.size)
                if (count == -1) break
                cancel.check()
                phase = Phase.SPACE
                val next = policy.beforeWrite(copied, count, reported)
                cancel.check()
                phase = Phase.WRITE
                output.write(buffer, 0, count)
                copied = next
                progress(copied)
            }
            phase = Phase.SIZE
            policy.atEof(copied, reported)
            cancel.check()
            phase = Phase.SOURCE_CLOSE
            sourceCloseAttempted = true
            if (!source.closed) source.close()
            check(source.closed) { "Source close not confirmed" }
            phase = Phase.FLUSH
            output.flush()
            phase = Phase.SYNC
            output.sync()
            phase = Phase.CANDIDATE_CLOSE
            candidateCloseAttempted = true
            if (!output.closed) output.close()
            check(output.closed) { "Candidate close not confirmed" }
            phase = Phase.HANDOFF
            cancel.check()
        } catch (failure: Exception) { primary = failure; failedAt = phase }
        val secondary = mutableListOf<Secondary>()
        fun cleanup(at: Phase, action: () -> Unit) {
            try { action() } catch (failure: Exception) {
                if (primary == null) { primary = failure; failedAt = at }
                else if (primary !== failure && secondary.size < MAX_SECONDARY) secondary += Secondary(at, failure)
            }
        }
        if (!sourceCloseAttempted) cleanup(Phase.SOURCE_CLOSE) {
            if (!source.closed) source.close()
            check(source.closed) { "Source close not confirmed" }
        }
        if (!candidateCloseAttempted) candidate?.let { output -> cleanup(Phase.CANDIDATE_CLOSE) {
            if (!output.closed) output.close()
            check(output.closed) { "Candidate close not confirmed" }
        } }
        primary?.let { failure ->
            val pending = !source.closed || candidate?.closed == false
            var discarded = false
            // Never remove a candidate while either owner may still be using it.
            if (!pending) candidate?.let { output -> cleanup(Phase.DISCARD) {
                output.discard()
                discarded = true
            } }
            throw Failure(failedAt, failure, candidate, pending, candidate != null && !discarded, secondary.toList())
        }
        return CopiedCandidate(checkNotNull(candidate), copied, name)
    }
}
