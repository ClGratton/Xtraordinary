package com.xteink.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xteink.companion.data.BluetoothCompanionClient
import com.xteink.companion.data.BookLibraryRepository
import com.xteink.companion.data.FirmwareRelease
import com.xteink.companion.data.FirmwareReleaseRepository
import com.xteink.companion.data.LinkPhase
import com.xteink.companion.protocol.SessionStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CompanionViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()
    private val companionClient = BluetoothCompanionClient(application)
    private val firmwareReleases = FirmwareReleaseRepository(application)
    private val bookLibrary = BookLibraryRepository(application)
    private var latestRelease: FirmwareRelease? = null

    init {
        viewModelScope.launch {
            companionClient.state.collect { link ->
                val capabilities = link.capabilities
                _uiState.update { state ->
                    state.copy(
                        isX3Connected = link.phase == LinkPhase.Connected,
                        connectedDeviceModel = capabilities?.model,
                        device = state.device.copy(
                            linkPhase = link.phase.name,
                            message = link.message,
                            firmwareVersion = capabilities?.firmwareVersion,
                            libraryRevision = capabilities?.libraryRevision ?: state.device.libraryRevision,
                            firmwareProgress = link.transferProgress ?: state.device.firmwareProgress,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            companionClient.libraries.collect { snapshot ->
                reconcileDeviceLibrary(snapshot.revision, snapshot.entries.map { it.path to it.sizeBytes })
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _uiState.update { state ->
                    if (state.focus.phase != FocusPhase.Running) return@update state
                    val nextRemaining = (state.focus.remainingSeconds - 1).coerceAtLeast(0)
                    state.copy(
                        focus = state.focus.copy(
                            remainingSeconds = nextRemaining,
                            phase = if (nextRemaining == 0) FocusPhase.Review else FocusPhase.Running,
                        ),
                    )
                }
            }
        }
    }

    fun setVisualTheme(theme: CompanionVisualTheme) {
        _uiState.update { it.copy(visualTheme = theme) }
    }

    fun setTask(task: String) {
        _uiState.update { state ->
            if (state.focus.phase != FocusPhase.Setup) state
            else state.copy(focus = state.focus.copy(task = task.take(80)))
        }
    }

    fun setDuration(minutes: Int) {
        val bounded = minutes.coerceIn(5, 60)
        _uiState.update { state ->
            if (state.focus.phase != FocusPhase.Setup) state
            else state.copy(
                focus = state.focus.copy(
                    selectedMinutes = bounded,
                    remainingSeconds = bounded * 60,
                ),
            )
        }
    }

    fun startFocus() {
        _uiState.update { state ->
            state.copy(
                surface = CompanionSurface.Focus,
                focus = state.focus.copy(
                    phase = FocusPhase.Running,
                    remainingSeconds = state.focus.selectedMinutes * 60,
                ),
                notice = if (state.isX3Connected) null else UiNotice.FocusStartedWithoutX3,
            )
        }
        val state = _uiState.value
        if (state.isX3Connected) viewModelScope.launch {
            runDeviceCommand {
                companionClient.startSession(
                    SessionStart(
                        deadlineEpochSeconds = System.currentTimeMillis() / 1_000 + state.focus.selectedMinutes * 60,
                        durationSeconds = state.focus.selectedMinutes * 60,
                        title = state.focus.task,
                    ),
                )
            }
        }
    }

    fun togglePause() {
        val wasRunning = _uiState.value.focus.phase == FocusPhase.Running
        _uiState.update { state ->
            val nextPhase = when (state.focus.phase) {
                FocusPhase.Running -> FocusPhase.Paused
                FocusPhase.Paused -> FocusPhase.Running
                else -> state.focus.phase
            }
            state.copy(focus = state.focus.copy(phase = nextPhase))
        }
        if (_uiState.value.isX3Connected) viewModelScope.launch {
            runDeviceCommand {
                if (wasRunning) companionClient.pauseSession() else companionClient.resumeSession()
            }
        }
    }

    fun endFocus() {
        _uiState.update { state ->
            state.copy(
                focus = state.focus.copy(
                    phase = FocusPhase.Setup,
                    remainingSeconds = state.focus.selectedMinutes * 60,
                ),
            )
        }
        if (_uiState.value.isX3Connected) viewModelScope.launch { runDeviceCommand { companionClient.stopSession() } }
    }

    fun resetFocus() {
        _uiState.update { state ->
            state.copy(
                surface = CompanionSurface.Focus,
                focus = state.focus.copy(
                    phase = FocusPhase.Setup,
                    remainingSeconds = state.focus.selectedMinutes * 60,
                ),
            )
        }
        if (_uiState.value.isX3Connected) viewModelScope.launch { runDeviceCommand { companionClient.stopSession() } }
    }

    fun showTools() {
        _uiState.update { it.copy(surface = CompanionSurface.Tools) }
    }

    fun showRead() {
        _uiState.update { it.copy(surface = CompanionSurface.Read) }
    }

    fun showFocus() {
        _uiState.update { it.copy(surface = CompanionSurface.Focus) }
    }

    fun openPasses() {
        _uiState.update {
            it.copy(surface = CompanionSurface.Tools, toolDestination = ToolDestination.Passes)
        }
    }

    fun showToolHub() {
        _uiState.update { it.copy(toolDestination = ToolDestination.Hub) }
    }

    fun setTicketMode(mode: TicketMode) {
        _uiState.update { it.copy(ticket = it.ticket.copy(mode = mode)) }
    }

    fun selectPass(passId: String) {
        _uiState.update { state ->
            if (state.ticket.passes.none { it.id == passId }) state
            else state.copy(ticket = state.ticket.copy(selectedPassId = passId))
        }
    }

    fun addImportedBook(book: ImportedBookUiState) {
        addImportedBooks(listOf(book))
    }

    fun addImportedBooks(books: List<ImportedBookUiState>) {
        _uiState.update { state ->
            state.copy(
                surface = CompanionSurface.Read,
                read = state.read.copy(
                    books = (books + state.read.books)
                        .distinctBy { it.id }
                        .sortedByDescending { it.importedAtEpochMs },
                ),
            )
        }
    }

    fun restoreBooks(books: List<ImportedBookUiState>) {
        _uiState.update { state ->
            state.copy(
                read = state.read.copy(
                    books = books.sortedByDescending { it.importedAtEpochMs },
                ),
            )
        }
    }

    fun setReadQuery(query: String) {
        _uiState.update { it.copy(read = it.read.copy(query = query.take(80))) }
    }

    fun setReadSort(sort: ReadSort) {
        _uiState.update { it.copy(read = it.read.copy(sort = sort)) }
    }

    fun setReadService(service: ReadService) {
        _uiState.update { it.copy(read = it.read.copy(service = service)) }
    }

    fun setOnX3Only(enabled: Boolean) {
        _uiState.update { it.copy(read = it.read.copy(onX3Only = enabled)) }
    }

    fun requestDeleteBooksFromX3(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        _uiState.update { state ->
            if (!state.isX3Connected) {
                state.copy(notice = UiNotice.ConnectX3ToDelete)
            } else state
        }
        if (!_uiState.value.isX3Connected) return
        val paths = _uiState.value.read.books.filter { it.id in bookIds }.mapNotNull { it.x3Path }
        if (paths.isEmpty()) return
        viewModelScope.launch {
            runDeviceCommand {
                companionClient.deleteLibraryEntries(_uiState.value.device.libraryRevision, paths)
                _uiState.update { it.copy(notice = UiNotice.X3DeleteQueued(paths.size)) }
            }
        }
    }

    fun hasCompanionPermissions(): Boolean = companionClient.hasPermissions()

    fun connectDevice(model: String) {
        companionClient.connect(model)
    }

    fun disconnectDevice() = companionClient.disconnect()

    fun checkLatestFirmware(model: String) {
        _uiState.update { it.copy(device = it.device.copy(firmwareCheckPhase = FirmwareCheckPhase.Checking, message = null)) }
        viewModelScope.launch {
            runCatching { firmwareReleases.latestFor(model) }
                .onSuccess { release ->
                    latestRelease = release
                    val currentVersion = _uiState.value.device.firmwareVersion
                    _uiState.update {
                        it.copy(device = it.device.copy(
                            firmwareCheckPhase = if (currentVersion == release.version) {
                                FirmwareCheckPhase.UpToDate
                            } else {
                                FirmwareCheckPhase.Available
                            },
                            latestFirmwareVersion = release.version,
                            message = "${release.version} is ready for ${release.model}",
                        ))
                    }
                }
                .onFailure { reportDeviceError(it) }
        }
    }

    fun flashLatestFirmware() {
        val release = latestRelease ?: return
        if (!_uiState.value.isX3Connected) {
            _uiState.update { it.copy(notice = UiNotice.DeviceMessage("Connect the device before flashing firmware")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(device = it.device.copy(firmwareCheckPhase = FirmwareCheckPhase.Downloading)) }
            runCatching {
                val file = firmwareReleases.downloadVerified(release)
                _uiState.update { it.copy(device = it.device.copy(firmwareCheckPhase = FirmwareCheckPhase.Transferring)) }
                companionClient.flashFirmware(release, file)
            }.onSuccess {
                _uiState.update { it.copy(device = it.device.copy(firmwareCheckPhase = FirmwareCheckPhase.Complete)) }
            }.onFailure(::reportDeviceError)
        }
    }

    fun setImporting(importing: Boolean) {
        _uiState.update { it.copy(read = it.read.copy(importing = importing)) }
    }

    fun setLibrarySyncState(syncing: Boolean, folderLinked: Boolean? = null) {
        _uiState.update { state ->
            state.copy(
                read = state.read.copy(
                    syncing = syncing,
                    folderLinked = folderLinked ?: state.read.folderLinked,
                ),
            )
        }
    }

    fun reportFolderSync(found: Int, added: Int) {
        _uiState.update {
            it.copy(surface = CompanionSurface.Read, notice = UiNotice.FolderSynced(found, added))
        }
    }

    fun reportImportResult(added: Int, duplicates: Int, failed: Int) {
        _uiState.update {
            it.copy(
                surface = CompanionSurface.Read,
                notice = UiNotice.BooksImported(added, duplicates, failed),
            )
        }
    }

    fun reportEpubImportFailure() {
        _uiState.update { it.copy(notice = UiNotice.EpubImportFailed) }
    }

    fun sendTicket() {
        _uiState.update { it.copy(notice = UiNotice.PairBeforeSend) }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(settingsVisible = show) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun reconcileDeviceLibrary(revision: UInt, entries: List<Pair<String, Long>>) {
        _uiState.update { state ->
            val byFileName = entries.associateBy { it.first.substringAfterLast('/').lowercase() }
            val matchedPaths = mutableSetOf<String>()
            val reconciled = state.read.books.map { book ->
                val match = byFileName[book.fileName.lowercase()]
                if (match != null) matchedPaths += match.first
                book.copy(isOnX3 = match != null, x3Path = match?.first)
            }.toMutableList()
            entries.filterNot { it.first in matchedPaths }.forEach { (path, size) ->
                val fileName = path.substringAfterLast('/')
                reconciled += ImportedBookUiState(
                    id = "xteink:${path.hashCode().toUInt().toString(16)}",
                    title = fileName.substringBeforeLast('.'),
                    author = "On XTEINK",
                    fileName = fileName,
                    fileSizeBytes = size,
                    isOnPhone = false,
                    isOnX3 = true,
                    x3Path = path,
                    metadataSource = "XTEINK",
                )
            }
            val saved = reconciled.sortedByDescending { it.importedAtEpochMs }
            bookLibrary.save(saved)
            state.copy(read = state.read.copy(books = saved), device = state.device.copy(libraryRevision = revision))
        }
    }

    private suspend fun runDeviceCommand(block: suspend () -> Unit) {
        runCatching { block() }.onFailure(::reportDeviceError)
    }

    private fun reportDeviceError(error: Throwable) {
        val message = error.message ?: "Device operation failed"
        _uiState.update {
            it.copy(
                device = it.device.copy(firmwareCheckPhase = FirmwareCheckPhase.Error, message = message),
                notice = UiNotice.DeviceMessage(message),
            )
        }
    }
}
