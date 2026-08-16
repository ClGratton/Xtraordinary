package com.xteink.companion.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassReducedMotionTest {
    private object ZeroMotionDurationScale : MotionDurationScale {
        override val scaleFactor: Float = 0f
    }

    private class SteppingFrameClock : MonotonicFrameClock {
        private var frameTimeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frameTimeNanos += 16_000_000L
            return onFrame(frameTimeNanos)
        }
    }

    @Test fun zeroDurationScaleReachesBothCardFacesAndChoiceWidths() = runBlocking {
        withContext(ZeroMotionDurationScale + SteppingFrameClock()) {
            val turn = Animatable(PassMotionPolicy.detailsFace)
            turn.animateTo(
                PassMotionPolicy.turnTarget(showCode = true),
                tween(PassMotionPolicy.turnDurationMillis),
            )
            assertTrue(PassMotionPolicy.showsCode(turn.value))
            assertEquals(PassMotionPolicy.codeFace, turn.value, 0f)

            turn.animateTo(
                PassMotionPolicy.turnTarget(showCode = false),
                tween(PassMotionPolicy.turnDurationMillis),
            )
            assertFalse(PassMotionPolicy.showsCode(turn.value))
            assertEquals(PassMotionPolicy.detailsFace, turn.value, 0f)

            val choiceWidth = Animatable(PassMotionPolicy.restingChoiceWeight)
            choiceWidth.animateTo(PassMotionPolicy.selectedChoiceWeight, PassMotionPolicy.choiceSpring)
            assertEquals(PassMotionPolicy.selectedChoiceWeight, choiceWidth.value, 0f)
            choiceWidth.animateTo(PassMotionPolicy.restingChoiceWeight, PassMotionPolicy.choiceSpring)
            assertEquals(PassMotionPolicy.restingChoiceWeight, choiceWidth.value, 0f)

            val mitosis = Animatable(PassMotionPolicy.actionResting)
            mitosis.animateTo(PassMotionPolicy.actionDeployed, PassMotionPolicy.actionMitosisSpring)
            assertEquals(PassMotionPolicy.actionDeployed, mitosis.value, 0f)
            mitosis.animateTo(PassMotionPolicy.actionResting, PassMotionPolicy.actionMitosisSpring)
            assertEquals(PassMotionPolicy.actionResting, mitosis.value, 0f)

            val actionGap = Animatable(PassMotionPolicy.actionRestingGapDp)
            actionGap.animateTo(PassMotionPolicy.actionDeployedGapDp, PassMotionPolicy.actionGapSpring)
            assertEquals(PassMotionPolicy.actionDeployedGapDp, actionGap.value, 0f)
            actionGap.animateTo(PassMotionPolicy.actionRestingGapDp, PassMotionPolicy.actionGapSpring)
            assertEquals(PassMotionPolicy.actionRestingGapDp, actionGap.value, 0f)
        }
    }
}
