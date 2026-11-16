package io.github.greprohan.llamadroid.modelimport

/** Opaque persistent identity only: never a URI, path or provider name. */
internal data class PickerToken(val epoch: String, val nonce: String) {
    companion object {
        private val id = Regex("[0-9a-f]{32}")
        fun validId(value: String): Boolean = id.matches(value)
    }
    init { require(validId(epoch) && validId(nonce)) { "Invalid picker identity" } }
}
