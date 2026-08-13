package com.xteink.companion.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keeps both pass faces equal while giving enlarged type room instead of clipping it. */
internal object PassCardLayoutPolicy {
    val normalHeight: Dp = 304.dp

    fun heightFor(fontScale: Float): Dp = when {
        fontScale <= 1f -> normalHeight
        fontScale <= 1.3f -> 400.dp
        else -> 576.dp
    }
}
