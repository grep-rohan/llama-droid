package io.github.greprohan.llamadroid.modelimport

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.util.UUID

/** Saved opaque routing only. Incoming Activity intent extras never enter this boundary. */
internal class AndroidPickerRoute private constructor(private val route: PickerRoute) {
    companion object {
        const val STATE_KEY = "model_import_route"
        private val fields = setOf("version", "id", "next", "retired", "code", "epoch", "nonce")
        private fun id() = UUID.randomUUID().toString().replace("-", "")
        fun restore(saved: Bundle?): AndroidPickerRoute {
            if (saved == null) return AndroidPickerRoute(PickerRoute(PickerRouteState(id())))
            val state = try {
                val data = checkNotNull(saved.getBundle(STATE_KEY))
                require(data.size() in 4..7 && data.keySet().all { it in fields })
                require(listOf("version", "id", "next", "retired").all { data.containsKey(it) })
                require(data.getInt("version") == 1)
                val routeId = checkNotNull(data.getString("id")); require(routeId.length == 32)
                val hasBinding = data.containsKey("code")
                require(data.containsKey("epoch") == hasBinding && data.containsKey("nonce") == hasBinding)
                val binding = if (!hasBinding) null else {
                    val epoch = checkNotNull(data.getString("epoch")); val nonce = checkNotNull(data.getString("nonce"))
                    require(epoch.length == 32 && nonce.length == 32)
                    PickerBinding(data.getInt("code"), PickerToken(epoch, nonce))
                }
                val retired = data.getBoolean("retired", false)
                require(retired == data.getBoolean("retired", true)) // Reject wrong types, not just missing keys.
                PickerRouteState(routeId, data.getInt("next"), binding, retired)
            } catch (_: Exception) {
                // Never restart codes at 1 in an uncertain restored Android result route.
                PickerRouteState(id(), 0xFFFF, retired = true)
            }
            return AndroidPickerRoute(PickerRoute(state))
        }
    }
    // Main-thread Activity route state; retained only in-process, never serialized as a URI.
    private var waitingResult: Result? = null
    fun pendingResult(): Result? = waitingResult
    fun receive(code: Int, resultCode: Int, data: Intent?): Result? {
        if (waitingResult != null) return null
        return result(code, resultCode, data)?.also { waitingResult = it }
    }
    fun handOff(expected: Result) { if (waitingResult === expected) waitingResult = null }
    enum class LaunchStatus { LAUNCHED, NO_ROUTE, UNAVAILABLE, DENIED }
    sealed interface Result {
        val token: PickerToken
        data class Document(override val token: PickerToken, val uri: Uri) : Result
        data class Dismissed(override val token: PickerToken) : Result
        data class Invalid(override val token: PickerToken) : Result
    }
    fun state(): PickerRouteState = route.state()
    fun needsFreshActivity(): Boolean = state().let { it.retired || (it.binding == null && it.nextCode > 0xFFFE) }
    fun retire() { route.retire(); waitingResult = null }
    fun save(out: Bundle) {
        val state = state()
        val data = Bundle().apply {
            putInt("version", 1); putString("id", state.routeId); putInt("next", state.nextCode); putBoolean("retired", state.retired)
            state.binding?.let { putInt("code", it.code); putString("epoch", it.token.epoch); putString("nonce", it.token.nonce) }
        }
        out.putBundle(STATE_KEY, data)
    }
    /** Testable dispatch port; production supplies only startActivityForResult, never a source path. */
    fun dispatch(token: PickerToken, launch: (Intent, Int) -> Unit): LaunchStatus {
        if (waitingResult != null) return LaunchStatus.NO_ROUTE
        val binding = route.bind(token) ?: return LaunchStatus.NO_ROUTE
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        return try {
            launch(intent, binding.code)
            LaunchStatus.LAUNCHED
        } catch (_: ActivityNotFoundException) {
            route.takeResult(binding.code); LaunchStatus.UNAVAILABLE
        } catch (_: SecurityException) {
            route.takeResult(binding.code); LaunchStatus.DENIED
        } catch (_: RuntimeException) {
            route.takeResult(binding.code); LaunchStatus.UNAVAILABLE
        }
    }
    fun result(code: Int, resultCode: Int, data: Intent?): Result? {
        val token = route.takeResult(code) ?: return null
        if (resultCode != Activity.RESULT_OK) return Result.Dismissed(token)
        if (data == null || data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return Result.Invalid(token)
        val clip = data.clipData
        if (clip != null && (clip.itemCount != 1 || clip.getItemAt(0).intent != null)) return Result.Invalid(token)
        val clipped = clip?.getItemAt(0)?.uri
        val uri = data.data ?: clipped ?: return Result.Invalid(token)
        if (clip != null && clipped != uri) return Result.Invalid(token)
        if (uri.scheme != "content" || uri.authority.isNullOrEmpty()) return Result.Invalid(token)
        return Result.Document(token, uri)
    }
}
