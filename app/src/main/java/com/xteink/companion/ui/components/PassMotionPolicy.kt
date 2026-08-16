package com.xteink.companion.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Shared final-state targets for the Passes turn and expanding mode choice. */
internal object PassMotionPolicy {
    const val turnDurationMillis = 220
    const val detailsFace = 0f
    const val codeFace = 1f
    const val selectedChoiceWeight = 1.45f
    const val restingChoiceWeight = 0.75f
    const val actionResting = 0f
    const val actionDeployed = 1f
    const val actionRestingGapDp = 0f
    const val actionDeployedGapDp = 8f

    val choiceSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val actionMitosisSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val actionGapSpring: SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)

    fun turnTarget(showCode: Boolean): Float = if (showCode) codeFace else detailsFace
    fun showsCode(turnProgress: Float): Boolean = turnProgress >= 0.5f
    fun actionMitosisTarget(deployed: Boolean): Float = if (deployed) actionDeployed else actionResting
    fun actionGapTarget(deployed: Boolean): Float = if (deployed) actionDeployedGapDp else actionRestingGapDp
}
