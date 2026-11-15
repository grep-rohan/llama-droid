package io.github.greprohan.llamadroid.modelimport

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import java.io.File
import java.io.FileDescriptor
import java.io.InputStream

/** Existing private root only; root creation/lease acquisition belongs to ModelStore. */
internal class AndroidModelFileOps(directory: File) : ModelFileOps {
    private val root = directory.absoluteFile.also {
        require(it == it.canonicalFile) { "Model root must be canonical and unlinked" }
    }
    private val rootIdentity = entry(Os.lstat(root.path)).also {
        check(it.kind == ModelFileOps.Kind.DIRECTORY) { "Unsafe model root" }
    }
    private companion object {
        // ARM64 Linux UAPI: Android 8.0 asm-generic/fcntl.h defines 02000000.
        // The native flag exists on API 26; its Java SDK field starts at API 27.
        const val ARM64_O_CLOEXEC = 0x80000
    }
    private val flags = OsConstants.O_NOFOLLOW or ARM64_O_CLOEXEC or OsConstants.O_NONBLOCK

    private fun entry(stat: StructStat): ModelFileOps.Entry {
        val kind = when {
            OsConstants.S_ISREG(stat.st_mode) -> ModelFileOps.Kind.REGULAR
            OsConstants.S_ISDIR(stat.st_mode) -> ModelFileOps.Kind.DIRECTORY
            OsConstants.S_ISLNK(stat.st_mode) -> ModelFileOps.Kind.LINK
            else -> ModelFileOps.Kind.OTHER
        }
        return ModelFileOps.Entry(stat.st_dev, stat.st_ino, stat.st_size, kind, stat.st_nlink)
    }

    private fun path(name: String): String {
        require(name != "." && name != ".." && name.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Invalid model entry name" }
        val current = entry(Os.lstat(root.path))
        check(current.kind == ModelFileOps.Kind.DIRECTORY && current.sameFile(rootIdentity)) { "Model root changed" }
        return File(root, name).path
    }

    private fun regular(info: ModelFileOps.Entry): ModelFileOps.Entry = info.also {
        check(it.kind == ModelFileOps.Kind.REGULAR && it.links == 1L) { "Unsafe model file" }
    }

    override fun stat(name: String): ModelFileOps.Entry? {
        val path = path(name)
        return try { entry(Os.lstat(path)) } catch (failure: ErrnoException) {
            if (failure.errno == OsConstants.ENOENT) null else throw failure
        }
    }

    private abstract inner class Descriptor(protected val fd: FileDescriptor) : ModelFileOps.Handle {
        override val info get() = regular(entry(Os.fstat(fd)))
        override val closed get() = !fd.valid()
        override fun close() {
            if (!closed) Os.close(fd)
            check(closed) { "Model descriptor close not confirmed" }
        }
    }

    override fun openRead(name: String): ModelFileOps.Reader {
        val expected = regular(checkNotNull(stat(name)) { "Missing model file" })
        val fd = Os.open(path(name), OsConstants.O_RDONLY or flags, 0)
        return object : Descriptor(fd), ModelFileOps.Reader {
            override val info get() = super.info.also { check(it.sameFile(expected)) { "Model file changed" } }
            override val input = object : InputStream() {
                override fun read(): Int {
                    val byte = ByteArray(1)
                    return if (read(byte, 0, 1) == -1) -1 else byte[0].toInt() and 255
                }
                override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
                    if (offset < 0 || count < 0 || count > bytes.size - offset) throw IndexOutOfBoundsException()
                    if (count == 0) return 0
                    return Os.read(fd, bytes, offset, count).let { if (it == 0) -1 else it }
                }
                // Borrowed stream: only its enclosing Reader owns/closes the fd.
            }
        }
    }

    override fun createExclusive(name: String): ModelFileOps.Writer {
        val fd = Os.open(path(name), OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or flags, 384)
        return object : Descriptor(fd), ModelFileOps.Writer {
            override fun write(bytes: ByteArray) {
                var offset = 0
                while (offset < bytes.size) {
                    val count = Os.write(fd, bytes, offset, bytes.size - offset)
                    check(count > 0 && count <= bytes.size - offset) { "Model write made no progress" }
                    offset += count
                }
            }
            override fun flush() { check(!closed) } // Unbuffered; no queued userspace bytes.
            override fun sync() { Os.fsync(fd) }
        }
    }

    override fun rename(from: String, to: String) {
        require(from != to)
        regular(checkNotNull(stat(from)) { "Missing model file" })
        stat(to)?.let { regular(it) }
        Os.rename(path(from), path(to))
    }

    override fun remove(name: String) {
        regular(checkNotNull(stat(name)) { "Missing model file" })
        Os.remove(path(name))
        check(stat(name) == null) { "Model removal not confirmed" }
    }
}
