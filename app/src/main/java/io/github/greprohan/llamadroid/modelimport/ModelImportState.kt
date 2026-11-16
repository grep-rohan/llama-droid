package io.github.greprohan.llamadroid.modelimport

internal enum class ImportPhase { PREPARING, COPYING, PROMOTING, CANCELLING, COMMITTING, FINISHED }
internal data class ImportOperationState(val id: String, val phase: ImportPhase,
    val cancelAccepted: Boolean, val helpers: Int, val acquiring: Boolean, val resourcePending: Boolean) {
    val quiescent: Boolean get() = helpers == 0 && !acquiring && !resourcePending
}

internal enum class ImportOutcome { IMPORTED, CANCELLED, FAILED }
internal enum class ImportIssue { SOURCE_UNREADABLE, INVALID_HEADER, UNSUPPORTED_HEADER, INCONSISTENT_SIZE,
    SPACE_CHECK, INSUFFICIENT_SPACE, READ_WRITE, SELECTION_SAVE, RECOVERY_REQUIRED, STALE_RESULT }
internal enum class ImportWarning { CLEANUP_DEBT, CLEANUP_FAILED }
/** No URI, untrusted path, Throwable or provider error text reaches rendering/diagnostics. */
internal data class ModelImportSnapshot(val revision: Long = 0, val store: ModelStore.State = ModelStore.State(),
    val picker: PickerCoordinator.Snapshot = PickerCoordinator.Snapshot(PickerCoordinator.Phase.IDLE),
    val operation: ImportOperationState? = null, val busy: Boolean = false, val resourcesPending: Boolean = false,
    val bytes: Long = 0, val total: Long? = null, val name: String? = null,
    val outcome: ImportOutcome? = null, val issue: ImportIssue? = null, val warnings: Set<ImportWarning> = emptySet())
