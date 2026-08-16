package com.xteink.companion.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassCardLayoutPolicyTest {
    @Test fun normalCardUsesAvailableTallPhoneSpaceWithinStableBounds() {
        assertEquals(304.dp, PassCardLayoutPolicy.heightFor(1f, 620.dp))
        assertEquals(344.dp, PassCardLayoutPolicy.heightFor(1f, 680.dp))
        assertEquals(352.dp, PassCardLayoutPolicy.heightFor(1f, 760.dp))
    }

    @Test fun enlargedTextGetsAdditionalStableFaceSpace() {
        assertTrue(PassCardLayoutPolicy.heightFor(1.3f, 620.dp) > PassCardLayoutPolicy.maximumNormalHeight)
        assertTrue(PassCardLayoutPolicy.heightFor(2f, 620.dp) > PassCardLayoutPolicy.heightFor(1.3f, 620.dp))
    }
}
