package com.xteink.companion.ui

import com.xteink.companion.data.FirmwareSource

internal enum class FirmwareInstallRoute { ManagedBle, GuardedUsb }

internal fun firmwareInstallAvailable(phase: FirmwareCheckPhase, usbConnected: Boolean, managedBleFirmwareReady: Boolean): Boolean =
    (phase == FirmwareCheckPhase.Available || phase == FirmwareCheckPhase.UpToDate) &&
        (usbConnected || managedBleFirmwareReady)

internal fun firmwareInstallRoutes(
    phase: FirmwareCheckPhase,
    source: FirmwareSource,
    usbConnected: Boolean,
    managedBleFirmwareReady: Boolean,
    selectedVersion: String?,
): Set<FirmwareInstallRoute> {
    if (phase != FirmwareCheckPhase.Available && phase != FirmwareCheckPhase.UpToDate) return emptySet()
    val routes = linkedSetOf<FirmwareInstallRoute>()
    if (usbConnected) routes += FirmwareInstallRoute.GuardedUsb
    val companion = source == FirmwareSource.Xtraordinary ||
        (source == FirmwareSource.LocalFile && selectedVersion?.startsWith("xtraordinary-", ignoreCase = true) == true)
    if (companion && managedBleFirmwareReady) routes += FirmwareInstallRoute.ManagedBle
    return routes
}
