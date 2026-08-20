package com.xteink.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.data.CloudBackupState
import com.xteink.companion.ui.CompanionSurface
import com.xteink.companion.ui.CompanionColorMode
import com.xteink.companion.ui.CompanionVisualTheme
import com.xteink.companion.ui.DevicePresence
import com.xteink.companion.ui.RadioPolicyUiState
import com.xteink.companion.ui.devicePresence
import com.xteink.companion.ui.isFastWindowChoiceEnabled
import com.xteink.companion.ui.selectFastWindow
import com.xteink.companion.ui.selectSleepAfter
import com.xteink.companion.ui.sceneArtworkFor
import kotlinx.coroutines.launch

private val FastDiscoveryMinuteChoices = listOf(1, 5, 10)
private val SleepAfterMinuteChoices = listOf(5, 10, 20)
private val StandbyCheckInSecondChoices = listOf(30, 60, 120)
private val ConnectedIntervalSecondChoices = listOf(1, 2, 4)

@Composable
fun CompanionTopBar(
    hasManagedX3: Boolean,
    isX3TransportConnected: Boolean,
    isX3Reconnecting: Boolean,
    isX3Connecting: Boolean,
    requiresBluetoothReset: Boolean,
    transportBlocker: com.xteink.companion.data.LinkBlocker?,
    connectedDeviceModel: String?,
    batteryPercentage: Int?,
    charging: Boolean,
    onShowDevices: () -> Unit,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presence = devicePresence(
        hasManagedX3,
        isX3TransportConnected,
        isX3Reconnecting,
        requiresBluetoothReset,
        isX3Connecting,
        transportBlocker,
    )
    val settingsDescription = stringResource(R.string.open_settings)
    val devicesDescription = stringResource(
        if (presence == DevicePresence.Connected) R.string.open_connected_device else R.string.open_devices,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onShowDevices,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.semantics {
                contentDescription = devicesDescription
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceOutlineIcon()
                Column {
                    Text(
                        text = if (hasManagedX3) {
                            connectedDeviceModel ?: stringResource(R.string.xteink_device_short)
                        } else {
                            stringResource(R.string.devices_title)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(
                            when {
                                presence == DevicePresence.Connected -> R.string.settings_device_connected
                                presence == DevicePresence.NeedsBluetoothReset -> R.string.settings_device_bluetooth_reset
                                presence == DevicePresence.BluetoothOff -> R.string.settings_device_bluetooth_off
                                presence == DevicePresence.PermissionRequired -> R.string.settings_device_permission_required
                                presence == DevicePresence.BluetoothUnavailable -> R.string.settings_device_bluetooth_unavailable
                                presence == DevicePresence.Reconnecting -> R.string.settings_device_reconnecting
                                presence == DevicePresence.Connecting -> R.string.settings_device_connecting
                                presence == DevicePresence.Available -> R.string.settings_device_available
                                else -> R.string.settings_device_value
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (presence in setOf(
                                DevicePresence.NeedsBluetoothReset,
                                DevicePresence.BluetoothOff,
                                DevicePresence.PermissionRequired,
                                DevicePresence.BluetoothUnavailable,
                            )) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                batteryPercentage?.let {
                    X3BatteryIndicator(percentage = it, charging = charging)
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
internal fun X3BatteryIndicator(
    percentage: Int,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    val bounded = percentage.coerceIn(0, 100)
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val description = stringResource(
        if (charging) R.string.device_battery_charging_description else R.string.device_battery_description,
        bounded,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CircleShape,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(width = if (charging) 30.dp else 23.dp, height = 14.dp)) {
                val stroke = 1.5.dp.toPx()
                val batteryWidth = 19.dp.toPx()
                val batteryHeight = 11.dp.toPx()
                val top = (size.height - batteryHeight) / 2f
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2f, top),
                    size = androidx.compose.ui.geometry.Size(batteryWidth, batteryHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawRect(
                    color = outline,
                    topLeft = Offset(batteryWidth + stroke, top + 3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 5.dp.toPx()),
                )
                val innerWidth = (batteryWidth - 4.dp.toPx()) * bounded / 100f
                if (innerWidth > 0f) {
                    drawRect(
                        color = fill,
                        topLeft = Offset(2.dp.toPx(), top + 2.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(innerWidth, batteryHeight - 4.dp.toPx()),
                    )
                }
                if (charging) {
                    val boltX = 25.dp.toPx()
                    drawLine(
                        outline,
                        Offset(boltX + 2.dp.toPx(), 1.dp.toPx()),
                        Offset(boltX - 1.dp.toPx(), 7.dp.toPx()),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        outline,
                        Offset(boltX - 1.dp.toPx(), 7.dp.toPx()),
                        Offset(boltX + 2.dp.toPx(), 7.dp.toPx()),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        outline,
                        Offset(boltX + 2.dp.toPx(), 7.dp.toPx()),
                        Offset(boltX - 1.dp.toPx(), 13.dp.toPx()),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text("$bounded%", style = MaterialTheme.typography.labelMedium)
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
    Canvas(modifier = Modifier.size(22.dp)) {
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
    colorMode: CompanionColorMode,
    radioPolicy: RadioPolicyUiState,
    minimumReadingPageSeconds: Int,
    settingsSyncPending: Boolean,
    hasManagedDevice: Boolean,
    onSetVisualTheme: (CompanionVisualTheme) -> Unit,
    onSetColorMode: (CompanionColorMode) -> Unit,
    onSetRadioPolicy: (RadioPolicyUiState) -> Unit,
    onSetMinimumReadingPageSeconds: (Int) -> Unit,
    onOpenSetup: () -> Unit,
    cloudBackupState: CloudBackupState,
    onSyncGoogleBackup: () -> Unit,
    onDeleteGoogleBackup: () -> Unit,
    monetizationState: com.xteink.companion.monetization.MonetizationRuntimeState,
    onBuyAdFree: () -> Unit,
    onRestorePurchase: () -> Unit,
    onAdPrivacyOptions: () -> Unit,
    onOpenCommunitySource: () -> Unit,
    onDismiss: () -> Unit,
) {
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SettingsSheetContent(
            visualTheme = visualTheme,
            colorMode = colorMode,
            radioPolicy = radioPolicy,
            minimumReadingPageSeconds = minimumReadingPageSeconds,
            settingsSyncPending = settingsSyncPending,
            hasManagedDevice = hasManagedDevice,
            onSetVisualTheme = onSetVisualTheme,
            onSetColorMode = onSetColorMode,
            onSetRadioPolicy = onSetRadioPolicy,
            onSetMinimumReadingPageSeconds = onSetMinimumReadingPageSeconds,
            onOpenSetup = onOpenSetup,
            cloudBackupState = cloudBackupState,
            onSyncGoogleBackup = onSyncGoogleBackup,
            onDeleteGoogleBackup = onDeleteGoogleBackup,
            monetizationState = monetizationState,
            onBuyAdFree = onBuyAdFree,
            onRestorePurchase = onRestorePurchase,
            onAdPrivacyOptions = onAdPrivacyOptions,
            onOpenCommunitySource = onOpenCommunitySource,
            onOpenLegal = { legalDocument = it },
            onDismiss = onDismiss,
        )
    }
    legalDocument?.let { document ->
        LegalDocumentDialog(document = document, onDismiss = { legalDocument = null })
    }
}

@Composable
fun SettingsSheetContent(
    visualTheme: CompanionVisualTheme,
    colorMode: CompanionColorMode,
    radioPolicy: RadioPolicyUiState,
    minimumReadingPageSeconds: Int,
    settingsSyncPending: Boolean,
    hasManagedDevice: Boolean,
    onSetVisualTheme: (CompanionVisualTheme) -> Unit,
    onSetColorMode: (CompanionColorMode) -> Unit,
    onSetRadioPolicy: (RadioPolicyUiState) -> Unit,
    onSetMinimumReadingPageSeconds: (Int) -> Unit,
    onOpenSetup: () -> Unit,
    cloudBackupState: CloudBackupState,
    onSyncGoogleBackup: () -> Unit,
    onDeleteGoogleBackup: () -> Unit,
    monetizationState: com.xteink.companion.monetization.MonetizationRuntimeState,
    onBuyAdFree: () -> Unit,
    onRestorePurchase: () -> Unit,
    onAdPrivacyOptions: () -> Unit,
    onOpenCommunitySource: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
        Spacer(Modifier.height(8.dp))
        ColorModeCarousel(
            colorMode = colorMode,
            onSetColorMode = onSetColorMode,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_visual_style),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeChip(
                label = stringResource(R.string.expressive_theme),
                description = stringResource(R.string.expressive_theme_body),
                expressive = true,
                selected = visualTheme == CompanionVisualTheme.Expressive,
                onClick = { onSetVisualTheme(CompanionVisualTheme.Expressive) },
                modifier = Modifier.weight(1f),
            )
            ThemeChip(
                label = stringResource(R.string.minimal_theme),
                description = stringResource(R.string.minimal_theme_body),
                expressive = false,
                selected = visualTheme == CompanionVisualTheme.Minimal,
                onClick = { onSetVisualTheme(CompanionVisualTheme.Minimal) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_radio_policy), style = MaterialTheme.typography.titleMedium)
        if (hasManagedDevice) {
            Surface(
                color = if (settingsSyncPending) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (settingsSyncPending) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                shape = CircleShape,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(
                        if (settingsSyncPending) R.string.settings_waiting_for_x3 else R.string.settings_synced_to_x3,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        PolicyChoiceRow(
            label = stringResource(R.string.settings_fast_discovery),
            values = FastDiscoveryMinuteChoices,
            selected = radioPolicy.fastWindowMinutes,
            suffix = " min",
            optionEnabled = { minutes ->
                isFastWindowChoiceEnabled(minutes, radioPolicy.sleepAfterMinutes)
            },
            onSelect = { minutes -> onSetRadioPolicy(selectFastWindow(radioPolicy, minutes, SleepAfterMinuteChoices)) },
        )
        PolicyChoiceRow(
            label = stringResource(R.string.settings_standby_check_in),
            values = StandbyCheckInSecondChoices,
            selected = radioPolicy.standbyIntervalSeconds,
            suffix = " s",
            onSelect = { onSetRadioPolicy(radioPolicy.copy(standbyIntervalSeconds = it)) },
        )
        PolicyChoiceRow(
            label = stringResource(R.string.settings_connected_interval),
            values = ConnectedIntervalSecondChoices,
            selected = radioPolicy.connectedIntervalMs / 1_000,
            suffix = " s",
            onSelect = { onSetRadioPolicy(radioPolicy.copy(connectedIntervalMs = it * 1_000)) },
        )
        PolicyChoiceRow(
            label = stringResource(R.string.settings_sleep_after),
            values = SleepAfterMinuteChoices,
            selected = radioPolicy.sleepAfterMinutes,
            suffix = " min",
            onSelect = { minutes ->
                onSetRadioPolicy(selectSleepAfter(radioPolicy, minutes, FastDiscoveryMinuteChoices))
            },
        )
        Text(
            stringResource(R.string.settings_radio_policy_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_reader_refresh), style = MaterialTheme.typography.titleMedium)
        PolicyChoiceRow(
            label = stringResource(R.string.settings_full_refresh_pages),
            values = listOf(1, 5, 10, 15, 30),
            selected = radioPolicy.fullRefreshPages,
            suffix = "",
            onSelect = { onSetRadioPolicy(radioPolicy.copy(fullRefreshPages = it)) },
        )
        Text(
            stringResource(R.string.settings_full_refresh_pages_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_controls), style = MaterialTheme.typography.titleMedium)
        val instantLabel = stringResource(R.string.instant)
        PolicyChoiceRow(
            label = stringResource(R.string.settings_power_button_hold),
            values = listOf(0, 1, 2),
            selected = radioPolicy.powerButtonHoldMs / 1_000,
            suffix = " s",
            valueLabel = { seconds -> if (seconds == 0) instantLabel else "$seconds s" },
            onSelect = { seconds -> onSetRadioPolicy(radioPolicy.copy(powerButtonHoldMs = seconds * 1_000)) },
        )
        Text(
            stringResource(R.string.settings_power_button_hold_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_reading_stats), style = MaterialTheme.typography.titleMedium)
        val offLabel = stringResource(R.string.off)
        PolicyChoiceRow(
            label = stringResource(R.string.settings_minimum_page_time),
            values = listOf(0, 5, 10, 15),
            selected = minimumReadingPageSeconds,
            suffix = " s",
            valueLabel = { value -> if (value == 0) offLabel else "$value s" },
            onSelect = onSetMinimumReadingPageSeconds,
        )
        Text(
            stringResource(R.string.settings_minimum_page_time_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        GoogleBackupSettingsCard(
            state = cloudBackupState,
            onConnectOrSync = onSyncGoogleBackup,
            onDelete = onDeleteGoogleBackup,
            onOpenLegal = onOpenLegal,
        )
        Spacer(Modifier.height(12.dp))
        MonetizationSettingsCard(
            state = monetizationState,
            onBuy = onBuyAdFree,
            onRestore = onRestorePurchase,
            onPrivacyOptions = onAdPrivacyOptions,
            onOpenCommunitySource = onOpenCommunitySource,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_legal), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { onOpenLegal(LegalDocument.Privacy) }) {
                Text(stringResource(R.string.setup_privacy))
            }
            TextButton(onClick = { onOpenLegal(LegalDocument.Terms) }) {
                Text(stringResource(R.string.setup_terms))
            }
            TextButton(onClick = { onOpenLegal(LegalDocument.Notices) }) {
                Text(stringResource(R.string.open_source_notices))
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onOpenSetup,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.run_setup_again), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.run_setup_again_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun GoogleBackupSettingsCard(
    state: CloudBackupState,
    onConnectOrSync: () -> Unit,
    onDelete: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
) {
    Text(stringResource(R.string.settings_google_backup), style = MaterialTheme.typography.titleMedium)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.enabled) {
                Text(
                    state.accountName ?: state.accountEmail ?: stringResource(R.string.google_backup_connected),
                    style = MaterialTheme.typography.titleSmall,
                )
                state.accountEmail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.google_backup_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onConnectOrSync, enabled = !state.syncing) {
                        Text(
                            stringResource(
                                if (state.syncing) R.string.google_backup_syncing
                                else R.string.google_backup_sync_now,
                            ),
                        )
                    }
                    TextButton(onClick = onDelete, enabled = !state.syncing) {
                        Text(stringResource(R.string.google_backup_delete_disconnect))
                    }
                }
            } else {
                Text(stringResource(R.string.google_backup_not_connected), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.google_backup_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.google_backup_consent_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onConnectOrSync,
                    enabled = !state.syncing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        stringResource(
                            if (state.syncing) R.string.setup_backup_connecting
                            else R.string.google_backup_connect,
                        ),
                    )
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onOpenLegal(LegalDocument.Privacy) }) {
                    Text(stringResource(R.string.setup_privacy))
                }
                TextButton(onClick = { onOpenLegal(LegalDocument.Terms) }) {
                    Text(stringResource(R.string.setup_terms))
                }
            }
        }
    }
}

@Composable
private fun PolicyChoiceRow(
    label: String,
    values: List<Int>,
    selected: Int,
    suffix: String,
    valueLabel: (Int) -> String = { "$it$suffix" },
    optionEnabled: (Int) -> Boolean = { true },
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            values.forEach { value ->
                val isSelected = selected == value
                val isEnabled = optionEnabled(value)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .alpha(if (isEnabled) 1f else 0.38f)
                        .selectable(
                            selected = isSelected,
                            enabled = isEnabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) },
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            valueLabel(value),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorModeCarousel(
    colorMode: CompanionColorMode,
    onSetColorMode: (CompanionColorMode) -> Unit,
) {
    val modes = CompanionColorMode.entries
    val pagerState = rememberPagerState(
        initialPage = colorMode.ordinal,
        pageCount = { modes.size },
    )
    val scope = rememberCoroutineScope()
    LaunchedEffect(colorMode) {
        if (pagerState.settledPage != colorMode.ordinal) {
            pagerState.animateScrollToPage(colorMode.ordinal)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        val settledMode = modes[pagerState.settledPage]
        if (settledMode != colorMode) onSetColorMode(settledMode)
    }

    MagneticHorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 44.dp),
        pageSpacing = 12.dp,
        colors = MagneticPagerColors(
            restingContainer = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainer = MaterialTheme.colorScheme.secondaryContainer,
            restingContent = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContent = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().height(152.dp).selectableGroup(),
    ) { page, containerColor, contentColor ->
        val mode = modes[page]
        val artwork = sceneArtworkFor(mode)
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxSize().selectable(
                selected = pagerState.settledPage == page,
                role = Role.RadioButton,
                onClick = { scope.launch { pagerState.animateScrollToPage(page) } },
            ),
        ) {
            Column {
                Image(
                    painter = painterResource(artwork.phonePreview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Text(
                    text = stringResource(
                        if (mode == CompanionColorMode.Light) R.string.light_mode else R.string.dark_mode,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    description: String,
    expressive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onClick()
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            ThemePreview(expressive = expressive, selected = selected)
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemePreview(expressive: Boolean, selected: Boolean) {
    val ink = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
        if (expressive) {
            drawCircle(accent, radius = 11.dp.toPx(), center = Offset(13.dp.toPx(), center.y))
            drawRoundRect(
                color = ink,
                topLeft = Offset(32.dp.toPx(), 5.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width - 38.dp.toPx(), 9.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
            )
            drawRoundRect(
                color = ink.copy(alpha = 0.45f),
                topLeft = Offset(32.dp.toPx(), 20.dp.toPx()),
                size = androidx.compose.ui.geometry.Size((size.width - 38.dp.toPx()) * 0.68f, 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
        } else {
            drawLine(ink, Offset(0f, 7.dp.toPx()), Offset(size.width, 7.dp.toPx()), 2.dp.toPx())
            drawLine(ink.copy(alpha = 0.65f), Offset(0f, 17.dp.toPx()), Offset(size.width * 0.78f, 17.dp.toPx()), 2.dp.toPx())
            drawLine(ink.copy(alpha = 0.4f), Offset(0f, 27.dp.toPx()), Offset(size.width * 0.55f, 27.dp.toPx()), 2.dp.toPx())
        }
    }
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
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(132.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
