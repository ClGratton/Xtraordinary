package com.xteink.companion.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xteink.companion.BuildConfig
import com.xteink.companion.data.BluetoothCompanionClient
import com.xteink.companion.data.BarcodeRasterizer
import com.xteink.companion.data.BookLibraryRepository
import com.xteink.companion.data.BookTransferForegroundService
import com.xteink.companion.data.BookTransferRuntimeControl
import com.xteink.companion.data.BookUploadPersistence
import com.xteink.companion.data.FirmwareRelease
import com.xteink.companion.data.FirmwareReleaseRepository
import com.xteink.companion.data.FirmwareSource
import com.xteink.companion.data.FlightBarcodeFormat
import com.xteink.companion.data.FlightIdentity
import com.xteink.companion.data.FlightStatusRefreshPolicy
import com.xteink.companion.data.FlightStatusSnapshot
import com.xteink.companion.data.ProxyFlightStatusProvider
import com.xteink.companion.data.ImportedFlightPass
import com.xteink.companion.data.InteractiveTransportCoordinator
import com.xteink.companion.data.InteractiveTransportContract
import com.xteink.companion.data.InteractiveTransportOwner
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class CompanionViewModel(application: Application) : AndroidViewModel(application) {
    private val readingStatsRepository = ReadingStatsRepository(application)
    private val focusSessionStore = FocusSessionStore(application)
    private val initialFocus = focusSessionStore.load()
    private val presentationStore = CompanionPresentationStore(application)
    private val initialVisualTheme = presentationStore.loadVisualTheme()
    private val initialColorMode = presentationStore.loadColorMode()
    private val radioPreferences =
        application.getSharedPreferences("xtraordinary_radio_policy", Application.MODE_PRIVATE)
    private val ticketPreferences =
        application.getSharedPreferences("xtraordinary_ticket_state", Application.MODE_PRIVATE)
    private val initialTicketOnX3 = ticketPreferences.getBoolean("is_on_x3", false)
    private val initialTicketModes = decodeTicketModes(
        legacyMode = ticketPreferences.getString("mode", null),
        selectedMode = ticketPreferences.getString("selected_mode", null),
        deployedMode = ticketPreferences.getString("deployed_mode", null),
        deployedPresent = initialTicketOnX3,
    )
    private val initialTicketRemovalPending = ticketPreferences.getBoolean("removal_pending", false)
    private val initialDeployedPassId = ticketPreferences.getString("deployed_pass_id", null)
    private val initialImportedPasses = decodeStoredPasses(ticketPreferences.getString("passes_json", null))
    private val initialPendingTicketPayload = ticketPreferences.getString("pending_show_payload", null)?.let { encoded ->
        runCatching { PayloadCodec.decodeBoardingPass(Base64.decode(encoded, Base64.DEFAULT)) }.getOrNull()
    }
    private val initialPendingTicketPassId = ticketPreferences.getString("pending_show_pass_id", null)
    private val initialRadioPolicy = RadioPolicyUiState(
        fastWindowMinutes = radioPreferences.getInt("fast_window_minutes", 5),
        standbyIntervalSeconds = radioPreferences.getInt("standby_interval_seconds", 30),
        connectedIntervalMs = radioPreferences.getInt(
            "connected_interval_ms",
            radioPreferences.getInt("slow_interval_ms", 2_000),
        ),
        sleepAfterMinutes = radioPreferences.getInt("sleep_after_minutes", 10),
        fullRefreshPages = radioPreferences.getInt("full_refresh_pages", 15),
        powerButtonHoldMs = radioPreferences.getInt("power_button_hold_ms", 1_000),
    )
    private val initialRadioPolicySyncPending = radioPreferences.getBoolean("sync_pending", true)
    private val initialBatteryPercentage = radioPreferences.getInt("last_battery_percentage", -1)
        .takeIf { it in 0..100 }
    private val _uiState = MutableStateFlow(
        CompanionUiState(
            visualTheme = initialVisualTheme,
            colorMode = initialColorMode,
            focus = initialFocus,
            radioPolicy = initialRadioPolicy,
            device = DeviceUiState(
                batteryPercentage = initialBatteryPercentage,
                settingsSyncPending = initialRadioPolicySyncPending,
            ),
            ticket = TicketUiState(
                mode = initialTicketModes.selected,
                passes = initialImportedPasses.ifEmpty { TicketUiState().passes },
                selectedPassId = initialImportedPasses.firstOrNull()?.id ?: TicketUiState().selectedPassId,
                isOnX3 = initialTicketOnX3,
                sendPending = initialPendingTicketPayload != null,
                pendingOperation = initialPendingTicketPayload?.let { payload ->
                    PendingTicketOperation(
                        passId = initialPendingTicketPassId ?: initialImportedPasses.firstOrNull()?.id
                            ?: TicketUiState().selectedPassId,
                        mode = if (payload.mode == TicketDisplayMode.Static) TicketMode.Static else TicketMode.Live,
                    )
                },
                removalPending = initialTicketRemovalPending,
                deployedPassId = initialDeployedPassId,
                deployedMode = initialTicketModes.deployed,
            ),
            readingStats = ReadingStatsUiState(
                sessions = readingStatsRepository.load(),
                minimumPageSeconds = readingStatsRepository.minimumPageSeconds(),
            ),
        ),
    )
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()
    private val companionClient = BluetoothCompanionClient(application)
    private val flightStatusProvider = ProxyFlightStatusProvider(BuildConfig.FLIGHT_STATUS_PROXY_ENDPOINT)
    private val usbFlasher = UsbEspFlasher(application)
    private val usbBookTransfer = UsbBookTransfer(application)
    private val firmwareReleases = FirmwareReleaseRepository(application)
    private val bookLibrary = BookLibraryRepository(application)
    private val connectionPreferences =
        application.getSharedPreferences(BookUploadPersistence.PreferencesName, Application.MODE_PRIVATE)
    private var latestRelease: FirmwareRelease? = null
    private var backgroundDisconnectJob: Job? = null
    private var reconnectJob: Job? = null
    private var focusSyncJob: Job? = null
    private var deleteSyncJob: Job? = null
    private var ticketSendJob: Job? = null
    private var ticketDeleteJob: Job? = null
    private var flightUpdateJob: Job? = null
    private var radioPolicySyncJob: Job? = null
    private var readingQuietJob: Job? = null
    private var bookUploadJob: Job? = null
    private var bookUploadGeneration = 0L
    private var usbMaintenanceActive = false
    private var interactiveTransportRenewalJob: Job? = null
    private var lastDeletedReadingSession: ReadingSessionStat? = null
    private var radioPolicyRevision = 0L
    private var radioPolicySyncPending = initialRadioPolicySyncPending
    private var radioPolicyValidatedForConnection = false
    private var lastTicketCapabilitiesSequence = -1L
    private var appForeground = false
    private var foregroundProbePending = false
    private val interactiveTransport = InteractiveTransportCoordinator()
    private var intentionalTransportIdle = true
    private var reconnectAttempt = 0
    private var pendingFocusSync = initialFocus.pendingAction != null ||
        initialFocus.phase == FocusPhase.Running || initialFocus.phase == FocusPhase.Paused
    private var liveTicketActive = initialTicketOnX3 && initialTicketModes.deployed == TicketMode.Live
    private var pendingTicketPayload: BoardingPassPayload? = initialPendingTicketPayload
    private var pendingTicketPassId: String? = initialPendingTicketPassId
    private val pendingDeletePaths = connectionPreferences
        .getStringSet(PendingDeletePathsKey, emptySet())
        .orEmpty()
        .toCollection(linkedSetOf())
    private val pendingBookUploadIds = connectionPreferences
        .getStringSet(BookUploadPersistence.PendingIdsKey, emptySet())
        .orEmpty()
        .toCollection(linkedSetOf())
    private var pendingBookUploadMethod = runCatching {
        BookTransferMethod.valueOf(
            connectionPreferences.getString(BookUploadPersistence.MethodKey, null)
                ?: BookTransferMethod.Bluetooth.name,
        )
    }.getOrDefault(BookTransferMethod.Bluetooth)

    init {
        managedDeviceModel()?.let { model ->
            _uiState.update { it.copy(isX3Connected = true, connectedDeviceModel = model) }
        }
        viewModelScope.launch {
            BookTransferRuntimeControl.cancelRequests.collect {
                stopBookUpload(showNotice = false)
            }
        }
        viewModelScope.launch {
            companionClient.state.collect { link ->
                val capabilities = link.capabilities
                val hasFreshTicketSnapshot = capabilities != null &&
                    link.capabilitiesSequence != lastTicketCapabilitiesSequence
                if (hasFreshTicketSnapshot) {
                    lastTicketCapabilitiesSequence = link.capabilitiesSequence
                }
                if (link.phase == LinkPhase.Connected && link.requestedModel != null) {
                    connectionPreferences.edit().putString(LastConnectedModelKey, link.requestedModel).apply()
                }
                val managedModel = managedDeviceModel()
                val transportConnected = link.phase == LinkPhase.Connected
                val connectionBlocked = link.requiresBluetoothReset || link.blocker != null
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
                if (hasFreshTicketSnapshot) {
                    val ticketPresent = capabilities.ticketPresent
                    val removalPending = ticketPresent && _uiState.value.ticket.removalPending
                    persistTicketState(ticketPresent, _uiState.value.ticket.deployedMode, removalPending)
                    if (!ticketPresent) {
                        liveTicketActive = false
                        flightUpdateJob?.cancel()
                        flightUpdateJob = null
                    }
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
                if (transportConnected && capabilities != null) foregroundProbePending = false
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
                            quietLinkProbe = when {
                                transportConnected || link.phase == LinkPhase.Error -> false
                                else -> state.device.quietLinkProbe
                            },
                            reconnecting = if (connectionBlocked) false else shouldShowReconnecting(
                                previous = state.device.reconnecting,
                                phase = link.phase,
                                intentionalTransportIdle = intentionalTransportIdle,
                            ),
                            requiresBluetoothReset = link.requiresBluetoothReset,
                            transportBlocker = link.blocker,
                            message = if (reconnectRequired && !connectionBlocked) null else link.message,
                            firmwareVersion = capabilities?.firmwareVersion ?: state.device.firmwareVersion,
                            libraryRevision = capabilities?.libraryRevision ?: state.device.libraryRevision,
                            firmwareProgress = link.transferProgress ?: state.device.firmwareProgress,
                            batteryPercentage = link.deviceStatus?.batteryPercentage
                                ?: state.device.batteryPercentage,
                            charging = transportConnected && link.deviceStatus?.charging == true,
                            settingsSyncPending = radioPolicySyncPending,
                        ),
                        // A Capabilities packet is authoritative once. Reusing its
                        // pre-command ticketPresent bit after ShowTicket is ACKed
                        // would roll the UI back until the next reconnect.
                        ticket = reconcileTicketFromCapabilities(
                            ticket = state.ticket,
                            ticketPresent = capabilities?.ticketPresent ?: false,
                            hasFreshSnapshot = hasFreshTicketSnapshot,
                        ),
                    )
                }
                scheduleInteractiveLeaseForConnection(link.capabilitiesSequence)
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
        ensureLiveFlightUpdateLoop()
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
                        UsbFlashPhase.ResettingSetup,
                        UsbFlashPhase.Erasing,
                        UsbFlashPhase.Writing,
                        UsbFlashPhase.Verifying,
                        UsbFlashPhase.Restarting -> FirmwareCheckPhase.Transferring
                        UsbFlashPhase.Complete,
                        UsbFlashPhase.ReconnectRequired -> FirmwareCheckPhase.Complete
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
                if (usb.deviceDetected) drainPendingUsbWork()
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
                if (_uiState.value.focus.phase == FocusPhase.Review) {
                    focusSessionStore.save(_uiState.value.focus)
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
        presentationStore.saveVisualTheme(theme)
        _uiState.update { it.copy(visualTheme = theme) }
    }

    fun setColorMode(mode: CompanionColorMode) {
        presentationStore.saveColorMode(mode)
        _uiState.update { it.copy(colorMode = mode) }
    }

    fun setRadioPolicy(policy: RadioPolicyUiState) {
        val normalized = policy.copy(
            fastWindowMinutes = policy.fastWindowMinutes.coerceIn(1, 30),
            standbyIntervalSeconds = policy.standbyIntervalSeconds.coerceIn(10, 300),
            connectedIntervalMs = policy.connectedIntervalMs.coerceIn(500, 4_000),
            sleepAfterMinutes = policy.sleepAfterMinutes.coerceIn(
                policy.fastWindowMinutes.coerceIn(1, 30) + 1,
                60,
            ),
            fullRefreshPages = policy.fullRefreshPages.takeIf { it in setOf(1, 5, 10, 15, 30) } ?: 15,
            powerButtonHoldMs = policy.powerButtonHoldMs.takeIf { it in setOf(0, 1_000, 2_000) } ?: 1_000,
        )
        radioPreferences.edit()
            .putInt("fast_window_minutes", normalized.fastWindowMinutes)
            .putInt("standby_interval_seconds", normalized.standbyIntervalSeconds)
            .putInt("connected_interval_ms", normalized.connectedIntervalMs)
            .putInt("sleep_after_minutes", normalized.sleepAfterMinutes)
            .putInt("full_refresh_pages", normalized.fullRefreshPages)
            .putInt("power_button_hold_ms", normalized.powerButtonHoldMs)
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
                    val capabilities = companionClient.state.value.capabilities
                        ?: error("X3 capabilities are not available")
                    if (capabilities.radioPolicyVersion < 2) {
                        error("Update X3 firmware to apply separate Bluetooth standby check-ins")
                    }
                    companionClient.setRadioPolicy(
                        RadioPolicy(
                            fastWindowMinutes = policy.fastWindowMinutes,
                            standbyIntervalSeconds = policy.standbyIntervalSeconds,
                            connectedIntervalMs = policy.connectedIntervalMs,
                            sleepAfterMinutes = policy.sleepAfterMinutes,
                        ),
                    )
                    if (capabilities.supportsReaderPolicy) {
                        val policyVersion = capabilities.readerPolicyVersion
                        companionClient.setReaderPolicy(
                            policy.fullRefreshPages,
                            policy.powerButtonHoldMs,
                            policyVersion,
                        )
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
            if (radioPolicySyncPending && _uiState.value.isX3TransportConnected) {
                // An edit can land after the loop observes a clean revision but
                // before this job publishes completion. Always hand the newest
                // desired state to a fresh worker instead of leaving the chip
                // at "Waiting for X3" until another tap.
                syncRadioPolicy()
            } else {
                releaseTransportIfIdle()
            }
        }
    }

    fun setTask(task: String) {
        _uiState.update { state ->
            if (state.focus.phase != FocusPhase.Setup) state
            else state.copy(focus = state.focus.copy(task = task.take(80)))
        }
        focusSessionStore.save(_uiState.value.focus)
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
        focusSessionStore.save(_uiState.value.focus)
    }

    fun startFocus() {
        _uiState.update { state ->
            state.copy(
                surface = CompanionSurface.Focus,
                focus = state.focus.copy(
                    remainingSeconds = state.focus.selectedMinutes * 60,
                    pendingAction = FocusPendingAction.Start,
                ),
                notice = if (state.isX3TransportConnected) null
                else UiNotice.DeviceMessage("Waiting for X3. Wake it to start Focus."),
            )
        }
        focusSessionStore.save(_uiState.value.focus)
        requestFocusSync()
    }

    fun togglePause() {
        _uiState.update { state ->
            val pendingAction = when (state.focus.phase) {
                FocusPhase.Running -> FocusPendingAction.Pause
                FocusPhase.Paused -> FocusPendingAction.Resume
                else -> null
            }
            state.copy(focus = state.focus.copy(pendingAction = pendingAction))
        }
        focusSessionStore.save(_uiState.value.focus)
        requestFocusSync()
    }

    fun endFocus() {
        _uiState.update { state ->
            state.copy(
                focus = state.focus.copy(
                    pendingAction = FocusPendingAction.Stop,
                ),
            )
        }
        focusSessionStore.save(_uiState.value.focus)
        requestFocusSync()
    }

    fun resetFocus() {
        _uiState.update { state ->
            state.copy(
                surface = CompanionSurface.Focus,
                focus = state.focus.copy(
                    pendingAction = FocusPendingAction.Stop,
                ),
            )
        }
        focusSessionStore.save(_uiState.value.focus)
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
        if (!TicketOperationPolicy.canChangeNextSendMode(_uiState.value.ticket)) return
        _uiState.update { it.copy(ticket = it.ticket.copy(mode = mode)) }
        ticketPreferences.edit().putString("selected_mode", mode.name).apply()
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
            arrivalTime = pass.arrivalTime,
            operatingDate = pass.operatingDate,
            countdown = "",
            gate = pass.gate,
            terminal = pass.terminal,
            seat = pass.seat,
            passenger = pass.passenger,
            boardingGroup = pass.boardingGroup,
            source = pass.source,
            barcodePayload = pass.barcodePayload,
            barcodeFormat = pass.barcodeFormat,
        )
        _uiState.update { state ->
            val passes = listOf(imported) + state.ticket.passes.filterNot {
                it.id == imported.id || it.isSample
            }
            state.copy(
                surface = CompanionSurface.Tools,
                toolDestination = ToolDestination.Passes,
                ticket = state.ticket.copy(passes = passes, selectedPassId = imported.id),
            )
        }
        persistImportedPasses()
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
        drainPendingUsbWork()
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
    }

    /** Gives firmware/reset maintenance exclusive USB ownership without discarding durable work. */
    private suspend fun <T> withExclusiveUsbMaintenance(block: suspend () -> T): T {
        usbMaintenanceActive = true
        bookUploadJob?.cancelAndJoin()
        return try {
            prepareForExternalDeviceReset()
            block()
        } finally {
            usbMaintenanceActive = false
            drainPendingUsbWork()
        }
    }

    fun onAppForegrounded() {
        appForeground = true
        foregroundProbePending = true
        intentionalTransportIdle = false
        val quietProbe = managedDeviceModel() != null && !requiresPersistentTransport()
        _uiState.update { state ->
            state.copy(
                device = state.device.copy(
                    quietLinkProbe = quietProbe,
                    reconnecting = if (quietProbe) false else state.device.reconnecting,
                    message = if (quietProbe) null else state.device.message,
                ),
            )
        }
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = null
        ensureTransportConnected()
        drainPendingUsbWork()
    }

    fun onAppBackgrounded() {
        appForeground = false
        foregroundProbePending = false
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = viewModelScope.launch {
            delay(BackgroundDisconnectGraceMs)
            val state = _uiState.value
            val firmwareInProgress = state.device.firmwareCheckPhase == FirmwareCheckPhase.Downloading ||
                state.device.firmwareCheckPhase == FirmwareCheckPhase.Transferring
            if (!firmwareInProgress && !requiresPersistentTransport()) {
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

    fun prepareLocalFirmware(model: String, uri: Uri) {
        _uiState.update {
            it.copy(device = it.device.copy(
                firmwareCheckPhase = FirmwareCheckPhase.Checking,
                firmwareSource = FirmwareSource.LocalFile,
                latestFirmwareVersion = null,
                message = null,
            ))
        }
        viewModelScope.launch {
            runCatching { firmwareReleases.localFor(model, uri) }
                .onSuccess { release ->
                    latestRelease = release
                    _uiState.update {
                        it.copy(device = it.device.copy(
                            firmwareCheckPhase = FirmwareCheckPhase.Available,
                            firmwareSource = FirmwareSource.LocalFile,
                            latestFirmwareVersion = release.version,
                            message = "SHA-256 ${release.sha256.uppercase()}",
                        ))
                    }
                }
                .onFailure { reportDeviceError(it) }
        }
    }

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
                    withExclusiveUsbMaintenance { usbFlasher.flash(file) }
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
        if (pendingBookUploadIds.isNotEmpty() || bookUploadJob?.isActive == true) {
            bookUploadGeneration += 1L
            pendingBookUploadIds.clear()
            persistPendingBookUpload()
            BookTransferForegroundService.stop(getApplication())
        }
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

    fun resetX3SetupOverUsb() {
        if (!_uiState.value.device.usbConnected) {
            _uiState.update {
                it.copy(notice = UiNotice.DeviceMessage("Connect the X3 to this phone by USB before resetting"))
            }
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                withExclusiveUsbMaintenance { usbFlasher.resetSetupData() }
            }
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(notice = UiNotice.DeviceMessage("X3 setup and pairing reset; SD card preserved"))
                }
            } else {
                result.exceptionOrNull()?.let(::reportDeviceError)
            }
        }
    }

    suspend fun readUsbCrashReport(): String = usbFlasher.readCrashReport()

    suspend fun readUsbDiagnostics(): String = usbFlasher.readDiagnostics()


    fun requestUploadBooksToX3(bookIds: Set<String>, method: BookTransferMethod) {
        if (bookIds.isEmpty() || _uiState.value.read.uploadingToX3 || bookUploadJob?.isActive == true) return
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
        bookUploadGeneration += 1L
        pendingBookUploadIds.clear()
        pendingBookUploadIds.addAll(books.map { it.id })
        pendingBookUploadMethod = method
        persistPendingBookUpload()
        BookTransferForegroundService.start(getApplication())
        _uiState.update { state ->
            state.copy(
                notice = null,
                read = state.read.copy(directUploadOfferBookIds = emptySet()),
            )
        }
        drainPendingUsbWork()
    }

    fun cancelBookUpload() = stopBookUpload(showNotice = true)

    private fun stopBookUpload(showNotice: Boolean) {
        if (pendingBookUploadIds.isEmpty() && bookUploadJob?.isActive != true) return
        bookUploadGeneration += 1L
        pendingBookUploadIds.clear()
        persistPendingBookUpload()
        val stoppingJob = bookUploadJob
        _uiState.update {
            it.copy(
                read = it.read.copy(
                    uploadingToX3 = stoppingJob?.isActive == true,
                    uploadProgress = null,
                    uploadingBookId = null,
                    uploadingBookProgress = null,
                ),
                notice = null,
            )
        }
        viewModelScope.launch {
            stoppingJob?.cancelAndJoin()
            if (bookUploadJob === stoppingJob) bookUploadJob = null
            BookTransferForegroundService.stop(getApplication())
            _uiState.update {
                it.copy(
                    read = it.read.copy(uploadingToX3 = false, uploadMethod = null),
                    notice = if (showNotice) UiNotice.DeviceMessage("Upload stopped") else null,
                )
            }
            releaseTransportIfIdle()
        }
    }

    fun offerImportedBooksForDirectUpload(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        _uiState.update { state ->
            state.copy(read = state.read.copy(directUploadOfferBookIds = bookIds))
        }
    }

    fun dismissDirectBookUploadOffer() {
        _uiState.update { state ->
            state.copy(read = state.read.copy(directUploadOfferBookIds = emptySet()))
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
        BookTransferForegroundService.start(getApplication())
        when (pendingBookUploadMethod) {
            BookTransferMethod.Bluetooth -> {
                if (managedDeviceModel() == null) return
                intentionalTransportIdle = false
                ensureTransportConnected()
            }
            BookTransferMethod.Usb -> if (!usbBookTransfer.isDeviceDetected()) return
        }
        bookUploadJob = viewModelScope.launch(Dispatchers.IO) {
            val generation = bookUploadGeneration
            val method = pendingBookUploadMethod
            _uiState.update {
                it.copy(
                    read = it.read.copy(
                        uploadingToX3 = true,
                        uploadProgress = 0f,
                        uploadingBookId = books.firstOrNull()?.id,
                        uploadingBookProgress = 0f,
                        uploadMethod = method,
                    ),
                )
            }
            val result = runCatching {
                if (method == BookTransferMethod.Bluetooth) companionClient.awaitConnected()
                books.forEachIndexed { index, book ->
                    _uiState.update { state ->
                        state.copy(
                            read = state.read.copy(
                                uploadingBookId = book.id,
                                uploadingBookProgress = 0f,
                            ),
                        )
                    }
                    val uri = Uri.parse(book.sourceUri)
                    val resolver = getApplication<Application>().contentResolver
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    val finalSnapshot = resolver.openInputStream(uri)?.use { input ->
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
                                state.copy(
                                    read = state.read.copy(
                                        uploadProgress = overall.coerceIn(0f, 1f),
                                        uploadingBookProgress = bookProgress.coerceIn(0f, 1f),
                                    ),
                                )
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
                            ).let { null }
                        }
                    } ?: error("${book.title} is no longer available on this phone")
                    // Commit proves the transfer transaction, not the library's
                    // published state. BLE returns a revisioned snapshot here;
                    // USB is reconciled only after a later BLE snapshot.
                    finalSnapshot?.let { snapshot ->
                        reconcileDeviceLibrary(snapshot.revision, snapshot.entries.map { it.path to it.sizeBytes })
                    }
                    pendingBookUploadIds.remove(book.id)
                    persistPendingBookUpload()
                }
                if (method == BookTransferMethod.Usb && companionClient.isReady()) {
                    companionClient.refreshLibraryAndAwaitSnapshot().also { snapshot ->
                        reconcileDeviceLibrary(snapshot.revision, snapshot.entries.map { it.path to it.sizeBytes })
                    }
                }
            }
            val error = result.exceptionOrNull()
            if (generation != bookUploadGeneration) return@launch
            val disposition = error?.let(BookUploadLifecyclePolicy::failureDisposition)
            if (disposition == BookUploadFailureDisposition.StopAndForget) {
                pendingBookUploadIds.clear()
                persistPendingBookUpload()
                BookTransferForegroundService.stop(getApplication())
            } else if (result.isSuccess) {
                BookTransferForegroundService.stop(getApplication())
            }
            _uiState.update { state ->
                state.copy(
                    read = state.read.copy(
                        uploadingToX3 = false,
                        uploadProgress = null,
                        uploadingBookId = null,
                        uploadingBookProgress = null,
                        uploadMethod = if (disposition == BookUploadFailureDisposition.RetryWhenTransportReturns) {
                            method
                        } else {
                            null
                        },
                    ),
                    notice = when {
                        result.isSuccess -> UiNotice.DeviceMessage(
                            "${books.size} book${if (books.size == 1) "" else "s"} uploaded to X3",
                        )
                        disposition == BookUploadFailureDisposition.RetryWhenTransportReturns ->
                            UiNotice.DeviceMessage("Upload paused; it will resume when X3 is available")
                        error is CancellationException -> state.notice
                        else -> UiNotice.DeviceMessage(
                            error?.message ?: "Book upload failed",
                        )
                    },
                )
            }
            if (generation == bookUploadGeneration) bookUploadJob = null
            releaseTransportIfIdle()
        }
    }

    /** Drains durable work whenever the shared USB-availability lifecycle permits it. */
    private fun drainPendingUsbWork() {
        if (usbMaintenanceActive) return
        resumePendingBookUploadIfPossible()
    }

    private fun persistPendingBookUpload() {
        val editor = connectionPreferences.edit()
        if (pendingBookUploadIds.isEmpty()) {
            editor.remove(BookUploadPersistence.PendingIdsKey).remove(BookUploadPersistence.MethodKey)
        } else {
            editor.putStringSet(BookUploadPersistence.PendingIdsKey, pendingBookUploadIds.toSet())
                .putString(BookUploadPersistence.MethodKey, pendingBookUploadMethod.name)
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
        if (pendingTicketPayload != null || ticketSendJob?.isActive == true ||
            !TicketOperationPolicy.canStartSend(_uiState.value.ticket)
        ) return
        val ticket = _uiState.value.ticket
        val pass = ticket.selectedPass
        val displayMode = if (ticket.mode == TicketMode.Static) TicketDisplayMode.Static else TicketDisplayMode.Live
        pendingTicketPayload = boardingPassPayload(pass, displayMode)
        pendingTicketPassId = pass.id
        persistPendingTicketPayload()
        _uiState.update {
            it.copy(
                ticket = it.ticket.copy(
                    sendPending = true,
                    pendingOperation = PendingTicketOperation(pass.id, ticket.mode),
                ),
            )
        }
        intentionalTransportIdle = false
        if (companionClient.isReady()) drainPendingTicketSend() else ensureTransportConnected()
    }

    private fun boardingPassPayload(pass: BoardingPassUiState, displayMode: TicketDisplayMode) =
        BoardingPassPayload(
            mode = displayMode,
            origin = pass.origin,
            destination = pass.destination,
            flight = pass.flight,
            status = pass.status,
            departureTime = pass.departureTime,
            arrivalTime = pass.arrivalTime,
            delayMinutes = pass.delayMinutes,
            gate = pass.gate,
            terminal = pass.terminal,
            seat = pass.seat,
            passenger = pass.passenger,
            boardingGroup = pass.boardingGroup,
            barcodePayload = pass.barcodePayload,
            barcodeFormat = pass.barcodeFormat.name,
        )

    private fun drainPendingTicketSend() {
        val payload = pendingTicketPayload ?: return
        if (!companionClient.isReady() || ticketSendJob?.isActive == true) return
        ticketSendJob = viewModelScope.launch {
            val result = runCatching {
                val format = runCatching { FlightBarcodeFormat.valueOf(payload.barcodeFormat) }
                    .getOrDefault(FlightBarcodeFormat.Unknown)
                val barcodeBmp = BarcodeRasterizer.render(payload.barcodePayload, format).bmpBytes
                withInteractiveTransport(TicketTransferOwner) {
                    companionClient.showTicket(payload, barcodeBmp)
                }
            }
            if (result.isSuccess) {
                val deployedPassId = pendingTicketPassId ?: _uiState.value.ticket.selectedPassId
                pendingTicketPayload = null
                pendingTicketPassId = null
                persistPendingTicketPayload()
                val mode = if (payload.mode == TicketDisplayMode.Static) TicketMode.Static else TicketMode.Live
                liveTicketActive = payload.mode == TicketDisplayMode.Live
                _uiState.update {
                    it.copy(
                        ticket = it.ticket.copy(
                            mode = mode,
                            isOnX3 = true,
                            sendPending = false,
                            pendingOperation = null,
                            removalPending = false,
                            deployedPassId = deployedPassId,
                            deployedMode = mode,
                        ),
                    )
                }
                persistTicketState(true, mode, false)
                ensureLiveFlightUpdateLoop()
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
            releaseTransportIfIdle()
        }
    }

    fun removeTicketFromX3() {
        if (!_uiState.value.isX3Connected) {
            _uiState.update { it.copy(notice = UiNotice.PairBeforeSend) }
            return
        }
        if (!TicketOperationPolicy.canStartRemoval(_uiState.value.ticket) ||
            ticketDeleteJob?.isActive == true
        ) return
        val ticket = _uiState.value.ticket
        persistTicketState(true, ticket.deployedMode, true)
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
                withInteractiveTransport(TicketTransferOwner) {
                    companionClient.clearTicket()
                }
            }
            if (result.isSuccess) {
                liveTicketActive = false
                flightUpdateJob?.cancel()
                flightUpdateJob = null
                persistTicketState(false, null, false)
                intentionalTransportIdle = true
                companionClient.disconnect()
                _uiState.update {
                    it.copy(
                        ticket = it.ticket.copy(
                            isOnX3 = false,
                            removalPending = false,
                            deployedPassId = null,
                            deployedMode = null,
                        ),
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

    private fun persistTicketState(present: Boolean, mode: TicketMode?, removalPending: Boolean) {
        val editor = ticketPreferences.edit()
            .putBoolean("is_on_x3", present)
            .putBoolean("removal_pending", removalPending)
        if (present && mode != null) editor.putString("deployed_mode", mode.name)
        else if (!present) editor.remove("deployed_mode")
        val deployedPassId = _uiState.value.ticket.deployedPassId
        if (present && deployedPassId != null) editor.putString("deployed_pass_id", deployedPassId)
        else if (!present) editor.remove("deployed_pass_id")
        editor.apply()
    }

    private fun persistImportedPasses() {
        val passes = _uiState.value.ticket.passes.filterNot { it.isSample }
        val array = JSONArray()
        passes.forEach { pass ->
            array.put(
                JSONObject()
                    .put("id", pass.id)
                    .put("origin", pass.origin)
                    .put("destination", pass.destination)
                    .put("flight", pass.flight)
                    .put("status", pass.status)
                    .put("departureTime", pass.departureTime)
                    .put("arrivalTime", pass.arrivalTime)
                    .put("delayMinutes", pass.delayMinutes)
                    .put("operatingDate", pass.operatingDate)
                    .put("liveUpdatedAtEpochMs", pass.liveUpdatedAtEpochMs)
                    .put("liveProvider", pass.liveProvider)
                    .put("gate", pass.gate)
                    .put("terminal", pass.terminal)
                    .put("seat", pass.seat)
                    .put("passenger", pass.passenger)
                    .put("boardingGroup", pass.boardingGroup)
                    .put("source", pass.source)
                    .put("barcodePayload", pass.barcodePayload)
                    .put("barcodeFormat", pass.barcodeFormat.name),
            )
        }
        ticketPreferences.edit().putString("passes_json", array.toString()).apply()
    }

    private fun decodeStoredPasses(encoded: String?): List<BoardingPassUiState> = runCatching {
        val array = JSONArray(encoded ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val value = array.getJSONObject(index)
                val payload = value.getString("barcodePayload")
                if (payload.isBlank()) continue
                add(
                    BoardingPassUiState(
                        id = value.getString("id"),
                        origin = value.optString("origin", "---"),
                        destination = value.optString("destination", "---"),
                        flight = value.optString("flight", "Flight"),
                        status = value.optString("status", "Imported"),
                        departureTime = value.optString("departureTime"),
                        arrivalTime = value.optString("arrivalTime"),
                        delayMinutes = if (value.has("delayMinutes") && !value.isNull("delayMinutes")) value.optInt("delayMinutes") else null,
                        operatingDate = value.optString("operatingDate"),
                        liveUpdatedAtEpochMs = if (value.has("liveUpdatedAtEpochMs") && !value.isNull("liveUpdatedAtEpochMs")) value.optLong("liveUpdatedAtEpochMs") else null,
                        liveProvider = value.optString("liveProvider"),
                        countdown = "",
                        gate = value.optString("gate", "TBD"),
                        terminal = value.optString("terminal"),
                        seat = value.optString("seat", "TBD"),
                        passenger = value.optString("passenger", "Passenger"),
                        boardingGroup = value.optString("boardingGroup"),
                        source = value.optString("source", "Imported pass"),
                        barcodePayload = payload,
                        barcodeFormat = runCatching {
                            FlightBarcodeFormat.valueOf(value.optString("barcodeFormat"))
                        }.getOrDefault(FlightBarcodeFormat.Unknown),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun persistPendingTicketPayload() {
        val encoded = pendingTicketPayload?.let {
            Base64.encodeToString(PayloadCodec.encodeBoardingPass(it, payloadVersion = 2), Base64.NO_WRAP)
        }
        ticketPreferences.edit().apply {
            if (encoded == null) remove("pending_show_payload") else putString("pending_show_payload", encoded)
            if (pendingTicketPassId == null) remove("pending_show_pass_id")
            else putString("pending_show_pass_id", pendingTicketPassId)
        }.apply()
    }

    private fun ensureLiveFlightUpdateLoop() {
        if (!liveTicketActive || !flightStatusProvider.isConfigured) {
            flightUpdateJob?.cancel()
            flightUpdateJob = null
            return
        }
        if (flightUpdateJob?.isActive == true) return
        flightUpdateJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive && liveTicketActive) {
                val ticket = _uiState.value.ticket
                val pass = ticket.passes.firstOrNull { it.id == ticket.deployedPassId }
                val identity = pass?.let {
                    FlightIdentity(
                        flightNumber = it.flight,
                        operatingDate = it.operatingDate,
                        origin = it.origin,
                    )
                }
                val result = if (identity?.isComplete == true) {
                    runCatching { flightStatusProvider.latest(identity) }
                } else {
                    Result.success(null)
                }
                val snapshot = result.getOrNull()
                if (snapshot != null && pass != null) {
                    applyFlightStatusSnapshot(pass.id, snapshot)
                    consecutiveFailures = 0
                } else if (result.isFailure) {
                    consecutiveFailures++
                }
                delay(
                    if (result.isFailure) FlightStatusRefreshPolicy.delayAfterFailure(consecutiveFailures)
                    else FlightStatusRefreshPolicy.NormalIntervalMs,
                )
            }
        }
    }

    private fun applyFlightStatusSnapshot(passId: String, snapshot: FlightStatusSnapshot) {
        var updatedPass: BoardingPassUiState? = null
        _uiState.update { state ->
            val existing = state.ticket.passes.firstOrNull { it.id == passId } ?: return@update state
            if ((existing.liveUpdatedAtEpochMs ?: 0L) >= snapshot.observedAtEpochMs) return@update state
            val candidate = existing.copy(
                status = snapshot.status.ifBlank { existing.status },
                departureTime = snapshot.departureTime.ifBlank { existing.departureTime },
                arrivalTime = snapshot.arrivalTime.ifBlank { existing.arrivalTime },
                gate = snapshot.gate.ifBlank { existing.gate },
                terminal = snapshot.terminal.ifBlank { existing.terminal },
                delayMinutes = snapshot.delayMinutes,
                source = "Live via ${snapshot.providerName}",
                liveUpdatedAtEpochMs = snapshot.observedAtEpochMs,
                liveProvider = snapshot.providerName,
            )
            updatedPass = candidate
            state.copy(
                ticket = state.ticket.copy(
                    passes = state.ticket.passes.map { if (it.id == passId) candidate else it },
                ),
            )
        }
        val pass = updatedPass ?: return
        persistImportedPasses()
        if (!liveTicketActive || _uiState.value.ticket.removalPending || pendingTicketPayload != null ||
            ticketSendJob?.isActive == true
        ) return
        pendingTicketPayload = boardingPassPayload(pass, TicketDisplayMode.Live)
        pendingTicketPassId = pass.id
        persistPendingTicketPayload()
        _uiState.update {
            it.copy(
                ticket = it.ticket.copy(
                    sendPending = true,
                    pendingOperation = PendingTicketOperation(pass.id, TicketMode.Live),
                ),
            )
        }
        intentionalTransportIdle = false
        if (companionClient.isReady()) drainPendingTicketSend() else ensureTransportConnected()
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
                    author = "On X3",
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
        val firmwareNeedsLink = state.device.firmwareCheckPhase == FirmwareCheckPhase.Downloading ||
            state.device.firmwareCheckPhase == FirmwareCheckPhase.Transferring
        val bluetoothBookUpload = pendingBookUploadMethod == BookTransferMethod.Bluetooth &&
            (pendingBookUploadIds.isNotEmpty() || bookUploadJob?.isActive == true)
        return interactiveTransport.isActive || radioPolicySyncPending || pendingTicketPayload != null || ticketSendJob?.isActive == true || firmwareNeedsLink || pendingFocusSync || bluetoothBookUpload ||
            pendingDeletePaths.isNotEmpty() || state.ticket.removalPending
    }

    private fun canMaintainTransport(): Boolean = foregroundProbePending || requiresPersistentTransport()

    private fun acquireInteractiveTransport(owner: InteractiveTransportOwner) {
        if (!interactiveTransport.acquire(owner)) return
        intentionalTransportIdle = false
        ensureTransportConnected()
        scheduleInteractiveLeaseForConnection()
        interactiveTransportRenewalJob?.cancel()
        interactiveTransportRenewalJob = viewModelScope.launch {
            while (isActive && interactiveTransport.isActive) {
                delay(InteractiveTransportContract.RenewalIntervalMs)
                applyInteractiveLease(force = true)
            }
        }
    }

    private fun releaseInteractiveTransport(owner: InteractiveTransportOwner) {
        if (!interactiveTransport.release(owner)) return
        interactiveTransportRenewalJob?.cancel()
        interactiveTransportRenewalJob = null
        if (companionClient.isReady()) {
            viewModelScope.launch {
                runCatching { companionClient.releaseInteractiveLease() }
                    .onFailure { error ->
                        Log.i("CompanionViewModel", "Interactive link release was unavailable", error)
                    }
            }
        }
        releaseTransportIfIdle()
    }

    private suspend fun <T> withInteractiveTransport(
        owner: InteractiveTransportOwner,
        block: suspend () -> T,
    ): T {
        acquireInteractiveTransport(owner)
        return try {
            applyInteractiveLease(force = true)
            block()
        } finally {
            releaseInteractiveTransport(owner)
        }
    }

    private fun scheduleInteractiveLeaseForConnection(
        capabilitiesSequence: Long = companionClient.state.value.capabilitiesSequence,
    ) {
        viewModelScope.launch {
            applyInteractiveLease(capabilitiesSequence = capabilitiesSequence)
        }
    }

    private suspend fun applyInteractiveLease(
        capabilitiesSequence: Long = companionClient.state.value.capabilitiesSequence,
        force: Boolean = false,
    ) {
        if (!companionClient.isReady() ||
            !interactiveTransport.shouldApplyLease(capabilitiesSequence, force)
        ) return
        runCatching {
            companionClient.acquireInteractiveLease(InteractiveTransportContract.LeaseSeconds)
        }.onFailure { error ->
            interactiveTransport.markLeaseFailed(capabilitiesSequence)
            Log.i("CompanionViewModel", "Interactive link lease was unavailable", error)
        }
    }

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
        if (requiresPersistentTransport()) {
            _uiState.update { state ->
                state.copy(device = state.device.copy(quietLinkProbe = false))
            }
        }
        if (!canMaintainTransport() || intentionalTransportIdle || reconnectJob?.isActive == true ||
            _uiState.value.device.requiresBluetoothReset
        ) return
        _uiState.update { it.copy(device = it.device.copy(reconnecting = true)) }
        val delayMs = reconnectDelayMs(reconnectAttempt, appForeground)
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

    private fun releaseTransportIfIdle() {
        if (foregroundProbePending || requiresPersistentTransport()) return
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
                val pendingAction = focus.pendingAction
                val result = runCatching {
                    withInteractiveTransport(FocusCommandOwner) { when (pendingAction) {
                        FocusPendingAction.Start -> companionClient.startSession(
                            SessionStart(
                                deadlineEpochSeconds = System.currentTimeMillis() / 1_000 + focus.selectedMinutes * 60,
                                durationSeconds = focus.selectedMinutes * 60,
                                title = focus.task,
                            ),
                        )
                        FocusPendingAction.Pause -> companionClient.pauseSession()
                        FocusPendingAction.Resume -> companionClient.resumeSession()
                        FocusPendingAction.Stop -> companionClient.stopSession()
                        null -> when (focus.phase) {
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
                    } }
                }
                if (result.isSuccess && pendingAction != null) {
                    _uiState.update { state ->
                        val current = state.focus
                        if (current.pendingAction != pendingAction) state else {
                            val applied = current.applyAcknowledged(pendingAction)
                            state.copy(focus = applied, notice = null)
                        }
                    }
                    focusSessionStore.save(_uiState.value.focus)
                } else if (result.isFailure) {
                    pendingFocusSync = true
                    _uiState.update {
                        it.copy(notice = UiNotice.DeviceMessage("Waiting for X3. Wake it to apply Focus."))
                    }
                    handleDeferredTransportFailure(result.exceptionOrNull())
                    break
                }
            }
            focusSyncJob = null
            releaseTransportIfIdle()
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
                releaseTransportIfIdle()
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
        val TicketTransferOwner = InteractiveTransportOwner("ticket-transfer")
        val FocusCommandOwner = InteractiveTransportOwner("focus-command")
        const val BackgroundDisconnectGraceMs = 1_500L
        const val LastConnectedModelKey = "last_connected_model"
        const val PendingDeletePathsKey = "pending_delete_paths"
    }
}
