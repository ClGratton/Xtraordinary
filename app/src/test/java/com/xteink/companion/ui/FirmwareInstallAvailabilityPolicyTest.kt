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

    @Test fun pairedCompanionKeepsBleRouteVisibleWhileTransportIsIdle() {
        val routes = firmwareInstallRoutes(
            FirmwareCheckPhase.Available,
            FirmwareSource.Xtraordinary,
            usbConnected = false,
            managedBleFirmwareReady = false,
            selectedVersion = "xtraordinary-v0.2.6-dev51",
            managedDeviceKnown = true,
        )
        assertTrue(FirmwareInstallRoute.ManagedBle in routes)
        assertTrue(FirmwareInstallRoute.GuardedUsb in routes)
    }

    @Test fun olderDefaultCatalogCannotExposeInstallRoutes() {
        val routes = firmwareInstallRoutes(
            FirmwareCheckPhase.UpToDate,
            FirmwareSource.Xtraordinary,
            usbConnected = true,
            managedBleFirmwareReady = true,
            selectedVersion = "xtraordinary-v0.2.4",
            managedDeviceKnown = true,
            candidateIsInstallable = false,
        )
        assertTrue(routes.isEmpty())
    }

    @Test fun equalCatalogCanExposeReinstallRoutes() {
        val routes = firmwareInstallRoutes(
            FirmwareCheckPhase.UpToDate,
            FirmwareSource.Xtraordinary,
            usbConnected = false,
            managedBleFirmwareReady = false,
            selectedVersion = "xtraordinary-v0.2.6-dev51",
            managedDeviceKnown = true,
            candidateIsInstallable = true,
        )
        assertTrue(FirmwareInstallRoute.ManagedBle in routes)
    }

    @Test fun explicitOlderLocalFileRemainsInstallable() {
        val routes = firmwareInstallRoutes(
            FirmwareCheckPhase.UpToDate,
            FirmwareSource.LocalFile,
            usbConnected = false,
            managedBleFirmwareReady = false,
            selectedVersion = "xtraordinary-v0.2.4",
            managedDeviceKnown = true,
            candidateIsInstallable = true,
        )
        assertTrue(FirmwareInstallRoute.ManagedBle in routes)
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

    @Test fun nonCompanionLocalSelectionRemainsUsbOnly() {
        val routes = firmwareInstallRoutes(
            FirmwareCheckPhase.Available,
            FirmwareSource.LocalFile,
            usbConnected = false,
            managedBleFirmwareReady = true,
            selectedVersion = "XT-V5.1.6",
            managedDeviceKnown = true,
        )
        assertFalse(FirmwareInstallRoute.ManagedBle in routes)
        assertTrue(FirmwareInstallRoute.GuardedUsb in routes)
    }

    @Test fun crossPointUpdateNeverExposesManagedBleRoute() = assertFalse(
        FirmwareInstallRoute.ManagedBle in firmwareInstallRoutes(
            FirmwareCheckPhase.Available,
            FirmwareSource.CrossPoint,
            usbConnected = false,
            managedBleFirmwareReady = true,
            selectedVersion = "v1.2.3",
            managedDeviceKnown = true,
        ),
    )
}
