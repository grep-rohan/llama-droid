package io.github.greprohan.llamadroid.modelimport

import org.junit.Assert.*
import org.junit.Test

class PickerRequestTest {
    private val epoch = "a".repeat(32)
    private val first = PickerToken(epoch, "b".repeat(32))
    private val second = PickerToken(epoch, "c".repeat(32))
    private val routeId = "d".repeat(32)

    @Test fun oneBindingSurvivesRecreationAndOnlyItsOwnCodeConsumesIt() {
        val route = PickerRoute(PickerRouteState(routeId))
        val binding = checkNotNull(route.bind(first))
        assertEquals(1, binding.code); assertNull(route.bind(second))
        val restored = PickerRoute(route.state())
        assertNull(restored.takeResult(2)); assertEquals(binding, restored.state().binding)
        assertEquals(first, restored.takeResult(1)); assertNull(restored.takeResult(1))
        assertEquals(PickerBinding(2, second), restored.bind(second))
        assertNull(restored.takeResult(1)); assertEquals(second, restored.takeResult(2))
        assertEquals(3, restored.state().nextCode)
    }

    @Test fun requestCodesReachFFFEThenRefuseInsteadOfWrapping() {
        val route = PickerRoute(PickerRouteState(routeId, nextCode = 0xFFFE))
        assertEquals(0xFFFE, route.bind(first)!!.code)
        assertEquals(first, route.takeResult(0xFFFE))
        assertEquals(0xFFFF, route.state().nextCode)
        assertNull(route.bind(second)); assertNull(PickerRoute(route.state()).bind(second))
    }

    @Test fun retiringARouteRejectsNewLaunchAndOldResults() {
        val route = PickerRoute(PickerRouteState(routeId)); route.bind(first); route.retire()
        assertTrue(route.state().retired); assertNull(route.state().binding)
        assertNull(route.bind(second)); assertNull(route.takeResult(1))
    }

    @Test fun malformedSavedIdentityCodeOrUnboundedBindingIsRejected() {
        for (id in listOf("", "a".repeat(31), "A".repeat(32), "../model", "a".repeat(33))) {
            assertThrows(IllegalArgumentException::class.java) { PickerToken(epoch, id) }
            assertThrows(IllegalArgumentException::class.java) { PickerRouteState(id) }
        }
        for (next in listOf(Int.MIN_VALUE, 0, 0x10000, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { PickerRouteState(routeId, nextCode = next) }
        }
        assertThrows(IllegalArgumentException::class.java) { PickerRouteState(routeId, 1, PickerBinding(1, first)) }
        assertThrows(IllegalArgumentException::class.java) { PickerRouteState(routeId, 2, PickerBinding(1, first), true) }
    }
}
