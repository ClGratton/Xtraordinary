package com.xteink.companion.data

/** Device-work lease only; never a persisted production power setting. */
internal object X3MaintenanceLeaseContract {
    const val MaxSeconds = 600
    const val DefaultSeconds = 600

    fun boundedSeconds(seconds: Int): Int = seconds.coerceIn(1, MaxSeconds)
}
