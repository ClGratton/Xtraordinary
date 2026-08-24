package com.xteink.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FirmwareTransportSelectionTest {
    @Test fun usbTakeoverRemainsPrimaryEvenWhenBleAdvertisesManagedUpdates() = assertEquals(
        FirmwareTransport.GuardedUsb,
        selectFirmwareTransport(true, true, true),
    )

    @Test fun usbRemainsGuardedFallbackWithoutFreshBleSupport() = assertEquals(
        FirmwareTransport.GuardedUsb,
        selectFirmwareTransport(true, false, false),
    )

    @Test fun disconnectedX3QueuesManagedBleReconnect() = assertEquals(
        FirmwareTransport.ManagedBle,
        selectFirmwareTransport(false, false, false),
    )
}
