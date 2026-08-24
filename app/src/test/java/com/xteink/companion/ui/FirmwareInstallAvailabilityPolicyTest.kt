package com.xteink.companion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareInstallAvailabilityPolicyTest {
    @Test fun availableManagedBleEnablesInstall() = assertTrue(
        firmwareInstallAvailable(FirmwareCheckPhase.Available, false, true),
    )

    @Test fun availableWithoutTransportStaysDisabled() = assertFalse(
        firmwareInstallAvailable(FirmwareCheckPhase.Available, false, false),
    )
}
