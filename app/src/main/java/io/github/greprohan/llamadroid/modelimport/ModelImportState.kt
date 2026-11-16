package io.github.greprohan.llamadroid.modelimport

internal enum class ImportPhase { PREPARING, COPYING, PROMOTING, CANCELLING, COMMITTING, FINISHED }
internal data class ImportOperationState(val id: String, val phase: ImportPhase,
    val cancelAccepted: Boolean, val helpers: Int, val acquiring: Boolean, val resourcePending: Boolean) {
    val quiescent: Boolean get() = helpers == 0 && !acquiring && !resourcePending
}
