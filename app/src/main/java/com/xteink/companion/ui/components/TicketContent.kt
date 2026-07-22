package com.xteink.companion.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xteink.companion.R
import com.xteink.companion.ui.BoardingPassUiState
import com.xteink.companion.ui.TicketMode
import com.xteink.companion.ui.TicketUiState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlin.math.absoluteValue
import kotlin.math.sign

private val PassMagneticSwipe = MagneticSwipeConfig(
    threshold = 0.42f,
    freeTravel = 0.08f,
    resistedTravel = 0.48f,
)

@Composable
fun PassesToolContent(
    ticket: TicketUiState,
    onSelectPass: (String) -> Unit,
    onSetTicketMode: (TicketMode) -> Unit,
    onSendTicket: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedPass = ticket.selectedPass
    val selectedIndex = ticket.passes.indexOfFirst { it.id == selectedPass.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { ticket.passes.size },
    )
    val isPagerDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val resistanceBlend = remember { Animatable(0f) }
    val snapKick = remember { Animatable(0f) }
    var beyondMagneticThreshold by remember { mutableStateOf(false) }
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = PassMagneticSwipe.threshold,
        snapAnimationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessHigh,
        ),
    )
    LaunchedEffect(isPagerDragged) {
        if (isPagerDragged) {
            resistanceBlend.snapTo(1f)
        } else {
            beyondMagneticThreshold = false
            resistanceBlend.animateTo(0f, tween(durationMillis = 110))
        }
    }
    LaunchedEffect(beyondMagneticThreshold) {
        if (isPagerDragged) {
            resistanceBlend.animateTo(
                targetValue = if (beyondMagneticThreshold) 0f else 1f,
                animationSpec = tween(durationMillis = 90),
            )
        }
    }
    PagerResistanceFeedback(
        pagerState = pagerState,
        isDragged = isPagerDragged,
        onThresholdChanged = { beyondMagneticThreshold = it },
    ) { direction ->
        snapKick.snapTo(direction * 0.018f)
        snapKick.animateTo(-direction * 0.006f, tween(durationMillis = 70))
        snapKick.animateTo(0f, tween(durationMillis = 100))
    }

    LaunchedEffect(pagerState.settledPage) {
        ticket.passes.getOrNull(pagerState.settledPage)?.let { onSelectPass(it.id) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 2.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("←  ${stringResource(R.string.back_to_tools)}") }
            Text(
                pluralStringResource(R.plurals.passes_count, ticket.passes.size, ticket.passes.size),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text = stringResource(R.string.passes_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 10.dp,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            PassControlCard(
                pass = ticket.passes[page],
                modifier = Modifier.graphicsLayer {
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceIn(0f, 1f)
                    val signedDrag = ((pagerState.currentPage - pagerState.settledPage) +
                        pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    val displayedProgress = PassMagneticSwipe.displayedProgress(
                        signedProgress = signedDrag,
                        resistance = resistanceBlend.value,
                    )
                    val resistedDistance = signedDrag - displayedProgress
                    translationX = size.width * resistedDistance
                    if (page == pagerState.settledPage) translationX += snapKick.value * size.width
                    scaleX = 1f - pageOffset * 0.014f
                    scaleY = 1f - pageOffset * 0.010f
                },
            )
        }
        PassModeChooser(
            mode = ticket.mode,
            onSetMode = onSetTicketMode,
            onSend = onSendTicket,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PassDetailsCard(
            pass = selectedPass,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = stringResource(R.string.ticket_sample_notice),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PagerResistanceFeedback(
    pagerState: PagerState,
    isDragged: Boolean,
    onThresholdChanged: (Boolean) -> Unit,
    onCenterSettled: suspend (Float) -> Unit,
) {
    val context = LocalContext.current
    val fallback = LocalHapticFeedback.current
    var dragSessionActive by remember { mutableStateOf(false) }
    var releaseDirection by remember { mutableFloatStateOf(1f) }
    val vibrator = remember(context) { context.touchVibrator() }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            dragSessionActive = true
            val dragStartPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
            var lastPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
            var travelSinceTick = 0f
            var samplesUntilTick = 0
            val magneticState = MagneticSwipeState(PassMagneticSwipe)
            if (!vibrator.playPrimitive(context, VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.18f)) {
                fallback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { position ->
                val movement = position - lastPosition
                val distance = movement.absoluteValue
                val signedProgress = position - dragStartPosition
                val progress = signedProgress.absoluteValue
                if (distance > 0.001f) releaseDirection = movement.sign
                travelSinceTick += distance

                val thresholdEvent = magneticState.update(signedProgress)
                if (thresholdEvent != null) {
                    if (!vibrator.playSnapThreshold(context)) {
                        fallback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    onThresholdChanged(magneticState.isBeyondThreshold)
                    travelSinceTick = 0f
                    samplesUntilTick = 8
                } else if (!magneticState.isBeyondThreshold && samplesUntilTick == 0) {
                    val approach = (progress / PassMagneticSwipe.threshold).coerceIn(0f, 1f)
                    val distancePerTick = 0.065f - approach * 0.037f
                    if (travelSinceTick >= distancePerTick) {
                        // Build tension up to the detent without competing with its full-strength hit.
                        val scale = 0.18f + approach * 0.44f
                        if (!vibrator.playPrimitive(context, VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale)) {
                            fallback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                        travelSinceTick = 0f
                    }
                } else if (samplesUntilTick > 0) {
                    samplesUntilTick -= 1
                }
                lastPosition = position
            }
        } else if (dragSessionActive) {
            onThresholdChanged(false)
            snapshotFlow { pagerState.isScrollInProgress }.first { scrolling -> !scrolling }
            dragSessionActive = false
            onCenterSettled(releaseDirection)
        }
    }
}

@Suppress("DEPRECATION")
private fun Context.touchVibrator(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    getSystemService(VibratorManager::class.java).defaultVibrator
} else {
    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
}

private fun Vibrator.playPrimitive(context: Context, primitive: Int, scale: Float): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !hasVibrator()) return false
    val touchEnabled = Settings.System.getInt(
        context.contentResolver,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        1,
    ) != 0
    if (!touchEnabled || !areAllPrimitivesSupported(primitive)) return false
    vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(primitive, scale.coerceIn(0f, 1f))
            .compose(),
    )
    return true
}

private fun Vibrator.playSnapThreshold(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !hasVibrator()) return false
    val touchEnabled = Settings.System.getInt(
        context.contentResolver,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        1,
    ) != 0
    val lowTick = VibrationEffect.Composition.PRIMITIVE_LOW_TICK
    val click = VibrationEffect.Composition.PRIMITIVE_CLICK
    if (!touchEnabled || !areAllPrimitivesSupported(lowTick, click)) return false
    vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(lowTick, 0.72f)
            .addPrimitive(click, 1f, 20)
            .compose(),
    )
    return true
}

@Composable
private fun PassControlCard(pass: BoardingPassUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 2.dp,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            RouteRail(origin = pass.origin, destination = pass.destination)
            TicketPerforation()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SampleMatrixPanel(modifier = Modifier.size(96.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(pass.flight, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                pass.status,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
                                Text(stringResource(R.string.departure), style = MaterialTheme.typography.labelMedium)
                                Text(pass.departureTime, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(pass.countdown, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PassFact(stringResource(R.string.gate).substringBefore(" "), pass.gate)
                    PassFact(stringResource(R.string.terminal), pass.terminal)
                    PassFact(stringResource(R.string.seat), pass.seat)
                }
            }
            TicketPerforation()
            RouteRail(origin = pass.origin, destination = pass.destination)
        }
    }
}

@Composable
private fun RouteRail(origin: String, destination: String) {
    val description = "$origin to $destination"
    Column(
        modifier = Modifier
            .width(38.dp)
            .fillMaxHeight()
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = origin,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
        RouteArrow(modifier = Modifier.size(width = 12.dp, height = 30.dp))
        Text(
            text = destination,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun RouteArrow(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val stroke = 1.5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(centerX, size.height * 0.14f),
            end = Offset(centerX, size.height * 0.78f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(centerX, size.height * 0.78f),
            end = Offset(size.width * 0.24f, size.height * 0.61f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(centerX, size.height * 0.78f),
            end = Offset(size.width * 0.76f, size.height * 0.61f),
            strokeWidth = stroke,
        )
    }
}

@Composable
private fun TicketPerforation() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .width(7.dp)
            .fillMaxHeight()
            .padding(vertical = 12.dp),
    ) {
        val dash = 5.dp.toPx()
        val gap = 5.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = color,
                start = Offset(size.width / 2f, y),
                end = Offset(size.width / 2f, (y + dash).coerceAtMost(size.height)),
                strokeWidth = 1.2.dp.toPx(),
            )
            y += dash + gap
        }
    }
}

@Composable
private fun PassModeChooser(
    mode: TicketMode,
    onSetMode: (TicketMode) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val staticWeight by animateFloatAsState(
        targetValue = if (mode == TicketMode.Static) 1.35f else 0.75f,
        label = "static option width",
    )
    val liveWeight by animateFloatAsState(
        targetValue = if (mode == TicketMode.Live) 1.35f else 0.75f,
        label = "live option width",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PassModeOption(
            title = stringResource(R.string.static_ticket),
            body = stringResource(R.string.static_ticket_body_short),
            sendLabel = stringResource(R.string.send_static_ticket),
            selected = mode == TicketMode.Static,
            onSelect = { onSetMode(TicketMode.Static) },
            onSend = onSend,
            modifier = Modifier.weight(staticWeight),
        )
        PassModeOption(
            title = stringResource(R.string.live_ticket),
            body = stringResource(R.string.live_ticket_body_short),
            sendLabel = stringResource(R.string.start_live_and_send),
            selected = mode == TicketMode.Live,
            onSelect = { onSetMode(TicketMode.Live) },
            onSend = onSend,
            modifier = Modifier.weight(liveWeight),
        )
    }
}

@Composable
private fun PassModeOption(
    title: String,
    body: String,
    sendLabel: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onSelect()
        },
        modifier = modifier.height(170.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (selected) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSend()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    SendToX3Icon(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = sendLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 7.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PassFact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PassDetailsCard(pass: BoardingPassUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ticket_title), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PassFact(stringResource(R.string.passenger), pass.passenger)
                PassFact(stringResource(R.string.group), pass.boardingGroup)
            }
            Column {
                Text(stringResource(R.string.source_and_freshness), style = MaterialTheme.typography.labelMedium)
                Text(pass.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SampleMatrixPanel(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.sample_boarding_pass_description)
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
            .padding(7.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        MockMatrixCode(modifier = Modifier.fillMaxSize())
        Text(
            text = stringResource(R.string.sample),
            color = Color.Black,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.White)
                .padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun MockMatrixCode(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val modules = 25
        val cell = size.minDimension / modules
        val origin = Offset((size.width - cell * modules) / 2f, (size.height - cell * modules) / 2f)
        fun finder(x: Int, y: Int, left: Int, top: Int): Boolean {
            val localX = x - left
            val localY = y - top
            if (localX !in 0..6 || localY !in 0..6) return false
            return localX == 0 || localX == 6 || localY == 0 || localY == 6 ||
                (localX in 2..4 && localY in 2..4)
        }
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                val marked = finder(x, y, 0, 0) || finder(x, y, 18, 0) || finder(x, y, 0, 18) ||
                    ((x * 11 + y * 7 + x * y * 3) % 13 < 5)
                if (marked) {
                    drawRect(
                        Color.Black,
                        Offset(origin.x + x * cell, origin.y + y * cell),
                        androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
            }
        }
    }
}
