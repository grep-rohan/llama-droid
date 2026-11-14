package io.github.greprohan.llamadroid

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Configuration tests only: no fixture file is ever executed as a native binary. */
class NativeServerLauncherTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun layout(executable: Boolean = true): NativeRuntimeLayout {
        val directory = temporaryFolder.newFolder("native libs")
        NativeRuntimeLayout.PAYLOAD_NAMES.forEach { name ->
            File(directory, name).writeText("non-executable configuration fixture")
        }
        // Exercise permission decisions deterministically, including on Windows.
        return NativeRuntimeLayout(directory) { executable }
    }

    private fun invalidConfiguration(layout: NativeRuntimeLayout, expected: String) {
        val failure = assertThrows(IllegalStateException::class.java) {
            NativeServerLauncher(layout) { fail("Must not start on invalid configuration"); error("unreachable") }
                .start(listOf("--version"))
        }
        assertTrue(failure.message, failure.message!!.contains(expected))
    }

    @Test
    fun commandUsesAbsoluteBundledExecutableAndSeparateArguments() {
        val layout = layout()
        val arguments = listOf("--model", "model with spaces.gguf", "; touch nope", "$(nope)", "")
        val builder = layout.processBuilder(arguments)
        assertEquals(listOf(layout.serverExecutable.absolutePath) + arguments, builder.command())
        assertEquals(layout.nativeDir, builder.directory())
        assertEquals("libllama-server.so", layout.serverExecutable.name)
    }

    @Test
    fun emptyArgumentsDoNotChangeExecutable() {
        val layout = layout()
        assertEquals(listOf(layout.serverExecutable.absolutePath), layout.processBuilder(emptyList()).command())
    }

    @Test
    fun commandDoesNotRetainMutableArgumentList() {
        val layout = layout()
        val arguments = mutableListOf("--version")
        val builder = layout.processBuilder(arguments)
        arguments[0] = "--help"
        assertEquals("--version", builder.command()[1])
    }

    @Test
    fun nulArgumentIsRejectedBeforeStart() {
        val launcher = NativeServerLauncher(layout()) { fail("Must not start"); error("unreachable") }
        assertThrows(IllegalArgumentException::class.java) { launcher.start(listOf("bad\u0000argument")) }
    }

    @Test
    fun environmentReplacesAmbientLibraryPathAndRemovesBackendAndPreloadOverrides() {
        val layout = layout()
        val environment = mutableMapOf(
            "LD_LIBRARY_PATH" to "/writable:/ambient",
            "GGML_BACKEND_PATH" to "/writable/backend.so",
            "LD_PRELOAD" to "/writable/preload.so",
            "LANG" to "C.UTF-8",
        )
        layout.configureEnvironment(environment)
        assertEquals(layout.nativeDir.absolutePath, environment["LD_LIBRARY_PATH"])
        assertFalse(environment.containsKey("GGML_BACKEND_PATH"))
        assertFalse(environment.containsKey("LD_PRELOAD"))
        assertEquals("C.UTF-8", environment["LANG"])
    }

    @Test
    fun emptyEnvironmentReceivesOnlyTrustedLibraryPath() {
        val layout = layout()
        val environment = mutableMapOf<String, String>()
        layout.configureEnvironment(environment)
        assertEquals(mapOf("LD_LIBRARY_PATH" to layout.nativeDir.absolutePath), environment)
    }

    @Test
    fun processBuilderUsesTrustedEnvironment() {
        val layout = layout()
        val builder = layout.processBuilder(listOf("--version"))
        assertEquals(layout.nativeDir.absolutePath, builder.environment()["LD_LIBRARY_PATH"])
        assertFalse(builder.environment().containsKey("GGML_BACKEND_PATH"))
        assertFalse(builder.environment().containsKey("LD_PRELOAD"))
    }

    @Test
    fun outputStreamsRemainSeparatePipes() {
        val builder = layout().processBuilder(listOf("--version"))
        assertFalse(builder.redirectErrorStream())
        assertEquals(ProcessBuilder.Redirect.PIPE, builder.redirectOutput())
        assertEquals(ProcessBuilder.Redirect.PIPE, builder.redirectError())
    }

    @Test
    fun relativeNativeDirectoryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { NativeRuntimeLayout(File("relative-native")) }
    }

    @Test
    fun missingNativeDirectoryIsDiagnosed() {
        invalidConfiguration(NativeRuntimeLayout(File(temporaryFolder.root, "missing")), "Native library directory")
    }

    @Test
    fun nonDirectoryNativePathIsDiagnosed() {
        invalidConfiguration(NativeRuntimeLayout(temporaryFolder.newFile("not-a-directory")), "Native library directory")
    }

    @Test
    fun missingLauncherPreventsStart() {
        val layout = layout()
        assertTrue(layout.serverExecutable.delete())
        invalidConfiguration(layout, "libllama-server.so")
    }

    @Test
    fun launcherDirectoryIsNotAcceptedAsExecutable() {
        val layout = layout()
        assertTrue(layout.serverExecutable.delete())
        assertTrue(layout.serverExecutable.mkdir())
        invalidConfiguration(layout, "libllama-server.so")
    }

    @Test
    fun everyRequiredDependencyIsChecked() {
        val layout = layout()
        for (name in NativeRuntimeLayout.PAYLOAD_NAMES.filterNot { it == "libllama-server.so" }) {
            val payload = File(layout.nativeDir, name)
            assertTrue(payload.delete())
            invalidConfiguration(layout, name)
            payload.writeText("non-executable configuration fixture")
        }
    }

    @Test
    fun dependencyDirectoryIsNotAcceptedAsLibrary() {
        val layout = layout()
        val payload = File(layout.nativeDir, "libggml-base.so")
        assertTrue(payload.delete())
        assertTrue(payload.mkdir())
        invalidConfiguration(layout, payload.name)
    }

    @Test
    fun nonExecutableLauncherPreventsStart() {
        invalidConfiguration(layout(executable = false), "not executable")
    }

    @Test
    fun launcherReturnsExactProcessWithoutConsumingStreams() {
        val layout = layout()
        val process = UnobservedProcess()
        var starts = 0
        val launcher = NativeServerLauncher(layout) { builder ->
            starts++
            assertEquals(listOf(layout.serverExecutable.absolutePath, "--version"), builder.command())
            assertEquals(layout.nativeDir, builder.directory())
            process
        }
        assertSame(process, launcher.start(listOf("--version")))
        assertEquals(1, starts)
    }

    @Test
    fun startFailureIncludesTrustedPathsAndPreservesCause() {
        val layout = layout()
        val cause = IOException("host start failure")
        val launcher = NativeServerLauncher(layout) { throw cause }
        val failure = assertThrows(IOException::class.java) { launcher.start(listOf("--version")) }
        assertSame(cause, failure.cause)
        assertTrue(failure.message!!.contains(layout.serverExecutable.absolutePath))
        assertTrue(failure.message!!.contains(layout.nativeDir.absolutePath))
        assertTrue(failure.message!!.contains("host start failure"))
    }

    @Test
    fun securityFailureHasActionableDiagnosticWithoutLoggingArguments() {
        val layout = layout()
        val cause = SecurityException("permission denied")
        val launcher = NativeServerLauncher(layout) { throw cause }
        val failure = assertThrows(IOException::class.java) { launcher.start(listOf("private-argument")) }
        assertSame(cause, failure.cause)
        assertTrue(failure.message!!.contains(layout.serverExecutable.absolutePath))
        assertFalse(failure.message!!.contains("private-argument"))
    }

    private class UnobservedProcess : Process() {
        override fun getOutputStream(): java.io.OutputStream = error("Caller owns stdin")
        override fun getInputStream(): java.io.InputStream = error("Caller owns stdout")
        override fun getErrorStream(): java.io.InputStream = error("Caller owns stderr")
        override fun waitFor(): Int = error("Caller owns waiting")
        override fun exitValue(): Int = error("Caller owns exit status")
        override fun destroy() = error("Caller owns cleanup")
    }
}
