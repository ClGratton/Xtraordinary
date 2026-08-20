package com.xteink.companion.ui.components

import kotlin.math.max

/**
 * Assigns the first viewport's remaining height to the surface that is meant
 * to breathe. Short viewports retain the semantic minimum and let the owning
 * screen's existing scroll container expose the overflow.
 */
internal fun calculateAdaptiveSurfaceHeight(
    viewportHeight: Float,
    fixedContentHeight: Float,
    minimumSurfaceHeight: Float,
): Float {
    require(viewportHeight >= 0f)
    require(fixedContentHeight >= 0f)
    require(minimumSurfaceHeight >= 0f)

    return max(minimumSurfaceHeight, viewportHeight - fixedContentHeight)
}
