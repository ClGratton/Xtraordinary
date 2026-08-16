package com.xteink.companion.ui.components

/** Shared geometry contract for the pass face-change control. */
object PassTurnAffordancePolicy {
    const val objectGlyphSizeDp = 18
    const val minimumWidthForObjectGlyphDp = 180

    fun showsObjectGlyph(availableWidthDp: Int, isCodeDestination: Boolean): Boolean =
        isCodeDestination && availableWidthDp >= minimumWidthForObjectGlyphDp
}
