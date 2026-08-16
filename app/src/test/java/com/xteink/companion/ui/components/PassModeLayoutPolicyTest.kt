package com.xteink.companion.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassModeLayoutPolicyTest {
    @Test fun normalChooserKeepsReviewedBounds() {
        assertEquals(152.dp, PassModeLayoutPolicy.optionHeight(1f))
    }

    @Test fun enlargedTextReceivesContentRoomWithoutChangingSiblingGeometry() {
        assertTrue(PassModeLayoutPolicy.optionHeight(1.3f) > PassModeLayoutPolicy.optionHeight(1f))
        assertTrue(PassModeLayoutPolicy.optionHeight(2f) > PassModeLayoutPolicy.optionHeight(1.3f))
    }
}
