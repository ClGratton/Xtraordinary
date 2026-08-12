package com.xteink.companion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketReconciliationTest {
    @Test
    fun stalePreSendSnapshotCannotUndoAcknowledgedTicket() {
        val acknowledged = TicketUiState(isOnX3 = true)

        val reconciled = reconcileTicketFromCapabilities(
            ticket = acknowledged,
            ticketPresent = false,
            hasFreshSnapshot = false,
        )

        assertTrue(reconciled.isOnX3)
    }

    @Test
    fun freshReconnectSnapshotRemainsAuthoritative() {
        val remembered = TicketUiState(isOnX3 = true, removalPending = true)

        val reconciled = reconcileTicketFromCapabilities(
            ticket = remembered,
            ticketPresent = false,
            hasFreshSnapshot = true,
        )

        assertFalse(reconciled.isOnX3)
        assertFalse(reconciled.removalPending)
    }
}
