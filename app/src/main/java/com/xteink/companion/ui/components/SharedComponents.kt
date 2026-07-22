package com.xteink.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.ui.CompanionSurface
import com.xteink.companion.ui.CompanionVisualTheme

@Composable
fun CompanionTopBar(
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsDescription = stringResource(R.string.open_settings)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceOutlineIcon()
                Column {
                    Text(
                        text = stringResource(R.string.demo_status),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_device_value),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = CircleShape,
        ) {
            IconButton(
                onClick = onShowSettings,
                modifier = Modifier.semantics { contentDescription = settingsDescription },
            ) {
                Text(
                    text = "⚙",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun DeviceOutlineIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(18.dp, 28.dp)) {
        drawRoundRect(
            color = color,
            style = Stroke(width = 2.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.35f, size.height * 0.84f),
            end = Offset(size.width * 0.65f, size.height * 0.84f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun CompanionNavigation(
    selected: CompanionSurface,
    onShowFocus: () -> Unit,
    onShowRead: () -> Unit,
    onShowTools: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        NavigationBarItem(
            selected = selected == CompanionSurface.Focus,
            onClick = {
                if (selected != CompanionSurface.Focus) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                onShowFocus()
            },
            icon = { FocusTabIcon() },
            label = { Text(stringResource(R.string.focus_tab)) },
        )
        NavigationBarItem(
            selected = selected == CompanionSurface.Read,
            onClick = {
                if (selected != CompanionSurface.Read) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                onShowRead()
            },
            icon = { BookOutlineIcon() },
            label = { Text(stringResource(R.string.read_tab)) },
        )
        NavigationBarItem(
            selected = selected == CompanionSurface.Tools,
            onClick = {
                if (selected != CompanionSurface.Tools) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                onShowTools()
            },
            icon = { ToolsTabIcon() },
            label = { Text(stringResource(R.string.tools_tab)) },
        )
    }
}

@Composable
fun BookOutlineIcon(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val centerX = size.width / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(1.5.dp.toPx(), 3.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(centerX - 2.5.dp.toPx(), size.height - 6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(centerX + 1.dp.toPx(), 3.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(centerX - 2.5.dp.toPx(), size.height - 6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(centerX, 4.dp.toPx()),
            end = Offset(centerX, size.height - 3.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun FocusTabIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = color, style = Stroke(2.dp.toPx()))
        drawLine(color, center, Offset(center.x, 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        drawLine(color, center, Offset(size.width - 6.dp.toPx(), center.y), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun ToolsTabIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(24.dp)) {
        val cell = size.minDimension * 0.31f
        val gap = size.minDimension * 0.14f
        val grid = cell * 2f + gap
        val insetX = (size.width - grid) / 2f
        val insetY = (size.height - grid) / 2f
        listOf(
            Offset(insetX, insetY),
            Offset(insetX + cell + gap, insetY),
            Offset(insetX, insetY + cell + gap),
            Offset(insetX + cell + gap, insetY + cell + gap),
        ).forEach { origin ->
            drawRoundRect(
                color = color,
                topLeft = origin,
                size = androidx.compose.ui.geometry.Size(cell, cell),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.26f),
                style = Stroke(1.8.dp.toPx()),
            )
        }
    }
}

@Composable
fun SendToX3Icon(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Canvas(modifier = modifier.size(28.dp)) {
        val strokeWidth = 2.2.dp.toPx()
        val centerX = size.width / 2f
        drawLine(
            color = color,
            start = Offset(centerX, size.height * 0.62f),
            end = Offset(centerX, size.height * 0.16f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, size.height * 0.16f),
            end = Offset(size.width * 0.32f, size.height * 0.35f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, size.height * 0.16f),
            end = Offset(size.width * 0.68f, size.height * 0.35f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        val tray = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.21f, size.height * 0.55f)
            lineTo(size.width * 0.21f, size.height * 0.82f)
            quadraticTo(size.width * 0.21f, size.height * 0.88f, size.width * 0.29f, size.height * 0.88f)
            lineTo(size.width * 0.71f, size.height * 0.88f)
            quadraticTo(size.width * 0.79f, size.height * 0.88f, size.width * 0.79f, size.height * 0.82f)
            lineTo(size.width * 0.79f, size.height * 0.55f)
        }
        drawPath(
            path = tray,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    visualTheme: CompanionVisualTheme,
    onSetVisualTheme: (CompanionVisualTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SettingsSheetContent(
            visualTheme = visualTheme,
            onSetVisualTheme = onSetVisualTheme,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun SettingsSheetContent(
    visualTheme: CompanionVisualTheme,
    onSetVisualTheme: (CompanionVisualTheme) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_settings)) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeChip(
                label = stringResource(R.string.expressive_theme),
                selected = visualTheme == CompanionVisualTheme.Expressive,
                onClick = { onSetVisualTheme(CompanionVisualTheme.Expressive) },
                modifier = Modifier.weight(1f),
            )
            ThemeChip(
                label = stringResource(R.string.quiet_theme),
                selected = visualTheme == CompanionVisualTheme.Quiet,
                onClick = { onSetVisualTheme(CompanionVisualTheme.Quiet) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingsValue(stringResource(R.string.settings_device), stringResource(R.string.settings_device_value))
        SettingsValue(
            stringResource(R.string.settings_notifications),
            stringResource(R.string.settings_notifications_value),
        )
        SettingsValue(stringResource(R.string.settings_gemini), stringResource(R.string.settings_gemini_value))
        SettingsValue(stringResource(R.string.settings_flights), stringResource(R.string.settings_flights_value))
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = {
            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onClick()
        },
        label = { Text(label) },
        modifier = modifier,
    )
}

@Composable
private fun SettingsValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
