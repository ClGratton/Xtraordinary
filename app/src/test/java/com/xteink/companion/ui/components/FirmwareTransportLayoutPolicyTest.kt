package com.xteink.companion.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareTransportLayoutPolicyTest {
    @Test fun normalScalePreservesReviewedChoiceHeight() {
        assertEquals(190.dp, FirmwareTransportLayoutPolicy.optionHeight(1f))
    }

    @Test fun largeTextReceivesMonotonicChoiceRoom() {
        val normal = FirmwareTransportLayoutPolicy.optionHeight(1f)
        val large = FirmwareTransportLayoutPolicy.optionHeight(1.3f)
        val huge = FirmwareTransportLayoutPolicy.optionHeight(2f)
        assertEquals(248.dp, large)
        assertEquals(340.dp, huge)
        assertTrue(large > normal)
        assertTrue(huge > large)
    }
}
