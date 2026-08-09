package com.xteink.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.data.ReadingSessionStat
import com.xteink.companion.ui.ReadingStatsUiState
import com.xteink.companion.ui.ReadingStatsView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReadingStatsContent(
    state: ReadingStatsUiState,
    onSetView: (ReadingStatsView) -> Unit,
    onSelectSession: (UInt?) -> Unit,
    onDeleteSession: (UInt) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleSessions = state.sessions
        .map { it.filteredForDisplay(state.minimumPageSeconds * 1_000L) }
        .filter { it.pages.isNotEmpty() }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("←  ${stringResource(R.string.back_to_tools)}")
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(stringResource(R.string.reading_stats_title), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (state.syncing) stringResource(R.string.reading_stats_syncing)
                        else stringResource(R.string.reading_stats_offline_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${visibleSessions.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.view == ReadingStatsView.Cumulative,
                    onClick = { onSetView(ReadingStatsView.Cumulative) },
                    label = { Text(stringResource(R.string.reading_stats_cumulative)) },
                )
                FilterChip(
                    selected = state.view == ReadingStatsView.Sessions,
                    onClick = { onSetView(ReadingStatsView.Sessions) },
                    label = { Text(stringResource(R.string.reading_stats_sessions)) },
                )
            }
            if (visibleSessions.isEmpty()) {
                EmptyStats()
            } else if (state.view == ReadingStatsView.Cumulative) {
                CumulativeStats(visibleSessions)
            } else {
                SessionStats(visibleSessions, state.selectedSessionId, onSelectSession, onDeleteSession)
            }
        }
    }
}

