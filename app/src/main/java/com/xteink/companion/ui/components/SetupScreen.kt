package com.xteink.companion.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.data.FirmwareSource
import com.xteink.companion.data.CloudBackupState
import com.xteink.companion.ui.DeviceUiState
import com.xteink.companion.ui.CompanionVisualTheme
import com.xteink.companion.ui.theme.LocalCompanionVisualTheme
import kotlinx.coroutines.launch

private enum class SetupPage {
    Welcome,
    Library,
    Device,
}

private val SetupPages = SetupPage.entries

private object SetupSpacing {
    val ScreenTop = 8.dp
    val ScreenBottom = 16.dp
    val ScreenHorizontal = 20.dp
    val ScreenTitleTop = 8.dp
    val ScreenTitleBottom = 12.dp
    val PagerHorizontal = 20.dp
    val PageSpacing = 12.dp
    val PageHorizontal = 24.dp
    val PageVertical = 20.dp
    val MajorGroup = 16.dp
    val ChipHorizontal = 16.dp
    val ChipVertical = 8.dp
    val InnerCard = 16.dp
    val Related = 4.dp
    val Group = 12.dp
    val Action = 8.dp
    val FullButtonHeight = 56.dp
    val TextButtonHeight = 48.dp
}

@Composable
fun SetupScreen(
    folderLinked: Boolean,
    onChooseBookFolder: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    device: DeviceUiState = DeviceUiState(),
    isDeviceTransportConnected: Boolean = false,
    onConnectDevice: (String) -> Unit = {},
    onCheckFirmware: (String, FirmwareSource) -> Unit = { _, _ -> },
    onFlashFirmware: () -> Unit = {},
    onFlashFirmwareManagedBle: () -> Unit = {},
    cloudBackupState: CloudBackupState = CloudBackupState(),
    cloudConsentAccepted: Boolean = false,
    onCloudConsentChanged: (Boolean) -> Unit = {},
    onConnectGoogle: () -> Unit = {},
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(SetupPages.indices),
        pageCount = { SetupPages.size },
    )
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var devicesVisible by remember { mutableStateOf(false) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }

    fun moveTo(page: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = SetupSpacing.ScreenTop, bottom = SetupSpacing.ScreenBottom),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SetupSpacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onFinish) { Text(stringResource(R.string.skip_setup)) }
        }
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(
                start = SetupSpacing.ScreenHorizontal,
                top = SetupSpacing.ScreenTitleTop,
                end = SetupSpacing.ScreenHorizontal,
                bottom = SetupSpacing.ScreenTitleBottom,
            ),
        )
        MagneticHorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = SetupSpacing.PagerHorizontal),
            pageSpacing = SetupSpacing.PageSpacing,
            colors = MagneticPagerColors(
                restingContainer = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
                restingContent = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContent = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .weight(1f)
                .clipToBounds(),
        ) { page, containerColor, contentColor ->
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (SetupPages[page]) {
                    SetupPage.Welcome -> WelcomeSetupPage(
                        illustrationActive = pagerState.settledPage == page,
                        cloudBackupState = cloudBackupState,
                        cloudConsentAccepted = cloudConsentAccepted,
                        onCloudConsentChanged = onCloudConsentChanged,
                        onConnectGoogle = onConnectGoogle,
                        onOpenLegal = { legalDocument = it },
                    )
                    SetupPage.Library -> LibrarySetupPage(
                        illustrationActive = pagerState.settledPage == page,
                        folderLinked = folderLinked,
                        onChooseBookFolder = onChooseBookFolder,
                        onContinue = { moveTo(SetupPage.Device.ordinal) },
                    )
                    SetupPage.Device -> DeviceSetupPage(
                        illustrationActive = pagerState.settledPage == page,
                        onConnectDevice = { devicesVisible = true },
                        onFinish = onFinish,
                    )
                }
            }
        }
        SetupPageIndicator(
            selectedPage = pagerState.settledPage,
            modifier = Modifier.padding(top = SetupSpacing.Group),
        )
    }

    if (devicesVisible) {
        DeviceConnectionSheet(
            onDismiss = { devicesVisible = false },
            device = device,
            isTransportConnected = isDeviceTransportConnected,
            onConnect = onConnectDevice,
            onCheckFirmware = onCheckFirmware,
            onFlashFirmware = onFlashFirmware,
            onFlashFirmwareManagedBle = onFlashFirmwareManagedBle,
            showFirmwareUpdate = true,
            initialStep = DeviceSetupStep.ChooseModel,
            startWithFirstTimeFlash = true,
        )
    }
    legalDocument?.let { document ->
        LegalDocumentDialog(document = document, onDismiss = { legalDocument = null })
    }
}

