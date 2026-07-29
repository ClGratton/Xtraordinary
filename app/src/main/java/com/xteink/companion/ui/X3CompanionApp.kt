package com.xteink.companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xteink.companion.R
import com.xteink.companion.data.FirmwareSource
import com.xteink.companion.monetization.AccessState
import com.xteink.companion.monetization.PremiumAction
import com.xteink.companion.ui.components.CompanionNavigation
import com.xteink.companion.ui.components.CompanionTopBar
import com.xteink.companion.ui.components.ConnectedAccessPrompt
import com.xteink.companion.ui.components.ControlDeckFocusContent
import com.xteink.companion.ui.components.DeviceConnectionSheet
import com.xteink.companion.ui.components.PassesToolContent
import com.xteink.companion.ui.components.ReadContent
import com.xteink.companion.ui.components.SettingsSheet
import com.xteink.companion.ui.components.ToolsHubContent

@Composable
fun X3CompanionApp(
    state: CompanionUiState,
    access: AccessState,
    onSetVisualTheme: (CompanionVisualTheme) -> Unit,
    onSetNormalPollSeconds: (Int) -> Unit,
    onSetSlowPollSeconds: (Int) -> Unit,
    onSetSleepTimeoutMinutes: (Int) -> Unit,
    onSetDuration: (Int) -> Unit,
    onStartFocus: () -> Unit,
    onStartFocusPhoneOnly: () -> Unit,
    onTogglePause: () -> Unit,
    onEndFocus: () -> Unit,
    onResetFocus: () -> Unit,
    onShowTools: () -> Unit,
    onShowRead: () -> Unit,
    onShowFocus: () -> Unit,
    onSetReadQuery: (String) -> Unit,
    onSetReadSort: (ReadSort) -> Unit,
    onSetReadService: (ReadService) -> Unit,
    onSetOnX3Only: (Boolean) -> Unit,
    onChooseBookFolder: () -> Unit,
    onOpenEpub: () -> Unit,
    onDeleteBooksFromX3: (Set<String>) -> Unit,
    onSendBooksToX3: (Set<String>) -> Unit,
    onOpenPasses: () -> Unit,
    onShowToolHub: () -> Unit,
    onSelectPass: (String) -> Unit,
    onSetTicketMode: (TicketMode) -> Unit,
    onSendTicket: () -> Unit,
    onShowSettings: (Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onDismissNotice: () -> Unit,
    onConnectDevice: (String) -> Unit = {},
    onDisconnectDevice: () -> Unit = {},
    onCheckFirmware: (String, FirmwareSource) -> Unit = { _, _ -> },
    onFlashFirmware: () -> Unit = {},
    onWatchAd: ((() -> Unit) -> Unit) = {},
    onGetPro: () -> Unit = {},
    onShowPrivacyOptions: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var devicesVisible by rememberSaveable { mutableStateOf(false) }
    var gatedAction by remember { mutableStateOf<PremiumAction?>(null) }
    var gatedBookIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val noticeText = when (val notice = state.notice) {
        UiNotice.FocusStartedWithoutX3 -> stringResource(R.string.focus_started_without_x3)
        UiNotice.PairBeforeSend -> stringResource(R.string.pair_before_send)
        UiNotice.EpubImportFailed -> stringResource(R.string.epub_import_failed)
        UiNotice.ConnectX3ToDelete -> stringResource(R.string.connect_x3_to_delete)
        is UiNotice.X3DeleteQueued -> stringResource(R.string.x3_delete_queued, notice.count)
        is UiNotice.BooksSentToX3 -> stringResource(R.string.books_sent_to_x3, notice.count)
        is UiNotice.BooksImported -> stringResource(
            R.string.books_import_result,
            notice.added,
            notice.duplicates,
            notice.failed,
        )
        is UiNotice.FolderSynced -> stringResource(
            R.string.folder_sync_result,
            notice.found,
            notice.added,
        )
        is UiNotice.DeviceMessage -> notice.text
        null -> null
    }

    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            onDismissNotice()
        }
    }

    BackHandler(enabled = devicesVisible || state.settingsVisible || state.toolDestination != ToolDestination.Hub) {
        when {
            devicesVisible -> devicesVisible = false
            state.settingsVisible -> onShowSettings(false)
            else -> onShowToolHub()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            CompanionNavigation(
                selected = state.surface,
                onShowFocus = onShowFocus,
                onShowRead = onShowRead,
                onShowTools = onShowTools,
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            CompanionTopBar(
                isX3Connected = state.isX3Connected,
                hasManagedX3 = state.hasManagedX3,
                isX3Reconnecting = state.device.reconnecting,
                deviceActivity = state.device.activity,
                lowPowerGraceExpired = state.device.lowPowerGraceExpired,
                connectedDeviceModel = state.connectedDeviceModel,
                onShowDevices = { devicesVisible = true },
                onShowSettings = { onShowSettings(true) },
            )
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = state.surface to state.toolDestination,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "primary destination",
                ) { (surface, toolDestination) ->
                    when (surface) {
                        CompanionSurface.Focus -> ControlDeckFocusContent(
                            focus = state.focus,
                            visualTheme = state.visualTheme,
                            onSetDuration = onSetDuration,
                            onStartFocus = {
                                if (!state.hasManagedX3 || access.hasConnectedAccess()) onStartFocus()
                                else gatedAction = PremiumAction.FocusOnDevice
                            },
                            onTogglePause = onTogglePause,
                            onEndFocus = onEndFocus,
                            onResetFocus = onResetFocus,
                        )
                        CompanionSurface.Read -> ReadContent(
                            state = state.read,
                            isX3Connected = state.hasManagedX3,
                            connectedDeviceModel = state.connectedDeviceModel,
                            onSetQuery = onSetReadQuery,
                            onSetSort = onSetReadSort,
                            onSetService = onSetReadService,
                            onSetOnX3Only = onSetOnX3Only,
                            onChooseBookFolder = onChooseBookFolder,
                            onOpenEpub = onOpenEpub,
                            onOpenSettings = { onShowSettings(true) },
                            onDeleteBooksFromX3 = onDeleteBooksFromX3,
                            onSendBooksToX3 = { bookIds ->
                                if (access.hasConnectedAccess()) onSendBooksToX3(bookIds)
                                else {
                                    gatedBookIds = bookIds
                                    gatedAction = PremiumAction.PushBooks
                                }
                            },
                        )
                        CompanionSurface.Tools -> when (toolDestination) {
                            ToolDestination.Hub -> ToolsHubContent(
                                passCount = state.ticket.passes.size,
                                onOpenPasses = onOpenPasses,
                            )
                            ToolDestination.Passes -> PassesToolContent(
                                ticket = state.ticket,
                                onSelectPass = onSelectPass,
                                onSetTicketMode = onSetTicketMode,
                                onSendTicket = {
                                    if (access.hasConnectedAccess()) onSendTicket()
                                    else gatedAction = PremiumAction.SendPass
                                },
                                onBack = onShowToolHub,
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.settingsVisible) {
        SettingsSheet(
            visualTheme = state.visualTheme,
            powerSyncConfig = state.powerSyncConfig,
            access = access,
            onSetVisualTheme = onSetVisualTheme,
            onSetNormalPollSeconds = onSetNormalPollSeconds,
            onSetSlowPollSeconds = onSetSlowPollSeconds,
            onSetSleepTimeoutMinutes = onSetSleepTimeoutMinutes,
            onGetPro = onGetPro,
            onShowPrivacyOptions = onShowPrivacyOptions,
            onOpenSetup = onOpenSetup,
            onDismiss = { onShowSettings(false) },
        )
    }
    if (devicesVisible) {
        DeviceConnectionSheet(
            onDismiss = { devicesVisible = false },
            device = state.device,
            isConnected = state.isX3Connected,
            isManaged = state.hasManagedX3,
            connectedDeviceModel = state.connectedDeviceModel,
            onConnect = onConnectDevice,
            onDisconnect = onDisconnectDevice,
            onCheckFirmware = onCheckFirmware,
            onFlashFirmware = onFlashFirmware,
        )
    }
    gatedAction?.let { action ->
        ConnectedAccessPrompt(
            action = action,
            access = access,
            onWatchAd = {
                onWatchAd {
                    when (action) {
                        PremiumAction.FocusOnDevice -> onStartFocus()
                        PremiumAction.SendPass -> onSendTicket()
                        PremiumAction.PushBooks -> onSendBooksToX3(gatedBookIds)
                    }
                    gatedBookIds = emptySet()
                    gatedAction = null
                }
            },
            onUsePhoneOnly = if (action == PremiumAction.FocusOnDevice) onStartFocusPhoneOnly else null,
            onGetPro = onGetPro,
            onDismiss = {
                gatedBookIds = emptySet()
                gatedAction = null
            },
        )
    }
}
