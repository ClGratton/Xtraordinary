package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FirmwareInstallVerificationPolicyTest {
    @Test
    fun transferAckCannotCompleteBeforeExactCapabilities() {
        assertEquals(
            FirmwareCheckPhase.Verifying,
            firmwareVerificationPhaseAfterCapabilities(
                phase = FirmwareCheckPhase.Verifying,
                pendingVersion = "xtraordinary-v0.2.6-dev52",
                capabilitiesVersion = "xtraordinary-v0.2.4",
            ),
        )
        assertEquals(
            FirmwareCheckPhase.Verifying,
            firmwareVerificationPhaseAfterCapabilities(
                phase = FirmwareCheckPhase.Verifying,
                pendingVersion = "xtraordinary-v0.2.6-dev52",
                capabilitiesVersion = null,
            ),
        )
    }

    @Test
    fun exactCapabilitiesCompletePendingInstall() {
        assertEquals(
            FirmwareCheckPhase.Complete,
            firmwareVerificationPhaseAfterCapabilities(
                phase = FirmwareCheckPhase.Verifying,
                pendingVersion = "xtraordinary-v0.2.6-dev52",
                capabilitiesVersion = "xtraordinary-v0.2.6-dev52",
            ),
        )
    }

    @Test
    fun unrelatedPhaseRemainsUnchanged() {
        assertEquals(
            FirmwareCheckPhase.Transferring,
            firmwareVerificationPhaseAfterCapabilities(
                phase = FirmwareCheckPhase.Transferring,
                pendingVersion = "xtraordinary-v0.2.6-dev52",
                capabilitiesVersion = "xtraordinary-v0.2.6-dev52",
            ),
        )
    }
}
