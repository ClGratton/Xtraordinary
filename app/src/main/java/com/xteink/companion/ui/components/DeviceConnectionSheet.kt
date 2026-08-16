package com.xteink.companion.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.data.FirmwareSource
import com.xteink.companion.data.UsbFlashPhase
import com.xteink.companion.ui.DeviceUiState
import com.xteink.companion.ui.DevicePresence
import com.xteink.companion.ui.FirmwareCheckPhase
import com.xteink.companion.ui.devicePresence

enum class DeviceSetupStep {
    Devices,
    ChooseModel,
    Discover,
    FirmwareDefault,
    FirmwareSources,
}

enum class DeviceConnectionPath {
    FirstTimeFlash,
    AlreadyFlashed,
}

private enum class XteinkModel(val label: String) {
    X3("X3"),
    X4("X4"),
    X4Pro("X4 Pro"),
}

private val XteinkModels = XteinkModel.entries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectionSheet(
    onDismiss: () -> Unit,
    device: DeviceUiState = DeviceUiState(),
    hasManagedDevice: Boolean = false,
    managedDeviceModel: String? = null,
    isTransportConnected: Boolean = false,
    onConnect: (String) -> Unit = {},
    onCheckFirmware: (String, FirmwareSource) -> Unit = { _, _ -> },
    onFlashFirmware: () -> Unit = {},
    onResetUsbSetup: () -> Unit = {},
    showFirmwareUpdate: Boolean = true,
    initialStep: DeviceSetupStep = DeviceSetupStep.Devices,
    startWithFirstTimeFlash: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        DeviceConnectionSheetContent(
            onDismiss = onDismiss,
            device = device,
            hasManagedDevice = hasManagedDevice,
            managedDeviceModel = managedDeviceModel,
            isTransportConnected = isTransportConnected,
            onConnect = onConnect,
            onCheckFirmware = onCheckFirmware,
            onFlashFirmware = onFlashFirmware,
            onResetUsbSetup = onResetUsbSetup,
            showFirmwareUpdate = showFirmwareUpdate,
            initialStep = initialStep,
            startWithFirstTimeFlash = startWithFirstTimeFlash,
        )
    }
}

