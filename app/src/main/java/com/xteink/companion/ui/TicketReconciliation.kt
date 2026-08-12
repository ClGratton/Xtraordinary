package com.xteink.companion.ui

internal fun reconcileTicketFromCapabilities(
    ticket: TicketUiState,
    ticketPresent: Boolean,
    hasFreshSnapshot: Boolean,
): TicketUiState = if (!hasFreshSnapshot) {
    ticket
} else {
    ticket.copy(
        isOnX3 = ticketPresent,
        removalPending = ticketPresent && ticket.removalPending,
    )
}
