package com.xteink.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import kotlinx.coroutines.launch

private enum class SetupPage {
    Welcome,
    Library,
    Device,
}

private val SetupPages = SetupPage.entries

@Composable
fun SetupScreen(
    folderLinked: Boolean,
    onChooseBookFolder: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(SetupPages.indices),
        pageCount = { SetupPages.size },
    )
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var devicesVisible by remember { mutableStateOf(false) }

    fun moveTo(page: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 10.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onFinish) { Text(stringResource(R.string.skip_setup)) }
        }
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        MagneticHorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 18.dp),
            pageSpacing = 12.dp,
            colors = MagneticPagerColors(
                restingContainer = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
                restingContent = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContent = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.weight(1f),
        ) { page, containerColor, contentColor ->
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (SetupPages[page]) {
                    SetupPage.Welcome -> WelcomeSetupPage(
                        contentColor = contentColor,
                        onContinue = { moveTo(SetupPage.Library.ordinal) },
                    )
                    SetupPage.Library -> LibrarySetupPage(
                        folderLinked = folderLinked,
                        contentColor = contentColor,
                        onChooseBookFolder = onChooseBookFolder,
                        onContinue = { moveTo(SetupPage.Device.ordinal) },
                    )
                    SetupPage.Device -> DeviceSetupPage(
                        contentColor = contentColor,
                        onConnectDevice = { devicesVisible = true },
                        onFinish = onFinish,
                    )
                }
            }
        }
        SetupPageIndicator(
            selectedPage = pagerState.settledPage,
            modifier = Modifier.padding(top = 14.dp),
        )
    }

    if (devicesVisible) {
        DeviceConnectionSheet(onDismiss = { devicesVisible = false })
    }
}

@Composable
private fun WelcomeSetupPage(contentColor: Color, onContinue: () -> Unit) {
    SetupPageColumn {
        SetupPageLabel(step = 1, label = stringResource(R.string.setup_welcome_tab))
        SetupBridgeIllustration(color = contentColor, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = stringResource(R.string.setup_value_proposition),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.setup_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.start_setup))
        }
    }
}

@Composable
private fun LibrarySetupPage(
    folderLinked: Boolean,
    contentColor: Color,
    onChooseBookFolder: () -> Unit,
    onContinue: () -> Unit,
) {
    SetupPageColumn {
        SetupPageLabel(step = 2, label = stringResource(R.string.setup_library_tab))
        LibrarySetupIllustration(color = contentColor, modifier = Modifier.align(Alignment.CenterHorizontally))
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
        FilledTonalButton(
            onClick = onChooseBookFolder,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(if (folderLinked) R.string.change_epub_folder else R.string.choose_book_folder))
        }
        TextButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(if (folderLinked) R.string.continue_setup else R.string.do_this_later))
        }
    }
}

@Composable
private fun DeviceSetupPage(contentColor: Color, onConnectDevice: () -> Unit, onFinish: () -> Unit) {
    SetupPageColumn {
        SetupPageLabel(step = 3, label = stringResource(R.string.setup_device_tab))
        DeviceSetupIllustration(color = contentColor, modifier = Modifier.align(Alignment.CenterHorizontally))
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
        FilledTonalButton(
            onClick = onConnectDevice,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.choose_device))
        }
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Text(stringResource(R.string.finish_setup))
        }
    }
}

@Composable
private fun SetupPageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun SetupPageLabel(step: Int, label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
        Text(
            text = stringResource(R.string.setup_step_label, step, SetupPages.size, label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
private fun SetupBridgeIllustration(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(176.dp, 154.dp)) {
        val stroke = 3.dp.toPx()
        drawRoundRect(color, Offset(size.width * 0.08f, size.height * 0.10f), Size(size.width * 0.32f, size.height * 0.78f), CornerRadius(14.dp.toPx()), style = Stroke(stroke))
        drawRoundRect(color, Offset(size.width * 0.59f, size.height * 0.18f), Size(size.width * 0.33f, size.height * 0.64f), CornerRadius(8.dp.toPx()), style = Stroke(stroke))
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.49f), Offset(size.width * 0.59f, size.height * 0.49f), stroke, StrokeCap.Round)
        repeat(3) { index ->
            drawCircle(color, 2.5.dp.toPx(), Offset(size.width * (0.46f + index * 0.045f), size.height * 0.49f))
        }
    }
}

@Composable
private fun LibrarySetupIllustration(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(176.dp, 150.dp)) {
        val stroke = 3.dp.toPx()
        drawRoundRect(color, Offset(size.width * 0.15f, size.height * 0.28f), Size(size.width * 0.70f, size.height * 0.52f), CornerRadius(16.dp.toPx()), style = Stroke(stroke))
        drawLine(color, Offset(size.width * 0.16f, size.height * 0.37f), Offset(size.width * 0.84f, size.height * 0.37f), stroke, StrokeCap.Round)
        drawRoundRect(color, Offset(size.width * 0.30f, size.height * 0.12f), Size(size.width * 0.40f, size.height * 0.42f), CornerRadius(8.dp.toPx()), style = Stroke(stroke))
        drawLine(color, Offset(size.width * 0.50f, size.height * 0.13f), Offset(size.width * 0.50f, size.height * 0.53f), stroke)
    }
}

@Composable
private fun DeviceSetupIllustration(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(176.dp, 150.dp)) {
        val stroke = 3.dp.toPx()
        drawRoundRect(color, Offset(size.width * 0.31f, size.height * 0.08f), Size(size.width * 0.38f, size.height * 0.80f), CornerRadius(12.dp.toPx()), style = Stroke(stroke))
        drawLine(color, Offset(size.width * 0.42f, size.height * 0.77f), Offset(size.width * 0.58f, size.height * 0.77f), stroke, StrokeCap.Round)
        drawArc(color, -55f, 110f, false, Offset(size.width * 0.10f, size.height * 0.28f), Size(size.width * 0.22f, size.height * 0.34f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, 125f, 110f, false, Offset(size.width * 0.68f, size.height * 0.28f), Size(size.width * 0.22f, size.height * 0.34f), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
