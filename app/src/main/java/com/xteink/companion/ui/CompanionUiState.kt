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
)

enum class ReadFilter {
    All,
    WithCover,
    NeedsDetails,
}

data class ReadUiState(
    val books: List<ImportedBookUiState> = emptyList(),
    val query: String = "",
    val filter: ReadFilter = ReadFilter.All,
    val importing: Boolean = false,
)

data class CompanionUiState(
    val visualTheme: CompanionVisualTheme = CompanionVisualTheme.Expressive,
    val surface: CompanionSurface = CompanionSurface.Focus,
    val toolDestination: ToolDestination = ToolDestination.Hub,
    val focus: FocusUiState = FocusUiState(),
    val read: ReadUiState = ReadUiState(),
    val ticket: TicketUiState = TicketUiState(),
    val settingsVisible: Boolean = false,
    val notice: UiNotice? = null,
)

sealed interface UiNotice {
    data object FocusStartedWithoutX3 : UiNotice
    data object PairBeforeSend : UiNotice
    data object EpubImportFailed : UiNotice
    data class BooksImported(
        val added: Int,
        val duplicates: Int,
        val failed: Int,
    ) : UiNotice
}
