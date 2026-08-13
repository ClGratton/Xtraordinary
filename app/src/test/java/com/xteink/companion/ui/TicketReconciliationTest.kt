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
        val remembered = TicketUiState(
            isOnX3 = true,
            removalPending = true,
            deployedPassId = "dl2048",
            deployedMode = TicketMode.Live,
        )

        val reconciled = reconcileTicketFromCapabilities(
            ticket = remembered,
            ticketPresent = false,
            hasFreshSnapshot = true,
        )

        assertFalse(reconciled.isOnX3)
        assertFalse(reconciled.removalPending)
        assertTrue(reconciled.deployedPassId == null)
        assertTrue(reconciled.deployedMode == null)
    }

    @Test
    fun nextSendModeDoesNotRelabelAcknowledgedMode() {
        val acknowledged = TicketUiState(
            mode = TicketMode.Live,
            isOnX3 = true,
            deployedPassId = "dl2048",
            deployedMode = TicketMode.Static,
        )

        assertTrue(acknowledged.mode == TicketMode.Live)
        assertTrue(acknowledged.deployedMode == TicketMode.Static)
    }

    @Test
    fun selectedAndDeployedPassIdentityRemainIndependent() {
        val ticket = TicketUiState(
            selectedPassId = "az610",
            isOnX3 = true,
            deployedPassId = "dl2048",
            deployedMode = TicketMode.Live,
        )

        assertTrue(ticket.selectedPass.id == "az610")
        assertTrue(ticket.deployedPassId == "dl2048")
        assertTrue(ticket.selectedPass.id != ticket.deployedPassId)
    }

    @Test
    fun desiredLiveSurvivesWithoutAnyDeployedPass() {
        val restored = TicketUiState(mode = TicketMode.Live, isOnX3 = false, deployedMode = null)
        assertTrue(restored.mode == TicketMode.Live)
        assertTrue(restored.deployedMode == null)
    }

    @Test
    fun removalOnlyClearsAcknowledgedMode() {
        val afterClear = TicketUiState(mode = TicketMode.Live, isOnX3 = false, deployedMode = null)
        assertTrue(afterClear.mode == TicketMode.Live)
        assertTrue(afterClear.deployedMode == null)
    }
}
