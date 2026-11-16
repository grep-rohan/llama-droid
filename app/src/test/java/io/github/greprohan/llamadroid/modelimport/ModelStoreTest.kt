package io.github.greprohan.llamadroid.modelimport

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ModelStoreTest {
    @Test fun oneRootCannotHaveTwoLiveOwners() {
        val key = UUID.randomUUID().toString()
        ModelOwnership.acquire(key).use {
            assertThrows(IllegalStateException::class.java) { ModelOwnership.acquire(key) }
        }
        ModelOwnership.acquire(key).close()
    }

    @Test fun independentRootsAndIdempotentCloseDoNotReleaseAnotherOwner() {
        val key = UUID.randomUUID().toString()
        val first = ModelOwnership.acquire(key)
        val other = ModelOwnership.acquire("$key-other")
        first.close()
        ModelOwnership.acquire(key).use {
            first.close()
            assertThrows(IllegalStateException::class.java) { ModelOwnership.acquire(key) }
        }
        other.close()
    }
}
