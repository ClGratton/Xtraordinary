package com.xteink.companion.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveTransportCoordinatorTest {
    private val first = InteractiveTransportOwner("first")
    private val second = InteractiveTransportOwner("second")

    @Test
    fun `first owner starts and last owner ends shared session`() {
        val coordinator = InteractiveTransportCoordinator()

        assertTrue(coordinator.acquire(first))
        assertFalse(coordinator.acquire(first))
        assertFalse(coordinator.acquire(second))
        assertFalse(coordinator.release(first))
        assertTrue(coordinator.release(second))
        assertFalse(coordinator.isActive)
    }

    @Test
    fun `lease applies once per connection and can be forced at a boundary`() {
        val coordinator = InteractiveTransportCoordinator()
        coordinator.acquire(first)

        assertTrue(coordinator.shouldApplyLease(capabilitiesSequence = 1L))
        assertFalse(coordinator.shouldApplyLease(capabilitiesSequence = 1L))
        assertTrue(coordinator.shouldApplyLease(capabilitiesSequence = 1L, force = true))
        assertTrue(coordinator.shouldApplyLease(capabilitiesSequence = 2L))
    }

    @Test
    fun `failed lease remains eligible for retry`() {
        val coordinator = InteractiveTransportCoordinator()
        coordinator.acquire(first)

        assertTrue(coordinator.shouldApplyLease(capabilitiesSequence = 7L))
        coordinator.markLeaseFailed(capabilitiesSequence = 7L)
        assertTrue(coordinator.shouldApplyLease(capabilitiesSequence = 7L))
    }
}
