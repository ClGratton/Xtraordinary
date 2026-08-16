package com.xteink.companion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketMetadataPresentationTest {
    @Test fun importProvenanceDoesNotOccupyTheOperationalFace() {
        val presentation = ticketMetadataPresentation(
            isSample = false,
            liveProvider = "",
            liveUpdatedAtEpochMs = null,
        )

        assertEquals(TicketMetadataPresentation.Hidden, presentation)
    }

    @Test fun freshLiveProviderKeepsActionableFreshness() {
        val presentation = ticketMetadataPresentation(
            isSample = false,
            liveProvider = "Flight status",
            liveUpdatedAtEpochMs = 1234L,
        )

        assertEquals(TicketMetadataPresentation.LiveFresh("Flight status", 1234L), presentation)
    }

    @Test fun unavailableLiveProviderKeepsAnHonestWarning() {
        val presentation = ticketMetadataPresentation(
            isSample = false,
            liveProvider = "Flight status",
            liveUpdatedAtEpochMs = null,
        )

        assertEquals(TicketMetadataPresentation.LiveUnavailable("Flight status"), presentation)
    }

    @Test fun sampleWarningAlwaysWins() {
        assertTrue(
            ticketMetadataPresentation(
                isSample = true,
                liveProvider = "Flight status",
                liveUpdatedAtEpochMs = 1234L,
            ) is TicketMetadataPresentation.SampleWarning,
        )
    }
}
