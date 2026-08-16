package com.xteink.companion.ui.components

/**
 * Decides whether tertiary ticket metadata earns space on the operational face.
 * Import provenance is retained in the model for history/debugging, but is not
 * presented unless metadata changes trust, freshness, or recovery.
 */
internal sealed interface TicketMetadataPresentation {
    data object Hidden : TicketMetadataPresentation
    data object SampleWarning : TicketMetadataPresentation
    data class LiveFresh(val provider: String, val observedAtEpochMs: Long) : TicketMetadataPresentation
    data class LiveUnavailable(val provider: String) : TicketMetadataPresentation
}

internal fun ticketMetadataPresentation(
    isSample: Boolean,
    liveProvider: String,
    liveUpdatedAtEpochMs: Long?,
): TicketMetadataPresentation = when {
    isSample -> TicketMetadataPresentation.SampleWarning
    liveProvider.isBlank() -> TicketMetadataPresentation.Hidden
    liveUpdatedAtEpochMs != null -> TicketMetadataPresentation.LiveFresh(liveProvider, liveUpdatedAtEpochMs)
    else -> TicketMetadataPresentation.LiveUnavailable(liveProvider)
}
