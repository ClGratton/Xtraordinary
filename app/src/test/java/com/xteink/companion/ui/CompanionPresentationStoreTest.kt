package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionPresentationStoreTest {
    @Test
    fun storedThemeRestoresByStableEnumName() {
        assertEquals(CompanionVisualTheme.Quiet, decodeCompanionVisualTheme("Quiet"))
    }

    @Test
    fun unknownThemeFallsBackWithoutCrashing() {
        assertEquals(CompanionVisualTheme.Expressive, decodeCompanionVisualTheme("RemovedTheme"))
    }
}
