package com.xteink.companion.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
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
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ControlDeckFocusContent(
    focus: FocusUiState,
    onSetDuration: (Int) -> Unit,
    onStartFocus: () -> Unit,
    onTogglePause: () -> Unit,
    onEndFocus: () -> Unit,
    onResetFocus: () -> Unit,
    onSendScene: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        X3ImageField()
        DurationControlDeck(focus = focus, onSetDuration = onSetDuration)
        FocusDeckActions(
            phase = focus.phase,
            onStartFocus = onStartFocus,
            onTogglePause = onTogglePause,
            onEndFocus = onEndFocus,
            onResetFocus = onResetFocus,
            onSendScene = onSendScene,
        )
    }
}

@Composable
private fun X3ImageField() {
    val description = stringResource(R.string.x3_preview_description)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.52f)
            .semantics { contentDescription = description },
        color = Color(0xFF111111),
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
                EInkLighthouseArtwork(modifier = Modifier.fillMaxSize())
            }
            Text(
                text = stringResource(R.string.x3_brand),
                color = Color.White,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EInkLighthouseArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val ink = Color.Black
        val fine = 1.2.dp.toPx()
        val medium = 2.dp.toPx()
        val horizon = size.height * 0.63f

        drawLine(ink, Offset(0f, horizon), Offset(size.width, horizon), medium)

        val island = Path().apply {
            moveTo(size.width * 0.39f, horizon)
            cubicTo(
                size.width * 0.48f,
                size.height * 0.54f,
                size.width * 0.62f,
                size.height * 0.53f,
                size.width * 0.73f,
                horizon,
            )
            close()
        }
        drawPath(island, ink)

        drawRect(
            color = ink,
            topLeft = Offset(size.width * 0.535f, size.height * 0.26f),
            size = Size(size.width * 0.075f, size.height * 0.31f),
            style = Stroke(width = medium),
        )
        drawLine(ink, Offset(size.width * 0.525f, size.height * 0.26f), Offset(size.width * 0.62f, size.height * 0.26f), medium)
        drawLine(ink, Offset(size.width * 0.54f, size.height * 0.22f), Offset(size.width * 0.615f, size.height * 0.22f), medium)
        drawLine(ink, Offset(size.width * 0.54f, size.height * 0.22f), Offset(size.width * 0.575f, size.height * 0.16f), medium)
        drawLine(ink, Offset(size.width * 0.615f, size.height * 0.22f), Offset(size.width * 0.575f, size.height * 0.16f), medium)
        drawCircle(ink, radius = size.width * 0.013f, center = Offset(size.width * 0.575f, size.height * 0.245f))

        val house = Path().apply {
            moveTo(size.width * 0.62f, horizon)
            lineTo(size.width * 0.62f, size.height * 0.48f)
            lineTo(size.width * 0.76f, size.height * 0.48f)
            lineTo(size.width * 0.76f, horizon)
            close()
        }
        drawPath(house, ink, style = Stroke(width = medium))
        drawLine(ink, Offset(size.width * 0.60f, size.height * 0.48f), Offset(size.width * 0.69f, size.height * 0.40f), medium)
        drawLine(ink, Offset(size.width * 0.69f, size.height * 0.40f), Offset(size.width * 0.79f, size.height * 0.48f), medium)

        listOf(0.70f, 0.76f, 0.82f, 0.88f).forEachIndexed { index, yFraction ->
            val y = size.height * yFraction
            val amplitude = size.height * (0.020f + index * 0.004f)
            val wave = Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x < size.width) {
                    quadraticTo(x + size.width * 0.035f, y - amplitude, x + size.width * 0.07f, y)
                    quadraticTo(x + size.width * 0.105f, y + amplitude, x + size.width * 0.14f, y)
                    x += size.width * 0.14f
                }
            }
            drawPath(wave, ink, style = Stroke(width = fine, cap = StrokeCap.Round))
        }

        listOf(
            Offset(0.24f, 0.25f),
            Offset(0.34f, 0.34f),
            Offset(0.80f, 0.28f),
        ).forEach { bird ->
            val center = Offset(size.width * bird.x, size.height * bird.y)
            drawLine(ink, center, center + Offset(-10.dp.toPx(), -4.dp.toPx()), fine, StrokeCap.Round)
            drawLine(ink, center, center + Offset(10.dp.toPx(), -4.dp.toPx()), fine, StrokeCap.Round)
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
) {
    val editable = focus.phase == FocusPhase.Setup
    val haptics = LocalHapticFeedback.current
    var lastHapticMinute by remember { mutableIntStateOf(focus.selectedMinutes) }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = DurationDeckShape,
    ) {
        BoxWithConstraints {
            val timerFontSize = (maxWidth.value * 0.225f).coerceIn(72f, 88f).sp
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
