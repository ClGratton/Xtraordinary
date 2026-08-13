package com.xteink.companion.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xteink.companion.R
import com.xteink.companion.data.BarcodeRasterizer
import com.xteink.companion.data.FlightBarcodeFormat
import com.xteink.companion.data.isLinear
import com.xteink.companion.ui.BoardingPassUiState
import com.xteink.companion.ui.TicketMode
import com.xteink.companion.ui.TicketUiState

@Composable
fun PassesToolContent(
    ticket: TicketUiState,
    onSelectPass: (String) -> Unit,
    onSetTicketMode: (TicketMode) -> Unit,
    onSendTicket: () -> Unit,
    onRemoveTicket: () -> Unit,
    onImportPhoto: () -> Unit,
    onImportWalletLink: (String) -> Unit,
    onImportPassFile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var importChoiceVisible by remember { mutableStateOf(false) }
    var walletLinkVisible by remember { mutableStateOf(false) }
    var walletLink by remember { mutableStateOf("") }
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
            TextButton(onClick = { importChoiceVisible = true }) { Text(stringResource(R.string.import_flight)) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.passes_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                pluralStringResource(R.plurals.passes_count, ticket.passes.size, ticket.passes.size),
                style = MaterialTheme.typography.labelLarge,
            )
        }
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
            onRemove = onRemoveTicket,
            isOnX3 = ticket.isOnX3,
            sendPending = ticket.sendPending,
            removalPending = ticket.removalPending,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    if (importChoiceVisible) {
        AlertDialog(
            onDismissRequest = { importChoiceVisible = false },
            title = { Text(stringResource(R.string.import_flight)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.import_flight_body))
                    Button(
                        onClick = {
                            importChoiceVisible = false
                            onImportPhoto()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_from_photo)) }
                    FilledTonalButton(
                        onClick = {
                            importChoiceVisible = false
                            walletLinkVisible = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_wallet_link)) }
                    TextButton(
                        onClick = {
                            importChoiceVisible = false
                            onImportPassFile()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_pass_file)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { importChoiceVisible = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (walletLinkVisible) {
        AlertDialog(
            onDismissRequest = { walletLinkVisible = false },
            title = { Text(stringResource(R.string.import_wallet_link)) },
            text = {
                OutlinedTextField(
                    value = walletLink,
                    onValueChange = { walletLink = it },
                    label = { Text(stringResource(R.string.wallet_link_label)) },
                    supportingText = { Text(stringResource(R.string.wallet_link_help)) },
                    singleLine = false,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        walletLinkVisible = false
                        onImportWalletLink(walletLink.trim())
                    },
                    enabled = walletLink.isNotBlank(),
                ) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { walletLinkVisible = false }) { Text(stringResource(R.string.cancel)) }
            },
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
            UnifiedPassBody(
                pass = pass,
                modifier = Modifier.weight(1f),
            )
            TicketPerforation()
            RouteRail(origin = pass.origin, destination = pass.destination)
        }
    }
}

