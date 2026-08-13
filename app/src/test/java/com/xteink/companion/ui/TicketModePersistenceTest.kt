package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketModePersistenceTest {
    @Test fun legacyModeMigratesToBothStatesWhenPresent() = assertEquals(PersistedTicketModes(TicketMode.Static, TicketMode.Static), decodeTicketModes("Static", null, null, true))
    @Test fun selectedLiveRestoresWithoutDeployedTicket() = assertEquals(PersistedTicketModes(TicketMode.Live, null), decodeTicketModes("Static", "Live", null, false))
    @Test fun deployedStaticAndDesiredLiveStayDistinct() = assertEquals(PersistedTicketModes(TicketMode.Live, TicketMode.Static), decodeTicketModes("Static", "Live", "Static", true))
    @Test fun clearingDeployedDoesNotEraseSelected() { val state = decodeTicketModes("Static", "Live", null, false); assertEquals(TicketMode.Live, state.selected); assertNull(state.deployed) }
}
