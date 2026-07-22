package com.xteink.companion.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlin.math.absoluteValue
import kotlin.math.sign

internal val DefaultMagneticSwipe = MagneticSwipeConfig(
    threshold = 0.42f,
    freeTravel = 0.08f,
    resistedTravel = 0.48f,
)

/**
 * The shared direct-manipulation pager used by passes and device selection.
 * Resistance, haptics, fling commitment, and the settled kick intentionally live here together.
 */
@Composable
internal fun MagneticHorizontalPager(
    state: PagerState,
    contentPadding: PaddingValues,
    pageSpacing: Dp,
    modifier: Modifier = Modifier,
    config: MagneticSwipeConfig = DefaultMagneticSwipe,
    content: @Composable (page: Int) -> Unit,
) {
    val isDragged by state.interactionSource.collectIsDraggedAsState()
    val resistanceBlend = remember { Animatable(0f) }
    val snapKick = remember { Animatable(0f) }
    var beyondThreshold by remember { mutableStateOf(false) }
    var returningToOrigin by remember { mutableStateOf(false) }
    val flingBehavior = PagerDefaults.flingBehavior(
        state = state,
        snapPositionalThreshold = config.threshold,
        snapAnimationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessHigh,
        ),
    )

    LaunchedEffect(isDragged) {
        if (isDragged) {
            returningToOrigin = false
            resistanceBlend.snapTo(1f)
        } else {
            beyondThreshold = false
            resistanceBlend.animateTo(0f, tween(durationMillis = 110))
        }
    }
    LaunchedEffect(beyondThreshold) {
        if (isDragged) {
            resistanceBlend.animateTo(
                targetValue = if (beyondThreshold) 0f else 1f,
                animationSpec = tween(durationMillis = 90),
            )
        }
    }
    PagerResistanceFeedback(
        pagerState = state,
        isDragged = isDragged,
        config = config,
        onThresholdChanged = { isBeyondThreshold ->
            beyondThreshold = isBeyondThreshold
        },
        onReturningChanged = { returningToOrigin = it },
    ) { direction ->
        snapKick.snapTo(direction * 0.018f)
        snapKick.animateTo(-direction * 0.006f, tween(durationMillis = 70))
        snapKick.animateTo(0f, tween(durationMillis = 100))
    }

    HorizontalPager(
        state = state,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
        flingBehavior = flingBehavior,
        modifier = modifier,
    ) { page ->
        Box(
            modifier = Modifier.graphicsLayer {
                val pageOffset = ((state.currentPage - page) + state.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)
                val signedDrag = ((state.currentPage - state.settledPage) +
                    state.currentPageOffsetFraction).coerceIn(-1f, 1f)
                val displayedProgress = config.displayedProgress(
                    signedProgress = signedDrag,
                    resistance = resistanceBlend.value,
                    returningToOrigin = returningToOrigin,
                )
                translationX = size.width * (signedDrag - displayedProgress)
                if (page == state.settledPage) translationX += snapKick.value * size.width
                scaleX = 1f - pageOffset * 0.014f
                scaleY = 1f - pageOffset * 0.010f
            },
        ) {
            content(page)
        }
    }
}