@Composable
private fun EmptyStats() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reading_stats_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.reading_stats_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CumulativeStats(sessions: List<ReadingSessionStat>) {
    val pages = sessions.sumOf { it.pages.size }
    val measuredSessions = sessions.filter { it.hasWordCounts }
    val words = measuredSessions.sumOf { it.wordCount }
    val duration = sessions.sumOf { it.durationMs }
    val measuredDuration = measuredSessions.sumOf { it.durationMs }
    val wpm = if (measuredDuration > 0 && words > 0) ((words * 60_000L) / measuredDuration).toInt() else null
    MetricStrip(
        listOf(
            stringResource(R.string.reading_stats_pages) to pages.toString(),
            stringResource(R.string.reading_stats_words) to compactNumber(words),
            stringResource(R.string.reading_stats_time) to formatDuration(duration),
            stringResource(R.string.reading_stats_avg_wpm) to (wpm?.toString() ?: "—"),
        ),
    )
    WpmChart(
        values = sessions.sortedBy { it.endedAtEpochMs }.takeLast(16).mapNotNull { it.averageWordsPerMinute },
        title = stringResource(R.string.reading_stats_pace_by_session),
    )
    val longest = sessions.maxByOrNull { it.durationMs }
    if (longest != null) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reading_stats_longest), style = MaterialTheme.typography.labelLarge)
                    Text(longest.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Text(formatDuration(longest.durationMs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SessionStats(
    sessions: List<ReadingSessionStat>,
    selectedId: UInt?,
    onSelectSession: (UInt?) -> Unit,
    onDeleteSession: (UInt) -> Unit,
) {
    val selected = sessions.firstOrNull { it.id == selectedId } ?: sessions.first()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(selected.title, style = MaterialTheme.typography.titleLarge)
                Text(formatDate(selected.endedAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MetricStrip(
                listOf(
                    stringResource(R.string.reading_stats_pages) to selected.pages.size.toString(),
                    stringResource(R.string.reading_stats_words) to if (selected.hasWordCounts) compactNumber(selected.wordCount) else "—",
                    stringResource(R.string.reading_stats_time) to formatDuration(selected.durationMs),
                    stringResource(R.string.reading_stats_avg_wpm) to (selected.averageWordsPerMinute?.toString() ?: "—"),
                ),
                nested = true,
            )
            if (!selected.hasWordCounts) {
                Text(stringResource(R.string.reading_stats_xtc_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    WpmChart(
        values = selected.pages.mapNotNull { it.wordsPerMinute },
        title = stringResource(R.string.reading_stats_pace_by_page),
    )
    Text(stringResource(R.string.reading_stats_recent), style = MaterialTheme.typography.titleMedium)
    sessions.forEach { session ->
        androidx.compose.runtime.key(session.id) {
            SwipeSessionRow(
                session = session,
                selected = session.id == selected.id,
                onSelect = { onSelectSession(session.id) },
                onDelete = { onDeleteSession(session.id) },
            )
        }
    }
}

@Composable
private fun SwipeSessionRow(
    session: ReadingSessionStat,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val vibrator = remember(context) { context.touchVibrator() }
    val scope = rememberCoroutineScope()
    var rowWidthPx by remember(session.id) { mutableFloatStateOf(1f) }
    var rowHeightPx by remember(session.id) { mutableFloatStateOf(1f) }
    var rawProgress by remember(session.id) { mutableFloatStateOf(0f) }
    var settlingOffsetPx by remember(session.id) { mutableStateOf<Float?>(null) }
    var armed by remember(session.id) { mutableStateOf(false) }
    var removing by remember(session.id) { mutableStateOf(false) }
    var resistanceJob by remember(session.id) { mutableStateOf<Job?>(null) }
    var settleJob by remember(session.id) { mutableStateOf<Job?>(null) }
    val resistanceBlend = remember(session.id) { Animatable(1f) }
    val visibleState = remember(session.id) {
        MutableTransitionState(false).apply { targetState = true }
    }
    val magneticState = remember(session.id) { MagneticSwipeState(DefaultMagneticSwipe) }
    val directOffsetPx = DefaultMagneticSwipe.displayedProgress(
        signedProgress = rawProgress,
        resistance = resistanceBlend.value,
    ) * rowWidthPx
    val offsetPx = settlingOffsetPx ?: directOffsetPx
    val revealFraction = (-offsetPx / rowWidthPx).coerceIn(0f, 1f)
    val actionWidthPx = -offsetPx.coerceAtMost(0f)
    val actionWidth = with(density) { actionWidthPx.toDp() }
    val iconCenterFromEndPx = maxOf(actionWidthPx / 2f, rowHeightPx / 2f)
    val iconHalfSizePx = with(density) { 28.dp.toPx() }
    val iconOffset = with(density) { -(iconCenterFromEndPx - iconHalfSizePx).toDp() }
    val morph by animateFloatAsState(
        targetValue = (revealFraction / DefaultMagneticSwipe.threshold).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "session delete morph",
    )

    suspend fun settle(target: Float, durationMillis: Int) {
        resistanceJob?.cancel()
        settlingOffsetPx = offsetPx
        animate(
            initialValue = offsetPx,
            targetValue = target,
            animationSpec = if (target == 0f) {
                spring(dampingRatio = 0.78f, stiffness = 560f)
            } else {
                tween(durationMillis)
            },
        ) { value, _ -> settlingOffsetPx = value }
        if (target == 0f) {
            rawProgress = 0f
            magneticState.update(0f)
            armed = false
            resistanceBlend.snapTo(1f)
            settlingOffsetPx = null
        }
    }

    fun updateDrag(dragAmountPx: Float) {
        rawProgress = (rawProgress + (dragAmountPx / rowWidthPx)).coerceIn(-1f, 0f)
        val thresholdEvent = magneticState.update(rawProgress)
        if (thresholdEvent != null) {
            if (!vibrator.playSnapThreshold(context)) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
            armed = magneticState.isBeyondThreshold
            resistanceJob?.cancel()
            resistanceJob = scope.launch {
                resistanceBlend.animateTo(
                    targetValue = if (magneticState.isBeyondThreshold) 0f else 1f,
                    animationSpec = tween(durationMillis = 90),
                )
            }
        }
    }

    LaunchedEffect(removing) {
        if (removing) {
            delay(230)
            onDelete()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = expandVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)) + fadeIn(tween(120)),
        exit = shrinkVertically(animationSpec = tween(220), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    rowWidthPx = it.width.toFloat().coerceAtLeast(1f)
                    rowHeightPx = it.height.toFloat().coerceAtLeast(1f)
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .width(actionWidth)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.errorContainer),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = iconOffset)
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = 0.82f + (0.18f * morph)
                            scaleY = 0.82f + (0.18f * morph)
                            rotationZ = -10f + (10f * morph)
                            transformOrigin = TransformOrigin.Center
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DeleteSessionIcon()
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = offsetPx }
                    .pointerInput(session.id, rowWidthPx, removing) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                settleJob?.cancel()
                                settlingOffsetPx = null
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!removing) {
                                    change.consume()
                                    updateDrag(dragAmount)
                                }
                            },
                            onDragEnd = {
                                if (!removing) {
                                    settleJob = scope.launch {
                                        if (armed) {
                                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                            settle(-rowWidthPx, 220)
                                            removing = true
                                            visibleState.targetState = false
                                        } else {
                                            settle(0f, 0)
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                settleJob = scope.launch {
                                    settle(0f, 0)
                                }
                            },
                        )
                    }
                    .clickable(enabled = offsetPx == 0f && !removing, onClick = onSelect),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Box(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                            Text(
                                session.pages.size.toString(),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(session.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${formatDate(session.endedAtEpochMs)} · ${formatDuration(session.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(session.averageWordsPerMinute?.let { "$it wpm" } ?: "—", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DeleteSessionIcon() {
    val color = MaterialTheme.colorScheme.onErrorContainer
    Canvas(Modifier.size(26.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(color, Offset(size.width * 0.26f, size.height * 0.29f), Offset(size.width * 0.74f, size.height * 0.29f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.20f), Offset(size.width * 0.60f, size.height * 0.20f), stroke, StrokeCap.Round)
        val body = Path().apply {
            moveTo(size.width * 0.31f, size.height * 0.36f)
            lineTo(size.width * 0.36f, size.height * 0.82f)
            lineTo(size.width * 0.64f, size.height * 0.82f)
            lineTo(size.width * 0.69f, size.height * 0.36f)
        }
        drawPath(body, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * 0.44f, size.height * 0.45f), Offset(size.width * 0.45f, size.height * 0.72f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.56f, size.height * 0.45f), Offset(size.width * 0.55f, size.height * 0.72f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun MetricStrip(metrics: List<Pair<String, String>>, nested: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        metrics.forEach { (label, value) ->
            Surface(
                modifier = Modifier.weight(1f),
                color = if (nested) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WpmChart(values: List<Int>, title: String) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (values.size < 2) {
                Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.reading_stats_chart_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Canvas(Modifier.fillMaxWidth().height(148.dp)) {
                    val top = values.maxOrNull()?.coerceAtLeast(1) ?: 1
                    repeat(3) { i ->
                        val y = size.height * i / 2f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = size.width * index / (values.lastIndex.coerceAtLeast(1)).toFloat()
                        val y = size.height - (value.coerceAtMost(top).toFloat() / top) * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}

private fun compactNumber(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes.coerceAtLeast(1)}m"
}

private val sessionDateFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm")
private fun formatDate(epochMs: Long): String = sessionDateFormatter.format(
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
)
