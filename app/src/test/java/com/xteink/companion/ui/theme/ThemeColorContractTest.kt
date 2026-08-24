package com.xteink.companion.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.xteink.companion.ui.CompanionColorMode
import com.xteink.companion.ui.CompanionColorModeBoundary
import com.xteink.companion.ui.companionColorModeForSettledPage
import com.xteink.companion.ui.components.DefaultMagneticSwipe
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

    @Test
    fun displayedProgressMapsContinuouslyFromLightTowardDark() {
        assertTrue(DefaultMagneticSwipe.displayedProgress(0.25f, 0f) == 0.25f)
        val resisted = DefaultMagneticSwipe.displayedProgress(0.5f, 1f)
        assertTrue(resisted in 0.08f..0.5f)
    }

    @Test
    fun onlySettledPagesMapToPersistableColorModes() {
        assertTrue(companionColorModeForSettledPage(0) == CompanionColorMode.Light)
        assertTrue(companionColorModeForSettledPage(1) == CompanionColorMode.Dark)
        assertTrue(companionColorModeForSettledPage(-1) == CompanionColorMode.Light)
        assertTrue(companionColorModeForSettledPage(9) == CompanionColorMode.Dark)
    }

    @Test
    fun contentPolarityUsesTheSameHystereticBoundaryInBothDirections() {
        assertTrue(CompanionColorModeBoundary.modeForDisplayedPosition(CompanionColorMode.Light, 0.61f) == CompanionColorMode.Light)
        assertTrue(CompanionColorModeBoundary.modeForDisplayedPosition(CompanionColorMode.Light, 0.62f) == CompanionColorMode.Dark)
        assertTrue(CompanionColorModeBoundary.modeForDisplayedPosition(CompanionColorMode.Dark, 0.39f) == CompanionColorMode.Dark)
        assertTrue(CompanionColorModeBoundary.modeForDisplayedPosition(CompanionColorMode.Dark, 0.38f) == CompanionColorMode.Light)
    }

    @Test
    fun intermediatePaletteKeepsPrimaryAndSurfaceTextContrasting() {
        val scheme = interpolateColorScheme(lightColorScheme(), darkColorScheme(), 0.5f)
        assertTrue(contrastRatio(scheme.primary, scheme.onPrimary) >= 4.5f)
        assertTrue(contrastRatio(scheme.surface, scheme.onSurface) >= 4.5f)
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        fun channel(value: Float) = if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        fun luminance(color: Color) = 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
