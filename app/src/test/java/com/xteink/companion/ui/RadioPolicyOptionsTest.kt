package com.xteink.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPolicyOptionsTest {
    private val fastChoices = listOf(1, 5, 10)
    private val sleepChoices = listOf(5, 10, 20)

    @Test
    fun fastChoiceMustOccurStrictlyBeforeSleep() {
        assertTrue(isFastWindowChoiceEnabled(1, 5))
        assertFalse(isFastWindowChoiceEnabled(5, 5))
        assertFalse(isFastWindowChoiceEnabled(10, 5))
    }

    @Test
    fun selectingShorterSleepChoosesLatestCompatibleFastWindow() {
        val policy = RadioPolicyUiState(fastWindowMinutes = 10, sleepAfterMinutes = 20)

        assertEquals(
            RadioPolicyUiState(fastWindowMinutes = 1, sleepAfterMinutes = 5),
            selectSleepAfter(policy, sleepAfterMinutes = 5, fastWindowChoicesMinutes = fastChoices),
        )
    }

    @Test
    fun selectingLongerFastWindowChoosesEarliestCompatibleSleep() {
        val policy = RadioPolicyUiState(fastWindowMinutes = 1, sleepAfterMinutes = 5)

        assertEquals(
            RadioPolicyUiState(fastWindowMinutes = 10, sleepAfterMinutes = 20),
            selectFastWindow(policy, fastWindowMinutes = 10, sleepChoicesMinutes = sleepChoices),
        )
    }
}
