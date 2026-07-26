package com.xteink.companion.ui

import com.xteink.companion.data.LinkPhase

internal fun shouldShowReconnecting(
    previous: Boolean,
    phase: LinkPhase,
    intentionalTransportIdle: Boolean,
): Boolean = when {
    intentionalTransportIdle || phase == LinkPhase.Connected -> false
    phase == LinkPhase.Error -> true
    previous && phase in setOf(LinkPhase.Disconnected, LinkPhase.Scanning, LinkPhase.Connecting) -> true
    else -> false
}
