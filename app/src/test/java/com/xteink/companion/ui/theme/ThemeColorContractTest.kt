package com.xteink.companion.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeColorContractTest {
    @Test
    fun quietPassRolesAreTrueGrayscale() {
        listOf(
            QuietPrimary, QuietOnPrimary, QuietPrimaryContainer, QuietOnPrimaryContainer,
            QuietTertiary, QuietOnTertiary, QuietTertiaryContainer, QuietOnTertiaryContainer,
            QuietError, QuietOnError, QuietErrorContainer, QuietOnErrorContainer,
        ).forEach { color -> assertTrue(color.red == color.green && color.green == color.blue) }
    }

    @Test
    fun expressivePrimarySupportsOrdinaryOnPrimaryText() {
        assertTrue(contrastRatio(ExpressivePrimary, ExpressiveOnPrimary) >= 4.5f)
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        fun channel(value: Float) = if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        fun luminance(color: Color) = 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