@Composable
private fun WelcomeSetupPage(
    illustrationActive: Boolean,
    cloudBackupState: CloudBackupState,
    cloudConsentAccepted: Boolean,
    onCloudConsentChanged: (Boolean) -> Unit,
    onConnectGoogle: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
) {
    SetupPageColumn {
        SetupPageLabel(step = 1, label = stringResource(R.string.setup_welcome_tab))
        WelcomeSetupIllustration(
            active = illustrationActive,
            description = stringResource(R.string.setup_welcome_illustration_description),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = stringResource(R.string.setup_value_proposition),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(SetupSpacing.InnerCard)) {
                Text(stringResource(R.string.setup_backup_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(SetupSpacing.Related))
                Text(
                    stringResource(R.string.setup_backup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SetupSpacing.Group))
                if (cloudBackupState.enabled) {
                    Text(
                        cloudBackupState.accountEmail ?: stringResource(R.string.setup_backup_connected),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = cloudConsentAccepted,
                            onCheckedChange = onCloudConsentChanged,
                        )
                        Text(
                            stringResource(R.string.setup_backup_consent),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(SetupSpacing.Related))
                    Row(horizontalArrangement = Arrangement.spacedBy(SetupSpacing.Action)) {
                        TextButton(onClick = { onOpenLegal(LegalDocument.Privacy) }) {
                            Text(stringResource(R.string.setup_privacy))
                        }
                        TextButton(onClick = { onOpenLegal(LegalDocument.Terms) }) {
                            Text(stringResource(R.string.setup_terms))
                        }
                    }
                    Spacer(Modifier.height(SetupSpacing.Action))
                    FilledTonalButton(
                        onClick = onConnectGoogle,
                        enabled = cloudConsentAccepted && !cloudBackupState.syncing,
                        modifier = Modifier.fillMaxWidth().height(SetupSpacing.FullButtonHeight),
                    ) {
                        Text(
                            stringResource(
                                if (cloudBackupState.syncing) R.string.setup_backup_connecting
                                else R.string.setup_backup_action,
                            ),
                        )
                    }
                }
                cloudBackupState.message?.let { message ->
                    Spacer(Modifier.height(SetupSpacing.Action))
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LibrarySetupPage(
    illustrationActive: Boolean,
    folderLinked: Boolean,
    onChooseBookFolder: () -> Unit,
    onContinue: () -> Unit,
) {
    SetupActionPage(
        content = {
            SetupPageLabel(step = 2, label = stringResource(R.string.setup_library_tab))
            LibrarySetupIllustration(
                active = illustrationActive,
                description = stringResource(R.string.setup_library_illustration_description),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = stringResource(R.string.setup_library_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.setup_library_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (folderLinked) R.string.folder_linked else R.string.folder_not_linked),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp),
                )
            }
        },
        actions = {
            FilledTonalButton(
                onClick = onChooseBookFolder,
                modifier = Modifier.fillMaxWidth().height(SetupSpacing.FullButtonHeight),
            ) {
                Text(stringResource(if (folderLinked) R.string.change_epub_folder else R.string.choose_book_folder))
            }
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(SetupSpacing.TextButtonHeight),
            ) {
                Text(stringResource(if (folderLinked) R.string.continue_setup else R.string.do_this_later))
            }
        },
    )
}

@Composable
private fun DeviceSetupPage(
    illustrationActive: Boolean,
    onConnectDevice: () -> Unit,
    onFinish: () -> Unit,
) {
    SetupActionPage(
        content = {
            SetupPageLabel(step = 3, label = stringResource(R.string.setup_device_tab))
            DeviceSetupIllustration(
                active = illustrationActive,
                description = stringResource(R.string.setup_device_illustration_description),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = stringResource(R.string.setup_device_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.setup_device_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
        actions = {
            FilledTonalButton(
                onClick = onConnectDevice,
                modifier = Modifier.fillMaxWidth().height(SetupSpacing.FullButtonHeight),
            ) {
                Text(stringResource(R.string.choose_device))
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(SetupSpacing.FullButtonHeight),
            ) {
                Text(stringResource(R.string.finish_setup))
            }
        },
    )
}

@Composable
private fun SetupPageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = SetupSpacing.PageHorizontal,
                vertical = SetupSpacing.PageVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(SetupSpacing.MajorGroup),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun SetupActionPage(
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        val verticalPadding = SetupSpacing.PageVertical
        val density = LocalDensity.current
        val viewportHeightPx = constraints.maxHeight - with(density) { verticalPadding.roundToPx() * 2 }
        val minimumGapPx = with(density) { SetupSpacing.MajorGroup.roundToPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = SetupSpacing.PageHorizontal,
                    vertical = verticalPadding,
                ),
        ) {
            Layout(
                content = {
                    Column(
                        modifier = Modifier.fillMaxWidth().layoutId("content"),
                        verticalArrangement = Arrangement.spacedBy(SetupSpacing.MajorGroup),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = content,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().layoutId("actions"),
                        verticalArrangement = Arrangement.spacedBy(SetupSpacing.Action),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = actions,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { measurables, layoutConstraints ->
                val childConstraints = layoutConstraints.copy(minHeight = 0)
                val contentPlaceable = measurables.first { it.layoutId == "content" }.measure(childConstraints)
                val actionsPlaceable = measurables.first { it.layoutId == "actions" }.measure(childConstraints)
                val placement = calculateSetupActionPlacement(
                    viewportHeight = viewportHeightPx.coerceAtLeast(0),
                    contentHeight = contentPlaceable.height,
                    actionsHeight = actionsPlaceable.height,
                    minimumGap = minimumGapPx,
                )
                layout(layoutConstraints.maxWidth, placement.layoutHeight) {
                    contentPlaceable.placeRelative(0, 0)
                    actionsPlaceable.placeRelative(0, placement.actionsY)
                }
            }
        }
    }
}

@Composable
private fun SetupPageLabel(step: Int, label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
        Text(
            text = stringResource(R.string.setup_step_label, step, SetupPages.size, label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(
                horizontal = SetupSpacing.ChipHorizontal,
                vertical = SetupSpacing.ChipVertical,
            ),
        )
    }
}

@Composable
private fun SetupPageIndicator(selectedPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetupPages.indices.forEach { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == selectedPage) 10.dp else 7.dp)
                    .background(
                        color = if (index == selectedPage) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeSetupIllustration(
    active: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val progress = onboardingMotionProgress(active = active, label = "welcome transfer")
    val signalColor = MaterialTheme.colorScheme.primary
    SetupIllustrationFrame(description = description, modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.onboarding_welcome),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            repeat(3) { index ->
                val phase = (progress + index / 3f) % 1f
                val pulse = 1f - kotlin.math.abs(phase - 0.5f) * 2f
                drawCircle(
                    color = signalColor.copy(alpha = 0.28f + pulse * 0.72f),
                    radius = (2.4f + pulse * 1.5f).dp.toPx(),
                    center = Offset(size.width * (0.465f + index * 0.035f), size.height * 0.47f),
                )
            }
        }
    }
}

@Composable
private fun LibrarySetupIllustration(
    active: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val progress = onboardingMotionProgress(active = active, label = "books filing")
    val bookFill = MaterialTheme.colorScheme.surfaceContainerHighest
    val bookOutline = MaterialTheme.colorScheme.primary
    SetupIllustrationFrame(description = description, modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val bookWidth = 22.dp.toPx()
            val bookHeight = 34.dp.toPx()
            val stroke = 2.5.dp.toPx()
            repeat(3) { index ->
                val phase = (progress + index * 0.24f) % 1f
                val alpha = when {
                    phase < 0.12f -> phase / 0.12f
                    phase > 0.86f -> (1f - phase) / 0.14f
                    else -> 1f
                }.coerceIn(0f, 1f)
                val x = size.width * (0.38f + index * 0.12f) - bookWidth / 2f
                val y = size.height * (0.06f + phase * 0.38f)
                drawRoundRect(
                    color = bookFill.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(bookWidth, bookHeight),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                )
                drawRoundRect(
                    color = bookOutline.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(bookWidth, bookHeight),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Stroke(stroke),
                )
                drawLine(
                    color = bookOutline.copy(alpha = alpha),
                    start = Offset(x + bookWidth * 0.28f, y + bookHeight * 0.27f),
                    end = Offset(x + bookWidth * 0.72f, y + bookHeight * 0.27f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.onboarding_library),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DeviceSetupIllustration(
    active: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val progress = onboardingMotionProgress(active = active, label = "device signal")
    val signalColor = MaterialTheme.colorScheme.primary
    SetupIllustrationFrame(description = description, modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            repeat(2) { index ->
                val phase = (progress + index * 0.38f) % 1f
                val alpha = (1f - phase).coerceIn(0f, 1f)
                val inset = (index * 9).dp.toPx()
                val arcWidth = 34.dp.toPx() + inset
                val arcHeight = 58.dp.toPx() + inset
                val top = (size.height - arcHeight) / 2f
                drawArc(
                    color = signalColor.copy(alpha = alpha),
                    startAngle = 125f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.12f - inset / 2f, top),
                    size = Size(arcWidth, arcHeight),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = signalColor.copy(alpha = alpha),
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.69f - inset / 2f, top),
                    size = Size(arcWidth, arcHeight),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.onboarding_device),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SetupIllustrationFrame(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(176.dp, 150.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun onboardingMotionProgress(active: Boolean, label: String): Float {
    if (!active || LocalInspectionMode.current || LocalCompanionVisualTheme.current == CompanionVisualTheme.Minimal) {
        return 0.46f
    }
    val transition = rememberInfiniteTransition(label = label)
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "$label progress",
    )
    return progress
}
