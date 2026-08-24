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
    managedDeviceKnown: Boolean = false,
): Set<FirmwareInstallRoute> {
    if (phase != FirmwareCheckPhase.Available && phase != FirmwareCheckPhase.UpToDate) return emptySet()
    val routes = linkedSetOf<FirmwareInstallRoute>()
    // USB is an explicit, always-visible recovery/reinstall route. Its action
    // explains the cable requirement when the device is not present.
    routes += FirmwareInstallRoute.GuardedUsb
    val companion = source == FirmwareSource.Xtraordinary ||
        (source == FirmwareSource.LocalFile && selectedVersion?.startsWith("xtraordinary-", ignoreCase = true) == true)
    // A paired companion owns a reusable logical device identity even while its
    // short-lived GATT transport is idle. Visibility is not acceptance: the
    // tap must still wait for fresh capabilities before BEGIN_FIRMWARE.
    if (companion && (managedBleFirmwareReady || managedDeviceKnown)) {
        routes += FirmwareInstallRoute.ManagedBle
    }
    return routes
}
