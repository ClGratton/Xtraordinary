package com.xteink.companion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupActionLayoutPolicyTest {
    @Test
    fun actionsAnchorToViewportBottomWhenContentFits() {
        val placement = calculateSetupActionPlacement(
            viewportHeight = 600,
            contentHeight = 300,
            actionsHeight = 120,
            minimumGap = 16,
        )

        assertEquals(600, placement.layoutHeight)
        assertEquals(480, placement.actionsY)
    }

    @Test
    fun pageExpandsAndPreservesMinimumGapWhenContentOverflows() {
        val placement = calculateSetupActionPlacement(
            viewportHeight = 400,
            contentHeight = 300,
            actionsHeight = 120,
            minimumGap = 16,
        )

        assertEquals(436, placement.layoutHeight)
        assertEquals(316, placement.actionsY)
    }
}
