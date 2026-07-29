package com.xteink.companion.ui

import com.xteink.companion.data.LinkPhase
import com.xteink.companion.protocol.DeviceActivity
import com.xteink.companion.protocol.PowerSyncConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPolicyTest {
    @Test
    fun `remembered device is never presented as connected without transport`() {
        assertEquals(
            DeviceConnectionPresentation.Offline,
            deviceConnectionPresentation(
                hasManagedDevice = true,
                transportConnected = false,
                reconnecting = false,
            ),
        )
        assertEquals(
            DeviceConnectionPresentation.Reconnecting,
            deviceConnectionPresentation(
                hasManagedDevice = true,
                transportConnected = false,
                reconnecting = true,
            ),
        )
    }

    @Test
    fun `live transport is the only connected presentation`() {
        assertEquals(
            DeviceConnectionPresentation.Connected,
            deviceConnectionPresentation(
                hasManagedDevice = true,
                transportConnected = true,
                reconnecting = true,
            ),
        )
    }

    @Test
    fun `intentional wake does not look like a broken connection`() {
        assertFalse(shouldShowReconnecting(false, LinkPhase.Scanning, intentionalTransportIdle = false))
        assertFalse(shouldShowReconnecting(false, LinkPhase.Connecting, intentionalTransportIdle = false))
    }

    @Test
    fun `failed link is offline until an actual retry begins`() {
        val failed = shouldShowReconnecting(false, LinkPhase.Error, intentionalTransportIdle = false)
        assertFalse(failed)
        assertTrue(shouldShowReconnecting(true, LinkPhase.Scanning, intentionalTransportIdle = false))
        assertTrue(shouldShowReconnecting(true, LinkPhase.Connecting, intentionalTransportIdle = false))
    }

    @Test
    fun `successful or intentional idle transport clears reconnecting`() {
        assertFalse(shouldShowReconnecting(true, LinkPhase.Connected, intentionalTransportIdle = false))
        assertFalse(shouldShowReconnecting(true, LinkPhase.Disconnected, intentionalTransportIdle = true))
    }

    @Test
    fun `reading and sleeping are device states rather than disconnect errors`() {
        assertEquals(
            DeviceConnectionPresentation.Reading,
            deviceConnectionPresentation(
                hasManagedDevice = true,
                transportConnected = false,
                reconnecting = true,
                activity = DeviceActivity.Reading,
            ),
        )
        assertEquals(
            DeviceConnectionPresentation.Sleeping,
            deviceConnectionPresentation(
                hasManagedDevice = true,
                transportConnected = false,
                reconnecting = true,
                activity = DeviceActivity.Sleeping,
            ),
        )
        assertFalse(
            shouldShowReconnecting(
                previous = true,
                phase = LinkPhase.Error,
                intentionalTransportIdle = false,
                activity = DeviceActivity.Reading,
            ),
        )
    }

    @Test
    fun `reading grace follows configured slow check in`() {
        val config = PowerSyncConfig(normalPollSeconds = 15, slowPollSeconds = 600, sleepTimeoutMinutes = 5)
        assertTrue(isWithinExpectedSilence(DeviceActivity.Reading, 1_000L, 601_000L, config))
        assertFalse(isWithinExpectedSilence(DeviceActivity.Reading, 1_000L, 622_000L, config))
        assertEquals(600_000L, reconnectDelayMs(DeviceActivity.Reading, config, attempt = 3))
        assertEquals(Long.MAX_VALUE, reconnectDelayMs(DeviceActivity.Sleeping, config, attempt = 0))
    }
}
