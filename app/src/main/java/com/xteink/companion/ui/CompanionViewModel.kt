package com.xteink.companion.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xteink.companion.data.BluetoothCompanionClient
import com.xteink.companion.data.BookLibraryRepository
import com.xteink.companion.data.FirmwareRelease
import com.xteink.companion.data.FirmwareReleaseRepository
import com.xteink.companion.data.FirmwareSource
import com.xteink.companion.data.ImportedFlightPass
import com.xteink.companion.data.LinkPhase
import com.xteink.companion.data.ReadingStatsRepository
import com.xteink.companion.data.ReadingSessionStat
import com.xteink.companion.data.UsbEspFlasher
import com.xteink.companion.data.UsbBookTransfer
import com.xteink.companion.data.UsbFlashPhase
import com.xteink.companion.protocol.SessionStart
import com.xteink.companion.protocol.BoardingPassPayload
import com.xteink.companion.protocol.DeviceActivity
import com.xteink.companion.protocol.PayloadCodec
import com.xteink.companion.protocol.RadioPolicy
import com.xteink.companion.protocol.TicketDisplayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class CompanionViewModel(application: Application) : AndroidViewModel(application) {
    private val readingStatsRepository = ReadingStatsRepository(application)
    private val radioPreferences =
        application.getSharedPreferences("xtraordinary_radio_policy", Application.MODE_PRIVATE)
    private val ticketPreferences =
        application.getSharedPreferences("xtraordinary_ticket_state", Application.MODE_PRIVATE)
    private val initialTicketMode = runCatching {
        TicketMode.valueOf(
            ticketPreferences.getString("mode", TicketMode.Static.name) ?: TicketMode.Static.name,
        )
    }.getOrDefault(TicketMode.Static)
    private val initialTicketOnX3 = ticketPreferences.getBoolean("is_on_x3", false)
    private val initialTicketRemovalPending = ticketPreferences.getBoolean("removal_pending", false)
    private val initialPendingTicketPayload = ticketPreferences.getString("pending_show_payload", null)?.let { encoded ->
        runCatching { PayloadCodec.decodeBoardingPass(Base64.decode(encoded, Base64.DEFAULT)) }.getOrNull()
    }
    private val initialRadioPolicy = RadioPolicyUiState(
        fastWindowMinutes = radioPreferences.getInt("fast_window_minutes", 5),
        slowIntervalMs = radioPreferences.getInt("slow_interval_ms", 2_000),
        sleepAfterMinutes = radioPreferences.getInt("sleep_after_minutes", 10),
        fullRefreshPages = radioPreferences.getInt("full_refresh_pages", 15),
    )
    private val initialRadioPolicySyncPending = radioPreferences.getBoolean("sync_pending", true)
    private val initialBatteryPercentage = radioPreferences.getInt("last_battery_percentage", -1)
        .takeIf { it in 0..100 }
    private val _uiState = MutableStateFlow(
        CompanionUiState(
            radioPolicy = initialRadioPolicy,
            device = DeviceUiState(
                batteryPercentage = initialBatteryPercentage,
                settingsSyncPending = initialRadioPolicySyncPending,
            ),
            ticket = TicketUiState(
                mode = initialTicketMode,
                isOnX3 = initialTicketOnX3,
                sendPending = initialPendingTicketPayload != null,
                removalPending = initialTicketRemovalPending,
            ),
            readingStats = ReadingStatsUiState(
                sessions = readingStatsRepository.load(),
                minimumPageSeconds = readingStatsRepository.minimumPageSeconds(),
            ),
        ),
    )
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()
    private val companionClient = BluetoothCompanionClient(application)
    private val usbFlasher = UsbEspFlasher(application)
    private val usbBookTransfer = UsbBookTransfer(application)
    private val firmwareReleases = FirmwareReleaseRepository(application)
    private val bookLibrary = BookLibraryRepository(application)
    private val connectionPreferences =
        application.getSharedPreferences("xtraordinary_connection", Application.MODE_PRIVATE)
    private var latestRelease: FirmwareRelease? = null
    private var backgroundDisconnectJob: Job? = null
    private var reconnectJob: Job? = null
    private var focusSyncJob: Job? = null
    private var deleteSyncJob: Job? = null
    private var ticketSendJob: Job? = null
    private var ticketDeleteJob: Job? = null
    private var radioPolicySyncJob: Job? = null
    private var readingQuietJob: Job? = null
    private var bookUploadJob: Job? = null
    private var lastDeletedReadingSession: ReadingSessionStat? = null
    private var radioPolicyRevision = 0L
    private var radioPolicySyncPending = initialRadioPolicySyncPending
    private var radioPolicyValidatedForConnection = false
    private var appForeground = false
    private var intentionalTransportIdle = true
    private var reconnectAttempt = 0
    private var pendingFocusSync = false
    private var liveTicketActive = initialTicketOnX3 && initialTicketMode == TicketMode.Live
    private var pendingTicketPayload: BoardingPassPayload? = initialPendingTicketPayload
    private val pendingDeletePaths = connectionPreferences
        .getStringSet(PendingDeletePathsKey, emptySet())
        .orEmpty()
        .toCollection(linkedSetOf())
    private val pendingBookUploadIds = connectionPreferences
        .getStringSet(PendingBookUploadIdsKey, emptySet())
        .orEmpty()
        .toCollection(linkedSetOf())
    private var pendingBookUploadMethod = runCatching {
        BookTransferMethod.valueOf(
            connectionPreferences.getString(PendingBookUploadMethodKey, null)
                ?: BookTransferMethod.Bluetooth.name,
        )
    }.getOrDefault(BookTransferMethod.Bluetooth)

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
                val connectionBlocked = link.requiresBluetoothReset
                val unexpectedDisconnect = link.phase == LinkPhase.Error && !intentionalTransportIdle && !connectionBlocked
                val reconnectRequired = unexpectedDisconnect && requiresPersistentTransport()
                if (connectionBlocked) {
                    reconnectJob?.cancel()
                    reconnectJob = null
                }
                if (unexpectedDisconnect && !reconnectRequired) {
                    // Foreground discovery is a one-shot status probe. An idle,
                    // sleeping, Reading, or Static-ticket X3 is still a managed
                    // device; failure to open GATT must settle back to Available
                    // instead of starting an endless reconnect loop.
                    intentionalTransportIdle = true
                }
                if (capabilities != null) {
                    val removalPending = capabilities.ticketPresent && _uiState.value.ticket.removalPending
                    persistTicketState(capabilities.ticketPresent, _uiState.value.ticket.mode, removalPending)
                    if (!capabilities.ticketPresent) liveTicketActive = false
                }
                if (transportConnected && capabilities != null && !radioPolicyValidatedForConnection) {
                    // The phone policy is authoritative. Re-apply it once per
                    // GATT session as well as after explicit edits, so a local
                    // device-side change cannot silently leave the two copies
                    // divergent.
                    radioPolicyValidatedForConnection = true
                    radioPolicySyncPending = true
                    radioPreferences.edit().putBoolean("sync_pending", true).apply()
                } else if (!transportConnected) {
                    radioPolicyValidatedForConnection = false
                }
                link.deviceStatus?.batteryPercentage?.let { percentage ->
                    radioPreferences.edit().putInt("last_battery_percentage", percentage).apply()
                }
                _uiState.update { state ->
                    state.copy(
                        isX3Connected = managedModel != null || transportConnected,
                        isX3TransportConnected = transportConnected,
                        connectedDeviceModel = capabilities?.model ?: managedModel ?: state.connectedDeviceModel,
                        device = state.device.copy(
                            linkPhase = link.phase.name,
                            reconnecting = if (connectionBlocked) false else shouldShowReconnecting(
                                previous = state.device.reconnecting,
                                phase = link.phase,
                                intentionalTransportIdle = intentionalTransportIdle,
                            ),
                            requiresBluetoothReset = connectionBlocked,
                            message = if (reconnectRequired) null else link.message,
                            firmwareVersion = capabilities?.firmwareVersion ?: state.device.firmwareVersion,
                            libraryRevision = capabilities?.libraryRevision ?: state.device.libraryRevision,
                            firmwareProgress = link.transferProgress ?: state.device.firmwareProgress,
                            batteryPercentage = link.deviceStatus?.batteryPercentage
                                ?: state.device.batteryPercentage,
                            charging = transportConnected && link.deviceStatus?.charging == true,
                            settingsSyncPending = radioPolicySyncPending,
                        ),
                        ticket = if (capabilities == null) state.ticket else state.ticket.copy(
                            isOnX3 = capabilities.ticketPresent,
                            removalPending = capabilities.ticketPresent && state.ticket.removalPending,
                        ),
                    )
                }
                if (transportConnected && capabilities != null) {
                    reconnectAttempt = 0
                    reconnectJob?.cancel()
                    reconnectJob = null
                    syncRadioPolicy()
                    drainPendingFocusSync()
                    drainPendingDeletes()
                    drainPendingTicketSend()
                    drainPendingTicketRemoval()
                    resumePendingBookUploadIfPossible()
                    if (link.deviceStatus?.activity == DeviceActivity.Reading) scheduleReadingRadioQuiet()
                } else if (reconnectRequired && !connectionBlocked) {
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
            companionClient.readingStats.collect { chunk ->
                _uiState.update { it.copy(readingStats = it.readingStats.copy(syncing = true)) }
                val completed = withContext(Dispatchers.IO) { readingStatsRepository.accept(chunk) }
                if (completed != null) {
                    _uiState.update {
                        it.copy(
                            readingStats = it.readingStats.copy(
                                sessions = readingStatsRepository.load(),
                                syncing = false,
                            ),
                        )
                    }
                    runCatching { companionClient.acknowledgeReadingStats(completed.id) }
                        .onFailure { error -> Log.w("ReadingStats", "Session persisted but ACK failed", error) }
                }
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
                // A persistent Focus or Live session must recover even if an
                // Android GATT callback races the one-shot reconnect job. This
                // watchdog only acts while the app is foregrounded and no scan
                // or connection attempt is already active.
                val linkPhase = companionClient.state.value.phase
                if (canMaintainTransport() && !intentionalTransportIdle && requiresPersistentTransport() &&
                    !_uiState.value.device.requiresBluetoothReset &&
                    !_uiState.value.isX3TransportConnected && reconnectJob?.isActive != true &&
                    linkPhase != LinkPhase.Scanning && linkPhase != LinkPhase.Connecting
                ) {
                    scheduleReconnect()
                }
            }
        }
    }

    fun setVisualTheme(theme: CompanionVisualTheme) {
        _uiState.update { it.copy(visualTheme = theme) }
    }

    fun setRadioPolicy(policy: RadioPolicyUiState) {
        val normalized = policy.copy(
            fastWindowMinutes = policy.fastWindowMinutes.coerceIn(1, 30),
            slowIntervalMs = policy.slowIntervalMs.coerceIn(500, 4_000),
            sleepAfterMinutes = policy.sleepAfterMinutes.coerceIn(
                policy.fastWindowMinutes.coerceIn(1, 30) + 1,
                60,
            ),
            fullRefreshPages = policy.fullRefreshPages.takeIf { it in setOf(1, 5, 10, 15, 30) } ?: 15,
        )
        radioPreferences.edit()
            .putInt("fast_window_minutes", normalized.fastWindowMinutes)
            .putInt("slow_interval_ms", normalized.slowIntervalMs)
            .putInt("sleep_after_minutes", normalized.sleepAfterMinutes)
            .putInt("full_refresh_pages", normalized.fullRefreshPages)
            .putBoolean("sync_pending", true)
            .apply()
        radioPolicyRevision++
        radioPolicySyncPending = true
        _uiState.update {
            it.copy(
                radioPolicy = normalized,
                device = it.device.copy(settingsSyncPending = true, message = null),
            )
        }
        if (managedDeviceModel() != null) {
            intentionalTransportIdle = false
            if (_uiState.value.isX3TransportConnected) syncRadioPolicy() else ensureTransportConnected()
        }
    }

    private fun syncRadioPolicy() {
        if (!radioPolicySyncPending || !_uiState.value.isX3TransportConnected || radioPolicySyncJob?.isActive == true) {
            return
        }
        radioPolicySyncJob = viewModelScope.launch {
            while (radioPolicySyncPending && _uiState.value.isX3TransportConnected) {
                val revision = radioPolicyRevision
                val policy = _uiState.value.radioPolicy
                val result = runCatching {
                    companionClient.setRadioPolicy(
                        RadioPolicy(
                            fastWindowMinutes = policy.fastWindowMinutes,
                            slowIntervalMs = policy.slowIntervalMs,
                            sleepAfterMinutes = policy.sleepAfterMinutes,
                        ),
                    )
                    if (companionClient.state.value.capabilities?.supportsReaderPolicy == true) {
                        companionClient.setReaderPolicy(policy.fullRefreshPages)
                    }
                }
                if (result.isFailure) {
                    Log.w("CompanionViewModel", "Could not sync device policy", result.exceptionOrNull())
                    handleDeferredTransportFailure(result.exceptionOrNull())
                    break
                }
                if (revision == radioPolicyRevision) {
                    radioPolicySyncPending = false
                    radioPreferences.edit().putBoolean("sync_pending", false).apply()
                    _uiState.update {
                        it.copy(device = it.device.copy(settingsSyncPending = false, message = null))
                    }
                }
            }
            radioPolicySyncJob = null
            releaseBackgroundTransportIfIdle()
        }
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

    fun openStats() {
        _uiState.update { it.copy(surface = CompanionSurface.Tools, toolDestination = ToolDestination.Stats) }
    }

    fun setReadingStatsView(view: ReadingStatsView) {
        _uiState.update { it.copy(readingStats = it.readingStats.copy(view = view, selectedSessionId = null)) }
    }

    fun setMinimumReadingPageSeconds(seconds: Int) {
        readingStatsRepository.setMinimumPageSeconds(seconds)
        _uiState.update {
            it.copy(readingStats = it.readingStats.copy(minimumPageSeconds = readingStatsRepository.minimumPageSeconds()))
        }
    }

    fun reloadReadingStats() {
        _uiState.update {
            it.copy(
                readingStats = it.readingStats.copy(
                    sessions = readingStatsRepository.load(),
                    minimumPageSeconds = readingStatsRepository.minimumPageSeconds(),
                ),
            )
        }
    }

    fun selectReadingSession(sessionId: UInt?) {
        _uiState.update { it.copy(readingStats = it.readingStats.copy(selectedSessionId = sessionId)) }
    }

    fun deleteReadingSession(sessionId: UInt) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { readingStatsRepository.deleteSession(sessionId) }
                .onSuccess { deleted ->
                    if (deleted == null) return@onSuccess
                    lastDeletedReadingSession = deleted
                    _uiState.update {
                        it.copy(
                            readingStats = it.readingStats.copy(
                                sessions = readingStatsRepository.load(),
                                selectedSessionId = null,
                            ),
                            notice = UiNotice.SessionDeleted(deleted.title),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(notice = UiNotice.DeviceMessage(error.message ?: "Could not delete the session"))
                    }
                }
        }
    }

    fun undoReadingSessionDeletion() {
        val session = lastDeletedReadingSession ?: return
        lastDeletedReadingSession = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { readingStatsRepository.restoreSession(session) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            readingStats = it.readingStats.copy(sessions = readingStatsRepository.load()),
                            notice = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(notice = UiNotice.DeviceMessage(error.message ?: "Could not restore the session"))
                    }
                }
        }
    }

    fun selectPass(passId: String) {
        _uiState.update { state ->
            if (state.ticket.passes.none { it.id == passId }) state
            else state.copy(ticket = state.ticket.copy(selectedPassId = passId))
        }
    }

    fun importFlightPass(pass: ImportedFlightPass) {
        val imported = BoardingPassUiState(
            id = pass.id,
            origin = pass.origin,
            destination = pass.destination,
            flight = pass.flight,
            status = pass.status,
            departureTime = pass.departureTime,
            countdown = "",
            gate = pass.gate,
            terminal = pass.terminal,
            seat = pass.seat,
            passenger = pass.passenger,
            boardingGroup = pass.boardingGroup,
            source = pass.source,
            barcodePayload = pass.barcodePayload,
        )
        _uiState.update { state ->
            val passes = listOf(imported) + state.ticket.passes.filterNot { it.id == imported.id }
            state.copy(
                surface = CompanionSurface.Tools,
                toolDestination = ToolDestination.Passes,
                ticket = state.ticket.copy(passes = passes, selectedPassId = imported.id),
            )
        }
    }

    fun reportFlightPassImportFailure(error: Throwable) {
        _uiState.update {
            it.copy(notice = UiNotice.DeviceMessage(error.message ?: "The flight pass could not be imported"))
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

    fun setReadLocation(location: ReadLocation) {
        _uiState.update { it.copy(read = it.read.copy(location = location)) }
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
        persistPendingDeletePaths()
        intentionalTransportIdle = false
        if (_uiState.value.isX3TransportConnected) drainPendingDeletes() else ensureTransportConnected()
    }

    fun hasCompanionPermissions(): Boolean = companionClient.hasPermissions()

    fun connectDevice(model: String) {
        intentionalTransportIdle = false
        reconnectJob?.cancel()
        reconnectJob = null
        companionClient.resetConnectionRecovery()
        _uiState.update {
            it.copy(device = it.device.copy(requiresBluetoothReset = false, reconnecting = false, message = null))
        }
        companionClient.connect(model)
    }

    fun disconnectDevice() {
        intentionalTransportIdle = true
        reconnectJob?.cancel()
        reconnectJob = null
        pendingFocusSync = false
        pendingDeletePaths.clear()
        persistPendingDeletePaths()
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

    suspend fun prepareForExternalDeviceReset() {
        intentionalTransportIdle = true
        reconnectJob?.cancel()
        reconnectJob = null
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = null
        companionClient.prepareForPeripheralReset()
        _uiState.update {
            it.copy(
                isX3TransportConnected = false,
                device = it.device.copy(reconnecting = false, message = "X3 is ready to restart"),
            )
        }
        resumePendingBookUploadIfPossible()
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
            if (!firmwareInProgress && !liveTicketActive && !requiresPersistentTransport()) {
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
                    prepareForExternalDeviceReset()
                    usbFlasher.flash(file)
                } else {
                    companionClient.awaitConnected()
                    intentionalTransportIdle = true
                    companionClient.flashFirmware(release, file)
                }
            }
            if (flashResult.isSuccess) {
                _uiState.update { it.copy(device = it.device.copy(firmwareCheckPhase = FirmwareCheckPhase.Complete)) }
                // USB flashing returns after hard reset; BLE flashing returns
                // before the delayed on-device copy and reboot. Keep Android
                // from opening a new GATT client inside either reset window.
                delay(if (useUsb) 7_000 else 12_000)
                intentionalTransportIdle = false
                ensureTransportConnected()
            } else {
                intentionalTransportIdle = false
                flashResult.exceptionOrNull()?.let(::reportDeviceError)
            }
        }
    }

    override fun onCleared() {
        usbBookTransfer.close()
        usbFlasher.close()
        companionClient.close()
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
            it.copy(surface = CompanionSurface.Read)
        }
    }

    fun requestUploadBooksToX3(bookIds: Set<String>, method: BookTransferMethod) {
        if (bookIds.isEmpty() || _uiState.value.read.uploadingToX3) return
        val books = _uiState.value.read.books.filter { it.id in bookIds && it.isOnPhone && !it.isOnX3 }
        if (books.isEmpty()) return
        when (method) {
            BookTransferMethod.Bluetooth -> if (!_uiState.value.isX3Connected) {
                _uiState.update { it.copy(notice = UiNotice.DeviceMessage("Pair an X3 before uploading books")) }
                return
            }
            BookTransferMethod.Usb -> if (!usbBookTransfer.isDeviceDetected()) {
                _uiState.update { it.copy(notice = UiNotice.DeviceMessage("Connect the X3 with a USB data cable")) }
                return
            }
        }
        pendingBookUploadIds.clear()
        pendingBookUploadIds.addAll(books.map { it.id })
        pendingBookUploadMethod = method
        persistPendingBookUpload()
        resumePendingBookUploadIfPossible()
    }

    fun cancelBookUpload() {
        pendingBookUploadIds.clear()
        persistPendingBookUpload()
        bookUploadJob?.cancel(CancellationException("Upload stopped"))
        _uiState.update {
            it.copy(
                read = it.read.copy(uploadingToX3 = false, uploadProgress = null, uploadMethod = null),
                notice = UiNotice.DeviceMessage("Upload stopped"),
            )
        }
    }

    private fun resumePendingBookUploadIfPossible() {
        if (pendingBookUploadIds.isEmpty() || bookUploadJob?.isActive == true) return
        if (_uiState.value.read.books.isEmpty()) return
        val books = _uiState.value.read.books.filter {
            it.id in pendingBookUploadIds && it.isOnPhone && !it.isOnX3
        }
        val completedOrUnavailable = pendingBookUploadIds - books.mapTo(hashSetOf()) { it.id }
        if (completedOrUnavailable.isNotEmpty()) {
            pendingBookUploadIds.removeAll(completedOrUnavailable)
            persistPendingBookUpload()
        }
        if (books.isEmpty()) return
        when (pendingBookUploadMethod) {
            BookTransferMethod.Bluetooth -> {
                if (managedDeviceModel() == null) return
                intentionalTransportIdle = false
                ensureTransportConnected()
            }
            BookTransferMethod.Usb -> if (!usbBookTransfer.isDeviceDetected()) return
        }
        bookUploadJob = viewModelScope.launch(Dispatchers.IO) {
            val method = pendingBookUploadMethod
            _uiState.update {
                it.copy(
                    read = it.read.copy(
                        uploadingToX3 = true,
                        uploadProgress = 0f,
                        uploadMethod = method,
                    ),
                )
            }
            val result = runCatching {
                if (method == BookTransferMethod.Bluetooth) companionClient.awaitConnected()
                books.forEachIndexed { index, book ->
                    val uri = Uri.parse(book.sourceUri)
                    val resolver = getApplication<Application>().contentResolver
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    resolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            digest.update(buffer, 0, count)
                            size += count
                        }
                    } ?: error("${book.title} is no longer available on this phone")
                    check(size > 0) { "${book.title} is empty" }
                    resolver.openInputStream(uri)?.use { input ->
                        val progress: (Float) -> Unit = { bookProgress ->
                            val overall = (index + bookProgress) / books.size.toFloat()
                            _uiState.update { state ->
                                state.copy(read = state.read.copy(uploadProgress = overall.coerceIn(0f, 1f)))
                            }
                        }
                        when (method) {
                            BookTransferMethod.Bluetooth -> companionClient.uploadBook(
                                book.fileName,
                                size,
                                digest.digest(),
                                input,
                                progress,
                            )
                            BookTransferMethod.Usb -> usbBookTransfer.uploadBook(
                                book.fileName,
                                size,
                                digest.digest(),
                                input,
                                progress,
                            )
                        }
                    } ?: error("${book.title} is no longer available on this phone")
                    val uploadedBooks = _uiState.value.read.books.map { existing ->
                        if (existing.id == book.id) {
                            existing.copy(isOnX3 = true, x3Path = "/Books/${book.fileName}")
                        } else {
                            existing
                        }
                    }
                    bookLibrary.save(uploadedBooks)
                    _uiState.update { state -> state.copy(read = state.read.copy(books = uploadedBooks)) }
                    pendingBookUploadIds.remove(book.id)
                    persistPendingBookUpload()
                }
                if (method == BookTransferMethod.Usb && companionClient.isReady()) {
                    runCatching { companionClient.refreshLibrary() }
                }
            }
            val error = result.exceptionOrNull()
            _uiState.update { state ->
                state.copy(
                    read = state.read.copy(uploadingToX3 = false, uploadProgress = null, uploadMethod = null),
                    notice = when {
                        result.isSuccess -> UiNotice.DeviceMessage(
                            "${books.size} book${if (books.size == 1) "" else "s"} uploaded to X3",
                        )
                        error is CancellationException -> state.notice ?: UiNotice.DeviceMessage("Upload stopped")
                        else -> UiNotice.DeviceMessage(
                            "${error?.message ?: "Book upload failed"}. Reopen the app to resume.",
                        )
                    },
                )
            }
            bookUploadJob = null
            releaseBackgroundTransportIfIdle()
        }
    }

    private fun persistPendingBookUpload() {
        val editor = connectionPreferences.edit()
        if (pendingBookUploadIds.isEmpty()) {
            editor.remove(PendingBookUploadIdsKey).remove(PendingBookUploadMethodKey)
        } else {
            editor.putStringSet(PendingBookUploadIdsKey, pendingBookUploadIds.toSet())
                .putString(PendingBookUploadMethodKey, pendingBookUploadMethod.name)
        }
        check(editor.commit()) { "Could not persist the pending book upload" }
    }

    fun reportImportResult(added: Int, duplicates: Int, failed: Int) {
        _uiState.update {
            it.copy(
                surface = CompanionSurface.Read,
                notice = if (failed > 0) UiNotice.BooksImported(added, duplicates, failed) else null,
            )
        }
    }

    fun reportEpubImportFailure() {
        _uiState.update { it.copy(notice = UiNotice.EpubImportFailed) }
    }

    fun sendTicket() {
        if (!_uiState.value.isX3Connected) {
            _uiState.update { it.copy(notice = UiNotice.PairBeforeSend) }
            return
        }
        if (pendingTicketPayload != null || ticketSendJob?.isActive == true) return
        val ticket = _uiState.value.ticket
        val pass = ticket.selectedPass
        val displayMode = if (ticket.mode == TicketMode.Static) TicketDisplayMode.Static else TicketDisplayMode.Live
        pendingTicketPayload = BoardingPassPayload(
            mode = displayMode,
            origin = pass.origin,
            destination = pass.destination,
            flight = pass.flight,
            status = pass.status,
            departureTime = pass.departureTime,
            gate = pass.gate,
            terminal = pass.terminal,
            seat = pass.seat,
            passenger = pass.passenger,
            boardingGroup = pass.boardingGroup,
            barcodePayload = pass.barcodePayload,
        )
        persistPendingTicketPayload()
        _uiState.update { it.copy(ticket = it.ticket.copy(sendPending = true)) }
        intentionalTransportIdle = false
        if (companionClient.isReady()) drainPendingTicketSend() else ensureTransportConnected()
    }

    private fun drainPendingTicketSend() {
        val payload = pendingTicketPayload ?: return
        if (!companionClient.isReady() || ticketSendJob?.isActive == true) return
        ticketSendJob = viewModelScope.launch {
            val result = runCatching {
                companionClient.showTicket(payload)
            }
            if (result.isSuccess) {
                pendingTicketPayload = null
                persistPendingTicketPayload()
                val mode = if (payload.mode == TicketDisplayMode.Static) TicketMode.Static else TicketMode.Live
                liveTicketActive = payload.mode == TicketDisplayMode.Live
                persistTicketState(true, mode, false)
                _uiState.update {
                    it.copy(ticket = it.ticket.copy(mode = mode, isOnX3 = true, sendPending = false, removalPending = false))
                }
                if (!liveTicketActive) {
                    // Keep the first bonded GATT session alive long enough for
                    // Android's own post-bond service discovery to finish. If we
                    // tear it down immediately, Android can retain a system GATT
                    // attempt that blocks later app connections until Bluetooth
                    // is restarted.
                    delay(5_000)
                    intentionalTransportIdle = true
                    companionClient.disconnect()
                    _uiState.update {
                        it.copy(
                            isX3TransportConnected = false,
                            device = it.device.copy(reconnecting = false, charging = false),
                        )
                    }
                }
            } else {
                handleDeferredTransportFailure(result.exceptionOrNull())
            }
            ticketSendJob = null
        }
    }

    fun removeTicketFromX3() {
        if (!_uiState.value.isX3Connected) {
            _uiState.update { it.copy(notice = UiNotice.PairBeforeSend) }
            return
        }
        if (_uiState.value.ticket.removalPending || ticketDeleteJob?.isActive == true) return
        val ticket = _uiState.value.ticket
        persistTicketState(true, ticket.mode, true)
        _uiState.update { it.copy(ticket = it.ticket.copy(removalPending = true)) }
        intentionalTransportIdle = false
        if (companionClient.isReady()) drainPendingTicketRemoval() else ensureTransportConnected()
    }

    private fun drainPendingTicketRemoval() {
        if (!_uiState.value.ticket.removalPending || !companionClient.isReady() ||
            ticketDeleteJob?.isActive == true
        ) {
            return
        }
        ticketDeleteJob = viewModelScope.launch {
            val result = runCatching {
                companionClient.clearTicket()
            }
            if (result.isSuccess) {
                liveTicketActive = false
                persistTicketState(false, _uiState.value.ticket.mode, false)
                intentionalTransportIdle = true
                companionClient.disconnect()
                _uiState.update {
                    it.copy(
                        ticket = it.ticket.copy(isOnX3 = false, removalPending = false),
                        isX3TransportConnected = false,
                        device = it.device.copy(reconnecting = false),
                    )
                }
            } else {
                handleDeferredTransportFailure(result.exceptionOrNull())
            }
            ticketDeleteJob = null
        }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(settingsVisible = show) }
    }

    private fun persistTicketState(present: Boolean, mode: TicketMode, removalPending: Boolean) {
        ticketPreferences.edit()
            .putBoolean("is_on_x3", present)
            .putString("mode", mode.name)
            .putBoolean("removal_pending", removalPending)
            .apply()
    }

    private fun persistPendingTicketPayload() {
        val encoded = pendingTicketPayload?.let {
            Base64.encodeToString(PayloadCodec.encodeBoardingPass(it), Base64.NO_WRAP)
        }
        ticketPreferences.edit().apply {
            if (encoded == null) remove("pending_show_payload") else putString("pending_show_payload", encoded)
        }.apply()
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
        if (!canMaintainTransport() || intentionalTransportIdle || !companionClient.hasPermissions() ||
            _uiState.value.device.requiresBluetoothReset
        ) return
        val model = managedDeviceModel() ?: return
        val phase = _uiState.value.device.linkPhase
        if (phase == LinkPhase.Connected.name || phase == LinkPhase.Connecting.name || phase == LinkPhase.Scanning.name) {
            return
        }
        companionClient.connect(model)
    }

    private fun requiresPersistentTransport(): Boolean {
        val state = _uiState.value
        val focusNeedsLink = state.focus.phase == FocusPhase.Running || state.focus.phase == FocusPhase.Paused
        val firmwareNeedsLink = state.device.firmwareCheckPhase == FirmwareCheckPhase.Downloading ||
            state.device.firmwareCheckPhase == FirmwareCheckPhase.Transferring
        val bluetoothBookUpload = pendingBookUploadMethod == BookTransferMethod.Bluetooth &&
            (pendingBookUploadIds.isNotEmpty() || bookUploadJob?.isActive == true)
        return radioPolicySyncPending || pendingTicketPayload != null || liveTicketActive || ticketSendJob?.isActive == true || focusNeedsLink || firmwareNeedsLink || pendingFocusSync || bluetoothBookUpload ||
            pendingDeletePaths.isNotEmpty() || state.ticket.removalPending
    }

    private fun canMaintainTransport(): Boolean = appForeground || requiresPersistentTransport()

    private fun scheduleReadingRadioQuiet() {
        if (readingQuietJob?.isActive == true) return
        readingQuietJob = viewModelScope.launch {
            // ACK_STATUS is queued by the transport before this state reaches
            // the ViewModel. Let it and any desired-state commands complete,
            // then release GATT so firmware can keep Reading fully radio-quiet.
            delay(300)
            while (_uiState.value.isX3TransportConnected &&
                companionClient.state.value.deviceStatus?.activity == DeviceActivity.Reading &&
                requiresPersistentTransport()
            ) {
                delay(100)
            }
            if (_uiState.value.isX3TransportConnected &&
                companionClient.state.value.deviceStatus?.activity == DeviceActivity.Reading
            ) {
                intentionalTransportIdle = true
                companionClient.disconnect()
                _uiState.update {
                    it.copy(
                        isX3TransportConnected = false,
                        device = it.device.copy(reconnecting = false, charging = false, message = null),
                    )
                }
            }
            readingQuietJob = null
        }
    }

    private fun scheduleReconnect() {
        val model = managedDeviceModel() ?: return
        if (!canMaintainTransport() || intentionalTransportIdle || reconnectJob?.isActive == true ||
            _uiState.value.device.requiresBluetoothReset
        ) return
        _uiState.update { it.copy(device = it.device.copy(reconnecting = true)) }
        val delayMs = ReconnectBackoffMs[reconnectAttempt.coerceAtMost(ReconnectBackoffMs.lastIndex)]
        reconnectAttempt++
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            reconnectJob = null
            if (canMaintainTransport() && !intentionalTransportIdle && !_uiState.value.isX3TransportConnected &&
                !_uiState.value.device.requiresBluetoothReset
            ) {
                companionClient.connect(model)
            }
        }
    }

    private fun releaseBackgroundTransportIfIdle() {
        if (appForeground || requiresPersistentTransport()) return
        intentionalTransportIdle = true
        reconnectJob?.cancel()
        reconnectJob = null
        companionClient.disconnect()
        _uiState.update {
            it.copy(
                isX3TransportConnected = false,
                device = it.device.copy(reconnecting = false, charging = false),
            )
        }
    }

    private fun requestFocusSync() {
        if (!_uiState.value.isX3Connected) return
        pendingFocusSync = true
        intentionalTransportIdle = false
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
                persistPendingDeletePaths()
                releaseBackgroundTransportIfIdle()
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
                    message = null,
                ),
            )
        }
        scheduleReconnect()
    }

    private fun persistPendingDeletePaths() {
        connectionPreferences.edit().putStringSet(PendingDeletePathsKey, pendingDeletePaths.toSet()).apply()
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
        const val PendingDeletePathsKey = "pending_delete_paths"
        const val PendingBookUploadIdsKey = "pending_book_upload_ids"
        const val PendingBookUploadMethodKey = "pending_book_upload_method"
        val ReconnectBackoffMs = longArrayOf(1_000L, 3_000L, 8_000L, 15_000L)
    }
}
