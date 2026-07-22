package com.xteink.companion.ui

import com.xteink.companion.ui.components.formatSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusFormattingTest {
    @Test
    fun formatsCountdownWithLeadingZeroes() {
        assertEquals("25:00", formatSeconds(1_500))
        assertEquals("09:05", formatSeconds(545))
        assertEquals("00:00", formatSeconds(-1))
    }
}
