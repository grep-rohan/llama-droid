package io.github.greprohan.llamadroid

import android.os.Build
import android.os.Process
import android.system.Os
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Informational command only, under the target app UID. No model, listener or WebUI. */
@RunWith(AndroidJUnit4::class)
class NativeVersionSmokeTest {
    @Test(timeout = 90_000) // Whole test guard; the child execution deadline is 30 seconds.
    fun installedPinnedServerExitsWithinDeadlineAndLeavesNoChild() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("Instrumentation must execute under target app UID", context.applicationInfo.uid, Process.myUid())
        assertTrue("ARM64 device required", Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        val pins = instrumentation.context.assets.open("native-provenance.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        assertEquals("b12020", pins.getString("release"))
        assertEquals("0ec4027a2a0183de87c007069adddc06e665f7de", pins.getString("source_commit"))
        assertEquals("64717fce80d758c220345864a0e69788aa1488ffe4f4b85e322155fdd1beaeec",
            pins.getString("archive_sha256"))
        val layout = NativeRuntimeLayout(context)
        // Configuration validation does not launch; hash every installed payload before testing.
        layout.processBuilder(listOf("--version"))
        val payloads = pins.getJSONArray("payloads")
        assertEquals(NativeRuntimeLayout.PAYLOAD_NAMES.size, payloads.length())
        Log.i(TAG, "Target uid=${Process.myUid()} parentPid=${Process.myPid()} model=${Build.MODEL} API=${Build.VERSION.SDK_INT}")
        Log.i(TAG, "Installed nativeLibraryDir=${layout.nativeDir.absolutePath} artifact=${pins.getString("archive_sha256")}")
        for (index in 0 until payloads.length()) {
            val pin = payloads.getJSONObject(index)
            val file = File(layout.nativeDir, pin.getString("apk_name"))
            assertTrue("Unallowlisted installed payload ${file.name}", NativeRuntimeLayout.PAYLOAD_NAMES.contains(file.name))
            assertEquals("Installed payload bytes changed: ${file.name}", pin.getString("sha256"), sha256(file))
            val stat = Os.stat(file.absolutePath)
            Log.i(TAG, "Installed ${file.name} bytes=${file.length()} mode=${Integer.toOctalString(stat.st_mode and 0xFFF)} fileOwnerUid=${stat.st_uid} executable=${file.canExecute()}")
        }
        val probe = NativeProcessProbe() // Validate budgets before acquiring a child.
        val started = System.nanoTime()
        val child = NativeServerLauncher(context).start(listOf("--version"))
        val result = probe.run(child, started)
        Log.i(TAG, "Version result exit=${result.exitCode} timeout=${result.timedOut} reaped=${result.processReaped} readersStopped=${result.readersStopped} streamsDrained=${result.streamsDrained} operationsStopped=${result.operationsStopped} exitMs=${result.exitElapsedMillis} totalMs=${result.elapsedMillis}")
        Log.i(TAG, "stdout bytes=${result.stdoutBytes} tail=${result.stdoutTail}")
        Log.i(TAG, "stderr bytes=${result.stderrBytes} tail=${result.stderrTail}")
        assertTrue("Probe failed: $result", result.successful)
        assertNotNull("Missing bounded exit observation", result.exitElapsedMillis)
        assertTrue("Exit was outside 30-second execution deadline", result.exitElapsedMillis!! <= 30_000)
        assertFalse("Child survived completed probe", child.isAlive)
        val output = result.stdoutTail + "\n" + result.stderrTail
        assertTrue("No pinned build/commit header in bounded output: $output",
            PinnedVersionEvidence.matches(output, 12020, pins.getString("source_commit")))
        // Physical S25 identity/properties and post-run ADB process cross-check are still
        // required T9 evidence; an emulator/other ARM64 run does not complete that milestone.
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    companion object { private const val TAG = "llama.droid" }
}