@Composable
fun DeviceConnectionSheetContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialStep: DeviceSetupStep = DeviceSetupStep.Devices,
    device: DeviceUiState = DeviceUiState(),
    hasManagedDevice: Boolean = false,
    managedDeviceModel: String? = null,
    isTransportConnected: Boolean = false,
    onConnect: (String) -> Unit = {},
    onCheckFirmware: (String, FirmwareSource) -> Unit = { _, _ -> },
    onFlashFirmware: () -> Unit = {},
    onResetUsbSetup: () -> Unit = {},
    showFirmwareUpdate: Boolean = true,
    startWithFirstTimeFlash: Boolean = false,
) {
    var stepName by rememberSaveable { mutableStateOf(initialStep.name) }
    var selectedModelName by rememberSaveable { mutableStateOf(XteinkModel.X3.name) }
    var connectionPathName by rememberSaveable {
        mutableStateOf(
            if (startWithFirstTimeFlash) DeviceConnectionPath.FirstTimeFlash.name
            else DeviceConnectionPath.AlreadyFlashed.name,
        )
    }
    var firmwareSourceName by rememberSaveable { mutableStateOf(FirmwareSource.Xtraordinary.name) }
    val step = DeviceSetupStep.valueOf(stepName)
    val selectedModel = XteinkModel.valueOf(selectedModelName)
    val connectionPath = DeviceConnectionPath.valueOf(connectionPathName)
    val firmwareSource = FirmwareSource.valueOf(firmwareSourceName)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 30.dp),
    ) {
        SheetHeader(
            step = step,
            onBack = {
                stepName = when (step) {
                    DeviceSetupStep.Devices -> DeviceSetupStep.Devices
                    DeviceSetupStep.ChooseModel -> DeviceSetupStep.Devices
                    DeviceSetupStep.Discover -> DeviceSetupStep.ChooseModel
                    DeviceSetupStep.FirmwareDefault -> DeviceSetupStep.Discover
                    DeviceSetupStep.FirmwareSources -> DeviceSetupStep.FirmwareDefault
                }.name
            },
            onDismiss = onDismiss,
        )
        Spacer(Modifier.height(14.dp))
        when (step) {
            DeviceSetupStep.Devices -> {
                if (hasManagedDevice) {
                    ManagedDeviceState(
                        model = managedDeviceModel ?: stringResource(R.string.xteink_device_short),
                        device = device,
                        presence = devicePresence(
                            hasManagedDevice = true,
                            transportConnected = isTransportConnected,
                            reconnecting = device.reconnecting,
                            requiresBluetoothReset = device.requiresBluetoothReset,
                            connecting = device.linkPhase == "Scanning" || device.linkPhase == "Connecting",
                            blocker = device.transportBlocker,
                        ),
                        onFirmware = {
                            selectedModelName = XteinkModel.X3.name
                            stepName = DeviceSetupStep.FirmwareDefault.name
                        },
                        onConnectAnother = { stepName = DeviceSetupStep.ChooseModel.name },
                        onRetry = { onConnect(managedDeviceModel ?: XteinkModel.X3.label) },
                    )
                } else {
                    EmptyDevicesState(
                        onConnect = { stepName = DeviceSetupStep.ChooseModel.name },
                    )
                }
            }
            DeviceSetupStep.ChooseModel -> ModelPicker(
                selectedModel = selectedModel,
                onSelectedModel = { selectedModelName = it.name },
                onContinue = { stepName = DeviceSetupStep.Discover.name },
            )
            DeviceSetupStep.Discover -> DiscoveryHandoff(
                model = selectedModel,
                device = device,
                isConnected = isTransportConnected,
                connectionPath = connectionPath,
                onConnectionPath = { connectionPathName = it.name },
                onConnect = { onConnect(selectedModel.label) },
                onInstallFirstTime = { stepName = DeviceSetupStep.FirmwareDefault.name },
                showFirmwareUpdate = showFirmwareUpdate,
            )
            DeviceSetupStep.FirmwareDefault -> DefaultFirmwarePage(
                model = selectedModel,
                device = device,
                onCheckFirmware = { onCheckFirmware(selectedModel.label, FirmwareSource.Xtraordinary) },
                onFlashFirmware = onFlashFirmware,
                onChooseAlternative = {
                    firmwareSourceName = FirmwareSource.XteinkStock.name
                    onCheckFirmware(selectedModel.label, FirmwareSource.XteinkStock)
                    stepName = DeviceSetupStep.FirmwareSources.name
                },
            )
            DeviceSetupStep.FirmwareSources -> FirmwareSourcePicker(
                model = selectedModel,
                device = device,
                selectedSource = firmwareSource,
                onSelectSource = { source ->
                    firmwareSourceName = source.name
                    onCheckFirmware(selectedModel.label, source)
                },
                onFlashFirmware = onFlashFirmware,
                onResetUsbSetup = onResetUsbSetup,
            )
        }
    }
}

