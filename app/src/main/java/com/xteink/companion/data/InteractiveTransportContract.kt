package com.xteink.companion.data

/** Shared timing contract for any app surface that needs sustained low-latency BLE. */
internal object InteractiveTransportContract {
    const val LeaseSeconds = 120
    const val RenewalIntervalMs = 90_000L
}
