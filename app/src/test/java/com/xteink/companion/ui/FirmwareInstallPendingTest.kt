package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test fun managedUpdateNackAllowsOneReplayThenClears() {
        val retry = FirmwareInstallPendingPolicy.afterNack(pending)
        assertNotNull(retry)
        assertEquals(1, retry!!.attempt)
        assertNull(FirmwareInstallPendingPolicy.afterNack(retry))
    }

    @Test fun pendingArtifactCannotBeReplacedUntilTransactionStops() {
        assertFalse(FirmwareInstallPendingPolicy.canChangeSource(FirmwareCheckPhase.Downloading))
        assertFalse(FirmwareInstallPendingPolicy.canChangeSource(FirmwareCheckPhase.Transferring))
        assertFalse(FirmwareInstallPendingPolicy.canChangeSource(FirmwareCheckPhase.Verifying))
        assertTrue(FirmwareInstallPendingPolicy.canChangeSource(FirmwareCheckPhase.Available))
        assertTrue(FirmwareInstallPendingPolicy.canChangeSource(FirmwareCheckPhase.Error))
        assertTrue(FirmwareInstallPendingPolicy.canChangeSource(FirmwareCheckPhase.Complete))
    }
}