@Composable
private fun PagerResistanceFeedback(
    pagerState: PagerState,
    isDragged: Boolean,
    config: MagneticSwipeConfig,
    onThresholdChanged: (Boolean) -> Unit,
    onReturningChanged: (Boolean) -> Unit,
    onCenterSettled: suspend (Float) -> Unit,
) {
    val context = LocalContext.current
    val fallback = LocalHapticFeedback.current
    var dragSessionActive by remember { mutableStateOf(false) }
    var releaseDirection by remember { mutableFloatStateOf(1f) }
    var dragStartPage by remember { mutableIntStateOf(pagerState.settledPage) }
    var crossedThresholdAtRelease by remember { mutableStateOf(false) }
    val vibrator = remember(context) { context.touchVibrator() }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            dragSessionActive = true
            dragStartPage = pagerState.settledPage
            crossedThresholdAtRelease = false
            val dragStartPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
            var lastPosition = dragStartPosition
            var travelSinceTick = 0f
            var samplesUntilTick = 0
            var returningAfterExit = false
            var engagedDirection = 1f
            val magneticState = MagneticSwipeState(config)
            if (!vibrator.playPrimitive(context, VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.18f)) {
                fallback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { position ->
                val movement = position - lastPosition
                val distance = movement.absoluteValue
                val signedProgress = position - dragStartPosition
                val progress = signedProgress.absoluteValue
                if (distance > 0.001f) releaseDirection = movement.sign
                travelSinceTick += distance

                val thresholdEvent = magneticState.update(signedProgress)
                if (thresholdEvent != null) {
                    if (!vibrator.playSnapThreshold(context)) {
                        fallback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    onThresholdChanged(magneticState.isBeyondThreshold)
                    engagedDirection = thresholdEvent.direction
                    returningAfterExit = thresholdEvent.transition == MagneticThresholdTransition.Exited
                    onReturningChanged(returningAfterExit)
                    crossedThresholdAtRelease = magneticState.isBeyondThreshold
                    travelSinceTick = 0f
                    samplesUntilTick = 8
                } else if (!magneticState.isBeyondThreshold && samplesUntilTick == 0) {
                    val approach = (progress / config.threshold).coerceIn(0f, 1f)
                    val distancePerTick = 0.065f - approach * 0.037f
                    if (travelSinceTick >= distancePerTick) {
                        val scale = 0.18f + approach * 0.44f
                        if (!vibrator.playPrimitive(context, VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale)) {
                            fallback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                        travelSinceTick = 0f
                    }
                } else if (samplesUntilTick > 0) {
                    samplesUntilTick -= 1
                }
                if (
                    returningAfterExit &&
                    (
                        progress <= config.freeTravel ||
                            signedProgress.sign != engagedDirection ||
                            (distance > 0.001f && movement.sign == engagedDirection)
                        )
                ) {
                    returningAfterExit = false
                    onReturningChanged(false)
                }
                lastPosition = position
            }
        } else if (dragSessionActive) {
            onThresholdChanged(false)
            val committedPage = snapshotFlow {
                pagerState.targetPage to pagerState.isScrollInProgress
            }.first { (targetPage, scrolling) ->
                targetPage != dragStartPage || !scrolling
            }.first
            if (committedPage != dragStartPage && !crossedThresholdAtRelease) {
                if (!vibrator.playSnapThreshold(context)) {
                    fallback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
            }
            snapshotFlow { pagerState.isScrollInProgress }.first { scrolling -> !scrolling }
            dragSessionActive = false
            onCenterSettled(releaseDirection)
        }
    }
}

@Suppress("DEPRECATION")
private fun Context.touchVibrator(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    getSystemService(VibratorManager::class.java).defaultVibrator
} else {
    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
}

private fun Vibrator.playPrimitive(context: Context, primitive: Int, scale: Float): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !hasVibrator()) return false
    val touchEnabled = Settings.System.getInt(
        context.contentResolver,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        1,
    ) != 0
    if (!touchEnabled || !areAllPrimitivesSupported(primitive)) return false
    vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(primitive, scale.coerceIn(0f, 1f))
            .compose(),
    )
    return true
}

private fun Vibrator.playSnapThreshold(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !hasVibrator()) return false
    val touchEnabled = Settings.System.getInt(
        context.contentResolver,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        1,
    ) != 0
    val lowTick = VibrationEffect.Composition.PRIMITIVE_LOW_TICK
    val click = VibrationEffect.Composition.PRIMITIVE_CLICK
    if (!touchEnabled || !areAllPrimitivesSupported(lowTick, click)) return false
    vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(lowTick, 0.72f)
            .addPrimitive(click, 1f, 20)
            .compose(),
    )
    return true
}
