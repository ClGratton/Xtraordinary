package com.xteink.companion.ui.components

import com.xteink.companion.ui.DevicePresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceConnectionPresentationPolicyTest {
    @Test fun passiveProbeFailureIsNotRepeatedForManagedAvailableDevice() {
        assertNull(managedDeviceStatusMessage(DevicePresence.Available, "No companion device found"))
    }

    @Test fun actionableManagedDeviceMessageRemainsVisible() {
        assertEquals(
            "Nearby devices permission is required",
            managedDeviceStatusMessage(
                DevicePresence.PermissionRequired,
                "Nearby devices permission is required",
            ),
        )
    }
}
