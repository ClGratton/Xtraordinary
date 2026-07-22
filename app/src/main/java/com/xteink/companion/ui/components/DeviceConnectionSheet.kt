package com.xteink.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.ui.DeviceUiState
import com.xteink.companion.ui.FirmwareCheckPhase

enum class DeviceSetupStep {
    Devices,
    ChooseModel,
    Prepare,
    Discover,
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
    isConnected: Boolean = false,
    onConnect: (String) -> Unit = {},
    onCheckFirmware: (String) -> Unit = {},
    onFlashFirmware: () -> Unit = {},
    showFirmwareUpdate: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        DeviceConnectionSheetContent(
            onDismiss = onDismiss,
            device = device,
            isConnected = isConnected,
            onConnect = onConnect,
            onCheckFirmware = onCheckFirmware,
            onFlashFirmware = onFlashFirmware,
            showFirmwareUpdate = showFirmwareUpdate,
        )
    }
}

@Composable
fun DeviceConnectionSheetContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialStep: DeviceSetupStep = DeviceSetupStep.Devices,
    device: DeviceUiState = DeviceUiState(),
    isConnected: Boolean = false,
    onConnect: (String) -> Unit = {},
    onCheckFirmware: (String) -> Unit = {},
    onFlashFirmware: () -> Unit = {},
    showFirmwareUpdate: Boolean = true,
) {
    var stepName by rememberSaveable { mutableStateOf(initialStep.name) }
    var selectedModelName by rememberSaveable { mutableStateOf(XteinkModel.X3.name) }
    val step = DeviceSetupStep.valueOf(stepName)
    val selectedModel = XteinkModel.valueOf(selectedModelName)

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
                    DeviceSetupStep.Prepare -> DeviceSetupStep.ChooseModel
                    DeviceSetupStep.Discover -> DeviceSetupStep.Prepare
                }.name
            },
            onDismiss = onDismiss,
        )
        Spacer(Modifier.height(14.dp))
        when (step) {
            DeviceSetupStep.Devices -> EmptyDevicesState(
                onConnect = { stepName = DeviceSetupStep.ChooseModel.name },
            )
            DeviceSetupStep.ChooseModel -> ModelPicker(
                selectedModel = selectedModel,
                onSelectedModel = { selectedModelName = it.name },
                onContinue = { stepName = DeviceSetupStep.Prepare.name },
            )
            DeviceSetupStep.Prepare -> PrepareDevice(
                model = selectedModel,
                onContinue = { stepName = DeviceSetupStep.Discover.name },
            )
            DeviceSetupStep.Discover -> DiscoveryHandoff(
                model = selectedModel,
                device = device,
                isConnected = isConnected,
                onConnect = { onConnect(selectedModel.label) },
                onCheckFirmware = { onCheckFirmware(selectedModel.label) },
                onFlashFirmware = onFlashFirmware,
                showFirmwareUpdate = showFirmwareUpdate,
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
private fun PrepareDevice(model: XteinkModel, onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.prepare_device, model.label),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        SetupStep(1, stringResource(R.string.prepare_device_power))
        SetupStep(2, stringResource(R.string.prepare_device_companion))
        SetupStep(3, stringResource(R.string.prepare_device_nearby))
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.device_is_ready))
        }
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), fontWeight = FontWeight.Bold)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DiscoveryHandoff(
    model: XteinkModel,
    device: DeviceUiState,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onCheckFirmware: () -> Unit,
    onFlashFirmware: () -> Unit,
    showFirmwareUpdate: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DeviceModelIcon(
            modifier = Modifier.size(74.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.ready_to_find_device, model.label),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = device.message ?: stringResource(R.string.discovery_ready_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onConnect,
            enabled = device.linkPhase !in setOf("Scanning", "Connecting") && !isConnected,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            if (device.linkPhase == "Scanning" || device.linkPhase == "Connecting") {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
            }
            Text(stringResource(if (isConnected) R.string.device_connected else R.string.search_nearby))
        }
        if (showFirmwareUpdate) {
            Spacer(Modifier.height(18.dp))
            FirmwareUpdatePanel(
                device = device,
                model = model.label,
                isConnected = isConnected,
                onCheckFirmware = onCheckFirmware,
                onFlashFirmware = onFlashFirmware,
            )
        }
    }
}

@Composable
private fun FirmwareUpdatePanel(
    device: DeviceUiState,
    model: String,
    isConnected: Boolean,
    onCheckFirmware: () -> Unit,
    onFlashFirmware: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.firmware_update), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    device.latestFirmwareVersion != null -> stringResource(
                        R.string.latest_firmware_ready,
                        device.latestFirmwareVersion,
                        model,
                    )
                    else -> stringResource(R.string.firmware_polls_github)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            device.firmwareProgress?.let { progress ->
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
            when (device.firmwareCheckPhase) {
                FirmwareCheckPhase.Available -> Button(
                    onClick = onFlashFirmware,
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(if (isConnected) R.string.flash_latest_firmware else R.string.connect_to_flash)) }
                FirmwareCheckPhase.Downloading, FirmwareCheckPhase.Transferring -> FilledTonalButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.preparing_firmware)) }
                FirmwareCheckPhase.Complete -> Text(
                    stringResource(R.string.firmware_restart_message),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                FirmwareCheckPhase.UpToDate -> Text(
                    stringResource(R.string.firmware_up_to_date),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                else -> FilledTonalButton(
                    onClick = onCheckFirmware,
                    enabled = device.firmwareCheckPhase != FirmwareCheckPhase.Checking,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (device.firmwareCheckPhase == FirmwareCheckPhase.Checking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(stringResource(R.string.check_github_firmware))
                }
            }
        }
    }
}

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
