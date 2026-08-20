package com.xteink.companion.ui.components

import kotlin.math.max

internal data class SetupActionPlacement(
    val layoutHeight: Int,
    val actionsY: Int,
)

/**
 * Anchors an onboarding page's action group to the common bottom edge when
 * everything fits. If content grows, the same calculation expands the page
 * and preserves the minimum semantic gap so the outer container can scroll.
 */
internal fun calculateSetupActionPlacement(
    viewportHeight: Int,
    contentHeight: Int,
    actionsHeight: Int,
    minimumGap: Int,
): SetupActionPlacement {
    require(viewportHeight >= 0)
    require(contentHeight >= 0)
    require(actionsHeight >= 0)
    require(minimumGap >= 0)

    val requiredHeight = contentHeight + minimumGap + actionsHeight
    val layoutHeight = max(viewportHeight, requiredHeight)
    return SetupActionPlacement(
        layoutHeight = layoutHeight,
        actionsY = layoutHeight - actionsHeight,
    )
}
