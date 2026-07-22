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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xteink.companion.R

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
fun DeviceConnectionSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        DeviceConnectionSheetContent(onDismiss = onDismiss)
    }
}

@Composable
fun DeviceConnectionSheetContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialStep: DeviceSetupStep = DeviceSetupStep.Devices,
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
            DeviceSetupStep.Discover -> DiscoveryHandoff(model = selectedModel)
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
    val haptics = LocalHapticFeedback.current
    val initialPage = XteinkModels.indexOf(selectedModel).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { XteinkModels.size })
    var lastHapticPage by remember { mutableIntStateOf(initialPage) }
    LaunchedEffect(pagerState.settledPage) {
        val page = pagerState.settledPage
        onSelectedModel(XteinkModels[page])
        if (page != lastHapticPage) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            lastHapticPage = page
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.choose_device), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.choose_device_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 44.dp),
            pageSpacing = 12.dp,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        ) { page ->
            val model = XteinkModels[page]
            val selected = pagerState.settledPage == page
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
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
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
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
private fun DiscoveryHandoff(model: XteinkModel) {
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
            text = stringResource(R.string.discovery_not_available),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.search_nearby))
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
