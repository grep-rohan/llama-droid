package io.github.greprohan.llamadroid.modelimport

import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Instrumentation APK only. No arbitrary path, URI, package, production root or fault switch. */
internal class ModelImportCrashProtocol {
    companion object {
        val pairs = mapOf(
            "first" to setOf("initial-selection-next", "request-reserved", "request-consumed", "candidate-created", "mid-copy", "streams-closed", "payload-renamed", "selection-next-synced", "selection-committed"),
            "replace" to setOf("request-reserved", "request-consumed", "candidate-created", "mid-copy", "streams-closed", "payload-renamed", "selection-next-synced", "selection-committed", "old-payload-delete"),
            "reset" to setOf("reset-next-synced", "reset-committed", "reset-delete", "reset-empty-committed", "reset-cleared"))
        val oldId = "a".repeat(32); val newId = "b".repeat(32)
        val epoch = "c".repeat(32); val nonce = "d".repeat(32); val resetEpoch = "e".repeat(32)
        fun bytes(old: Boolean): ByteArray = ByteArray(50) { if (old) 7 else 9 }.also {
            it[0] = 0x47; it[1] = 0x47; it[2] = 0x55; it[3] = 0x46
            it[4] = 3; it[5] = 0; it[6] = 0; it[7] = 0
        }
    }
    private val arguments = InstrumentationRegistry.getArguments()
    val runId = checkNotNull(arguments.getString("runId")).also { require(it.matches(Regex("[0-9a-f]{32}"))) }
    val scenario = checkNotNull(arguments.getString("scenario"))
    val checkpoint = checkNotNull(arguments.getString("checkpoint"))
    var armed = false
    private var paused = false
    init {
        require(arguments.getString("orchestrated") == "true") { "External orchestration required" }
        require(checkpoint in checkNotNull(pairs[scenario])) { "Invalid checkpoint pair" }
    }
    private fun emit(code: Int, field: String) {
        val message = JSONObject().put("v", 1).put("runId", runId).put("scenario", scenario)
            .put("checkpoint", checkpoint).put("pid", Process.myPid()).put("uid", Process.myUid())
            .put("startedElapsedMs", Process.getStartElapsedRealtime()).toString()
        check(message.toByteArray(Charsets.UTF_8).size <= 1024)
        InstrumentationRegistry.getInstrumentation().sendStatus(code, Bundle().apply { putString(field, message) })
    }
    fun hit(name: String) {
        if (!armed || name != checkpoint) return
        check(!paused); paused = true
        emit(1001, "model_import_checkpoint")
        check(CountDownLatch(1).await(120, TimeUnit.SECONDS)) { "Checkpoint fallback expired; no controlled stop" }
        error("Paused setup must never pass")
    }
    fun verified() { emit(1002, "model_import_verified") }
}
