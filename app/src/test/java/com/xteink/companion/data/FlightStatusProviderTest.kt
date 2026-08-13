package com.xteink.companion.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightStatusProviderTest {
    @Test
    fun acceptsOnlyTheRequestedFlightIdentity() {
        val requested = FlightIdentity("W4 6762", "2026-08-13", "AHO")
        val accepted = ProxyFlightStatusProvider.parseSnapshot(
            JSONObject(
                """{
                    "flightNumber":"W46762",
                    "operatingDate":"2026-08-13",
                    "origin":"AHO",
                    "status":"Delayed",
                    "departureTime":"13:35",
                    "arrivalTime":"15:10",
                    "gate":"7",
                    "terminal":"1",
                    "delayMinutes":25,
                    "observedAtEpochMs":1786620000000,
                    "provider":"AeroAPI proxy"
                }""",
            ),
            requested,
        )
        assertEquals(25, accepted?.delayMinutes)
        assertEquals("15:10", accepted?.arrivalTime)

        val wrongOrigin = JSONObject(
            """{"flightNumber":"W46762","operatingDate":"2026-08-13","origin":"FCO","observedAtEpochMs":1}""",
        )
        assertNull(ProxyFlightStatusProvider.parseSnapshot(wrongOrigin, requested))
    }

    @Test
    fun failuresBackOffToThirtyMinutes() {
        assertEquals(5 * 60_000L, FlightStatusRefreshPolicy.delayAfterFailure(1))
        assertEquals(30 * 60_000L, FlightStatusRefreshPolicy.delayAfterFailure(99))
    }
}
