package io.github.greprohan.llamadroid.modelimport

import java.io.InputStream

/** Root-relative I/O; caller must serialize access under the store's exclusive lease. */
internal interface ModelFileOps {
    enum class Kind { REGULAR, DIRECTORY, LINK, OTHER }
    data class Entry(val device: Long, val inode: Long, val size: Long, val kind: Kind, val links: Long = 1) {
        fun sameFile(other: Entry): Boolean = device == other.device && inode == other.inode
    }
    interface Handle : AutoCloseable {
        val info: Entry
        val closed: Boolean
    }
    interface Reader : Handle { val input: InputStream }
    interface Writer : Handle {
        fun write(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset)
        fun flush()
        fun sync()
    }
    /** No-follow inspection; null means ENOENT only, not an arbitrary I/O error. */
    fun stat(name: String): Entry?
    fun openRead(name: String): Reader
    fun createExclusive(name: String): Writer
    fun rename(from: String, to: String)
    fun remove(name: String)
}
