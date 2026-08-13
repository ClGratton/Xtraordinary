package com.xteink.companion.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassCardLayoutPolicyTest {
    @Test fun normalCardRemainsCompact() = assertEquals(304.dp, PassCardLayoutPolicy.heightFor(1f))
    @Test fun enlargedTextGetsAdditionalStableFaceSpace() {
        assertTrue(PassCardLayoutPolicy.heightFor(1.3f) > PassCardLayoutPolicy.normalHeight)
        assertTrue(PassCardLayoutPolicy.heightFor(2f) > PassCardLayoutPolicy.heightFor(1.3f))
    }
}
