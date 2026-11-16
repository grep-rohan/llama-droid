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
            "reset" to setOf("reset-next-synced", "reset-committed", "reset-delete", "reset-empty-committed", "reset-cleared"),
            "cancel-hang" to setOf("query-hung", "open-hung", "read-hung", "close-hung"))
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

    /** Wraps real Android operations; all pause controls live exclusively in the test APK. */
    fun files(delegate: ModelFileOps): ModelFileOps = object : ModelFileOps by delegate {
        private fun record(name: String): ModelRecord = delegate.openRead(name).use { ModelRecordCodec.read(it.input) }
        override fun createExclusive(name: String): ModelFileOps.Writer {
            val writer = delegate.createExclusive(name)
            try { if (ModelOwnership.isCandidate(name)) hit("candidate-created") }
            catch (failure: Throwable) { writer.close(); throw failure }
            return object : ModelFileOps.Writer by writer {
                private var synced = false
                private var bytes = 0L
                private var checkpointed = false
                override fun write(data: ByteArray, offset: Int, count: Int) {
                    writer.write(data, offset, count); bytes += count
                    if (ModelOwnership.isCandidate(name) && bytes in 24..49) hit("mid-copy")
                }
                override fun sync() { writer.sync(); synced = true }
                override fun close() {
                    writer.close()
                    if (!armed || !synced || !writer.closed || checkpointed) return
                    checkpointed = true
                    when (name) {
                        "reset.next" -> hit("reset-next-synced")
                        "selection.next" -> {
                            val value = record(name) as ModelRecord.Selection
                            if (value.active?.id == newId) hit("selection-next-synced")
                            else if (delegate.stat("selection") == null && scenario == "first") hit("initial-selection-next")
                        }
                    }
                }
            }
        }
        override fun rename(from: String, to: String) {
            delegate.rename(from, to)
            if (!armed) return
            when {
                to == "reset" -> hit("reset-committed")
                to == "model-$newId.gguf" -> hit("payload-renamed")
                to == "selection" -> {
                    val value = record(to) as ModelRecord.Selection
                    if (value.active?.id == newId) hit("selection-committed")
                    if (value == ModelRecord.Selection(resetEpoch)) hit("reset-empty-committed")
                }
            }
        }
        override fun remove(name: String) {
            delegate.remove(name)
            when (name) {
                "model-$oldId.gguf" -> hit(if (scenario == "reset") "reset-delete" else "old-payload-delete")
                "reset" -> hit("reset-cleared")
            }
        }
    }
}
