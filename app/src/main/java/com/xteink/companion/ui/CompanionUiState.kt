package com.xteink.companion.ui

import com.xteink.companion.data.FirmwareSource
import com.xteink.companion.data.FlightBarcodeFormat
import com.xteink.companion.data.ReadingSessionStat

enum class CompanionVisualTheme {
    Expressive,
    Minimal,
}

enum class CompanionColorMode {
    Light,
    Dark,
}

enum class CompanionSurface {
    Focus,
    Read,
    Tools,
}

enum class ToolDestination {
    Hub,
    Passes,
    Stats,
}

enum class ReadingStatsView { Cumulative, Sessions }

data class ReadingStatsUiState(
    val sessions: List<ReadingSessionStat> = emptyList(),
    val minimumPageSeconds: Int = 5,
    val view: ReadingStatsView = ReadingStatsView.Cumulative,
    val selectedSessionId: UInt? = null,
    val syncing: Boolean = false,
)

enum class FocusPhase {
    Setup,
    Running,
    Paused,
    Review,
}

enum class FocusPendingAction {
    Start,
    Pause,
    Resume,
    Stop,
}

enum class TicketMode {
    Static,
    Live,
}

data class PendingTicketOperation(
    val passId: String,
    val mode: TicketMode,
)

data class FocusUiState(
    val task: String = "Deep work",
    val selectedMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val phase: FocusPhase = FocusPhase.Setup,
    val pendingAction: FocusPendingAction? = null,
) {
    val progress: Float
        get() {
            val total = selectedMinutes * 60
            if (total <= 0) return 0f
            return (remainingSeconds.toFloat() / total).coerceIn(0f, 1f)
        }
}

data class BoardingPassUiState(
    val id: String,
    val origin: String,
    val destination: String,
    val flight: String,
    val status: String,
    val departureTime: String,
    val arrivalTime: String = "",
    val delayMinutes: Int? = null,
    val operatingDate: String = "",
    val liveUpdatedAtEpochMs: Long? = null,
    val liveProvider: String = "",
    val countdown: String,
    val gate: String,
    val terminal: String,
    val seat: String,
    val passenger: String,
    val boardingGroup: String,
    val source: String,
    val barcodePayload: String,
    val barcodeFormat: FlightBarcodeFormat = FlightBarcodeFormat.Unknown,
    val isSample: Boolean = false,
)

private val SamplePasses = listOf(
    BoardingPassUiState(
        id = "dl2048",
        origin = "SFO",
        destination = "JFK",
        flight = "DL 2048",
        status = "On time",
        departureTime = "11:45 AM",
        arrivalTime = "8:05 PM",
        delayMinutes = 0,
        operatingDate = "2026-08-13",
        countdown = "in 1h 25m",
        gate = "A12",
        terminal = "2",
        seat = "22B",
        passenger = "CLAUDIO A.",
        boardingGroup = "Main 2",
        barcodePayload = "SAMPLE-DL2048-NOT-SCANNABLE",
        barcodeFormat = FlightBarcodeFormat.Qr,
        isSample = true,
        source = "Sample Wallet pass · updated 2 min ago",
    ),
    BoardingPassUiState(
        id = "az610",
        origin = "FCO",
        destination = "JFK",
        flight = "AZ 610",
        status = "Boarding",
        departureTime = "2:50 PM",
        arrivalTime = "6:10 PM",
        delayMinutes = 18,
        operatingDate = "2026-08-13",
        countdown = "gate closes in 18m",
        gate = "E31",
        terminal = "3",
        seat = "14A",
        passenger = "CLAUDIO A.",
        boardingGroup = "Group 3",
        barcodePayload = "SAMPLE-AZ610-NOT-SCANNABLE",
        barcodeFormat = FlightBarcodeFormat.Qr,
        isSample = true,
        source = "Sample airline notification · updated now",
    ),
)

data class TicketUiState(
    val mode: TicketMode = TicketMode.Static,
    val passes: List<BoardingPassUiState> = SamplePasses,
    val selectedPassId: String = SamplePasses.first().id,
    val isOnX3: Boolean = false,
    val sendPending: Boolean = false,
    /** Immutable identity of the payload currently queued or being sent. */
    val pendingOperation: PendingTicketOperation? = null,
    val removalPending: Boolean = false,
    val deployedPassId: String? = null,
    /** Last mode confirmed by the X3, never the user's next-send selection. */
    val deployedMode: TicketMode? = null,
) {
    val selectedPass: BoardingPassUiState
        get() = passes.firstOrNull { it.id == selectedPassId } ?: passes.first()
}

