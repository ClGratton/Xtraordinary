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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import com.xteink.companion.R
import com.xteink.companion.data.BarcodeRasterizer
import com.xteink.companion.data.FlightBarcodeFormat
import com.xteink.companion.data.isLinear
import com.xteink.companion.ui.BoardingPassUiState
import com.xteink.companion.ui.PendingTicketOperation
import com.xteink.companion.ui.TicketMode
import com.xteink.companion.ui.TicketOperationPolicy
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
    val pagerScope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(pagerState.settledPage) {
        ticket.passes.getOrNull(pagerState.settledPage)?.let { onSelectPass(it.id) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val passCardHeight = PassCardLayoutPolicy.heightFor(
            fontScale = fontScale,
            viewportHeight = maxHeight,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = 2.dp,
                    bottom = if (fontScale >= 1.6f) 112.dp else 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        if (fontScale >= 1.6f) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.align(Alignment.Start),
                ) { Text("←  ${stringResource(R.string.back_to_tools)}") }
                TextButton(
                    onClick = { importChoiceVisible = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.import_flight)) }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) { Text("←  ${stringResource(R.string.back_to_tools)}") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { importChoiceVisible = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) { Text(stringResource(R.string.import_flight)) }
            }
        }
        if (fontScale >= 1.6f) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.passes_title), style = MaterialTheme.typography.headlineLarge, maxLines = 2)
                Text(
                    pluralStringResource(R.plurals.passes_count, ticket.passes.size, ticket.passes.size),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.passes_title), style = MaterialTheme.typography.headlineLarge, maxLines = 1)
                Text(
                    pluralStringResource(R.plurals.passes_count, ticket.passes.size, ticket.passes.size),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
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
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = context.getString(R.string.pass_position, pagerState.settledPage + 1, ticket.passes.size)
                customActions = buildList {
                    if (pagerState.settledPage > 0) add(CustomAccessibilityAction(context.getString(R.string.pass_previous)) {
                        pagerScope.launch { pagerState.animateScrollToPage(pagerState.settledPage - 1) }; true
                    })
                    if (pagerState.settledPage < ticket.passes.lastIndex) add(CustomAccessibilityAction(context.getString(R.string.pass_next)) {
                        pagerScope.launch { pagerState.animateScrollToPage(pagerState.settledPage + 1) }; true
                    })
                }
            },
        ) { page, containerColor, contentColor ->
            PassControlCard(
                pass = ticket.passes[page],
                containerColor = containerColor,
                contentColor = contentColor,
                cardHeight = passCardHeight,
                fontScale = fontScale,
            )
        }
        DeploymentStatus(ticket = ticket, modifier = Modifier.padding(horizontal = 16.dp))
        PassModeChooser(
            mode = ticket.mode,
            onSetMode = onSetTicketMode,
            onSend = onSendTicket,
            onRemove = onRemoveTicket,
            isOnX3 = ticket.isOnX3,
            deployedPassId = ticket.deployedPassId,
            deployedMode = ticket.deployedMode,
            selectedPass = selectedPass,
            ticketState = ticket,
            sendPending = ticket.sendPending,
            pendingOperation = ticket.pendingOperation,
            removalPending = ticket.removalPending,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
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
    cardHeight: androidx.compose.ui.unit.Dp,
    fontScale: Float,
    modifier: Modifier = Modifier,
) {
    var requestedCodeFace by remember(pass.id) { mutableStateOf(false) }
    val turn by animateFloatAsState(
        targetValue = PassMotionPolicy.turnTarget(requestedCodeFace),
        // Compose's MotionDurationScale makes this immediately settle at a
        // system duration scale of zero.
        animationSpec = tween(durationMillis = PassMotionPolicy.turnDurationMillis),
        label = "pass vertical turn",
    )
    val showCode = PassMotionPolicy.showsCode(turn)
    Surface(
        modifier = modifier.height(cardHeight),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = turn * 180f }) {
            if (showCode) {
                PassCodeBody(
                    pass = pass,
                    onShowDetails = { requestedCodeFace = false },
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
                )
            } else Row(modifier = Modifier.fillMaxSize()) {
            if (fontScale < 1.6f) {
                RouteRail(origin = pass.origin, destination = pass.destination)
                TicketPerforation()
            }
            UnifiedPassBody(
                pass = pass,
                onShowCode = { requestedCodeFace = true },
                fontScale = fontScale,
                modifier = Modifier.weight(1f),
            )
            if (fontScale < 1.6f) {
                TicketPerforation()
                RouteRail(origin = pass.origin, destination = pass.destination)
            }
            }
        }
    }
}

