package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionPresentationStoreTest {
    @Test
    fun storedThemeRestoresByStableEnumName() {
        assertEquals(CompanionVisualTheme.Minimal, decodeCompanionVisualTheme("Minimal"))
    }

    @Test
    fun legacyQuietMigratesToMinimalStyle() {
        assertEquals(CompanionVisualTheme.Minimal, decodeCompanionVisualTheme("Quiet"))
    }

    @Test
    fun unknownThemeFallsBackWithoutCrashing() {
        assertEquals(CompanionVisualTheme.Expressive, decodeCompanionVisualTheme("RemovedTheme"))
    }

    @Test
    fun legacyQuietMigratesToDarkColorMode() {
        assertEquals(CompanionColorMode.Dark, decodeCompanionColorMode(null, "Quiet"))
    }

    @Test
    fun storedColorModeWinsOverLegacyTheme() {
        assertEquals(CompanionColorMode.Light, decodeCompanionColorMode("Light", "Quiet"))
    }
}
