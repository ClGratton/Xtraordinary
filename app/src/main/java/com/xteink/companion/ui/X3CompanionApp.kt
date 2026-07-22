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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xteink.companion.R
import com.xteink.companion.ui.components.CompanionNavigation
import com.xteink.companion.ui.components.CompanionTopBar
import com.xteink.companion.ui.components.ControlDeckFocusContent
import com.xteink.companion.ui.components.PassesToolContent
import com.xteink.companion.ui.components.ReadContent
import com.xteink.companion.ui.components.SettingsSheet
import com.xteink.companion.ui.components.ToolsHubContent

@Composable
fun X3CompanionApp(
    state: CompanionUiState,
    onSetVisualTheme: (CompanionVisualTheme) -> Unit,
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
    onSetOnDeviceOnly: (Boolean) -> Unit,
    onChooseBookFolder: () -> Unit,
    onOpenEpub: () -> Unit,
    onOpenPasses: () -> Unit,
    onShowToolHub: () -> Unit,
    onSelectPass: (String) -> Unit,
    onSetTicketMode: (TicketMode) -> Unit,
    onSendTicket: () -> Unit,
    onShowSettings: (Boolean) -> Unit,
    onDismissNotice: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeText = when (val notice = state.notice) {
        UiNotice.FocusStartedWithoutX3 -> stringResource(R.string.focus_started_without_x3)
        UiNotice.PairBeforeSend -> stringResource(R.string.pair_before_send)
        UiNotice.EpubImportFailed -> stringResource(R.string.epub_import_failed)
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
        null -> null
    }

    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            onDismissNotice()
        }
    }

    BackHandler(enabled = state.settingsVisible || state.toolDestination != ToolDestination.Hub) {
        if (state.settingsVisible) onShowSettings(false) else onShowToolHub()
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
            CompanionTopBar(onShowSettings = { onShowSettings(true) })
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
                            onStartFocus = onStartFocus,
                            onTogglePause = onTogglePause,
                            onEndFocus = onEndFocus,
                            onResetFocus = onResetFocus,
                            onSendScene = onSendTicket,
                        )
                        CompanionSurface.Read -> ReadContent(
                            state = state.read,
                            onSetQuery = onSetReadQuery,
                            onSetSort = onSetReadSort,
                            onSetOnDeviceOnly = onSetOnDeviceOnly,
                            onChooseBookFolder = onChooseBookFolder,
                            onOpenEpub = onOpenEpub,
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
                                onSendTicket = onSendTicket,
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
            onSetVisualTheme = onSetVisualTheme,
            onDismiss = { onShowSettings(false) },
        )
    }
}
