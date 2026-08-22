package com.xteink.companion.ui

import com.xteink.companion.data.LinkPhase
import com.xteink.companion.data.LinkBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPolicyTest {
    @Test
    fun `remembered device is available but not connected without live transport`() {
        assertTrue(devicePresence(true, false, false) == DevicePresence.Available)
        assertTrue(devicePresence(true, false, true) == DevicePresence.Reconnecting)
        assertTrue(devicePresence(true, false, true, true) == DevicePresence.NeedsBluetoothReset)
        assertTrue(devicePresence(true, false, false, false, true) == DevicePresence.Connecting)
        assertTrue(devicePresence(true, true, false) == DevicePresence.Connected)
        assertTrue(devicePresence(false, false, false) == DevicePresence.None)
    }

    @Test
    fun `transport blockers outrank reconnecting`() {
        assertEquals(
            DevicePresence.BluetoothOff,
            devicePresence(true, false, true, blocker = LinkBlocker.BluetoothOff),
        )
        assertEquals(
            DevicePresence.PermissionRequired,
            devicePresence(true, false, true, blocker = LinkBlocker.NearbyPermissionRequired),
        )
        assertEquals(
            DevicePresence.BluetoothUnavailable,
            devicePresence(true, false, true, blocker = LinkBlocker.BluetoothUnavailable),
        )
    }

    @Test
    fun `foreground pending work has no long blind retry gap`() {
        assertEquals(500L, reconnectDelayMs(attempt = 0, appForeground = true))
        assertEquals(500L, reconnectDelayMs(attempt = 20, appForeground = true))
        assertEquals(1_000L, reconnectDelayMs(attempt = 0, appForeground = false))
        assertEquals(15_000L, reconnectDelayMs(attempt = 20, appForeground = false))
    }

    @Test
    fun `intentional wake does not look like a broken connection`() {
        assertFalse(shouldShowReconnecting(false, LinkPhase.Scanning, intentionalTransportIdle = false))
        assertFalse(shouldShowReconnecting(false, LinkPhase.Connecting, intentionalTransportIdle = false))
    }

    @Test
    fun `unexpected failure remains reconnecting throughout retry`() {
        val failed = shouldShowReconnecting(false, LinkPhase.Error, intentionalTransportIdle = false)
        assertTrue(failed)
        assertTrue(shouldShowReconnecting(failed, LinkPhase.Scanning, intentionalTransportIdle = false))
        assertTrue(shouldShowReconnecting(failed, LinkPhase.Connecting, intentionalTransportIdle = false))
    }

    @Test
    fun `successful or intentional idle transport clears reconnecting`() {
        assertFalse(shouldShowReconnecting(true, LinkPhase.Connected, intentionalTransportIdle = false))
        assertFalse(shouldShowReconnecting(true, LinkPhase.Disconnected, intentionalTransportIdle = true))
    }

    @Test
    fun `quiet foreground probe keeps the remembered device presented as paired`() {
        assertFalse(shouldPresentConnecting(LinkPhase.Scanning.name, quietLinkProbe = true))
        assertFalse(shouldPresentConnecting(LinkPhase.Connecting.name, quietLinkProbe = true))
        assertTrue(shouldPresentConnecting(LinkPhase.Connecting.name, quietLinkProbe = false))
    }
}
