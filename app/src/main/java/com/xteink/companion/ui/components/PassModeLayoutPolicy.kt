package com.xteink.companion.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stable chooser bounds at a given text scale; both options always share the same height. */
internal object PassModeLayoutPolicy {
    fun optionHeight(fontScale: Float): Dp = when {
        fontScale >= 1.6f -> 224.dp
        fontScale > 1.15f -> 176.dp
        else -> 152.dp
    }
}
