package com.xteink.companion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveViewportLayoutPolicyTest {
    @Test
    fun tallerViewportAssignsAllAdditionalHeightToBreathingSurface() {
        val compact = calculateAdaptiveSurfaceHeight(
            viewportHeight = 640f,
            fixedContentHeight = 370f,
            minimumSurfaceHeight = 270f,
        )
        val tall = calculateAdaptiveSurfaceHeight(
            viewportHeight = 760f,
            fixedContentHeight = 370f,
            minimumSurfaceHeight = 270f,
        )

        assertEquals(270f, compact)
        assertEquals(390f, tall)
        assertEquals(120f, tall - compact)
    }

    @Test
    fun shortViewportPreservesSemanticMinimumForScrollableOverflow() {
        val height = calculateAdaptiveSurfaceHeight(
            viewportHeight = 520f,
            fixedContentHeight = 370f,
            minimumSurfaceHeight = 270f,
        )

        assertEquals(270f, height)
    }
}
