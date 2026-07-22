package com.xteink.companion.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xteink.companion.R
import com.xteink.companion.ui.FocusPhase
import com.xteink.companion.ui.FocusUiState
import com.xteink.companion.ui.CompanionVisualTheme
import com.xteink.companion.ui.sceneArtworkFor
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ControlDeckFocusContent(
    focus: FocusUiState,
    visualTheme: CompanionVisualTheme,
    onSetDuration: (Int) -> Unit,
    onStartFocus: () -> Unit,
    onTogglePause: () -> Unit,
    onEndFocus: () -> Unit,
    onResetFocus: () -> Unit,
    onSendScene: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val imageAspect = 1.60f
        val imageHeight = maxWidth / imageAspect
        val durationHeight = (maxHeight - imageHeight - 168.dp).coerceIn(270.dp, 304.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            X3ImageField(visualTheme = visualTheme)
            DurationControlDeck(
                focus = focus,
                onSetDuration = onSetDuration,
                modifier = Modifier.height(durationHeight),
            )
            FocusDeckActions(
                phase = focus.phase,
                onStartFocus = onStartFocus,
                onTogglePause = onTogglePause,
                onEndFocus = onEndFocus,
                onResetFocus = onResetFocus,
                onSendScene = onSendScene,
            )
            SendSceneRow(onSendScene = onSendScene)
        }
    }
}

