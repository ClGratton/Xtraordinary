package com.xteink.companion.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