@Composable
private fun UnifiedPassBody(
    pass: BoardingPassUiState,
    onShowCode: () -> Unit,
    fontScale: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = PassLayout.bodyInset, vertical = 8.dp),
    ) {
      val compactFacts = maxWidth < 180.dp || fontScale > 1.15f
      val accessibilityLayout = compactFacts
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
        Spacer(modifier = Modifier.weight(0.25f))
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
        if (compactFacts) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
            OperationalFact(
                label = stringResource(R.string.seat),
                value = pass.seat,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OperationalFact(
                    label = stringResource(R.string.gate).substringBefore(" "),
                    value = pass.gate,
                    modifier = Modifier.weight(PassLayout.gateFactWeight),
                )
                OperationalFact(
                    label = stringResource(R.string.terminal),
                    value = pass.terminal.ifBlank { "—" },
                    modifier = Modifier.weight(PassLayout.terminalFactWeight),
                )
                OperationalFact(
                    label = stringResource(R.string.seat),
                    value = pass.seat,
                    modifier = Modifier.weight(PassLayout.seatFactWeight),
                )
            }
        }
        Spacer(modifier = Modifier.weight(0.25f))
        if (accessibilityLayout) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PassengerIdentity(pass = pass)
                if (pass.boardingGroup.isNotBlank()) BoardingGroup(pass.boardingGroup)
            }
        } else Row(
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
                    style = MaterialTheme.typography.bodyMedium,
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
                    Text(pass.boardingGroup, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        TicketMetadata(pass)
        Spacer(modifier = Modifier.weight(0.5f))
        TurnSurfaceControl(
            label = stringResource(R.string.show_pass_code),
            destination = PassFaceDestination.Code,
            onClick = onShowCode,
        )
      }
    }
}

@Composable
private fun PassengerIdentity(pass: BoardingPassUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.passenger),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(pass.passenger, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BoardingGroup(group: String) {
    Column {
        Text(
            stringResource(R.string.group),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(group, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PassCodeBody(pass: BoardingPassUiState, onShowDetails: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight().padding(horizontal = PassLayout.codeInset, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${pass.origin} → ${pass.destination}  ·  ${pass.flight}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (pass.isSample) Text(stringResource(R.string.sample_not_valid_for_boarding), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        BarcodePreviewPanel(
            payload = pass.barcodePayload,
            format = pass.barcodeFormat,
            isSample = pass.isSample,
            modifier = if (pass.barcodeFormat.isLinear) Modifier.fillMaxWidth().height(160.dp) else Modifier.size(190.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        TurnSurfaceControl(
            label = stringResource(R.string.show_pass_details),
            destination = PassFaceDestination.Details,
            onClick = onShowDetails,
        )
    }
}

private enum class PassFaceDestination { Code, Details }

@Composable
private fun TurnSurfaceControl(
    label: String,
    destination: PassFaceDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        val showObjectGlyph = PassTurnAffordancePolicy.showsObjectGlyph(
            availableWidthDp = maxWidth.value.toInt(),
            isCodeDestination = destination == PassFaceDestination.Code,
        )
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.Center)
                .heightIn(min = 48.dp)
                .testTag("pass_turn_surface"),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        ) {
            Text(label)
            if (showObjectGlyph) {
                Spacer(modifier = Modifier.width(8.dp))
                PassCodeGlyph(modifier = Modifier.size(PassTurnAffordancePolicy.objectGlyphSizeDp.dp))
            }
        }
    }
}

@Composable
private fun PassCodeGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val stroke = 1.5.dp.toPx()
        val finderSize = 6.dp.toPx()
        val inset = 1.dp.toPx()
        val far = size.width - finderSize - inset
        val finderStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        drawRect(color, topLeft = Offset(inset, inset), size = androidx.compose.ui.geometry.Size(finderSize, finderSize), style = finderStyle)
        drawRect(color, topLeft = Offset(far, inset), size = androidx.compose.ui.geometry.Size(finderSize, finderSize), style = finderStyle)
        drawRect(color, topLeft = Offset(inset, far), size = androidx.compose.ui.geometry.Size(finderSize, finderSize), style = finderStyle)
        drawRect(color, topLeft = Offset(10.dp.toPx(), 10.dp.toPx()), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), 3.dp.toPx()))
        drawRect(color, topLeft = Offset(14.dp.toPx(), 10.dp.toPx()), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), 7.dp.toPx()))
        drawRect(color, topLeft = Offset(10.dp.toPx(), 14.dp.toPx()), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), 3.dp.toPx()))
    }
}

