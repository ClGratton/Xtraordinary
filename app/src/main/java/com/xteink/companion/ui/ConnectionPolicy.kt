package com.xteink.companion.ui

import com.xteink.companion.data.LinkPhase

internal enum class DevicePresence {
    None,
    Available,
    Reconnecting,
    Connected,
}

internal fun devicePresence(
    hasManagedDevice: Boolean,
    transportConnected: Boolean,
    reconnecting: Boolean,
): DevicePresence = when {
    transportConnected -> DevicePresence.Connected
    reconnecting && hasManagedDevice -> DevicePresence.Reconnecting
    hasManagedDevice -> DevicePresence.Available
    else -> DevicePresence.None
}

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
