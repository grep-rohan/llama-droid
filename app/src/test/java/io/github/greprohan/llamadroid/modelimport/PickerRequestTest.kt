package io.github.greprohan.llamadroid.modelimport

import org.junit.Assert.*
import org.junit.Test

class PickerRequestTest {
    private val epoch = "a".repeat(32)
    private val first = PickerToken(epoch, "b".repeat(32))
    private val second = PickerToken(epoch, "c".repeat(32))
    private val routeId = "d".repeat(32)

    @Test fun durableReservationPrecedesExactlyOnePassiveFirstLaunchClaim() {
        ModelStore(FakeModelRoot()) { epoch }.use { store ->
            val coordinator = PickerCoordinator(store) { first.nonce }
            assertNull(coordinator.claimLaunch())
            assertTrue(coordinator.reserve()!!.accepted)
            assertEquals(first.nonce, store.recover().selection!!.pendingPickerNonce)
            assertEquals(PickerCoordinator.Phase.RESERVED, coordinator.snapshot().phase)
            assertEquals(first, coordinator.claimLaunch()); assertNull(coordinator.claimLaunch())
            assertNull(coordinator.reserve())
            assertTrue(coordinator.markLaunched(first))
            assertEquals(PickerCoordinator.Phase.AWAITING_RESULT, coordinator.snapshot().phase)
            assertTrue(coordinator.consumeResult(first)!!.accepted)
            assertNull(store.recover().selection!!.pendingPickerNonce)
            assertNull(coordinator.consumeResult(first))
        }
    }

    @Test fun processRestorationNeverRelaunchesButMayConsumeADeliveredMatchingResult() {
        ModelStore(FakeModelRoot()) { epoch }.use { store ->
            store.reservePicker(first.nonce)
            val coordinator = PickerCoordinator(store)
            coordinator.restore(store.recover())
            assertEquals(PickerCoordinator.Phase.RESTORED_UNCERTAIN, coordinator.snapshot().phase)
            assertNull(coordinator.claimLaunch())
            assertTrue(coordinator.consumeResult(first)!!.accepted)
            assertEquals(PickerCoordinator.Phase.IDLE, coordinator.snapshot().phase)
        }
    }

    @Test fun explicitFreshActionSupersedesAbandonedRouteAndNeverRebindsOldAResultToB() {
        ModelStore(FakeModelRoot()) { epoch }.use { store ->
            val nonces = ArrayDeque(listOf(first.nonce, second.nonce))
            val coordinator = PickerCoordinator(store) { nonces.removeFirst() }
            coordinator.reserve(); coordinator.claimLaunch(); coordinator.markLaunched(first)
            coordinator.abandonRoute(first)
            assertNull(coordinator.claimLaunch())
            assertTrue(coordinator.reserve()!!.accepted)
            assertNull(coordinator.consumeResult(first))
            assertEquals(second.nonce, store.recover().selection!!.pendingPickerNonce)
            assertEquals(second, coordinator.claimLaunch())
            assertTrue(coordinator.dismiss(second)!!.accepted)
            assertNull(coordinator.consumeResult(first)); assertNull(coordinator.consumeResult(second))
        }
    }

    @Test fun reservationIoNeverHoldsTheMemoryClaimGateAndDuplicatesCannotQueue() {
        val root = FakeModelRoot(); val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        ModelStore(root) { epoch }.use { store ->
            store.recover()
            val coordinator = PickerCoordinator(store) { first.nonce }
            root.files.hook = { if (it == "rename") {
                entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            } }
            val worker = Thread({ try { assertTrue(coordinator.reserve()!!.accepted) } catch (error: Throwable) { failure.set(error) } })
            worker.start()
            try {
                assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(PickerCoordinator.Phase.RESERVING, coordinator.snapshot().phase)
                assertNull(coordinator.claimLaunch()); assertNull(coordinator.reserve())
            } finally { release.countDown(); worker.join(5000); assertFalse("No surviving reservation worker", worker.isAlive) }
            assertNull(failure.get()); assertEquals(first, coordinator.claimLaunch())
        }
    }

    @Test fun failedDurableReservationCannotLaunchOrConsumeOrInventPendingRequest() {
        val root = FakeModelRoot()
        ModelStore(root) { epoch }.use { store ->
            store.recover(); root.files.hook = { if (it == "sync") throw java.io.IOException("sync") }
            val coordinator = PickerCoordinator(store) { first.nonce }
            assertFalse(coordinator.reserve()!!.accepted)
            assertEquals(PickerCoordinator.Phase.IDLE, coordinator.snapshot().phase)
            assertNull(coordinator.claimLaunch()); assertNull(coordinator.consumeResult(first))
            assertNull(store.recover().selection!!.pendingPickerNonce)
        }
    }

    @Test fun unlaunchedReservationCannotImportButCancellationClearsOnlyItsOwnToken() {
        ModelStore(FakeModelRoot()) { epoch }.use { store ->
            val coordinator = PickerCoordinator(store) { first.nonce }; coordinator.reserve()
            assertNull(coordinator.consumeResult(first))
            assertNull(coordinator.dismiss(second)); assertEquals(first.nonce, store.recover().selection!!.pendingPickerNonce)
            assertTrue(coordinator.dismiss(first)!!.accepted)
            assertEquals(PickerCoordinator.Phase.IDLE, coordinator.snapshot().phase)
        }
    }

    @Test fun staleRestoredEpochCannotConsumeOrLaunchANewerStoreRequest() {
        ModelStore(FakeModelRoot()) { epoch }.use { store ->
            store.reservePicker(first.nonce)
            val coordinator = PickerCoordinator(store); coordinator.restore(store.recover())
            assertNull(coordinator.consumeResult(first.copy(epoch = second.nonce)))
            assertNull(coordinator.claimLaunch()); assertTrue(coordinator.dismiss(first)!!.accepted)
        }
    }

    @Test fun blockedRecoveryCannotAutoLaunchAndReconciliationNeverOverwritesLiveTransition() {
        ModelStore(FakeModelRoot()) { epoch }.use { store ->
            val coordinator = PickerCoordinator(store) { first.nonce }
            coordinator.restore(ModelStore.State(problem = ModelStore.Problem.RECOVERY_REQUIRED))
            assertNull(coordinator.claimLaunch()); assertNull(coordinator.reserve())
            coordinator.reconcile(store.recover()); assertTrue(coordinator.reserve()!!.accepted)
            assertThrows(IllegalStateException::class.java) { coordinator.reconcile(store.recover()) }
            assertThrows(IllegalStateException::class.java) { coordinator.restore(store.recover()) }
            assertEquals(first, coordinator.claimLaunch())
        }
    }

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
