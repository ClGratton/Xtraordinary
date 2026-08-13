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
import com.xteink.companion.data.CloudBackupState
import com.xteink.companion.data.FlightBarcodeFormat
import com.xteink.companion.ui.components.SettingsSheetContent
import com.xteink.companion.ui.components.DeviceConnectionSheetContent
import com.xteink.companion.ui.components.DeviceSetupStep
import com.xteink.companion.ui.components.ReadContent
import com.xteink.companion.ui.components.SetupScreen
import com.xteink.companion.ui.theme.X3CompanionTheme

@PreviewTest
@Preview(name = "Expressive focus phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun expressiveFocusPhoneScreenshot() {
    ScreenshotApp(state = CompanionUiState())
}

@PreviewTest
@Preview(name = "First-run setup", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun firstRunSetupScreenshot() {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive, useDynamicColor = false) {
        SetupScreen(
            folderLinked = false,
            onChooseBookFolder = {},
            onFinish = {},
        )
    }
}

@PreviewTest
@Preview(name = "First-run setup compact height", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun firstRunSetupCompactScreenshot() {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive, useDynamicColor = false) {
        SetupScreen(
            folderLinked = false,
            onChooseBookFolder = {},
            onFinish = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "First-run setup large text",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.3f,
    showBackground = true,
)
@Composable
fun firstRunSetupLargeTextScreenshot() {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive, useDynamicColor = false) {
        SetupScreen(
            folderLinked = false,
            onChooseBookFolder = {},
            onFinish = {},
        )
    }
}

@PreviewTest
@Preview(name = "Setup library", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun setupLibraryScreenshot() {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive, useDynamicColor = false) {
        SetupScreen(
            folderLinked = false,
            onChooseBookFolder = {},
            onFinish = {},
            initialPage = 1,
        )
    }
}

@PreviewTest
@Preview(name = "Setup device", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun setupDeviceScreenshot() {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive, useDynamicColor = false) {
        SetupScreen(
            folderLinked = false,
            onChooseBookFolder = {},
            onFinish = {},
            initialPage = 2,
        )
    }
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
            isX3Connected = true,
            connectedDeviceModel = "X4 Pro",
            read = screenshotReadState(),
        ),
    )
}

@PreviewTest
@Preview(name = "Read content selected", widthDp = 412, heightDp = 760, showBackground = true)
@Composable
fun readContentSelectedScreenshot() {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive, useDynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ReadContent(
                state = screenshotReadState(),
                isX3Connected = true,
                usbConnected = false,
                connectedDeviceModel = "X4 Pro",
                onSetQuery = {},
                onSetSort = {},
                onSetService = {},
                onSetReadLocation = {},
                onChooseBookFolder = {},
                onOpenEpub = {},
                onOpenSettings = {},
                onUploadBooksToX3 = { _, _ -> },
                onCancelBookUpload = {},
                onDeleteBooksFromX3 = {},
                initialSelectedBookIds = setOf("sample-book"),
            )
        }
    }
}

private fun screenshotReadState() = ReadUiState(
    books = listOf(
        ImportedBookUiState(
            id = "sample-book",
            title = "The Left Hand of Darkness",
            author = "Ursula K. Le Guin",
            fileName = "left-hand-of-darkness.epub",
            language = "en",
            publisher = "Ace Books",
            isOnX3 = true,
            x3Path = "/Books/left-hand-of-darkness.epub",
        ),
        ImportedBookUiState(
            id = "phone-book",
            title = "The Dispossessed",
            author = "Ursula K. Le Guin",
            fileName = "the-dispossessed.epub",
            language = "en",
            publisher = "Harper & Row",
        ),
    ),
)

private val linearPassPreviewState = TicketUiState(
    mode = TicketMode.Live,
    passes = listOf(
        BoardingPassUiState(
            id = "linear-pass",
            origin = "AHO",
            destination = "VCE",
            flight = "W4 6762",
            status = "Departed",
            departureTime = "13:35",
            countdown = "departed",
            gate = "TBD",
            terminal = "-",
            seat = "17B",
            passenger = "CLAUDIO A.",
            boardingGroup = "",
            source = "Imported from photo - extracted on device",
            barcodePayload = "M1EXAMPLE/PASSENGER EABC123 AHOVCEW4 6762 222Y017B0001 100",
            barcodeFormat = FlightBarcodeFormat.Pdf417,
        ),
    ),
    selectedPassId = "linear-pass",
)

private val matrixPassPreviewState = TicketUiState(
    mode = TicketMode.Static,
    passes = listOf(
        BoardingPassUiState(
            id = "matrix-pass",
            origin = "AHO",
            destination = "VCE",
            flight = "FR 6779",
            status = "Departed",
            departureTime = "20:40",
            countdown = "departed",
            gate = "CLOSES",
            terminal = "-",
            seat = "05D",
            passenger = "Claudio Gratton",
            boardingGroup = "NON BAG",
            source = "Imported from Google Wallet",
            barcodePayload = "M1GRATTON/CLAUDIO EABC123 AHOVCEFR 6779 222Y005D0001 100",
            barcodeFormat = FlightBarcodeFormat.Aztec,
        ),
        linearPassPreviewState.passes.single(),
    ),
    selectedPassId = "matrix-pass",
)

