package com.xteink.companion.ui.components

/** Shared geometry contract for the pass face-change control. */
object PassTurnAffordancePolicy {
    const val actionHorizontalInsetDp = 28
    const val edgeCueSizeDp = 20
    const val objectGlyphSizeDp = 18
    const val minimumCueGapDp = 8
    const val minimumWidthForObjectGlyphDp = 180

    fun actionToEdgeCueGapDp(): Int = actionHorizontalInsetDp - edgeCueSizeDp

    fun keepsObjectAndDirectionCuesSeparated(): Boolean =
        actionToEdgeCueGapDp() >= minimumCueGapDp

    fun showsObjectGlyph(availableWidthDp: Int, isCodeDestination: Boolean): Boolean =
        isCodeDestination && availableWidthDp >= minimumWidthForObjectGlyphDp
}
