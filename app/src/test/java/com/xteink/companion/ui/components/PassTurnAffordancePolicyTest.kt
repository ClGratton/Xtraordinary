package com.xteink.companion.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

class PassTurnAffordancePolicyTest {
    @Test
    fun optionalObjectGlyphYieldsToTheLabelAtCompactWidth() {
        assertTrue(!PassTurnAffordancePolicy.showsObjectGlyph(150, isCodeDestination = true))
        assertTrue(PassTurnAffordancePolicy.showsObjectGlyph(200, isCodeDestination = true))
        assertTrue(!PassTurnAffordancePolicy.showsObjectGlyph(200, isCodeDestination = false))
    }
}
