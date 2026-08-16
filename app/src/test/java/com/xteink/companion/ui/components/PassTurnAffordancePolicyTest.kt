package com.xteink.companion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassTurnAffordancePolicyTest {
    @Test
    fun objectAndDirectionCuesHaveDedicatedNonOverlappingRegions() {
        assertEquals(8, PassTurnAffordancePolicy.actionToEdgeCueGapDp())
        assertTrue(PassTurnAffordancePolicy.keepsObjectAndDirectionCuesSeparated())
    }

    @Test
    fun objectGlyphRemainsSmallerThanTheIndependentEdgeCueRegion() {
        assertTrue(
            PassTurnAffordancePolicy.objectGlyphSizeDp <
                PassTurnAffordancePolicy.edgeCueSizeDp,
        )
    }
}
