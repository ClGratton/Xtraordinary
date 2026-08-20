package com.xteink.companion.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassCardLayoutPolicyTest {
    @Test fun normalCardConsumesAvailableTallPhoneSpaceWithoutAnArbitraryCap() {
        assertEquals(304.dp, PassCardLayoutPolicy.heightFor(1f, 620.dp))
        assertEquals(344.dp, PassCardLayoutPolicy.heightFor(1f, 680.dp))
        assertEquals(424.dp, PassCardLayoutPolicy.heightFor(1f, 760.dp))
        assertEquals(579.dp, PassCardLayoutPolicy.heightFor(1f, 915.dp))
    }

    @Test fun enlargedTextGetsAdditionalStableFaceSpace() {
        assertEquals(400.dp, PassCardLayoutPolicy.heightFor(1.3f, 620.dp))
        assertTrue(PassCardLayoutPolicy.heightFor(2f, 620.dp) > PassCardLayoutPolicy.heightFor(1.3f, 620.dp))
    }
}
