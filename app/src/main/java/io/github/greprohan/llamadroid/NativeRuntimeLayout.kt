package io.github.greprohan.llamadroid

import android.content.Context
import java.io.File

/** Installed, read-only payload layout. File/permission injection is internal for JVM tests. */
internal class NativeRuntimeLayout(
    nativeDirectory: File,
    private val executableCheck: (File) -> Boolean = File::canExecute,
) {
    constructor(context: Context) : this(
        File(checkNotNull(context.applicationInfo.nativeLibraryDir) { "Native library directory is unavailable" }),
    )

    init {
        require(nativeDirectory.isAbsolute) { "Native library directory must be absolute: $nativeDirectory" }
    }

    val nativeDir: File = nativeDirectory.canonicalFile
    val serverExecutable: File = File(nativeDir, "libllama-server.so")

    fun processBuilder(arguments: List<String>): ProcessBuilder {
        require(arguments.none { '\u0000' in it }) { "Native server arguments must not contain NUL" }
        check(nativeDir.isDirectory) { "Native library directory is missing or not a directory: $nativeDir" }
        for (name in PAYLOAD_NAMES) {
            val payload = File(nativeDir, name)
            check(payload.isFile && payload.canRead()) { "Bundled native payload is missing or unreadable: $payload" }
            check(payload.canonicalFile.parentFile == nativeDir) { "Bundled native payload escapes native directory: $payload" }
        }
        check(executableCheck(serverExecutable)) { "Bundled llama-server is not executable: $serverExecutable" }

        return ProcessBuilder(listOf(serverExecutable.absolutePath) + arguments.toList()).apply {
            directory(nativeDir)
            redirectErrorStream(false)
            configureEnvironment(environment())
        }
    }

    internal fun configureEnvironment(environment: MutableMap<String, String>) {
        environment["LD_LIBRARY_PATH"] = nativeDir.absolutePath
        environment.remove("GGML_BACKEND_PATH")
        // Do not permit inherited injection of an out-of-tree native library either.
        environment.remove("LD_PRELOAD")
    }

    companion object {
        // Keep aligned with third_party/llama.cpp.json; packaging verifies digests separately.
        val PAYLOAD_NAMES: List<String> = listOf(
            "libllama-server.so",
            "libllama-server-impl.so",
            "libllama-common.so",
            "libmtmd.so",
            "libllama.so",
            "libggml.so",
            "libggml-base.so",
            "libggml-cpu-android_armv8.0_1.so",
            "libggml-cpu-android_armv8.2_1.so",
            "libggml-cpu-android_armv8.2_2.so",
            "libggml-cpu-android_armv8.6_1.so",
            "libggml-cpu-android_armv9.0_1.so",
            "libggml-cpu-android_armv9.2_1.so",
            "libggml-cpu-android_armv9.2_2.so",
        )
    }
}
