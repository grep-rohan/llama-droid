package io.github.greprohan.llamadroid.modelimport

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.UUID

/** Every Android mutation test gets a fresh, leased root, never the real models directory. */
internal class ModelImportTestStore(context: Context,
    private val id: String = UUID.randomUUID().toString().replace("-", ""),
    existing: Boolean = false, decorate: (ModelFileOps) -> ModelFileOps = { it }) : AutoCloseable {
    private fun directory(file: File): File {
        if (!file.exists()) check(file.mkdir()) { "Cannot create isolated test directory" }
        check(OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) { "Unsafe test directory" }
        check(file == file.canonicalFile) { "Linked test directory" }
        return file
    }
    init { require(id.matches(Regex("[0-9a-f]{32}"))) { "Invalid isolated run id" } }
    private val base = context.applicationContext.noBackupFilesDir.canonicalFile
    private val namespace = directory(File(base, "model-import-tests"))
    private val parent = File(namespace, id).also {
        if (existing) check(it.isDirectory) { "Missing isolated run" }
        else check(it.mkdir()) { "Isolated run already exists" }
        directory(it)
    }
    private val platformRoot = AndroidModelRoot(parent)
    val root: ModelRoot = object : ModelRoot by platformRoot {
        override val files: ModelFileOps by lazy { decorate(platformRoot.files) }
    }
    val path = File(parent, "models")
    val store = ModelStore(root)
    private var closed = false

    init {
        check(parent.parentFile == namespace && path != File(base, "models"))
        try {
            if (existing) check(path.isDirectory) { "Missing isolated model root" }
            root.prepare()
        } catch (failure: Exception) {
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
