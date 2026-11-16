package io.github.greprohan.llamadroid.modelimport

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.nio.file.Files

/** Root setup/inventory boundary; record/payload I/O stays in ModelFileOps. */
internal interface ModelRoot {
    val key: String
    val files: ModelFileOps
    fun prepare()
    fun validate()
    fun entries(): Inventory
    fun payloadPath(name: String): String
    interface Inventory : Iterable<String>, AutoCloseable { val closed: Boolean }
}

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
        private val records = setOf("selection", "selection.next", "reset", "reset.next")
        fun isRecord(name: String): Boolean = name in records
    }
}

/** The caller supplies Android's noBackupFilesDir, not a document/source path. */
internal class AndroidModelRoot(noBackupParent: File) : ModelRoot {
    private val parent = noBackupParent.canonicalFile.also { require(it.isDirectory) }
    private val directory = File(parent, "models")
    override val key: String = directory.path
    private var identity: Pair<Long, Long>? = null
    override val files: ModelFileOps by lazy { validate(); AndroidModelFileOps(directory) }

    override fun prepare() {
        if (identity != null) { validate(); return }
        val info = try { Os.lstat(directory.path) } catch (error: ErrnoException) {
            if (error.errno != OsConstants.ENOENT) throw error
            check(directory.mkdir()) { "Model root creation failed" }
            Os.lstat(directory.path)
        }
        check(OsConstants.S_ISDIR(info.st_mode)) { "Unsafe model root" }
        identity = info.st_dev to info.st_ino
    }
    override fun validate() {
        val info = Os.lstat(directory.path)
        check(OsConstants.S_ISDIR(info.st_mode) && identity == (info.st_dev to info.st_ino)) { "Model root changed" }
    }
    override fun payloadPath(name: String): String {
        validate()
        require(ModelOwnership.isPayload(name))
        return File(directory, name).path
    }
    override fun entries(): ModelRoot.Inventory {
        validate()
        val path = directory.toPath()
        val stream = Files.newDirectoryStream(path)
        return object : ModelRoot.Inventory {
            override var closed = false
            override fun iterator(): Iterator<String> {
                val source = stream.iterator()
                return object : Iterator<String> {
                    override fun hasNext(): Boolean = source.hasNext()
                    override fun next(): String {
                        val entry = source.next()
                        check(entry.parent == path) { "Inventory escaped model root" }
                        return entry.fileName.toString()
                    }
                }
            }
            override fun close() {
                if (!closed) { stream.close(); closed = true }
            }
        }
    }
}
