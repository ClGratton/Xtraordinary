package com.xteink.companion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
        MagneticHorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 10.dp,
            colors = MagneticPagerColors(
                restingContainer = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainer = MaterialTheme.colorScheme.surfaceContainer,
                restingContent = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContent = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { page, containerColor, contentColor ->
            PassControlCard(
                pass = ticket.passes[page],
                containerColor = containerColor,
                contentColor = contentColor,
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
private fun PassControlCard(
    pass: BoardingPassUiState,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
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
