package com.xteink.companion.ui

internal fun firmwareInstallAvailable(phase: FirmwareCheckPhase, usbConnected: Boolean, managedBleFirmwareReady: Boolean): Boolean =
    (phase == FirmwareCheckPhase.Available || phase == FirmwareCheckPhase.UpToDate) &&
        (usbConnected || managedBleFirmwareReady)
