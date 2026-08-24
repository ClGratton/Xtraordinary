package com.xteink.companion.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirmwareInstallPendingTest {
    private val pending = PendingFirmwareInstall("X3", "dev38", 42, "ab".repeat(32))

    @Test fun disconnectAfterSelectionRetainsIntentForReplay() {
        assertEquals(pending, FirmwareInstallPendingPolicy.afterDisconnect(pending))
        assertTrue(FirmwareInstallPendingPolicy.shouldReplay(pending, true, true))
    }

    @Test fun leaseAckDoesNotClearIntentBeforeBeginAndDuplicateIsSame() {
        assertEquals(pending, FirmwareInstallPendingPolicy.afterLeaseAck(pending))
        assertTrue(FirmwareInstallPendingPolicy.isSame(pending, pending.copy(sha256 = pending.sha256.uppercase())))
        assertFalse(FirmwareInstallPendingPolicy.shouldReplay(pending, false, true))
    }

    @Test fun physicalConfirmNackAllowsOneReplayThenClears() {
        val retry = FirmwareInstallPendingPolicy.afterNack(pending)
        assertNotNull(retry)
        assertEquals(1, retry.attempt)
        assertNull(FirmwareInstallPendingPolicy.afterNack(retry))
    }
}