@Composable
private fun UnifiedPassBody(pass: BoardingPassUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 350.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pass.origin, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            HorizontalRouteArrow(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .padding(horizontal = 10.dp),
            )
            Text(pass.destination, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PassStatusBadge(pass.status)
                pass.delayMinutes?.takeIf { it != 0 }?.let { DelayBadge(it) }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                pass.flight,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (pass.countdown.isNotBlank() && !pass.countdown.equals(pass.status, ignoreCase = true)) {
            Text(
                pass.countdown,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OperationalFact(
                label = stringResource(R.string.departure),
                value = pass.departureTime,
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
            OperationalFact(
                label = stringResource(R.string.arrival),
                value = pass.arrivalTime.ifBlank { "—" },
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OperationalFact(
                label = stringResource(R.string.gate).substringBefore(" "),
                value = pass.gate,
                modifier = Modifier.weight(1f),
            )
            OperationalFact(
                label = stringResource(R.string.terminal),
                value = pass.terminal.ifBlank { "—" },
                modifier = Modifier.weight(1f),
            )
            OperationalFact(
                label = stringResource(R.string.seat),
                value = pass.seat,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.passenger),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pass.passenger,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pass.boardingGroup.isNotBlank()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.group),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(pass.boardingGroup, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BarcodePreviewPanel(
                payload = pass.barcodePayload,
                format = pass.barcodeFormat,
                isSample = pass.isSample,
                modifier = Modifier
                    .then(
                        if (pass.barcodeFormat.isLinear) {
                            Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                        } else {
                            Modifier.size(168.dp)
                        },
                    ),
            )
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
        VerticalRouteArrow(modifier = Modifier.size(width = 12.dp, height = 30.dp))
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
private fun VerticalRouteArrow(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val stroke = 1.5.dp.toPx()
        drawLine(color, Offset(centerX, size.height * 0.14f), Offset(centerX, size.height * 0.78f), strokeWidth = stroke)
        drawLine(color, Offset(centerX, size.height * 0.78f), Offset(size.width * 0.24f, size.height * 0.61f), strokeWidth = stroke)
        drawLine(color, Offset(centerX, size.height * 0.78f), Offset(size.width * 0.76f, size.height * 0.61f), strokeWidth = stroke)
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
private fun OperationalFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HorizontalRouteArrow(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val stroke = 1.5.dp.toPx()
        drawLine(color, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = stroke)
        drawLine(color, Offset(size.width, centerY), Offset(size.width - 8.dp.toPx(), centerY - 5.dp.toPx()), strokeWidth = stroke)
        drawLine(color, Offset(size.width, centerY), Offset(size.width - 8.dp.toPx(), centerY + 5.dp.toPx()), strokeWidth = stroke)
    }
}

@Composable
private fun PassStatusBadge(status: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun DelayBadge(delayMinutes: Int) {
    val delayed = delayMinutes > 0
    Surface(
        color = if (delayed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (delayed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = when {
                delayMinutes > 0 -> "+$delayMinutes min"
                delayMinutes < 0 -> "$delayMinutes min"
                else -> stringResource(R.string.on_time)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun PassModeChooser(
    mode: TicketMode,
    onSetMode: (TicketMode) -> Unit,
    onSend: () -> Unit,
    onRemove: () -> Unit,
    isOnX3: Boolean,
    sendPending: Boolean,
    removalPending: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val splitProgress by animateFloatAsState(
        targetValue = if (isOnX3) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "ticket action mitosis",
    )
    val splitGap by animateDpAsState(
        targetValue = if (isOnX3) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ticket action gap",
    )
    val staticKey = TicketMode.Static.name
    val liveKey = TicketMode.Live.name
    ExpandingChoiceRow(
        choices = listOf(
            ExpandingChoice(
                key = staticKey,
                title = stringResource(R.string.static_ticket),
                body = stringResource(R.string.static_ticket_body_short),
            ),
            ExpandingChoice(
                key = liveKey,
                title = stringResource(R.string.live_ticket),
                body = stringResource(R.string.live_ticket_body_short),
            ),
        ),
        selectedKey = mode.name,
        onSelect = { onSetMode(TicketMode.valueOf(it)) },
        optionHeight = 126.dp,
        selectedWeight = 1.25f,
        unselectedWeight = 0.90f,
        optionContentPadding = 10.dp,
        optionContentSpacing = 3.dp,
        modifier = modifier,
    ) { key ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(splitGap),
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSend()
                },
                enabled = !isOnX3 && !sendPending,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                SendToX3Icon(
                    modifier = Modifier.size(21.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = stringResource(
                        if (sendPending) R.string.ticket_send_pending
                        else if (isOnX3 && key == staticKey) R.string.static_ticket_on_x3
                        else if (isOnX3) R.string.live_ticket_on_x3
                        else if (key == staticKey) R.string.send_static_ticket
                        else R.string.start_live_and_send,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                )
            }
            if (splitProgress > 0.001f) {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        onRemove()
                    },
                    enabled = !removalPending,
                    modifier = Modifier
                        .weight(splitProgress.coerceAtLeast(0.001f))
                        .height(48.dp)
                        .graphicsLayer {
                            alpha = splitProgress
                            scaleX = 0.72f + splitProgress * 0.28f
                        },
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (removalPending && key == staticKey) R.string.ticket_static_removal_pending
                            else if (removalPending) R.string.ticket_removal_pending
                            else if (key == liveKey) R.string.stop_live_ticket
                            else R.string.remove_ticket_from_x3,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodePreviewPanel(
    payload: String,
    format: FlightBarcodeFormat,
    isSample: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        if (isSample) R.string.sample_boarding_pass_description else R.string.ticket_matrix_preview_description,
    )
    val barcodeImage = remember(payload, format) {
        runCatching {
            val bytes = BarcodeRasterizer.render(payload, format).bmpBytes
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.copy(Bitmap.Config.ARGB_8888, false)
                ?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
            .padding(7.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (barcodeImage != null) {
            Image(
                bitmap = barcodeImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(format.displayName, color = Color.Black, style = MaterialTheme.typography.labelMedium)
        }
    }
}
