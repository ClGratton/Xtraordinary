package com.xteink.companion.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared bounds for the expanding OTA/USB choices at each text scale. */
internal object FirmwareTransportLayoutPolicy {
    fun optionHeight(fontScale: Float): Dp = when {
        fontScale >= 1.6f -> 340.dp
        fontScale > 1.2f -> 248.dp
        else -> 190.dp
    }
}
