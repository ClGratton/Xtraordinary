package com.xteink.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightStatusProviderTest {
    @Test
    fun acceptsOnlyTheRequestedFlightIdentity() {
        val requested = FlightIdentity("W4 6762", "2026-08-13", "AHO")
        assertTrue(
            ProxyFlightStatusProvider.matchesIdentity(
                requested,
                FlightIdentity("W46762", "2026-08-13", "aho"),
            ),
        )
        assertFalse(
            ProxyFlightStatusProvider.matchesIdentity(
                requested,
                FlightIdentity("W46762", "2026-08-13", "FCO"),
            ),
        )
    }

    @Test
    fun failuresBackOffToThirtyMinutes() {
        assertEquals(5 * 60_000L, FlightStatusRefreshPolicy.delayAfterFailure(1))
        assertEquals(30 * 60_000L, FlightStatusRefreshPolicy.delayAfterFailure(99))
    }
}