@Composable
private fun TicketMetadata(pass: BoardingPassUiState) {
    val text = when (val presentation = ticketMetadataPresentation(
        isSample = pass.isSample,
        liveProvider = pass.liveProvider,
        liveUpdatedAtEpochMs = pass.liveUpdatedAtEpochMs,
    )) {
        TicketMetadataPresentation.Hidden -> null
        TicketMetadataPresentation.SampleWarning -> stringResource(R.string.sample_not_valid_for_boarding)
        is TicketMetadataPresentation.LiveFresh -> stringResource(
            R.string.pass_provider_last_confirmed,
            presentation.provider,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(presentation.observedAtEpochMs)),
        )
        is TicketMetadataPresentation.LiveUnavailable ->
            stringResource(R.string.pass_provider_unavailable, presentation.provider)
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shared Passes geometry; card faces use the same stable outer bounds. */
private object PassLayout {
    val bodyInset = 12.dp
    val codeInset = 12.dp
    const val gateFactWeight = 1.4f
    const val terminalFactWeight = 1f
    const val seatFactWeight = 0.8f
}

@Composable
private fun RouteRail(origin: String, destination: String) {
    Column(
        modifier = Modifier
            .width(38.dp)
            .fillMaxHeight()
            .clearAndSetSemantics { },
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
            style = if (emphasized) passTimeTypography() else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun passTimeTypography() = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")

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
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
    ) {
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DelayBadge(delayMinutes: Int) {
    val delayed = delayMinutes > 0
    Surface(
        color = if (delayed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (delayed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = CircleShape,
    ) {
        Text(
            text = when {
                delayMinutes > 0 -> "+$delayMinutes min"
                delayMinutes < 0 -> "$delayMinutes min"
                else -> stringResource(R.string.on_time)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeploymentStatus(ticket: TicketUiState, modifier: Modifier = Modifier) {
    val selected = ticket.selectedPass
    val deployed = ticket.passes.firstOrNull { it.id == ticket.deployedPassId }
    val pending = ticket.pendingOperation
    val pendingPass = pending?.let { operation -> ticket.passes.firstOrNull { it.id == operation.passId } }
    val text = when {
        ticket.removalPending && deployed != null ->
            stringResource(R.string.ticket_removing, deployed.flight)
        ticket.sendPending && pending != null -> stringResource(
            R.string.ticket_queued,
            pendingPass?.flight ?: pending.passId,
            pending.mode.name,
        )
        deployed == null -> stringResource(R.string.ticket_not_on_x3)
        deployed.id == selected.id -> stringResource(R.string.ticket_on_x3, deployed.flight, ticket.deployedMode?.name ?: "unknown mode")
        else -> stringResource(R.string.ticket_other_on_x3, deployed.flight, ticket.deployedMode?.name ?: "unknown mode")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PassModeChooser(
    mode: TicketMode,
    onSetMode: (TicketMode) -> Unit,
    onSend: () -> Unit,
    onRemove: () -> Unit,
    isOnX3: Boolean,
    deployedPassId: String?,
    deployedMode: TicketMode?,
    selectedPass: BoardingPassUiState,
    ticketState: TicketUiState,
    sendPending: Boolean,
    pendingOperation: PendingTicketOperation?,
    removalPending: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val optionHeight = PassModeLayoutPolicy.optionHeight(LocalDensity.current.fontScale)
    val deployedSelectedPass = isOnX3 && deployedPassId == selectedPass.id
    val splitProgress by animateFloatAsState(
        targetValue = PassMotionPolicy.actionMitosisTarget(deployedSelectedPass),
        animationSpec = PassMotionPolicy.actionMitosisSpring,
        label = "ticket action mitosis",
    )
    val splitGapDp by animateFloatAsState(
        targetValue = PassMotionPolicy.actionGapTarget(deployedSelectedPass),
        animationSpec = PassMotionPolicy.actionGapSpring,
        label = "ticket action gap",
    )
    val sendAvailable = TicketOperationPolicy.canStartSend(ticketState)
    Column(modifier = modifier) {
        ExpandingChoiceRow(
            choices = listOf(
                ExpandingChoice(
                    key = TicketMode.Static.name,
                    title = stringResource(R.string.static_ticket),
                    body = stringResource(R.string.static_ticket_body_short),
                ),
                ExpandingChoice(
                    key = TicketMode.Live.name,
                    title = stringResource(R.string.live_ticket),
                    body = stringResource(R.string.live_ticket_body_short),
                ),
            ),
            selectedKey = mode.name,
            onSelect = { onSetMode(TicketMode.valueOf(it)) },
            enabled = TicketOperationPolicy.canChangeNextSendMode(ticketState),
            optionHeight = optionHeight,
            selectedWeight = PassMotionPolicy.selectedChoiceWeight,
            unselectedWeight = PassMotionPolicy.restingChoiceWeight,
            choiceWidthAnimationSpec = PassMotionPolicy.choiceSpring,
            optionContentPadding = 14.dp,
            optionContentSpacing = 4.dp,
            groupContentDescription = stringResource(R.string.mode_for_next_send),
            modifier = Modifier.fillMaxWidth(),
        ) { key ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(splitGapDp.dp),
            ) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSend()
                    },
                    enabled = sendAvailable,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    if (sendAvailable) {
                        SendToX3Icon(
                            modifier = Modifier.size(21.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(
                        text = stringResource(
                            if (sendPending && pendingOperation?.mode?.name == key) R.string.ticket_send_pending
                            else if (removalPending) R.string.ticket_removal_pending
                            else if (!sendAvailable && key == TicketMode.Static.name) R.string.static_ticket_on_x3
                            else if (!sendAvailable) R.string.live_ticket_on_x3
                            else if (key == TicketMode.Static.name) R.string.send_static_ticket
                            else R.string.start_live_and_send,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = if (sendAvailable) 6.dp else 0.dp),
                        maxLines = 1,
                    )
                }
                if (splitProgress > 0.001f) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            onRemove()
                        },
                        enabled = TicketOperationPolicy.canStartRemoval(ticketState),
                        modifier = Modifier
                            .weight(splitProgress.coerceAtLeast(0.001f))
                            .height(48.dp)
                            .graphicsLayer {
                                alpha = splitProgress
                                scaleX = 0.72f + splitProgress * 0.28f
                            },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (removalPending && deployedMode == TicketMode.Static) R.string.ticket_static_removal_pending
                                else if (removalPending) R.string.ticket_removal_pending
                                else if (deployedMode == TicketMode.Live) R.string.stop_live_ticket
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
            .background(Color.White, MaterialTheme.shapes.small)
            .border(1.dp, Color.Black, MaterialTheme.shapes.small)
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
