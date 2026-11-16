package io.github.greprohan.llamadroid.modelimport

/** Opaque persistent identity only: never a URI, path or provider name. */
internal data class PickerToken(val epoch: String, val nonce: String) {
    companion object {
        private val id = Regex("[0-9a-f]{32}")
        fun validId(value: String): Boolean = id.matches(value)
    }
    init { require(validId(epoch) && validId(nonce)) { "Invalid picker identity" } }
}

internal data class PickerBinding(val code: Int, val token: PickerToken) {
    init { require(code in 1..0xFFFE) }
}
internal data class PickerRouteState(val routeId: String, val nextCode: Int = 1,
                                    val binding: PickerBinding? = null, val retired: Boolean = false) {
    init {
        require(PickerToken.validId(routeId) && nextCode in 1..0xFFFF)
        require(binding == null || binding.code < nextCode)
        require(!retired || binding == null)
    }
}

/** Saved logical Activity route, one unresolved binding, no request-code wrap/reuse. */
internal class PickerRoute(private var saved: PickerRouteState) {
    @Synchronized fun state(): PickerRouteState = saved
    @Synchronized fun bind(token: PickerToken): PickerBinding? {
        if (saved.retired || saved.binding != null || saved.nextCode > 0xFFFE) return null
        val binding = PickerBinding(saved.nextCode, token)
        saved = saved.copy(nextCode = saved.nextCode + 1, binding = binding)
        return binding
    }
    @Synchronized fun takeResult(code: Int): PickerToken? {
        val binding = saved.binding ?: return null
        if (saved.retired || binding.code != code) return null
        saved = saved.copy(binding = null)
        return binding.token
    }
    @Synchronized fun retire() { saved = saved.copy(binding = null, retired = true) }
}
