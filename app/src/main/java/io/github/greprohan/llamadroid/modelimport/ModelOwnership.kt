package io.github.greprohan.llamadroid.modelimport

/** In-process lease; a second process is outside the approved store architecture. */
internal class ModelOwnership private constructor(private val key: String) : AutoCloseable {
    private var closed = false
    override fun close() = synchronized(owners) {
        if (!closed) {
            check(owners.remove(key))
            closed = true
        }
    }

    companion object {
        private val owners = mutableSetOf<String>()
        fun acquire(key: String): ModelOwnership = synchronized(owners) {
            require(key.isNotEmpty())
            check(owners.add(key)) { "Model root already has an owner" }
            ModelOwnership(key)
        }
        private val payload = Regex("model-[0-9a-f]{32}\\.gguf")
        private val candidate = Regex("candidate-[0-9a-f]{32}\\.part")
        fun isPayload(name: String): Boolean = payload.matches(name)
        fun isCandidate(name: String): Boolean = candidate.matches(name)
        fun isRecord(name: String): Boolean = name in setOf("selection", "selection.next", "reset", "reset.next")
    }
}
