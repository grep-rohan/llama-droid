package io.github.greprohan.llamadroid.modelimport

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import io.github.greprohan.llamadroid.R

/** Views only. No singleton, URI, storage, native launch or worker is constructed here. */
internal class ModelImportScreen(private val root: View,
    private val confirmation: Confirmation = Confirmation { yes, dismissed ->
        val dialog = AlertDialog.Builder(root.context).setTitle(R.string.import_reset)
            .setMessage(R.string.import_reset_confirmation)
            .setNegativeButton(R.string.import_reset_decline, null)
            .setPositiveButton(R.string.import_reset_confirm) { _, _ -> yes() }
            .setOnDismissListener { dismissed() }.show()
        AutoCloseable { dialog.dismiss() }
    }) : AutoCloseable {
    fun interface Confirmation { fun show(yes: () -> Unit, dismissed: () -> Unit): AutoCloseable }
    data class Actions(val choose: (() -> Unit)? = null, val cancel: (() -> Unit)? = null, val reset: ((Long) -> Unit)? = null)
    private val name: TextView = root.findViewById(R.id.active_model_name)
    private val size: TextView = root.findViewById(R.id.active_model_size)
    private val status: TextView = root.findViewById(R.id.import_status)
    private val pending: View = root.findViewById(R.id.import_pending)
    private val pendingName: TextView = root.findViewById(R.id.import_pending_name)
    private val progress: ProgressBar = root.findViewById(R.id.import_progress)
    private val progressText: TextView = root.findViewById(R.id.import_progress_text)
    private val warnings: TextView = root.findViewById(R.id.import_warnings)
    private val choose: Button = root.findViewById(R.id.import_button)
    private val cancel: Button = root.findViewById(R.id.import_cancel)
    private val reset: Button = root.findViewById(R.id.import_reset)
    private var actions = Actions()
    private var latest = ModelImportSnapshot()
    private var detached = false
    private var prompt: AutoCloseable? = null
    private var promptRevision: Long? = null
    private fun dismissPrompt() {
        val previous = prompt
        prompt = null; promptRevision = null
        previous?.close()
    }
    private fun requestReset() {
        if (detached || !reset.isEnabled || promptRevision != null) return
        val revision = latest.revision
        val action = actions.reset ?: return
        promptRevision = revision
        try {
            prompt = confirmation.show({
                if (!detached && promptRevision == revision && latest.revision == revision && reset.isEnabled) {
                    dismissPrompt(); action(revision)
                }
            }, { prompt = null; promptRevision = null })
        } catch (_: android.view.WindowManager.BadTokenException) { dismissPrompt() }
    }
    private fun main() { check(Looper.myLooper() == Looper.getMainLooper()) { "Screen access off main thread" } }
    // Only selects singular/plural; the displayed count remains signed-Long, never truncated to Int.
    private fun quantity(bytes: Long) = if (bytes == 1L) 1 else 2
    init { render(latest) }
    fun bind(actions: Actions) {
        main(); check(!detached); this.actions = actions
        choose.setOnClickListener { this.actions.choose?.invoke() }
        cancel.setOnClickListener { this.actions.cancel?.invoke() }
        reset.setOnClickListener { requestReset() }
        render(latest)
    }
    fun render(state: ModelImportSnapshot) {
        main(); if (detached) return
        if (promptRevision != null && promptRevision != state.revision) dismissPrompt()
        latest = state
        val context = root.context
        val active = state.store.active.takeIf { state.store.available }
        name.text = active?.model?.name?.let(ModelName::sanitize) ?: context.getString(
            if (state.store.problem != null) R.string.import_unavailable_model else R.string.import_no_model)
        size.text = active?.let { context.resources.getQuantityString(R.plurals.import_size, quantity(it.model.sizeBytes), it.model.sizeBytes) } ?: ""
        choose.setText(if (active == null) R.string.import_choose else R.string.import_replace)
        choose.isEnabled = actions.choose != null && !state.busy && state.store.canWrite &&
            state.picker.phase in setOf(PickerCoordinator.Phase.IDLE, PickerCoordinator.Phase.RESTORED_UNCERTAIN)
        pending.visibility = if (state.busy && state.operation != null) View.VISIBLE else View.GONE
        pendingName.text = ModelName.sanitize(state.name)
        val total = state.total?.takeIf { it > 0 }
        progress.isIndeterminate = total == null
        progress.progress = total?.let { ImportPolicy.percent(state.bytes, it) } ?: 0
        progressText.text = if (total == null) context.resources.getQuantityString(R.plurals.import_progress_unknown, quantity(state.bytes), state.bytes)
            else context.resources.getQuantityString(R.plurals.import_progress_known, quantity(total), state.bytes, total, progress.progress)
        val running = state.busy && state.operation != null && state.operation.phase != ImportPhase.FINISHED
        cancel.visibility = if (running) View.VISIBLE else View.GONE
        cancel.isEnabled = running && actions.cancel != null && state.operation?.phase != ImportPhase.COMMITTING &&
            state.operation?.cancelAccepted != true
        val recoverable = !state.busy && !state.resourcesPending && state.store.problem in setOf(ModelStore.Problem.RECOVERY_REQUIRED, ModelStore.Problem.RESET_PENDING)
        reset.visibility = if (recoverable && actions.reset != null) View.VISIBLE else View.GONE
        reset.isEnabled = reset.visibility == View.VISIBLE
        val lines = state.warnings.map { context.getString(when (it) {
            ImportWarning.CLEANUP_DEBT -> R.string.import_warning_debt
            ImportWarning.CLEANUP_FAILED -> R.string.import_warning_cleanup
        }) }
        warnings.text = lines.joinToString("\n")
        warnings.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        status.setText(when {
            state.operation?.phase == ImportPhase.COMMITTING && state.busy -> R.string.import_finishing
            state.operation?.cancelAccepted == true && state.busy -> R.string.import_cancelling
            state.resourcesPending -> R.string.import_stopping
            state.busy && state.operation?.phase == ImportPhase.COPYING -> R.string.import_copying
            state.busy -> R.string.import_preparing
            state.issue != null -> issue(state.issue)
            state.store.problem != null -> R.string.import_error_recovery
            state.outcome == ImportOutcome.IMPORTED -> R.string.import_complete
            state.outcome == ImportOutcome.CANCELLED -> R.string.import_cancelled
            state.picker.token != null -> R.string.import_waiting_picker
            state.store.selection == null -> R.string.import_preparing
            else -> R.string.import_ready
        })
    }
    private fun issue(issue: ImportIssue): Int = when (issue) {
        ImportIssue.SOURCE_UNREADABLE -> R.string.import_error_source
        ImportIssue.INVALID_HEADER -> R.string.import_error_header
        ImportIssue.UNSUPPORTED_HEADER -> R.string.import_error_version
        ImportIssue.INCONSISTENT_SIZE -> R.string.import_error_size
        ImportIssue.SPACE_CHECK -> R.string.import_error_space_check
        ImportIssue.INSUFFICIENT_SPACE -> R.string.import_error_space
        ImportIssue.READ_WRITE -> R.string.import_error_io
        ImportIssue.SELECTION_SAVE -> R.string.import_error_selection
        ImportIssue.RECOVERY_REQUIRED -> R.string.import_error_recovery
        ImportIssue.STALE_RESULT -> R.string.import_error_stale
    }
    override fun close() {
        main(); if (detached) return
        detached = true; dismissPrompt(); actions = Actions()
        for (button in listOf(choose, cancel, reset)) { button.setOnClickListener(null); button.isEnabled = false }
        cancel.visibility = View.GONE; reset.visibility = View.GONE
    }
}
