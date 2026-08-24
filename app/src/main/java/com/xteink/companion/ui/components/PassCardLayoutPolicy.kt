package com.xteink.companion.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keeps both pass faces equal while giving enlarged type room instead of clipping it. */
internal object PassCardLayoutPolicy {
    val minimumNormalHeight: Dp = 304.dp
    /** Header, deployment status, chooser/action, navigation, and named gaps. */
    val normalScreenChrome: Dp = 336.dp

    fun normalFirstViewportFits(viewportHeight: Dp): Boolean =
        heightFor(1f, viewportHeight) + normalScreenChrome <= viewportHeight

    fun heightFor(fontScale: Float, viewportHeight: Dp): Dp = when {
        // Android's normal 420-dpi Pixel reports 1.15. Treat that as the
        // normal first-viewport layout instead of falling into a fixed 400dp
        // face that leaves the chooser below the navigation boundary.
        fontScale <= 1.2f -> (viewportHeight - normalScreenChrome)
            .coerceAtLeast(minimumNormalHeight)
        fontScale <= 1.3f -> 400.dp
        else -> 576.dp
    }
}
