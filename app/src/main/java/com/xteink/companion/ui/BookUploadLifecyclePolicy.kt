package com.xteink.companion.ui

import com.xteink.companion.data.CompanionCommandRejectedException
import com.xteink.companion.data.CompanionTransportInterruptedException
import kotlinx.coroutines.CancellationException

internal enum class BookUploadFailureDisposition {
    RetryWhenTransportReturns,
    StopAndForget,
}

/** One policy for persistence across BLE, USB, foreground, background, and process recreation. */
internal object BookUploadLifecyclePolicy {
    fun failureDisposition(error: Throwable): BookUploadFailureDisposition = when (error) {
        // Internal maintenance may pause a job. Explicit user cancellation first
        // advances the operation generation, so the stale job never reaches here.
        is CancellationException -> BookUploadFailureDisposition.RetryWhenTransportReturns
        is CompanionTransportInterruptedException -> BookUploadFailureDisposition.RetryWhenTransportReturns
        is CompanionCommandRejectedException -> BookUploadFailureDisposition.StopAndForget
        else -> BookUploadFailureDisposition.StopAndForget
    }
}
