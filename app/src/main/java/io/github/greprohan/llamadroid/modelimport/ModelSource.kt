package io.github.greprohan.llamadroid.modelimport

import java.io.InputStream

internal data class ModelMetadata(val name: String?, val size: Long?)

/** Already-acquired source; the stream is borrowed, and closed is nonblocking. */
internal interface ModelSource : AutoCloseable {
    val metadata: ModelMetadata
    val input: InputStream
    val closed: Boolean
}

/** Exclusive, unselected candidate. Failed acquisition must not leak a handle. */
internal interface ModelCandidate : AutoCloseable {
    val id: String
    val closed: Boolean
    fun write(bytes: ByteArray, offset: Int, count: Int)
    fun flush()
    fun sync()
    /** Only after all writers stop; checks ownership/unreferenced regular-file status. */
    fun discard()
}

internal fun interface ModelCancellation { fun check() }

internal data class CopiedCandidate(val candidate: ModelCandidate, val bytes: Long, val name: String) {
    init { check(candidate.closed) { "Candidate is not closed" } }
}