data class ImportedBookUiState(
    val id: String,
    val title: String,
    val author: String,
    val fileName: String,
    val sourceUri: String = "",
    val coverPath: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val isbn: String? = null,
    val subjects: List<String> = emptyList(),
    val fileSizeBytes: Long? = null,
    val metadataSource: String = "EPUB",
    val importedAtEpochMs: Long = 0L,
    val lastMetadataLookupEpochMs: Long? = null,
    val fileModifiedAtEpochMs: Long? = null,
    val isOnPhone: Boolean = true,
    val isOnX3: Boolean = false,
    val x3Path: String? = null,
    val sourceFolderUri: String? = null,
)

enum class ReadSort {
    Recent,
    Name,
    Size,
}

enum class ReadService {
    All,
    LocalEpub,
}

enum class ReadLocation {
    Anywhere,
    Phone,
    X3,
}

enum class BookTransferMethod {
    Bluetooth,
    Usb,
}

data class ReadUiState(
    val books: List<ImportedBookUiState> = emptyList(),
    val query: String = "",
    val sort: ReadSort = ReadSort.Recent,
    val service: ReadService = ReadService.All,
    val location: ReadLocation = ReadLocation.Anywhere,
    val importing: Boolean = false,
    val uploadingToX3: Boolean = false,
    val uploadProgress: Float? = null,
    val uploadingBookId: String? = null,
    val uploadingBookProgress: Float? = null,
    val uploadMethod: BookTransferMethod? = null,
    val directUploadOfferBookIds: Set<String> = emptySet(),
    val syncing: Boolean = false,
    val folderLinked: Boolean = false,
)

enum class FirmwareCheckPhase { Idle, Checking, Available, UpToDate, Downloading, Transferring, Complete, Error }

data class DeviceUiState(
    val linkPhase: String = "Disconnected",
    /** A best-effort foreground status probe must not replace Paired with Connecting. */
    val quietLinkProbe: Boolean = false,
    val reconnecting: Boolean = false,
    val requiresBluetoothReset: Boolean = false,
    val transportBlocker: com.xteink.companion.data.LinkBlocker? = null,
    val message: String? = null,
    val usbConnected: Boolean = false,
    val managedBleFirmwareReady: Boolean = false,
    val usbPhase: String = "Disconnected",
    val usbMessage: String? = null,
    val firmwareVersion: String? = null,
    val libraryRevision: UInt = 0u,
    val firmwareCheckPhase: FirmwareCheckPhase = FirmwareCheckPhase.Idle,
    val firmwareSource: FirmwareSource = FirmwareSource.Xtraordinary,
    val latestFirmwareVersion: String? = null,
    val firmwareProgress: Float? = null,
    val batteryPercentage: Int? = null,
    val charging: Boolean = false,
    val settingsSyncPending: Boolean = false,
)

data class RadioPolicyUiState(
    val fastWindowMinutes: Int = 5,
    val standbyIntervalSeconds: Int = 30,
    val connectedIntervalMs: Int = 2_000,
    val sleepAfterMinutes: Int = 10,
    val fullRefreshPages: Int = 15,
    val powerButtonHoldMs: Int = 1_000,
)

data class CompanionUiState(
    val visualTheme: CompanionVisualTheme = CompanionVisualTheme.Expressive,
    val colorMode: CompanionColorMode = CompanionColorMode.Light,
    val surface: CompanionSurface = CompanionSurface.Focus,
    val toolDestination: ToolDestination = ToolDestination.Hub,
    val focus: FocusUiState = FocusUiState(),
    val read: ReadUiState = ReadUiState(),
    val ticket: TicketUiState = TicketUiState(),
    val readingStats: ReadingStatsUiState = ReadingStatsUiState(),
    val device: DeviceUiState = DeviceUiState(),
    val radioPolicy: RadioPolicyUiState = RadioPolicyUiState(),
    // This is the logical, user-visible relationship: a managed device remains
    // connected while its short-lived BLE transport is intentionally idle.
    val isX3Connected: Boolean = false,
    val isX3TransportConnected: Boolean = false,
    val connectedDeviceModel: String? = null,
    val settingsVisible: Boolean = false,
    val notice: UiNotice? = null,
)

sealed interface UiNotice {
    data object PairBeforeSend : UiNotice
    data object EpubImportFailed : UiNotice
    data object ConnectX3ToDelete : UiNotice
    data class X3DeleteQueued(val count: Int) : UiNotice
    data class BooksImported(
        val added: Int,
        val duplicates: Int,
        val failed: Int,
    ) : UiNotice
    data class FolderSynced(val found: Int, val added: Int) : UiNotice
    data class DeviceMessage(val text: String) : UiNotice
    data class SessionDeleted(val title: String) : UiNotice
}
