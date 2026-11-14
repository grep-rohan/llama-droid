package io.github.greprohan.llamadroid

import android.content.Context
import java.io.IOException

/** Direct-process boundary only; the caller owns stream draining, waiting and cleanup. */
internal class NativeServerLauncher(
    private val layout: NativeRuntimeLayout,
    private val startProcess: (ProcessBuilder) -> Process = { it.start() },
) {
    constructor(context: Context) : this(NativeRuntimeLayout(context))

    fun start(arguments: List<String>): Process {
        val builder = layout.processBuilder(arguments)
        try {
            return startProcess(builder)
        } catch (failure: IOException) {
            throw launchFailure(failure)
        } catch (failure: SecurityException) {
            throw launchFailure(failure)
        }
    }

    private fun launchFailure(cause: Exception): IOException = IOException(
        "Unable to start bundled llama-server at ${layout.serverExecutable.absolutePath} " +
            "(working directory: ${layout.nativeDir.absolutePath}): ${cause.message}",
        cause,
    )
}
