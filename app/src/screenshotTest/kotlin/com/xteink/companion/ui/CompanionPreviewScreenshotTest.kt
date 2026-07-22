package com.xteink.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.xteink.companion.ui.components.SettingsSheetContent
import com.xteink.companion.ui.theme.X3CompanionTheme

@PreviewTest
@Preview(name = "Expressive focus phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun expressiveFocusPhoneScreenshot() {
    ScreenshotApp(state = CompanionUiState())
}

@PreviewTest
@Preview(name = "Quiet focus phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun quietFocusPhoneScreenshot() {
    ScreenshotApp(
        state = CompanionUiState(
            visualTheme = CompanionVisualTheme.Quiet,
            focus = FocusUiState(
                task = "Finish protocol",
                selectedMinutes = 25,
                remainingSeconds = 19 * 60 + 42,
                phase = FocusPhase.Running,
            ),
        ),
    )
}

@PreviewTest
@Preview(name = "Tools hub phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun toolsHubPhoneScreenshot() {
    ScreenshotApp(state = CompanionUiState(surface = CompanionSurface.Tools))
}

@PreviewTest
@Preview(name = "Read library phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun readLibraryPhoneScreenshot() {
    ScreenshotApp(
        state = CompanionUiState(
            surface = CompanionSurface.Read,
            read = ReadUiState(
                books = listOf(
                    ImportedBookUiState(
                        id = "sample-book",
                        title = "The Left Hand of Darkness",
                        author = "Ursula K. Le Guin",
                        fileName = "left-hand-of-darkness.epub",
                        language = "en",
                        publisher = "Ace Books",
                    ),
                ),
            ),
        ),
    )
}

@PreviewTest
@Preview(name = "Pass detail phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun passDetailPhoneScreenshot() {
    ScreenshotApp(
        state = CompanionUiState(
            surface = CompanionSurface.Tools,
            toolDestination = ToolDestination.Passes,
            ticket = TicketUiState(mode = TicketMode.Live),
        ),
    )
}

@PreviewTest
@Preview(name = "Quiet pass detail", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun quietPassDetailPhoneScreenshot() {
    ScreenshotApp(
        state = CompanionUiState(
            visualTheme = CompanionVisualTheme.Quiet,
            surface = CompanionSurface.Tools,
            toolDestination = ToolDestination.Passes,
            ticket = TicketUiState(mode = TicketMode.Live),
        ),
    )
}

@PreviewTest
@Preview(
    name = "Expressive focus large text",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.3f,
    showBackground = true,
)
@Composable
fun expressiveFocusLargeTextScreenshot() {
    ScreenshotApp(state = CompanionUiState())
}

@PreviewTest
@Preview(name = "Settings themes", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun settingsThemesScreenshot() {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                SettingsSheetContent(
                    visualTheme = CompanionVisualTheme.Expressive,
                    onSetVisualTheme = {},
                    onDismiss = {},
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun ScreenshotApp(state: CompanionUiState) {
    X3CompanionTheme(visualTheme = state.visualTheme) {
        X3CompanionApp(
            state = state,
            onSetVisualTheme = {},
            onSetDuration = {},
            onStartFocus = {},
            onTogglePause = {},
            onEndFocus = {},
            onResetFocus = {},
            onShowTools = {},
            onShowRead = {},
            onShowFocus = {},
            onSetReadQuery = {},
            onSetReadFilter = {},
            onOpenEpub = {},
            onOpenPasses = {},
            onShowToolHub = {},
            onSelectPass = {},
            onSetTicketMode = {},
            onSendTicket = {},
            onShowSettings = {},
            onDismissNotice = {},
        )
    }
}
