package com.xteink.companion.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xteink.companion.data.BluetoothCompanionClient
import com.xteink.companion.data.BookLibraryRepository
import com.xteink.companion.data.FirmwareRelease
import com.xteink.companion.data.FirmwareReleaseRepository
import com.xteink.companion.data.FirmwareSource
import com.xteink.companion.data.LinkPhase
import com.xteink.companion.data.UsbEspFlasher
import com.xteink.companion.data.UsbFlashPhase
import com.xteink.companion.protocol.SessionStart
import kotlinx.coroutines.Job
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
    private val usbFlasher = UsbEspFlasher(application)
    private val firmwareReleases = FirmwareReleaseRepository(application)
    private val bookLibrary = BookLibraryRepository(application)
    private val connectionPreferences =
        application.getSharedPreferences("xtraordinary_connection", Application.MODE_PRIVATE)
    private var latestRelease: FirmwareRelease? = null
    private var backgroundDisconnectJob: Job? = null
    private var reconnectJob: Job? = null
    private var focusSyncJob: Job? = null
    private var deleteSyncJob: Job? = null
    private var appForeground = false
    private var intentionalTransportIdle = true
    private var reconnectAttempt = 0
    private var pendingFocusSync = false
    private val pendingDeletePaths = linkedSetOf<String>()

    init {
        managedDeviceModel()?.let { model ->
            _uiState.update { it.copy(isX3Connected = true, connectedDeviceModel = model) }
        }
        viewModelScope.launch {
            companionClient.state.collect { link ->
                val capabilities = link.capabilities
                if (link.phase == LinkPhase.Connected && link.requestedModel != null) {
                    connectionPreferences.edit().putString(LastConnectedModelKey, link.requestedModel).apply()
                }
                val managedModel = managedDeviceModel()
                val transportConnected = link.phase == LinkPhase.Connected
                val unexpectedDisconnect = link.phase == LinkPhase.Error && !intentionalTransportIdle
                _uiState.update { state ->
                    state.copy(
                        isX3Connected = managedModel != null || transportConnected,
                        isX3TransportConnected = transportConnected,
                        connectedDeviceModel = capabilities?.model ?: managedModel ?: state.connectedDeviceModel,
                        device = state.device.copy(
                            linkPhase = link.phase.name,
                            reconnecting = shouldShowReconnecting(
                                previous = state.device.reconnecting,
                                phase = link.phase,
                                intentionalTransportIdle = intentionalTransportIdle,
                            ),
                            message = link.message,
                            firmwareVersion = capabilities?.firmwareVersion ?: state.device.firmwareVersion,
                            libraryRevision = capabilities?.libraryRevision ?: state.device.libraryRevision,
                            firmwareProgress = link.transferProgress ?: state.device.firmwareProgress,
                        ),
                    )
                }
                if (transportConnected) {
                    reconnectAttempt = 0
                    reconnectJob?.cancel()
                    reconnectJob = null
                    drainPendingFocusSync()
                    drainPendingDeletes()
                } else if (unexpectedDisconnect) {
                    scheduleReconnect()
                }
            }
        }
        viewModelScope.launch {
            companionClient.libraries.collect { snapshot ->
                reconcileDeviceLibrary(snapshot.revision, snapshot.entries.map { it.path to it.sizeBytes })
            }
        }
        viewModelScope.launch {
            usbFlasher.state.collect { usb ->
                _uiState.update { state ->
                    val firmwarePhase = when (usb.phase) {
                        UsbFlashPhase.EnteringBootloader,
                        UsbFlashPhase.Erasing,
                        UsbFlashPhase.Writing,
                        UsbFlashPhase.Verifying,
                        UsbFlashPhase.Restarting -> FirmwareCheckPhase.Transferring
                        UsbFlashPhase.Complete -> FirmwareCheckPhase.Complete
                        UsbFlashPhase.Error -> if (state.device.firmwareCheckPhase == FirmwareCheckPhase.Transferring) {
                            FirmwareCheckPhase.Error
                        } else state.device.firmwareCheckPhase
                        else -> state.device.firmwareCheckPhase
                    }
                    state.copy(
                        device = state.device.copy(
                            usbConnected = usb.deviceDetected,
                            usbPhase = usb.phase.name,
                            usbMessage = usb.message,
                            firmwareCheckPhase = firmwarePhase,
                            firmwareProgress = usb.progress ?: state.device.firmwareProgress,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                usbFlasher.refresh()
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
        requestFocusSync()
    }

    fun togglePause() {
        _uiState.update { state ->
            val nextPhase = when (state.focus.phase) {
                FocusPhase.Running -> FocusPhase.Paused
                FocusPhase.Paused -> FocusPhase.Running
                else -> state.focus.phase
            }
            state.copy(focus = state.focus.copy(phase = nextPhase))
        }
        requestFocusSync()
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
        requestFocusSync()
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
        requestFocusSync()
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
        pendingDeletePaths += paths
        if (_uiState.value.isX3TransportConnected) drainPendingDeletes() else ensureTransportConnected()
    }

    fun hasCompanionPermissions(): Boolean = companionClient.hasPermissions()

    fun connectDevice(model: String) {
        intentionalTransportIdle = false
        reconnectJob?.cancel()
        reconnectJob = null
        companionClient.connect(model)
    }

    fun disconnectDevice() {
        intentionalTransportIdle = true
        reconnectJob?.cancel()
        reconnectJob = null
        pendingFocusSync = false
        pendingDeletePaths.clear()
        connectionPreferences.edit().remove(LastConnectedModelKey).apply()
        companionClient.disconnect()
        _uiState.update {
            it.copy(
                isX3Connected = false,
                isX3TransportConnected = false,
                connectedDeviceModel = null,
                device = it.device.copy(reconnecting = false),
            )
        }
    }

    fun onAppForegrounded() {
        appForeground = true
        intentionalTransportIdle = false
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = null
        ensureTransportConnected()
    }

    fun onAppBackgrounded() {
        appForeground = false
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = viewModelScope.launch {
            delay(BackgroundDisconnectGraceMs)
            val state = _uiState.value
            val firmwareInProgress = state.device.firmwareCheckPhase == FirmwareCheckPhase.Downloading ||
                state.device.firmwareCheckPhase == FirmwareCheckPhase.Transferring
            if (!firmwareInProgress) {
                intentionalTransportIdle = true
                reconnectJob?.cancel()
                reconnectJob = null
                companionClient.disconnect()
                _uiState.update {
                    it.copy(
                        isX3TransportConnected = false,
                        device = it.device.copy(reconnecting = false),
                    )
                }
            }
            backgroundDisconnectJob = null
        }
    }

    fun checkLatestFirmware(model: String) = checkFirmware(model, FirmwareSource.Xtraordinary)

    fun checkFirmware(model: String, source: FirmwareSource) {
        _uiState.update {
            it.copy(device = it.device.copy(
                firmwareCheckPhase = FirmwareCheckPhase.Checking,
                firmwareSource = source,
                latestFirmwareVersion = null,
                message = null,
            ))
        }
        viewModelScope.launch {
            runCatching { firmwareReleases.latestFor(model, source) }
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
                            firmwareSource = release.source,
                            latestFirmwareVersion = release.version,
                        ))
                    }
                }
                .onFailure { reportDeviceError(it) }
        }
    }

    fun flashLatestFirmware() {
        val release = latestRelease ?: return
        val useUsb = _uiState.value.device.usbConnected
        if (release.source != FirmwareSource.Xtraordinary && !useUsb) {
            _uiState.update {
                it.copy(notice = UiNotice.DeviceMessage("Connect the X3 to this phone by USB before flashing"))
            }
            return
        }
        if (!useUsb && !_uiState.value.isX3Connected) {
            _uiState.update { it.copy(notice = UiNotice.DeviceMessage("Connect the X3 to this phone by USB before flashing")) }
            return
        }
        if (!useUsb) ensureTransportConnected()
        viewModelScope.launch {
            _uiState.update {
                it.copy(device = it.device.copy(
                    firmwareCheckPhase = FirmwareCheckPhase.Downloading,
                    firmwareProgress = null,
                    message = null,
                ))
            }
            val flashResult = runCatching {
                val file = firmwareReleases.downloadVerified(release)
                _uiState.update { it.copy(device = it.device.copy(firmwareCheckPhase = FirmwareCheckPhase.Transferring)) }
                if (useUsb) {
                    usbFlasher.flash(file)
                } else {
                    companionClient.awaitConnected()
                    companionClient.flashFirmware(release, file)
                }
            }
            if (flashResult.isSuccess) {
                _uiState.update { it.copy(device = it.device.copy(firmwareCheckPhase = FirmwareCheckPhase.Complete)) }
                if (useUsb) {
                    delay(7_000)
                    runCatching { usbFlasher.readCrashReport() }
                        .onSuccess { report ->
                            getApplication<Application>().filesDir.resolve("x3_crash_report.txt").writeText(report)
                            Log.i("XtraordinaryCrash", report)
                        }
                        .onFailure { Log.e("XtraordinaryCrash", "Could not retrieve X3 crash report", it) }
                }
            } else {
                flashResult.exceptionOrNull()?.let(::reportDeviceError)
            }
        }
    }

    override fun onCleared() {
        usbFlasher.close()
        companionClient.disconnect()
        super.onCleared()
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
            val reconciled = state.read.books.mapNotNull { book ->
                val match = byFileName[book.fileName.lowercase()]
                if (match != null) {
                    matchedPaths += match.first
                    book.copy(isOnX3 = true, x3Path = match.first)
                } else if (!book.isOnPhone && book.metadataSource == "XTEINK") {
                    null
                } else {
                    book.copy(isOnX3 = false, x3Path = null)
                }
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

    private fun managedDeviceModel(): String? =
        connectionPreferences.getString(LastConnectedModelKey, null)

    private fun ensureTransportConnected() {
        if (!appForeground || intentionalTransportIdle || !companionClient.hasPermissions()) return
        val model = managedDeviceModel() ?: return
        val phase = _uiState.value.device.linkPhase
        if (phase == LinkPhase.Connected.name || phase == LinkPhase.Connecting.name || phase == LinkPhase.Scanning.name) {
            return
        }
        companionClient.connect(model)
    }

    private fun scheduleReconnect() {
        val model = managedDeviceModel() ?: return
        if (!appForeground || intentionalTransportIdle || reconnectJob?.isActive == true) return
        _uiState.update { it.copy(device = it.device.copy(reconnecting = true)) }
        val delayMs = ReconnectBackoffMs[reconnectAttempt.coerceAtMost(ReconnectBackoffMs.lastIndex)]
        reconnectAttempt++
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            reconnectJob = null
            if (appForeground && !intentionalTransportIdle && !_uiState.value.isX3TransportConnected) {
                companionClient.connect(model)
            }
        }
    }

    private fun requestFocusSync() {
        if (!_uiState.value.isX3Connected) return
        pendingFocusSync = true
        if (_uiState.value.isX3TransportConnected) drainPendingFocusSync() else ensureTransportConnected()
    }

    private fun drainPendingFocusSync() {
        if (!pendingFocusSync || !_uiState.value.isX3TransportConnected || focusSyncJob?.isActive == true) return
        focusSyncJob = viewModelScope.launch {
            while (pendingFocusSync && _uiState.value.isX3TransportConnected) {
                pendingFocusSync = false
                val focus = _uiState.value.focus
                val result = runCatching {
                    when (focus.phase) {
                        FocusPhase.Running, FocusPhase.Paused -> {
                            val remaining = focus.remainingSeconds.coerceAtLeast(1)
                            companionClient.startSession(
                                SessionStart(
                                    deadlineEpochSeconds = System.currentTimeMillis() / 1_000 + remaining,
                                    durationSeconds = remaining,
                                    title = focus.task,
                                ),
                            )
                            if (focus.phase == FocusPhase.Paused) companionClient.pauseSession()
                        }
                        FocusPhase.Setup, FocusPhase.Review -> companionClient.stopSession()
                    }
                }
                if (result.isFailure) {
                    pendingFocusSync = true
                    handleDeferredTransportFailure(result.exceptionOrNull())
                    break
                }
            }
            focusSyncJob = null
        }
    }

    private fun drainPendingDeletes() {
        if (pendingDeletePaths.isEmpty() || !_uiState.value.isX3TransportConnected || deleteSyncJob?.isActive == true) {
            return
        }
        deleteSyncJob = viewModelScope.launch {
            val paths = pendingDeletePaths.toList()
            val result = runCatching {
                companionClient.deleteLibraryEntries(_uiState.value.device.libraryRevision, paths)
            }
            if (result.isSuccess) {
                pendingDeletePaths.removeAll(paths.toSet())
                _uiState.update { it.copy(notice = UiNotice.X3DeleteQueued(paths.size)) }
            } else {
                handleDeferredTransportFailure(result.exceptionOrNull())
            }
            deleteSyncJob = null
        }
    }

    private fun handleDeferredTransportFailure(error: Throwable?) {
        companionClient.disconnect()
        _uiState.update {
            it.copy(
                isX3TransportConnected = false,
                device = it.device.copy(
                    reconnecting = true,
                    message = error?.message ?: "XTEINK connection interrupted",
                ),
            )
        }
        scheduleReconnect()
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

    private companion object {
        const val BackgroundDisconnectGraceMs = 1_500L
        const val LastConnectedModelKey = "last_connected_model"
        val ReconnectBackoffMs = longArrayOf(1_000L, 3_000L, 8_000L, 15_000L)
    }
}