@Composable
private fun SheetHeader(
    step: DeviceSetupStep,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step == DeviceSetupStep.Devices) {
            Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.headlineMedium)
        } else {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun ManagedDeviceState(
    model: String,
    device: DeviceUiState,
    presence: DevicePresence,
    onFirmware: () -> Unit,
    onConnectAnother: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val status = stringResource(
        when (presence) {
            DevicePresence.Connected -> R.string.device_connected
            DevicePresence.NeedsBluetoothReset -> R.string.settings_device_bluetooth_reset
            DevicePresence.BluetoothOff -> R.string.settings_device_bluetooth_off
            DevicePresence.PermissionRequired -> R.string.settings_device_permission_required
            DevicePresence.BluetoothUnavailable -> R.string.settings_device_bluetooth_unavailable
            DevicePresence.Reconnecting -> R.string.settings_device_reconnecting
            DevicePresence.Connecting -> R.string.settings_device_connecting
            DevicePresence.Available -> R.string.device_available
            DevicePresence.None -> R.string.settings_device_value
        },
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape,
                ) {
                    DeviceModelIcon(
                        modifier = Modifier.padding(16.dp).size(42.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(model, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelLarge,
                        color = when (presence) {
                            DevicePresence.Connected -> MaterialTheme.colorScheme.primary
                            DevicePresence.NeedsBluetoothReset -> MaterialTheme.colorScheme.error
                            DevicePresence.BluetoothOff,
                            DevicePresence.PermissionRequired,
                            DevicePresence.BluetoothUnavailable -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    device.firmwareVersion?.takeIf { it.isNotBlank() }?.let { version ->
                        Text(
                            text = stringResource(R.string.device_firmware_version, version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    device.batteryPercentage?.let { percentage ->
                        X3BatteryIndicator(
                            percentage = percentage,
                            charging = device.charging,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        if (device.requiresBluetoothReset) {
            Text(
                text = stringResource(R.string.bluetooth_reset_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }.recoverCatching {
                        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    }.onFailure {
                        onRetry()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) {
                Text(stringResource(R.string.fix_bluetooth))
            }
        } else managedDeviceStatusMessage(presence, device.message)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onFirmware,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.install_firmware))
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
            onClick = onConnectAnother,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.connect_a_device))
        }
    }
}

/** A passive discovery miss is already represented by Available for a managed X3. */
internal fun managedDeviceStatusMessage(presence: DevicePresence, message: String?): String? =
    message?.takeIf { it.isNotBlank() && presence != DevicePresence.Available }

@Composable
private fun EmptyDevicesState(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = CircleShape,
        ) {
            DeviceModelIcon(
                modifier = Modifier.padding(22.dp).size(48.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.no_connected_devices),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.no_connected_devices_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.connect_a_device))
        }
    }
}

@Composable
private fun ModelPicker(
    selectedModel: XteinkModel,
    onSelectedModel: (XteinkModel) -> Unit,
    onContinue: () -> Unit,
) {
    val initialPage = XteinkModels.indexOf(selectedModel).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { XteinkModels.size })
    LaunchedEffect(pagerState.settledPage) {
        val page = pagerState.settledPage
        onSelectedModel(XteinkModels[page])
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.choose_device), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.choose_device_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        MagneticHorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 44.dp),
            pageSpacing = 12.dp,
            colors = MagneticPagerColors(
                restingContainer = MaterialTheme.colorScheme.surfaceContainer,
                selectedContainer = MaterialTheme.colorScheme.primaryContainer,
                restingContent = MaterialTheme.colorScheme.onSurface,
                selectedContent = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { page, containerColor, contentColor ->
            val model = XteinkModels[page]
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(218.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DeviceModelIcon(
                        modifier = Modifier.size(76.dp),
                        color = contentColor,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(model.label, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = stringResource(R.string.xteink_device),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            XteinkModels.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == pagerState.settledPage) 10.dp else 7.dp)
                        .background(
                            color = if (index == pagerState.settledPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.continue_with_device, selectedModel.label))
        }
    }
}

@Composable
private fun DiscoveryHandoff(
    model: XteinkModel,
    device: DeviceUiState,
    isConnected: Boolean,
    connectionPath: DeviceConnectionPath,
    onConnectionPath: (DeviceConnectionPath) -> Unit,
    onConnect: () -> Unit,
    onInstallFirstTime: () -> Unit,
    showFirmwareUpdate: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DeviceModelIcon(
            modifier = Modifier.size(56.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.connect_device_model, model.label),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        ExpandingChoiceRow(
            choices = listOf(
                ExpandingChoice(
                    key = DeviceConnectionPath.FirstTimeFlash.name,
                    title = stringResource(R.string.first_time_flash),
                    body = stringResource(R.string.first_time_flash_body),
                ),
                ExpandingChoice(
                    key = DeviceConnectionPath.AlreadyFlashed.name,
                    title = stringResource(R.string.already_flashed),
                    body = stringResource(R.string.already_flashed_body),
                ),
            ),
            selectedKey = connectionPath.name,
            onSelect = { onConnectionPath(DeviceConnectionPath.valueOf(it)) },
            optionHeight = 226.dp,
            selectedContainer = MaterialTheme.colorScheme.secondaryContainer,
            selectedContent = MaterialTheme.colorScheme.onSecondaryContainer,
        ) { key ->
            if (key == DeviceConnectionPath.FirstTimeFlash.name) {
                Button(
                    onClick = onInstallFirstTime,
                    enabled = showFirmwareUpdate && model == XteinkModel.X3,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    Text(
                        stringResource(
                            if (showFirmwareUpdate && model == XteinkModel.X3) {
                                R.string.install_firmware
                            } else {
                                R.string.firmware_not_available
                            },
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = device.linkPhase !in setOf("Scanning", "Connecting") && !isConnected,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    if (device.linkPhase == "Scanning" || device.linkPhase == "Connecting") {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        stringResource(if (isConnected) R.string.device_connected else R.string.search_nearby),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = connectionPath == DeviceConnectionPath.AlreadyFlashed && !device.message.isNullOrBlank(),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Text(
                text = device.message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun DefaultFirmwarePage(
    model: XteinkModel,
    device: DeviceUiState,
    onCheckFirmware: () -> Unit,
    onFlashFirmware: () -> Unit,
    onChooseAlternative: () -> Unit,
) {
    LaunchedEffect(model) {
        if (model == XteinkModel.X3) onCheckFirmware()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.install_firmware_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.install_firmware_body, model.label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        FirmwareSourceCard(
            source = FirmwareSource.Xtraordinary,
            device = device,
            selected = true,
            onSelect = null,
            onFlashFirmware = onFlashFirmware,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onChooseAlternative, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.choose_another_firmware))
        }
    }
}

@Composable
private fun FirmwareSourcePicker(
    model: XteinkModel,
    device: DeviceUiState,
    selectedSource: FirmwareSource,
    onSelectSource: (FirmwareSource) -> Unit,
    onFlashFirmware: () -> Unit,
    onResetUsbSetup: () -> Unit,
) {
    val alternatives = listOf(
        FirmwareSource.LocalFile,
        FirmwareSource.XteinkStock,
        FirmwareSource.CrossPoint,
        FirmwareSource.CrossInk,
    )
    var resetConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.choose_firmware), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.choose_firmware_body, model.label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        alternatives.forEachIndexed { index, source ->
            FirmwareSourceCard(
                source = source,
                device = device,
                selected = source == selectedSource,
                onSelect = if (source == selectedSource) {
                    null
                } else {
                    { onSelectSource(source) }
                },
                onFlashFirmware = onFlashFirmware,
            )
            if (index != alternatives.lastIndex) Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(
            onClick = { resetConfirmationVisible = true },
            enabled = device.usbConnected,
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        ) {
            Text(stringResource(R.string.reset_x3_setup))
        }
        Text(
            text = stringResource(
                if (device.usbPhase == UsbFlashPhase.ReconnectRequired.name) {
                    R.string.reconnect_x3_usb
                } else {
                    R.string.reset_x3_setup_body
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (resetConfirmationVisible) {
            AlertDialog(
                onDismissRequest = { resetConfirmationVisible = false },
                title = { Text(stringResource(R.string.reset_x3_setup)) },
                text = { Text(stringResource(R.string.reset_x3_setup_confirm)) },
                confirmButton = {
                    Button(onClick = {
                        resetConfirmationVisible = false
                        onResetUsbSetup()
                    }) { Text(stringResource(R.string.reset)) }
                },
                dismissButton = {
                    TextButton(onClick = { resetConfirmationVisible = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun FirmwareSourceCard(
    source: FirmwareSource,
    device: DeviceUiState,
    selected: Boolean,
    onSelect: (() -> Unit)?,
    onFlashFirmware: () -> Unit,
) {
    val sourcePhase = if (device.firmwareSource == source) device.firmwareCheckPhase else FirmwareCheckPhase.Idle
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier)
            .animateContentSize(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(firmwareSourceTitle(source), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = firmwareSourceBody(source),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column {
                    device.latestFirmwareVersion
                        ?.takeIf { device.firmwareSource == source }
                        ?.let { version ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(
                                    if (source == FirmwareSource.LocalFile) {
                                        R.string.firmware_selected_version
                                    } else {
                                        R.string.firmware_version
                                    },
                                    version,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    device.message
                        ?.takeIf { source == FirmwareSource.LocalFile && device.firmwareSource == source }
                        ?.let { message ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    device.firmwareProgress?.let { progress ->
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    FirmwareInstallAction(
                        phase = sourcePhase,
                        device = device,
                        onFlashFirmware = onFlashFirmware,
                    )
                }
            }
        }
    }
}

@Composable
private fun FirmwareInstallAction(
    phase: FirmwareCheckPhase,
    device: DeviceUiState,
    onFlashFirmware: () -> Unit,
) {
    val canFlash = device.usbConnected
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.96f))
        },
        label = "firmware install action",
    ) { currentPhase ->
        when (currentPhase) {
            FirmwareCheckPhase.Available, FirmwareCheckPhase.UpToDate -> Column {
                Button(
                    onClick = onFlashFirmware,
                    enabled = canFlash,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                ) {
                    Text(
                        stringResource(if (canFlash) R.string.install_firmware else R.string.wake_x3_to_flash),
                        textAlign = TextAlign.Center,
                    )
                }
                if (!canFlash) {
                    Text(
                        text = stringResource(R.string.wake_x3_to_flash_help),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            FirmwareCheckPhase.Downloading, FirmwareCheckPhase.Transferring -> FilledTonalButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    device.usbMessage ?: stringResource(R.string.preparing_firmware),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            FirmwareCheckPhase.Complete -> Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(
                            if (device.usbPhase == UsbFlashPhase.ReconnectRequired.name) {
                                R.string.reconnect_x3_usb
                            } else {
                                R.string.firmware_installed_short
                            },
                        ),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            FirmwareCheckPhase.Error -> FilledTonalButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            ) {
                Text(device.message ?: stringResource(R.string.firmware_check_failed), textAlign = TextAlign.Center)
            }
            else -> FilledTonalButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.finding_latest_firmware))
            }
        }
    }
}

@Composable
private fun firmwareSourceTitle(source: FirmwareSource): String = stringResource(
    when (source) {
        FirmwareSource.Xtraordinary -> R.string.firmware_xtraordinary_title
        FirmwareSource.LocalFile -> R.string.firmware_local_file_title
        FirmwareSource.XteinkStock -> R.string.firmware_xteink_stock_title
        FirmwareSource.CrossPoint -> R.string.firmware_crosspoint_title
        FirmwareSource.CrossInk -> R.string.firmware_crossink_title
    },
)

@Composable
private fun firmwareSourceBody(source: FirmwareSource): String = stringResource(
    when (source) {
        FirmwareSource.Xtraordinary -> R.string.firmware_xtraordinary_body
        FirmwareSource.LocalFile -> R.string.firmware_local_file_body
        FirmwareSource.XteinkStock -> R.string.firmware_xteink_stock_body
        FirmwareSource.CrossPoint -> R.string.firmware_crosspoint_body
        FirmwareSource.CrossInk -> R.string.firmware_crossink_body
    },
)

@Composable
private fun DeviceModelIcon(
    modifier: Modifier,
    color: Color,
) {
    val description = stringResource(R.string.device_outline_description)
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val stroke = size.minDimension * 0.065f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.20f, size.height * 0.04f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.90f),
            cornerRadius = CornerRadius(size.minDimension * 0.10f),
            style = Stroke(stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.39f, size.height * 0.83f),
            end = Offset(size.width * 0.61f, size.height * 0.83f),
            strokeWidth = stroke,
        )
    }
}
