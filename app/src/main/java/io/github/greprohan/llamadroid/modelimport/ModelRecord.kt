package io.github.greprohan.llamadroid.modelimport

/** Values only: validity is enforced by the codec before persistence/exposure. */
internal sealed interface ModelRecord {
    data class Selection(
        val epoch: String,
        val pendingPickerNonce: String? = null,
        val active: ActiveModel? = null,
    ) : ModelRecord

    data class ResetAuthorization(val resetId: String, val newEpoch: String) : ModelRecord
}

internal data class ActiveModel(val id: String, val sizeBytes: Long, val name: String)
