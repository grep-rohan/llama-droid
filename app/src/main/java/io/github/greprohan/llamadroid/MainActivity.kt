package io.github.greprohan.llamadroid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import io.github.greprohan.llamadroid.modelimport.AndroidPickerRoute
import io.github.greprohan.llamadroid.modelimport.ModelImportScreen
import io.github.greprohan.llamadroid.modelimport.ModelImportSnapshot
import io.github.greprohan.llamadroid.modelimport.ModelImports
import io.github.greprohan.llamadroid.modelimport.PickerCoordinator

/** Thin Views/picker host. Incoming data, file paths and argument extras are deliberately ignored. */
class MainActivity : Activity() {
    private lateinit var imports: ModelImports
    private lateinit var screen: ModelImportScreen
    private lateinit var route: AndroidPickerRoute
    private var detach: (() -> Unit)? = null
    private var visible = false
    // One token-bound transient result while serialized store recovery is busy. Never saved as a URI.
    private var result: AndroidPickerRoute.Result? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LayoutInflater.from(this).inflate(R.layout.main_activity, null)
        setContentView(root)
        screen = ModelImportScreen(root)
        imports = ModelImports.get(applicationContext)
        route = AndroidPickerRoute.restore(savedInstanceState)
        screen.bind(ModelImportScreen.Actions(choose = ::choose, cancel = { imports.controller.requestCancel() }))
    }
    override fun onStart() {
        super.onStart(); visible = true
        detach = imports.controller.observe { state ->
            screen.render(state)
            deliverResult(state)
            val current = imports.controller.snapshot()
            if (!current.busy && current.picker.phase == PickerCoordinator.Phase.RESERVED) launchReserved()
        }
        imports.controller.initialize()
    }
    override fun onStop() {
        visible = false; detach?.invoke(); detach = null
        super.onStop()
    }
    override fun onDestroy() {
        detach?.invoke(); detach = null; screen.close()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        route.save(outState) // Epoch/nonce/request code only, never result URI or an operation resume.
        super.onSaveInstanceState(outState)
    }
    private fun message(resource: Int) { Toast.makeText(this, resource, Toast.LENGTH_LONG).show() }
    private fun choose() {
        if (result != null || route.state().binding != null) { message(R.string.import_picker_busy); return }
        if (route.needsFreshActivity()) {
            imports.controller.snapshot().picker.token?.let { imports.controller.abandonRoute(it) }
            route.retire()
            startActivity(Intent(this, MainActivity::class.java)) // No import extras, auto-picker or URI transfer.
            finish(); message(R.string.import_picker_fresh_route)
            return
        }
        if (!imports.controller.beginPicker()) message(R.string.import_picker_busy)
    }
    private fun launchReserved() {
        if (!visible || imports.controller.snapshot().busy) return
        val token = imports.controller.claimLaunch() ?: return
        val status = route.dispatch(token) { intent, code -> startActivityForResult(intent, code) }
        if (status == AndroidPickerRoute.LaunchStatus.LAUNCHED) imports.controller.markLaunched(token)
        else {
            imports.controller.dismissPicker(token)
            message(if (status == AndroidPickerRoute.LaunchStatus.UNAVAILABLE) R.string.import_picker_missing else R.string.import_picker_invalid)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val reply = route.result(requestCode, resultCode, data) ?: return
        if (result != null) return
        result = reply
        if (reply is AndroidPickerRoute.Result.Invalid) message(R.string.import_picker_invalid)
        deliverResult(imports.controller.snapshot())
    }
    private fun deliverResult(state: ModelImportSnapshot) {
        if (!visible || state.busy || state.store.selection == null && state.store.problem == null) return
        val reply = result ?: return
        val accepted = when (reply) {
            is AndroidPickerRoute.Result.Document -> imports.importResult(reply.token, reply.uri)
            is AndroidPickerRoute.Result.Dismissed -> imports.controller.dismissPicker(reply.token)
            is AndroidPickerRoute.Result.Invalid -> imports.controller.dismissPicker(reply.token)
        }
        if (accepted) result = null // The one reserved controller command now owns the transient URI.
    }
}
