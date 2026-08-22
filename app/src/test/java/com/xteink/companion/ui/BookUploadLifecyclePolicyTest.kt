package com.xteink.companion.ui

import com.xteink.companion.data.CompanionCommandRejectedException
import com.xteink.companion.data.CompanionTransportInterruptedException
import org.junit.Assert.assertEquals
import org.junit.Test

class BookUploadLifecyclePolicyTest {
    @Test
    fun `transport interruption retains durable intent`() {
        assertEquals(
            BookUploadFailureDisposition.RetryWhenTransportReturns,
            BookUploadLifecyclePolicy.failureDisposition(CompanionTransportInterruptedException("disconnected")),
        )
    }

    @Test
    fun `device rejection and source failures never restart automatically`() {
        assertEquals(
            BookUploadFailureDisposition.StopAndForget,
            BookUploadLifecyclePolicy.failureDisposition(CompanionCommandRejectedException("rejected")),
        )
        assertEquals(
            BookUploadFailureDisposition.StopAndForget,
            BookUploadLifecyclePolicy.failureDisposition(IllegalStateException("empty source")),
        )
    }
}
