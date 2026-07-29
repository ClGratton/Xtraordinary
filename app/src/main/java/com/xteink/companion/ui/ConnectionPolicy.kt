package com.xteink.companion.ui

import com.xteink.companion.data.LinkPhase
import com.xteink.companion.protocol.DeviceActivity
import com.xteink.companion.protocol.PowerSyncConfig

internal enum class DeviceConnectionPresentation {
    None,
    Offline,
    Reconnecting,
    Connected,
    Reading,
    Sleeping,
}

internal fun deviceConnectionPresentation(
    hasManagedDevice: Boolean,
    transportConnected: Boolean,
    reconnecting: Boolean,
    activity: DeviceActivity? = null,
    lowPowerGraceExpired: Boolean = false,
): DeviceConnectionPresentation = when {
    activity == DeviceActivity.Sleeping -> DeviceConnectionPresentation.Sleeping
    activity == DeviceActivity.Reading && !lowPowerGraceExpired -> DeviceConnectionPresentation.Reading
    transportConnected -> DeviceConnectionPresentation.Connected
    reconnecting -> DeviceConnectionPresentation.Reconnecting
    hasManagedDevice -> DeviceConnectionPresentation.Offline
    else -> DeviceConnectionPresentation.None
}

internal fun shouldShowReconnecting(
    previous: Boolean,
    phase: LinkPhase,
    intentionalTransportIdle: Boolean,
    activity: DeviceActivity? = null,
): Boolean = when {
    activity == DeviceActivity.Reading || activity == DeviceActivity.Sleeping -> false
    intentionalTransportIdle || phase == LinkPhase.Connected -> false
    phase == LinkPhase.Error -> false
    previous && phase in setOf(LinkPhase.Disconnected, LinkPhase.Scanning, LinkPhase.Connecting) -> true
    else -> false
}

internal fun reconnectDelayMs(
    activity: DeviceActivity?,
    config: PowerSyncConfig,
    attempt: Int,
): Long = when (activity) {
    DeviceActivity.Sleeping -> Long.MAX_VALUE
    DeviceActivity.Reading -> config.slowPollSeconds * 1_000L
    else -> {
        val normalDelay = config.normalPollSeconds * 1_000L
        val multiplier = 1L shl attempt.coerceIn(0, 3)
        (normalDelay * multiplier).coerceAtMost(2 * 60_000L)
    }
}

internal fun isWithinExpectedSilence(
    activity: DeviceActivity?,
    lastStatusAtEpochMs: Long?,
    nowEpochMs: Long,
    config: PowerSyncConfig,
): Boolean {
    if (activity == DeviceActivity.Sleeping) return true
    if (activity != DeviceActivity.Reading || lastStatusAtEpochMs == null) return false
    return nowEpochMs - lastStatusAtEpochMs <= config.slowPollSeconds * 1_000L + 20_000L
}
