package io.github.greprohan.llamadroid.modelimport

/** Display metadata only; never a filename or a diagnostic value. */
internal object ModelName {
    const val MAX_CODE_POINTS = 256
    const val MAX_UTF8_BYTES = 1024
    const val FALLBACK = "Unnamed model"

    fun sanitize(value: String?): String {
        if (value == null) return FALLBACK
        val result = StringBuilder()
        var offset = 0
        var count = 0
        while (offset < value.length && count < MAX_CODE_POINTS) {
            val point = Character.codePointAt(value, offset)
            offset += Character.charCount(point)
            val type = Character.getType(point)
            if (type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() ||
                type == Character.SURROGATE.toInt() || type == Character.LINE_SEPARATOR.toInt() ||
                type == Character.PARAGRAPH_SEPARATOR.toInt()
            ) {
                result.append(' ')
            } else {
                result.appendCodePoint(point)
            }
            count++
        }
        // Trim only the bounded prefix, never scan/allocate from the whole input.
        return result.toString().trim().ifEmpty { FALLBACK }
    }

    fun isValid(value: String): Boolean =
        value.length <= MAX_CODE_POINTS * 2 && value == sanitize(value) &&
            value.toByteArray(Charsets.UTF_8).size <= MAX_UTF8_BYTES
}