@PreviewTest
@Preview(name = "Pass detail phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun passDetailPhoneScreenshot() {
    ScreenshotApp(
        state = CompanionUiState(
            surface = CompanionSurface.Tools,
            toolDestination = ToolDestination.Passes,
            ticket = matrixPassPreviewState,
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
            ticket = linearPassPreviewState,
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
    SettingsScreenshotContent(cloudBackupState = CloudBackupState())
}

@PreviewTest
@Preview(name = "Pass detail compact", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun passDetailCompactScreenshot() {
    ScreenshotApp(state = CompanionUiState(surface = CompanionSurface.Tools, toolDestination = ToolDestination.Passes, ticket = matrixPassPreviewState))
}

@PreviewTest
@Preview(name = "Pass detail large text", widthDp = 412, heightDp = 915, fontScale = 1.3f, showBackground = true)
@Composable
fun passDetailLargeTextScreenshot() {
    ScreenshotApp(state = CompanionUiState(surface = CompanionSurface.Tools, toolDestination = ToolDestination.Passes, ticket = matrixPassPreviewState))
}

@PreviewTest
@Preview(name = "Pass detail huge text", widthDp = 412, heightDp = 915, fontScale = 2f, showBackground = true)
@Composable
fun passDetailHugeTextScreenshot() {
    ScreenshotApp(state = CompanionUiState(surface = CompanionSurface.Tools, toolDestination = ToolDestination.Passes, ticket = matrixPassPreviewState))
}

@PreviewTest
@Preview(name = "Settings Google disconnected", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun settingsGoogleDisconnectedScreenshot() {
    SettingsScreenshotContent(cloudBackupState = CloudBackupState())
}

@PreviewTest
@Preview(name = "Settings Google connected", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun settingsGoogleConnectedScreenshot() {
    SettingsScreenshotContent(
        cloudBackupState = CloudBackupState(
            enabled = true,
            accountName = "Reader",
            accountEmail = "reader@example.com",
            message = "Reading history is up to date",
        ),
    )
}

@PreviewTest
@Preview(
    name = "Settings Google large text",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.3f,
    showBackground = true,
)
@Composable
fun settingsGoogleLargeTextScreenshot() {
    SettingsScreenshotContent(cloudBackupState = CloudBackupState())
}

@Composable
private fun SettingsScreenshotContent(cloudBackupState: CloudBackupState) {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive, useDynamicColor = false) {
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
                    radioPolicy = RadioPolicyUiState(fastWindowMinutes = 1, sleepAfterMinutes = 5),
                    minimumReadingPageSeconds = 5,
                    settingsSyncPending = true,
                    hasManagedDevice = true,
                    onSetVisualTheme = {},
                    onSetRadioPolicy = {},
                    onSetMinimumReadingPageSeconds = {},
                    onOpenSetup = {},
                    cloudBackupState = cloudBackupState,
                    onSyncGoogleBackup = {},
                    onDeleteGoogleBackup = {},
                    onOpenLegal = {},
                    onDismiss = {},
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "Devices empty", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun devicesEmptyScreenshot() {
    DeviceSheetScreenshot(step = DeviceSetupStep.Devices)
}

@PreviewTest
@Preview(name = "Device model picker", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun deviceModelPickerScreenshot() {
    DeviceSheetScreenshot(step = DeviceSetupStep.ChooseModel)
}

@PreviewTest
@Preview(name = "Firmware wake guidance", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun firmwareWakeGuidanceScreenshot() {
    DeviceSheetScreenshot(
        step = DeviceSetupStep.FirmwareDefault,
        device = DeviceUiState(
            firmwareCheckPhase = FirmwareCheckPhase.Available,
            latestFirmwareVersion = "xtraordinary-v0.2.6-dev16-local",
            usbConnected = false,
        ),
    )
}

@Composable
private fun DeviceSheetScreenshot(
    step: DeviceSetupStep,
    device: DeviceUiState = DeviceUiState(),
) {
    X3CompanionTheme(visualTheme = CompanionVisualTheme.Expressive, useDynamicColor = false) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                DeviceConnectionSheetContent(
                    onDismiss = {},
                    initialStep = step,
                    device = device,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun ScreenshotApp(state: CompanionUiState) {
    X3CompanionTheme(visualTheme = state.visualTheme, useDynamicColor = false) {
        X3CompanionApp(
            state = state,
            onSetVisualTheme = {},
            onSetRadioPolicy = {},
            onSetDuration = {},
            onStartFocus = {},
            onTogglePause = {},
            onEndFocus = {},
            onResetFocus = {},
            onShowTools = {},
            onShowRead = {},
            onShowFocus = {},
            onSetReadQuery = {},
            onSetReadSort = {},
            onSetReadService = {},
            onSetReadLocation = {},
            onChooseBookFolder = {},
            onOpenEpub = {},
            onUploadBooksToX3 = { _, _ -> },
            onCancelBookUpload = {},
            onDeleteBooksFromX3 = {},
            onOpenPasses = {},
            onOpenStats = {},
            onSetReadingStatsView = {},
            onSelectReadingSession = {},
            onDeleteReadingSession = {},
            onUndoReadingSessionDeletion = {},
            onSetMinimumReadingPageSeconds = {},
            onShowToolHub = {},
            onSelectPass = {},
            onSetTicketMode = {},
            onSendTicket = {},
            onShowSettings = {},
            onOpenSetup = {},
            onDismissNotice = {},
        )
    }
}
