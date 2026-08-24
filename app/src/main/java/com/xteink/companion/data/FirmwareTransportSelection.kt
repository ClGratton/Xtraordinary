package com.xteink.companion.data

/** Selects a firmware transport without silently crossing the BLE/USB safety boundary. */
enum class FirmwareTransport { ManagedBle, GuardedUsb, Unavailable }

internal fun selectFirmwareTransport(
    usbConnected: Boolean,
    bleConnected: Boolean,
    bleSupportsFirmwareUpdate: Boolean,
): FirmwareTransport = when {
    bleConnected && bleSupportsFirmwareUpdate -> FirmwareTransport.ManagedBle
    usbConnected -> FirmwareTransport.GuardedUsb
    bleConnected -> FirmwareTransport.Unavailable
    else -> FirmwareTransport.ManagedBle
}
