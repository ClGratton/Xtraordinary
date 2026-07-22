package com.xteink.companion.ui.components

import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.math.sin

internal data class MagneticSwipeConfig(
    val threshold: Float,
    val freeTravel: Float,
    val resistedTravel: Float,
) {
    init {
        require(threshold in 0f..1f)
        require(freeTravel >= 0f && freeTravel < threshold)
        require(resistedTravel > 0f)
    }

    /**
     * Tracks the finger at first, compresses travel while approaching the threshold, then
     * releases the withheld distance and tracks the finger exactly after the bump is crossed.
     */
    fun displayedProgress(signedProgress: Float): Float {
        val bounded = signedProgress.coerceIn(-1f, 1f)
        val progress = bounded.absoluteValue
        if (progress <= freeTravel || progress >= threshold) return bounded

        val constrainedProgress = (progress - freeTravel) / (1f - freeTravel)
        val resistedProgress = freeTravel +
            resistedTravel * sin(constrainedProgress * (PI.toFloat() / 2f))
        return bounded.sign * resistedProgress.coerceAtMost(progress)
    }
}

internal enum class MagneticThresholdTransition {
    Entered,
    Exited,
}

internal data class MagneticThresholdEvent(
    val transition: MagneticThresholdTransition,
    val direction: Float,
)

/** Reusable crossing detector that emits on both sides of a magnetic threshold. */
internal class MagneticSwipeState(private val config: MagneticSwipeConfig) {
    var isBeyondThreshold: Boolean = false
        private set

    private var engagedDirection = 1f

    fun update(signedProgress: Float): MagneticThresholdEvent? {
        val progress = signedProgress.absoluteValue
        val beyondThreshold = progress >= config.threshold
        if (beyondThreshold == isBeyondThreshold) return null

        isBeyondThreshold = beyondThreshold
        return if (beyondThreshold) {
            engagedDirection = signedProgress.sign.takeUnless { it == 0f } ?: engagedDirection
            MagneticThresholdEvent(MagneticThresholdTransition.Entered, engagedDirection)
        } else {
            MagneticThresholdEvent(MagneticThresholdTransition.Exited, engagedDirection)
        }
    }
}
