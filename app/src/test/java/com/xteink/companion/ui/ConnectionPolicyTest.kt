package com.xteink.companion.ui

import com.xteink.companion.data.LinkPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPolicyTest {
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
}