@Composable
private fun X3ImageField(visualTheme: CompanionVisualTheme) {
    val description = stringResource(R.string.x3_preview_description)
    val artwork = sceneArtworkFor(visualTheme).phonePreview
    val quiet = visualTheme == CompanionVisualTheme.Quiet
    val frameColor = if (quiet) Color.White else Color(0xFF111111)
    val brandColor = if (quiet) Color.Black else Color.White
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.60f)
            .semantics { contentDescription = description },
        color = frameColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
            ) {
                Image(
                    painter = painterResource(artwork),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                text = stringResource(R.string.x3_brand),
                color = brandColor,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val DurationDeckShape = GenericShape { size, _ ->
    moveTo(0f, size.height * 0.22f)
    cubicTo(0f, size.height * 0.06f, size.width * 0.08f, 0f, size.width * 0.23f, size.height * 0.01f)
    cubicTo(size.width * 0.46f, size.height * 0.075f, size.width * 0.65f, 0f, size.width * 0.83f, size.height * 0.01f)
    cubicTo(size.width * 0.95f, size.height * 0.02f, size.width, size.height * 0.11f, size.width, size.height * 0.25f)
    lineTo(size.width, size.height * 0.76f)
    cubicTo(size.width, size.height * 0.92f, size.width * 0.93f, size.height, size.width * 0.79f, size.height)
    cubicTo(size.width * 0.56f, size.height * 0.94f, size.width * 0.35f, size.height * 0.97f, size.width * 0.16f, size.height)
    cubicTo(size.width * 0.05f, size.height, 0f, size.height * 0.91f, 0f, size.height * 0.77f)
    close()
}

@Composable
private fun DurationControlDeck(
    focus: FocusUiState,
    onSetDuration: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editable = focus.phase == FocusPhase.Setup
    val haptics = LocalHapticFeedback.current
    var lastHapticMinute by remember { mutableIntStateOf(focus.selectedMinutes) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = DurationDeckShape,
    ) {
        BoxWithConstraints {
            val timerFontSize = minOf(maxWidth.value * 0.23f, maxHeight.value * 0.34f)
                .coerceIn(78f, 94f).sp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.display_duration).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = when (focus.phase) {
                                FocusPhase.Setup -> stringResource(R.string.duration_format, focus.selectedMinutes)
                                else -> formatSeconds(focus.remainingSeconds)
                            },
                            fontSize = timerFontSize,
                            lineHeight = timerFontSize,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp,
                            maxLines = 1,
                        )
                        Text(stringResource(R.string.time_format_hint), style = MaterialTheme.typography.labelMedium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DurationStepButton(
                            label = "+",
                            enabled = editable,
                            onClick = { onSetDuration(focus.selectedMinutes + 5) },
                        )
                        DurationStepButton(
                            label = "-",
                            enabled = editable,
                            onClick = { onSetDuration(focus.selectedMinutes - 5) },
                        )
                    }
                }
                Column {
                    Slider(
                        value = focus.selectedMinutes.toFloat(),
                        onValueChange = { value ->
                            val snappedMinute = ((value / 5f).roundToInt() * 5).coerceIn(5, 60)
                            if (snappedMinute != lastHapticMinute) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                lastHapticMinute = snappedMinute
                                onSetDuration(snappedMinute)
                            }
                        },
                        enabled = editable,
                        valueRange = 5f..60f,
                        steps = 10,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        listOf("5m", "25m", "45m", "60m").forEach {
                            Text(it, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendSceneRow(onSendScene: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSendScene()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PaperPlaneIcon()
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.send_to_x3), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.send_to_x3_body),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PaperPlaneIcon() {
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    Canvas(modifier = Modifier.size(28.dp)) {
        val plane = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.48f)
            lineTo(size.width * 0.88f, size.height * 0.12f)
            lineTo(size.width * 0.62f, size.height * 0.88f)
            lineTo(size.width * 0.43f, size.height * 0.57f)
            close()
            moveTo(size.width * 0.43f, size.height * 0.57f)
            lineTo(size.width * 0.88f, size.height * 0.12f)
        }
        drawPath(
            path = plane,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun DurationStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.size(width = 64.dp, height = 52.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun FocusDeckActions(
    phase: FocusPhase,
    onStartFocus: () -> Unit,
    onTogglePause: () -> Unit,
    onEndFocus: () -> Unit,
    onResetFocus: () -> Unit,
    onSendScene: () -> Unit,
) {
    val sendDescription = stringResource(R.string.send_to_x3_body)
    val haptics = LocalHapticFeedback.current
    val active = phase == FocusPhase.Running || phase == FocusPhase.Paused
    val splitProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "focus action mitosis",
    )
    val actionCorner by animateDpAsState(
        targetValue = if (active) 20.dp else 32.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "focus action corners",
    )
    val splitGap by animateDpAsState(
        targetValue = if (active) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "focus action gap",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(splitGap),
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(
                        if (active) HapticFeedbackType.ToggleOn else HapticFeedbackType.Confirm,
                    )
                    when (phase) {
                        FocusPhase.Setup -> onStartFocus
                        FocusPhase.Running, FocusPhase.Paused -> onTogglePause
                        FocusPhase.Review -> onResetFocus
                    }.invoke()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                shape = RoundedCornerShape(actionCorner),
            ) {
                when (phase) {
                    FocusPhase.Setup, FocusPhase.Review -> PlayTriangleIcon(modifier = Modifier.padding(end = 8.dp))
                    FocusPhase.Running -> PauseIcon(modifier = Modifier.padding(end = 7.dp))
                    FocusPhase.Paused -> PlayTriangleIcon(modifier = Modifier.padding(end = 7.dp))
                }
                Text(
                    text = when (phase) {
                        FocusPhase.Setup -> stringResource(R.string.start_focus)
                        FocusPhase.Running -> stringResource(R.string.pause_focus)
                        FocusPhase.Paused -> stringResource(R.string.resume_focus)
                        FocusPhase.Review -> stringResource(R.string.start_another)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            if (splitProgress > 0.001f) {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        onEndFocus()
                    },
                    modifier = Modifier
                        .weight(splitProgress.coerceAtLeast(0.001f))
                        .height(60.dp)
                        .graphicsLayer {
                            alpha = splitProgress
                            scaleX = 0.72f + splitProgress * 0.28f
                        },
                    shape = RoundedCornerShape(actionCorner),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    StopIcon(modifier = Modifier.padding(end = 7.dp))
                    Text(
                        text = stringResource(R.string.stop_focus),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSendScene()
            },
            modifier = Modifier
                .size(width = 92.dp, height = 60.dp)
                .semantics { contentDescription = sendDescription },
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            SendToX3Icon()
        }
    }
}

@Composable
private fun PauseIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = modifier.size(20.dp)) {
        val width = 4.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.22f, size.height * 0.18f),
            size = Size(width, size.height * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.58f, size.height * 0.18f),
            size = Size(width, size.height * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        )
    }
}

@Composable
private fun StopIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    Canvas(modifier = modifier.size(18.dp)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
            size = Size(size.width * 0.64f, size.height * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
        )
    }
}

@Composable
private fun PlayTriangleIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = modifier.size(22.dp)) {
        val triangle = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.14f)
            lineTo(size.width * 0.82f, size.height * 0.50f)
            lineTo(size.width * 0.28f, size.height * 0.86f)
            close()
        }
        drawPath(triangle, color)
    }
}

internal fun formatSeconds(totalSeconds: Int): String {
    val bounded = totalSeconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", bounded / 60, bounded % 60)
}
