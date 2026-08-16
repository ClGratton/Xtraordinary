package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketOperationPolicyTest {
    @Test fun pendingSendFreezesItsIdentityWhenCarouselSelectionChanges() {
        val initial = TicketUiState()
        val pending = PendingTicketOperation(initial.selectedPass.id, TicketMode.Static)
        val changedSelection = initial.copy(
            selectedPassId = initial.passes.last().id,
            mode = TicketMode.Live,
            sendPending = true,
            pendingOperation = pending,
        )

        assertEquals(pending, changedSelection.pendingOperation)
        assertEquals(TicketMode.Static, changedSelection.pendingOperation?.mode)
        assertFalse(TicketOperationPolicy.canChangeNextSendMode(changedSelection))
    }

    @Test fun sendAndClearCanNeverOverlap() {
        assertFalse(TicketOperationPolicy.canStartSend(TicketUiState(removalPending = true)))
        assertFalse(TicketOperationPolicy.canStartSend(TicketUiState(sendPending = true)))
        assertTrue(TicketOperationPolicy.canStartSend(TicketUiState()))

        val deployed = TicketUiState().let { state ->
            state.copy(isOnX3 = true, deployedPassId = state.selectedPass.id)
        }
        assertTrue(TicketOperationPolicy.canStartRemoval(deployed))
        assertFalse(TicketOperationPolicy.canStartRemoval(deployed.copy(sendPending = true)))
        assertFalse(TicketOperationPolicy.canStartRemoval(deployed.copy(removalPending = true)))
    }

    @Test fun stateOwnerCanRejectModeChangesDuringEitherOperation() {
        assertFalse(TicketOperationPolicy.canChangeNextSendMode(TicketUiState(sendPending = true)))
        assertFalse(TicketOperationPolicy.canChangeNextSendMode(TicketUiState(removalPending = true)))
        assertTrue(TicketOperationPolicy.canChangeNextSendMode(TicketUiState()))
    }
}
