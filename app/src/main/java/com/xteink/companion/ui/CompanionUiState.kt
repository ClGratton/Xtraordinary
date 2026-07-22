package com.xteink.companion.ui

enum class CompanionVisualTheme {
    Expressive,
    Quiet,
}

enum class CompanionSurface {
    Focus,
    Read,
    Tools,
}

enum class ToolDestination {
    Hub,
    Passes,
}

enum class FocusPhase {
    Setup,
    Running,
    Paused,
    Review,
}

enum class TicketMode {
    Static,
    Live,
}

data class FocusUiState(
    val task: String = "Deep work",
    val selectedMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val phase: FocusPhase = FocusPhase.Setup,
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
    val countdown: String,
    val gate: String,
    val terminal: String,
    val seat: String,
    val passenger: String,
    val boardingGroup: String,
    val source: String,
)

private val SamplePasses = listOf(
    BoardingPassUiState(
        id = "dl2048",
        origin = "SFO",
        destination = "JFK",
        flight = "DL 2048",
        status = "On time",
        departureTime = "11:45 AM",
        countdown = "in 1h 25m",
        gate = "A12",
        terminal = "2",
        seat = "22B",
        passenger = "CLAUDIO A.",
        boardingGroup = "Main 2",
        source = "Sample Wallet pass · updated 2 min ago",
    ),
    BoardingPassUiState(
        id = "az610",
        origin = "FCO",
        destination = "JFK",
        flight = "AZ 610",
        status = "Boarding",
        departureTime = "2:50 PM",
        countdown = "gate closes in 18m",
        gate = "E31",
        terminal = "3",
        seat = "14A",
        passenger = "CLAUDIO A.",
        boardingGroup = "Group 3",
        source = "Sample airline notification · updated now",
    ),
)

data class TicketUiState(
    val mode: TicketMode = TicketMode.Static,
    val passes: List<BoardingPassUiState> = SamplePasses,
    val selectedPassId: String = SamplePasses.first().id,
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

data class ReadUiState(
    val books: List<ImportedBookUiState> = emptyList(),
    val query: String = "",
    val sort: ReadSort = ReadSort.Recent,
    val service: ReadService = ReadService.All,
    val onX3Only: Boolean = false,
    val importing: Boolean = false,
    val syncing: Boolean = false,
    val folderLinked: Boolean = false,
)

enum class FirmwareCheckPhase { Idle, Checking, Available, UpToDate, Downloading, Transferring, Complete, Error }

data class DeviceUiState(
    val linkPhase: String = "Disconnected",
    val message: String? = null,
    val firmwareVersion: String? = null,
    val libraryRevision: UInt = 0u,
    val firmwareCheckPhase: FirmwareCheckPhase = FirmwareCheckPhase.Idle,
    val latestFirmwareVersion: String? = null,
    val firmwareProgress: Float? = null,
)

data class CompanionUiState(
    val visualTheme: CompanionVisualTheme = CompanionVisualTheme.Expressive,
    val surface: CompanionSurface = CompanionSurface.Focus,
    val toolDestination: ToolDestination = ToolDestination.Hub,
    val focus: FocusUiState = FocusUiState(),
    val read: ReadUiState = ReadUiState(),
    val ticket: TicketUiState = TicketUiState(),
    val device: DeviceUiState = DeviceUiState(),
    val isX3Connected: Boolean = false,
    val connectedDeviceModel: String? = null,
    val settingsVisible: Boolean = false,
    val notice: UiNotice? = null,
)

sealed interface UiNotice {
    data object FocusStartedWithoutX3 : UiNotice
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
}
