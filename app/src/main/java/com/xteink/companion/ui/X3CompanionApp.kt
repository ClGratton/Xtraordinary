package com.xteink.companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.xteink.companion.data.CloudBackupState
import com.xteink.companion.ui.components.CompanionNavigation
import com.xteink.companion.ui.components.CompanionTopBar
import com.xteink.companion.ui.components.ControlDeckFocusContent
import com.xteink.companion.ui.components.DeviceConnectionSheet
import com.xteink.companion.ui.components.PassesToolContent
import com.xteink.companion.ui.components.ReadContent
import com.xteink.companion.ui.components.ReadingStatsContent
import com.xteink.companion.ui.components.SettingsSheet
import com.xteink.companion.ui.components.ToolsHubContent

@Composable
fun X3CompanionApp(
    state: CompanionUiState,
    onSetVisualTheme: (CompanionVisualTheme) -> Unit,
    onSetColorMode: (CompanionColorMode) -> Unit,
    onSetRadioPolicy: (RadioPolicyUiState) -> Unit,
    onSetDuration: (Int) -> Unit,
    onStartFocus: () -> Unit,
    onTogglePause: () -> Unit,
    onEndFocus: () -> Unit,
    onResetFocus: () -> Unit,
    onShowTools: () -> Unit,
    onShowRead: () -> Unit,
    onShowFocus: () -> Unit,
    onSetReadQuery: (String) -> Unit,
    onSetReadSort: (ReadSort) -> Unit,
    onSetReadService: (ReadService) -> Unit,
    onSetReadLocation: (ReadLocation) -> Unit,
    onChooseBookFolder: () -> Unit,
    onOpenEpub: () -> Unit,
    onUploadBooksToX3: (Set<String>, BookTransferMethod) -> Unit,
    onCancelBookUpload: () -> Unit,
    onDeleteBooksFromX3: (Set<String>) -> Unit,
    onOpenPasses: () -> Unit,
    onOpenStats: () -> Unit,
    onSetReadingStatsView: (ReadingStatsView) -> Unit,
    onSelectReadingSession: (UInt?) -> Unit,
    onDeleteReadingSession: (UInt) -> Unit,
    onUndoReadingSessionDeletion: () -> Unit,
    onSetMinimumReadingPageSeconds: (Int) -> Unit,
    onShowToolHub: () -> Unit,
    onSelectPass: (String) -> Unit,
    onSetTicketMode: (TicketMode) -> Unit,
    onSendTicket: () -> Unit,
    onRemoveTicket: () -> Unit = {},
    onImportPhoto: () -> Unit = {},
    onImportWalletLink: (String) -> Unit = {},
    onImportPassFile: () -> Unit = {},
    onShowSettings: (Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onDismissNotice: () -> Unit,
    onConnectDevice: (String) -> Unit = {},
    onCheckFirmware: (String, FirmwareSource) -> Unit = { _, _ -> },
    onFlashFirmware: () -> Unit = {},
    onResetX3Setup: () -> Unit = {},
    cloudBackupState: CloudBackupState = CloudBackupState(),
    onSyncGoogleBackup: () -> Unit = {},
    onDeleteGoogleBackup: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var devicesVisible by rememberSaveable { mutableStateOf(false) }
    val noticeText = when (val notice = state.notice) {
        UiNotice.PairBeforeSend -> stringResource(R.string.pair_before_send)
        UiNotice.EpubImportFailed -> stringResource(R.string.epub_import_failed)
        UiNotice.ConnectX3ToDelete -> stringResource(R.string.connect_x3_to_delete)
        is UiNotice.X3DeleteQueued -> stringResource(R.string.x3_delete_queued, notice.count)
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
        is UiNotice.SessionDeleted -> stringResource(R.string.reading_session_deleted, notice.title)
        null -> null
    }
    val undoText = stringResource(R.string.undo)

    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            val result = snackbarHostState.showSnackbar(
                message = noticeText,
                actionLabel = if (state.notice is UiNotice.SessionDeleted) undoText else null,
                withDismissAction = state.notice is UiNotice.SessionDeleted,
            )
            if (result == SnackbarResult.ActionPerformed && state.notice is UiNotice.SessionDeleted) {
                onUndoReadingSessionDeletion()
            }
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
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
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
                hasManagedX3 = state.isX3Connected,
                isX3TransportConnected = state.isX3TransportConnected,
                isX3Reconnecting = state.device.reconnecting,
                isX3Connecting = state.device.linkPhase == "Scanning" || state.device.linkPhase == "Connecting",
                requiresBluetoothReset = state.device.requiresBluetoothReset,
                transportBlocker = state.device.transportBlocker,
                connectedDeviceModel = state.connectedDeviceModel,
                batteryPercentage = state.device.batteryPercentage,
                charging = state.device.charging,
                onShowDevices = { devicesVisible = true },
                onShowSettings = { onShowSettings(true) },
            )
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = state.surface to state.toolDestination,
                    transitionSpec = {
                        if (state.visualTheme == CompanionVisualTheme.Minimal) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn() togetherWith fadeOut()
                        }
                    },
                    label = "primary destination",
                ) { (surface, toolDestination) ->
                    when (surface) {
                        CompanionSurface.Focus -> ControlDeckFocusContent(
                            focus = state.focus,
                            visualTheme = state.visualTheme,
                            colorMode = state.colorMode,
                            onSetDuration = onSetDuration,
                            onStartFocus = onStartFocus,
                            onTogglePause = onTogglePause,
                            onEndFocus = onEndFocus,
                            onResetFocus = onResetFocus,
                        )
                        CompanionSurface.Read -> ReadContent(
                            state = state.read,
                            isX3Connected = state.isX3Connected,
                            usbConnected = state.device.usbConnected,
                            connectedDeviceModel = state.connectedDeviceModel,
                            onSetQuery = onSetReadQuery,
                            onSetSort = onSetReadSort,
                            onSetService = onSetReadService,
                            onSetReadLocation = onSetReadLocation,
                            onChooseBookFolder = onChooseBookFolder,
                            onOpenEpub = onOpenEpub,
                            onOpenSettings = { onShowSettings(true) },
                            onUploadBooksToX3 = onUploadBooksToX3,
                            onCancelBookUpload = onCancelBookUpload,
                            onDeleteBooksFromX3 = onDeleteBooksFromX3,
                        )
                        CompanionSurface.Tools -> when (toolDestination) {
                            ToolDestination.Hub -> ToolsHubContent(
                                passCount = state.ticket.passes.size,
                                onOpenPasses = onOpenPasses,
                                onOpenStats = onOpenStats,
                            )
                            ToolDestination.Passes -> PassesToolContent(
                                ticket = state.ticket,
                                onSelectPass = onSelectPass,
                                onSetTicketMode = onSetTicketMode,
                                onSendTicket = onSendTicket,
                                onRemoveTicket = onRemoveTicket,
                                onImportPhoto = onImportPhoto,
                                onImportWalletLink = onImportWalletLink,
                                onImportPassFile = onImportPassFile,
                                onBack = onShowToolHub,
                            )
                            ToolDestination.Stats -> ReadingStatsContent(
                                state = state.readingStats,
                                onSetView = onSetReadingStatsView,
                                onSelectSession = onSelectReadingSession,
                                onDeleteSession = onDeleteReadingSession,
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
            colorMode = state.colorMode,
            radioPolicy = state.radioPolicy,
            minimumReadingPageSeconds = state.readingStats.minimumPageSeconds,
            settingsSyncPending = state.device.settingsSyncPending,
            hasManagedDevice = state.connectedDeviceModel != null,
            onSetVisualTheme = onSetVisualTheme,
            onSetColorMode = onSetColorMode,
            onSetRadioPolicy = onSetRadioPolicy,
            onSetMinimumReadingPageSeconds = onSetMinimumReadingPageSeconds,
            onOpenSetup = onOpenSetup,
            cloudBackupState = cloudBackupState,
            onSyncGoogleBackup = onSyncGoogleBackup,
            onDeleteGoogleBackup = onDeleteGoogleBackup,
            onDismiss = { onShowSettings(false) },
        )
    }
    if (devicesVisible) {
        DeviceConnectionSheet(
            onDismiss = { devicesVisible = false },
            device = state.device,
            hasManagedDevice = state.isX3Connected,
            managedDeviceModel = state.connectedDeviceModel,
            isTransportConnected = state.isX3TransportConnected,
            onConnect = onConnectDevice,
            onCheckFirmware = onCheckFirmware,
            onFlashFirmware = onFlashFirmware,
            onResetUsbSetup = onResetX3Setup,
        )
    }
}
