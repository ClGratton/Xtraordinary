package com.xteink.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.data.ReadingSessionStat
import com.xteink.companion.ui.ReadingStatsUiState
import com.xteink.companion.ui.ReadingStatsView
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(selected.title, style = MaterialTheme.typography.titleLarge)
                    Text(formatDate(selected.endedAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onDeleteSession(selected.id) }) {
                    Text(stringResource(R.string.delete_session))
                }
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
        SwipeSessionRow(
            session = session,
            selected = session.id == selected.id,
            onSelect = { onSelectSession(session.id) },
            onDelete = { onDeleteSession(session.id) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeSessionRow(
    session: ReadingSessionStat,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.42f },
    )
    val armed = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
    val morph by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "session delete morph",
    )
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = 0.72f + (0.28f * morph)
                            scaleY = 0.72f + (0.28f * morph)
                            rotationZ = -12f + (12f * morph)
                            transformOrigin = TransformOrigin.Center
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DeleteSessionIcon()
                }
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
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
