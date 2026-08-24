package com.xteink.companion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.xteink.companion.data.FirmwareSource

class FirmwareInstallAvailabilityPolicyTest {
    @Test fun availableManagedBleEnablesInstall() = assertTrue(
        firmwareInstallAvailable(FirmwareCheckPhase.Available, false, true),
    )

    @Test fun availableWithoutTransportStaysDisabled() = assertFalse(
        firmwareInstallAvailable(FirmwareCheckPhase.Available, false, false),
    )

    @Test fun companionUpdateExposesBleAndUsbSeparately() {
        val routes = firmwareInstallRoutes(
            FirmwareCheckPhase.Available,
            FirmwareSource.LocalFile,
            usbConnected = true,
            managedBleFirmwareReady = true,
            selectedVersion = "xtraordinary-v0.2.6-dev51",
        )
        assertTrue(FirmwareInstallRoute.ManagedBle in routes)
        assertTrue(FirmwareInstallRoute.GuardedUsb in routes)
    }

    @Test fun stockUpdateNeverExposesManagedBleRoute() = assertFalse(
        FirmwareInstallRoute.ManagedBle in firmwareInstallRoutes(
            FirmwareCheckPhase.Available,
            FirmwareSource.XteinkStock,
            usbConnected = true,
            managedBleFirmwareReady = true,
            selectedVersion = "XT-V5.1.6",
        ),
    )
}
