package com.xteink.companion.ui

import com.xteink.companion.data.LinkPhase
import com.xteink.companion.data.LinkBlocker

internal enum class DevicePresence {
    None,
    Available,
    Connecting,
    Reconnecting,
    NeedsBluetoothReset,
    BluetoothOff,
    PermissionRequired,
    BluetoothUnavailable,
    Connected,
}

internal fun devicePresence(
    hasManagedDevice: Boolean,
    transportConnected: Boolean,
    reconnecting: Boolean,
    requiresBluetoothReset: Boolean = false,
    connecting: Boolean = false,
    blocker: LinkBlocker? = null,
): DevicePresence = when {
    transportConnected -> DevicePresence.Connected
    requiresBluetoothReset && hasManagedDevice -> DevicePresence.NeedsBluetoothReset
    blocker == LinkBlocker.BluetoothOff && hasManagedDevice -> DevicePresence.BluetoothOff
    blocker == LinkBlocker.NearbyPermissionRequired && hasManagedDevice -> DevicePresence.PermissionRequired
    blocker == LinkBlocker.BluetoothUnavailable && hasManagedDevice -> DevicePresence.BluetoothUnavailable
    reconnecting && hasManagedDevice -> DevicePresence.Reconnecting
    connecting && hasManagedDevice -> DevicePresence.Connecting
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

internal fun reconnectDelayMs(attempt: Int, appForeground: Boolean): Long {
    if (appForeground) return ForegroundReconnectGapMs
    return BackgroundReconnectBackoffMs[
        attempt.coerceIn(0, BackgroundReconnectBackoffMs.lastIndex)
    ]
}

internal fun shouldPresentConnecting(linkPhase: String, quietLinkProbe: Boolean): Boolean =
    !quietLinkProbe && linkPhase in setOf(LinkPhase.Scanning.name, LinkPhase.Connecting.name)

private const val ForegroundReconnectGapMs = 500L
private val BackgroundReconnectBackoffMs = longArrayOf(1_000L, 3_000L, 8_000L, 15_000L)
