package io.github.greprohan.llamadroid.modelimport

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.UUID

/** Every Android mutation test gets a fresh, leased root, never the real models directory. */
internal class ModelImportTestStore(context: Context) : AutoCloseable {
    private fun directory(file: File): File {
        if (!file.exists()) check(file.mkdir()) { "Cannot create isolated test directory" }
        check(OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) { "Unsafe test directory" }
        check(file == file.canonicalFile) { "Linked test directory" }
        return file
    }
    private val base = context.applicationContext.noBackupFilesDir.canonicalFile
    private val namespace = directory(File(base, "model-import-tests"))
    private val id = UUID.randomUUID().toString().replace("-", "")
    private val parent = directory(File(namespace, id))
    val root: ModelRoot = AndroidModelRoot(parent)
    val path = File(parent, "models")
    val store = ModelStore(root)
    private var closed = false

    init {
        check(parent.parentFile == namespace && path != File(base, "models"))
        try { root.prepare() } catch (failure: Exception) {
            try { store.close() } catch (close: Exception) { if (close !== failure) failure.addSuppressed(close) }
            throw failure
        }
    }

    override fun close() {
        if (closed) return
        // If a worker/descriptor is unresolved, close throws and NO deletion occurs.
        store.close()
        ModelOwnership.acquire(root.key).use {
            directory(namespace); directory(parent); root.validate()
            root.entries().use { inventory ->
                for (name in inventory) {
                    check(ModelOwnership.isRecord(name) || ModelOwnership.isCandidate(name) || ModelOwnership.isPayload(name)) {
                        "Unknown entry in isolated test root"
                    }
                    val entry = checkNotNull(root.files.stat(name))
                    check(entry.kind == ModelFileOps.Kind.REGULAR && entry.links == 1L)
                    root.files.remove(name)
                    check(root.files.stat(name) == null)
                }
            }
            root.validate()
            check(path.delete()) { "Isolated root is not empty" }
            check(parent.delete()) { "Isolated container is not empty" }
            closed = true
        }
    }
}
