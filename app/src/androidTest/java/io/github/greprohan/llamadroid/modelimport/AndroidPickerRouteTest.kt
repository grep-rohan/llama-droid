package io.github.greprohan.llamadroid.modelimport

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android memory objects/dispatch fakes only; no real picker, singleton or owner store. */
@RunWith(AndroidJUnit4::class)
class AndroidPickerRouteTest {
    private val first = PickerToken("a".repeat(32), "b".repeat(32))
    private val second = first.copy(nonce = "c".repeat(32))
    private val uri = Uri.parse("content://generated.fixture/document/one")
    private fun result(uri: Uri = this.uri) = Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    private fun launch(route: AndroidPickerRoute, token: PickerToken = first): Int {
        var code = 0
        assertEquals(AndroidPickerRoute.LaunchStatus.LAUNCHED, route.dispatch(token) { _, request -> code = request })
        return code
    }

    @Test fun dispatchUsesOnlySingleReadOnlySafAndSavesBoundedOpaqueIdentity() {
        val route = AndroidPickerRoute.restore(null)
        var request: Intent? = null
        route.dispatch(first) { intent, code -> request = intent; assertEquals(1, code) }
        val intent = checkNotNull(request)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE)); assertEquals("*/*", intent.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
        assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true))
        val saved = Bundle(); route.save(saved)
        assertEquals(setOf(AndroidPickerRoute.STATE_KEY), saved.keySet())
        val data = checkNotNull(saved.getBundle(AndroidPickerRoute.STATE_KEY))
        assertEquals(setOf("version", "id", "next", "retired", "code", "epoch", "nonce"), data.keySet())
        val parcel = Parcel.obtain()
        try { parcel.writeBundle(saved); assertTrue(parcel.dataSize() <= 2048) } finally { parcel.recycle() }
        assertEquals(first, route.state().binding!!.token)
    }

    @Test fun restoredBindingMatchesOneResultWithoutRebindingAnOldCodeToANewerToken() {
        val original = AndroidPickerRoute.restore(null); val code = launch(original)
        val saved = Bundle(); original.save(saved)
        val restored = AndroidPickerRoute.restore(saved)
        assertNull(restored.result(code + 1, Activity.RESULT_OK, result()))
        val reply = restored.result(code, Activity.RESULT_OK, result()) as AndroidPickerRoute.Result.Document
        assertEquals(first, reply.token); assertEquals(uri, reply.uri)
        assertNull(restored.result(code, Activity.RESULT_OK, result()))
        assertEquals(code + 1, launch(restored, second))
        assertNull(restored.result(code, Activity.RESULT_OK, result()))
        assertEquals(second, (restored.result(code + 1, Activity.RESULT_CANCELED, null) as AndroidPickerRoute.Result.Dismissed).token)
    }

    @Test fun failedDispatchConsumesItsCodeWithoutClearingAnotherBindingOrReusingTheRoute() {
        val route = AndroidPickerRoute.restore(null)
        assertEquals(AndroidPickerRoute.LaunchStatus.UNAVAILABLE, route.dispatch(first) { _, _ -> throw android.content.ActivityNotFoundException() })
        assertNull(route.state().binding); assertEquals(2, route.state().nextCode)
        assertEquals(AndroidPickerRoute.LaunchStatus.DENIED, route.dispatch(first) { _, _ -> throw SecurityException("private provider detail") })
        assertEquals(3, launch(route, second))
        assertEquals(AndroidPickerRoute.LaunchStatus.NO_ROUTE, route.dispatch(first) { _, _ -> error("No duplicate picker") })
        assertEquals(second, route.state().binding!!.token)
        assertNull(route.result(1, Activity.RESULT_OK, result()))
    }

    @Test fun invalidOrMissingSavedRouteNeverRestartsAtOneInsideTheSameLogicalActivity() {
        val valid = Bundle().also { AndroidPickerRoute.restore(null).save(it) }
        val wrongBoolean = Bundle().also { AndroidPickerRoute.restore(null).save(it) }.apply {
            getBundle(AndroidPickerRoute.STATE_KEY)!!.putString("retired", "not-a-boolean")
        }
        val cases = listOf(Bundle(), wrongBoolean, Bundle(valid).apply { getBundle(AndroidPickerRoute.STATE_KEY)!!.putInt("version", 99) },
            Bundle().apply { putBundle(AndroidPickerRoute.STATE_KEY, Bundle().apply {
                putInt("version", 1); putString("id", "x".repeat(4096)); putInt("next", 1); putBoolean("retired", false)
            }) })
        for (saved in cases) {
            val route = AndroidPickerRoute.restore(saved)
            assertTrue(route.needsFreshActivity())
            assertEquals(AndroidPickerRoute.LaunchStatus.NO_ROUTE, route.dispatch(first) { _, _ -> error("No uncertain launch") })
            val clean = Bundle(); route.save(clean)
            assertNull(clean.getBundle(AndroidPickerRoute.STATE_KEY)!!.getString("uri"))
        }
    }

    @Test fun exhaustedSavedCodesAndRetiredRoutesCannotWrapOrAcceptOldCallbacks() {
        val saved = Bundle().also { AndroidPickerRoute.restore(null).save(it) }
        saved.getBundle(AndroidPickerRoute.STATE_KEY)!!.putInt("next", 0xFFFE)
        val route = AndroidPickerRoute.restore(saved)
        assertEquals(0xFFFE, launch(route))
        assertTrue(route.result(0xFFFE, Activity.RESULT_OK, result()) is AndroidPickerRoute.Result.Document)
        assertTrue(route.needsFreshActivity())
        assertEquals(AndroidPickerRoute.LaunchStatus.NO_ROUTE, route.dispatch(second) { _, _ -> error("No wrap") })
        route.retire(); assertNull(route.result(0xFFFE, Activity.RESULT_OK, result()))
    }

    @Test fun retainedTransientResultSurvivesInProcessButIsNeverRestoredFromBundle() {
        val route = AndroidPickerRoute.restore(null); val code = launch(route)
        val reply = route.receive(code, Activity.RESULT_OK, result()) as AndroidPickerRoute.Result.Document
        val retained = route // The object returned by Activity non-configuration retention, not a new Activity launch.
        assertSame(reply, retained.pendingResult()); assertEquals(uri, reply.uri)
        assertNull(retained.receive(code, Activity.RESULT_OK, result()))
        assertEquals(AndroidPickerRoute.LaunchStatus.NO_ROUTE, retained.dispatch(second) { _, _ -> error("One transient result only") })
        val saved = Bundle(); retained.save(saved)
        val data = checkNotNull(saved.getBundle(AndroidPickerRoute.STATE_KEY))
        assertEquals(setOf("version", "id", "next", "retired"), data.keySet())
        val afterProcessRestart = AndroidPickerRoute.restore(saved)
        assertNull(afterProcessRestart.pendingResult()); assertNull(afterProcessRestart.result(code, Activity.RESULT_OK, result()))
        retained.handOff(reply); assertNull(retained.pendingResult())
        assertEquals(code + 1, launch(retained, second))
    }

    @Test fun oldActivityHandoffCannotConsumeANewerResultAndRetirementDropsTransientUri() {
        val route = AndroidPickerRoute.restore(null); val firstCode = launch(route)
        val old = checkNotNull(route.receive(firstCode, Activity.RESULT_OK, result()))
        route.handOff(old)
        val secondCode = launch(route, second)
        val newer = checkNotNull(route.receive(secondCode, Activity.RESULT_OK, result()))
        route.handOff(old); assertSame(newer, route.pendingResult())
        route.retire(); assertNull(route.pendingResult()); assertTrue(route.needsFreshActivity())
        assertNull(route.receive(secondCode, Activity.RESULT_OK, result()))
    }

    @Test fun malformedMultipleNonContentAndUngrantedResultsNeverBecomeDocuments() {
        val multiple = ClipData.newRawUri("fixture", uri).also { it.addItem(ClipData.Item(uri)) }
        val conflicting = ClipData.newRawUri("fixture", Uri.parse("content://generated.fixture/other"))
        val invalid = listOf(null, Intent(), Intent().setData(uri), result(Uri.parse("file:///private")),
            result(Uri.parse("https://example.invalid/model")), result(Uri.parse("content:/missing-authority")),
            result().apply { clipData = multiple }, result().apply { clipData = conflicting })
        for (data in invalid) {
            val route = AndroidPickerRoute.restore(null); val code = launch(route)
            assertTrue(route.result(code, Activity.RESULT_OK, data) is AndroidPickerRoute.Result.Invalid)
            assertNull(route.state().binding)
        }
    }
}
