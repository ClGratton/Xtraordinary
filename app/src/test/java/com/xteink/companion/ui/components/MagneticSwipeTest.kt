package com.xteink.companion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagneticSwipeTest {
    private val config = MagneticSwipeConfig(
        threshold = 0.42f,
        freeTravel = 0.08f,
        resistedTravel = 0.48f,
    )

    @Test
    fun blendsSmoothlyFromResistanceToFingerTracking() {
        assertEquals(0.05f, config.displayedProgress(0.05f, resistance = 1f), 0.0001f)
        assertTrue(config.displayedProgress(0.30f, resistance = 1f) < 0.30f)
        val halfway = config.displayedProgress(0.42f, resistance = 0.5f)
        assertTrue(halfway < 0.42f)
        assertTrue(halfway > config.displayedProgress(0.42f, resistance = 1f))
        assertEquals(0.80f, config.displayedProgress(0.80f, resistance = 0f), 0.0001f)
    }

    @Test
    fun preservesResistanceAndReleaseInBothDirections() {
        assertTrue(config.displayedProgress(-0.30f, resistance = 1f) > -0.30f)
        assertEquals(-0.42f, config.displayedProgress(-0.42f, resistance = 0f), 0.0001f)
    }

    @Test
    fun reversesResistanceWhileReturningToCenter() {
        val outward = config.displayedProgress(0.30f, resistance = 1f)
        val returning = config.displayedProgress(
            signedProgress = 0.30f,
            resistance = 1f,
            returningToOrigin = true,
        )

        assertTrue(outward < 0.30f)
        assertTrue(returning > 0.30f)
        assertTrue(
            config.displayedProgress(0.40f, resistance = 1f, returningToOrigin = true) <= config.threshold,
        )
        assertEquals(
            0.05f,
            config.displayedProgress(0.05f, resistance = 1f, returningToOrigin = true),
            0.0001f,
        )
        assertTrue(
            config.displayedProgress(-0.30f, resistance = 1f, returningToOrigin = true) < -0.30f,
        )
    }

    @Test
    fun emitsEveryCrossingWithoutRequiringFingerLift() {
        val state = MagneticSwipeState(config)

        assertNull(state.update(0.20f))
        assertEquals(MagneticThresholdTransition.Entered, state.update(0.43f)?.transition)
        assertEquals(MagneticThresholdTransition.Exited, state.update(0.40f)?.transition)
        assertEquals(MagneticThresholdTransition.Entered, state.update(0.44f)?.transition)
        assertEquals(MagneticThresholdTransition.Exited, state.update(0.10f)?.transition)
        val opposite = state.update(-0.45f)
        assertEquals(MagneticThresholdTransition.Entered, opposite?.transition)
        assertEquals(-1f, opposite?.direction ?: 0f, 0.0001f)
    }
}
