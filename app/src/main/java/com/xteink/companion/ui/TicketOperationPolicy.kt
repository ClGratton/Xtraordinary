package com.xteink.companion.ui

/** One owner for mutually exclusive ticket operations and next-send selection. */
internal object TicketOperationPolicy {
    fun canStartSend(ticket: TicketUiState): Boolean =
        !ticket.sendPending && !ticket.removalPending && !(
            ticket.isOnX3 &&
                ticket.deployedPassId == ticket.selectedPass.id &&
                ticket.deployedMode == ticket.mode
            )

    fun canChangeNextSendMode(ticket: TicketUiState): Boolean =
        !ticket.sendPending && !ticket.removalPending

    fun canStartRemoval(ticket: TicketUiState): Boolean =
        ticket.isOnX3 && ticket.deployedPassId != null &&
            !ticket.sendPending && !ticket.removalPending
}
