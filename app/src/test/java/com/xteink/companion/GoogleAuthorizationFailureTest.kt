package com.xteink.companion

import com.google.android.gms.common.api.CommonStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleAuthorizationFailureTest {
    @Test
    fun developerErrorExplainsBuildConfiguration() {
        assertEquals(
            "Google backup isn't configured for this build",
            googleAuthorizationFailureMessage(CommonStatusCodes.DEVELOPER_ERROR),
        )
    }

    @Test
    fun canceledAuthorizationIsNotReportedAsAConfigurationFailure() {
        assertEquals(
            "Google backup authorization was canceled",
            googleAuthorizationFailureMessage(CommonStatusCodes.CANCELED),
        )
    }

    @Test
    fun unknownFailureKeepsTheGeneralFallback() {
        assertEquals(
            "Google backup was not authorized",
            googleAuthorizationFailureMessage(null),
        )
    }
}
