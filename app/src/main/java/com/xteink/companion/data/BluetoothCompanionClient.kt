package com.xteink.companion.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.xteink.companion.protocol.DeviceCapabilities
import com.xteink.companion.protocol.DeviceStatus
import com.xteink.companion.protocol.BoardingPassPayload
import com.xteink.companion.protocol.BOOK_UPLOAD_CHUNK_BYTES
import com.xteink.companion.protocol.BookUploadBegin
import com.xteink.companion.protocol.Envelope
import com.xteink.companion.protocol.EnvelopeCodec
import com.xteink.companion.protocol.FIRMWARE_CHUNK_BYTES
import com.xteink.companion.protocol.FirmwareBegin
import com.xteink.companion.protocol.LibraryEntry
import com.xteink.companion.protocol.MessageType
import com.xteink.companion.protocol.PayloadCodec
import com.xteink.companion.protocol.RadioPolicy
import com.xteink.companion.protocol.ReadingStatsChunkPayload
import com.xteink.companion.protocol.SessionStart
import com.xteink.companion.protocol.XTEINK_CONTROL_UUID
import com.xteink.companion.protocol.XTEINK_DATA_UUID
import com.xteink.companion.protocol.XTEINK_EVENTS_UUID
import com.xteink.companion.protocol.XTEINK_SERVICE_UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

enum class LinkPhase { Disconnected, Scanning, Connecting, Connected, Error }

data class CompanionLinkState(
    val phase: LinkPhase = LinkPhase.Disconnected,
    val requestedModel: String? = null,
    val capabilities: DeviceCapabilities? = null,
    val deviceStatus: DeviceStatus? = null,
    val message: String? = null,
    val transferProgress: Float? = null,
    val requiresBluetoothReset: Boolean = false,
)

data class DeviceLibrarySnapshot(val revision: UInt, val entries: List<LibraryEntry>)

class BluetoothCompanionClient(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val messageIds = AtomicInteger(1)
    private val pendingAcks = ConcurrentHashMap<UInt, CompletableDeferred<Unit>>()
    private val writes = ConcurrentLinkedQueue<PendingWrite>()
    private val libraryEntries = mutableListOf<LibraryEntry>()
    private var libraryRevision = 0u
    private var writeInFlight = false
    private var gatt: BluetoothGatt? = null
    private var service: BluetoothGattService? = null
    private var scanCallback: ScanCallback? = null
    private var bondReceiver: BroadcastReceiver? = null
    private var adapterReceiver: BroadcastReceiver? = null
    private var preferAutoConnect = false
    private var gracefulDisconnectGatt: BluetoothGatt? = null
    private var gracefulDisconnectCompletion: CompletableDeferred<Unit>? = null
    private var negotiatedMtu = DefaultAttMtu

    private val _state = MutableStateFlow(CompanionLinkState())
    val state: StateFlow<CompanionLinkState> = _state.asStateFlow()
    private val _libraries = MutableSharedFlow<DeviceLibrarySnapshot>(extraBufferCapacity = 2)
    val libraries: SharedFlow<DeviceLibrarySnapshot> = _libraries.asSharedFlow()
    // One maximum-sized X3 session is 64 chunks (2048 pages / 32). Buffer the
    // whole transfer so tryEmit cannot silently drop a middle chunk while the
    // repository is committing the completed session on the IO dispatcher.
    private val _readingStats = MutableSharedFlow<ReadingStatsChunkPayload>(extraBufferCapacity = 64)
    val readingStats: SharedFlow<ReadingStatsChunkPayload> = _readingStats.asSharedFlow()

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> preferAutoConnect = false
                    BluetoothAdapter.STATE_ON -> {
                        preferAutoConnect = false
                        if (_state.value.requiresBluetoothReset) {
                            _state.value = _state.value.copy(
                                phase = LinkPhase.Disconnected,
                                message = null,
                                requiresBluetoothReset = false,
                            )
                        }
                    }
                }
            }
        }
        adapterReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun connect(model: String) {
        if (!hasPermissions()) {
            _state.value = CompanionLinkState(LinkPhase.Error, model, message = "Nearby devices permission is required")
            return
        }
        Log.i(LogTag, "connect requested model=$model phase=${_state.value.phase}")
        disconnect()
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = CompanionLinkState(LinkPhase.Error, model, message = "Bluetooth is turned off")
            return
        }
        _state.value = CompanionLinkState(LinkPhase.Scanning, model, message = "Searching nearby")
        val companionService = ParcelUuid.fromString(XTEINK_SERVICE_UUID)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result, companionService)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it, companionService) }
            }

            private fun handleScanResult(result: ScanResult, companionService: ParcelUuid) {
                val scanRecord = result.scanRecord
                val advertisesCompanion = scanRecord?.serviceUuids?.contains(companionService) == true
                val deviceName = scanRecord?.deviceName
                    ?: runCatching { result.device.name }.getOrNull()
                val hasCompanionName = deviceName.equals(CompanionDeviceName, ignoreCase = true)
                if (!advertisesCompanion && !hasCompanionName) return
                Log.i(
                    LogTag,
                    "scan matched address=${result.device.address} rssi=${result.rssi} uuid=$advertisesCompanion name=$deviceName",
                )
                stopScan()
                pairThenConnect(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                _state.value = _state.value.copy(phase = LinkPhase.Error, message = "Bluetooth scan failed ($errorCode)")
            }
        }
        scanCallback = callback
        val scanner = adapter.bluetoothLeScanner ?: run {
            _state.value = CompanionLinkState(LinkPhase.Error, model, message = "Bluetooth LE is unavailable")
            return
        }
        scanner.startScan(
            emptyList<ScanFilter>(),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        handler.postDelayed({
            if (_state.value.phase == LinkPhase.Scanning) {
                stopScan()
                _state.value = _state.value.copy(phase = LinkPhase.Error, message = "No companion device found")
            }
        }, 15_000)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val previousState = _state.value
        stopScan()
        unregisterBondReceiver()
        gatt?.disconnect()
        gatt?.close()
        gracefulDisconnectCompletion?.complete(Unit)
        gracefulDisconnectCompletion = null
        gracefulDisconnectGatt = null
        gatt = null
        service = null
        writes.clear()
        writeInFlight = false
        pendingAcks.values.forEach { it.cancel() }
        pendingAcks.clear()
        _state.value = CompanionLinkState(
            requestedModel = previousState.requestedModel,
            deviceStatus = previousState.deviceStatus,
            requiresBluetoothReset = previousState.requiresBluetoothReset,
        )
    }

    /**
     * Releases Android's native GATT client before the X3 is deliberately reset.
     * Closing immediately before a firmware reset can leave Android 17 with a
     * stale direct-GATT operation that absorbs every later connectGatt call.
     */
    @SuppressLint("MissingPermission")
    suspend fun prepareForPeripheralReset() {
        val completion = withContext(Dispatchers.Main.immediate) {
            stopScan()
            unregisterBondReceiver()
            writes.clear()
            writeInFlight = false
            pendingAcks.values.forEach { it.cancel() }
            pendingAcks.clear()
            service = null

            val currentGatt = gatt
            _state.value = _state.value.copy(
                phase = LinkPhase.Disconnected,
                message = "Preparing X3 restart",
                transferProgress = null,
            )
            if (currentGatt == null) {
                null
            } else {
                CompletableDeferred<Unit>().also { deferred ->
                    gracefulDisconnectGatt = currentGatt
                    gracefulDisconnectCompletion = deferred
                    runCatching { currentGatt.disconnect() }
                        .onFailure { deferred.complete(Unit) }
                }
            }
        }

        if (completion != null) {
            withTimeoutOrNull(GattGracefulDisconnectTimeoutMs) { completion.await() }
        }
        withContext(Dispatchers.Main.immediate) {
            val releasingGatt = gracefulDisconnectGatt
            if (releasingGatt != null) runCatching { releasingGatt.close() }
            if (gatt === releasingGatt) gatt = null
            gracefulDisconnectCompletion = null
            gracefulDisconnectGatt = null
        }
        // BluetoothGatt.close() unregisters the native client asynchronously.
        // Do not reset the peripheral in the same scheduler turn.
        delay(GattReleaseSettleMs)
    }

    fun resetConnectionRecovery() {
        preferAutoConnect = false
        _state.value = _state.value.copy(
            phase = LinkPhase.Disconnected,
            requiresBluetoothReset = false,
            message = null,
        )
    }

    fun close() {
        disconnect()
        adapterReceiver?.let { receiver -> runCatching { context.unregisterReceiver(receiver) } }
        adapterReceiver = null
    }

    @SuppressLint("MissingPermission")
    private fun pairThenConnect(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            connectGatt(device)
            return
        }
        unregisterBondReceiver()
        _state.value = _state.value.copy(phase = LinkPhase.Connecting, message = "Pairing")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val changedDevice = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (changedDevice?.address != device.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> {
                        unregisterBondReceiver()
                        connectGatt(device)
                    }
                    BluetoothDevice.BOND_NONE -> {
                        val previous =
                            intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                        if (previous == BluetoothDevice.BOND_BONDING) {
                            unregisterBondReceiver()
                            _state.value = _state.value.copy(
                                phase = LinkPhase.Error,
                                message = "Pairing was not completed",
                            )
                        }
                    }
                }
            }
        }
        bondReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        @Suppress("DEPRECATION")
        if (!device.createBond()) {
            unregisterBondReceiver()
            _state.value = _state.value.copy(phase = LinkPhase.Error, message = "Could not start pairing")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt(device: BluetoothDevice, autoConnect: Boolean = preferAutoConnect) {
        Log.i(LogTag, "opening GATT address=${device.address} bond=${device.bondState} auto=$autoConnect")
        _state.value = _state.value.copy(
            phase = LinkPhase.Connecting,
            message = if (autoConnect) "Recovering Bluetooth" else "Connecting",
            capabilities = null,
        )
        // Keep the initial link on the mandatory 1M PHY. Some Android controllers
        // otherwise leave a direct connection pending after service discovery,
        // even though the X3 is still advertising strongly on the legacy PHY.
        val pendingGatt = (if (Build.VERSION.SDK_INT >= 37) {
            val settings = BluetoothGattConnectionSettings.Builder()
                .setAutoConnectEnabled(autoConnect)
                .setAutomaticMtuEnabled(false)
                .setOpportunisticEnabled(false)
                .setTransport(BluetoothDevice.TRANSPORT_LE)
                .build()
            Log.i(LogTag, "using API 37 GATT connection settings auto=$autoConnect")
            device.connectGatt(settings, context.mainExecutor, gattCallback)
        } else {
            device.connectGatt(
                context,
                autoConnect,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK,
                handler,
            )
        }) ?: run {
            failLink("Android could not create a Bluetooth GATT client")
            return
        }
        gatt = pendingGatt
        handler.postDelayed({
            if (gatt === pendingGatt && _state.value.phase == LinkPhase.Connecting) {
                if (!autoConnect) {
                    if (Build.VERSION.SDK_INT >= 37) {
                        // API 37 already uses the current connection-settings API. On the
                        // observed Pixel failure, retrying the same wedged native client as
                        // auto-connect only hid the actionable presence result for 25 seconds.
                        Log.w(LogTag, "API 37 direct GATT produced no callback; probing X3 presence")
                        probeDeviceAfterGattTimeout(device, pendingGatt)
                    } else {
                        Log.w(LogTag, "direct GATT produced no callback; switching to auto-connect recovery")
                        preferAutoConnect = true
                        runCatching { pendingGatt.disconnect() }
                        runCatching { pendingGatt.close() }
                        if (gatt === pendingGatt) gatt = null
                        service = null
                        handler.postDelayed({
                            if (gatt == null && _state.value.phase == LinkPhase.Connecting) {
                                connectGatt(device, autoConnect = true)
                            }
                        }, GattRecoverySettleMs)
                    }
                } else {
                    probeDeviceAfterGattTimeout(device, pendingGatt)
                }
            }
        }, if (autoConnect) GattRecoveryTimeoutMs else GattConnectTimeoutMs)
    }

    @SuppressLint("MissingPermission")
    private fun probeDeviceAfterGattTimeout(device: BluetoothDevice, pendingGatt: BluetoothGatt) {
        Log.w(LogTag, "GATT produced no callback; verifying that X3 is still advertising")
        runCatching { pendingGatt.disconnect() }
        runCatching { pendingGatt.close() }
        if (gatt === pendingGatt) gatt = null
        service = null
        preferAutoConnect = false
        _state.value = _state.value.copy(message = "Checking X3 availability")

        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: run {
            failLink("X3 connection is unavailable. We'll retry when it can be reached.")
            return
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.address != device.address) return
                Log.w(LogTag, "presence probe still sees X3 rssi=${result.rssi}; Android GATT reset required")
                stopScan()
                failLink(
                    "Android's Bluetooth connection is stuck. Restart Bluetooth from the system controls, then return to reconnect.",
                    requiresBluetoothReset = true,
                )
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.firstOrNull { it.device.address == device.address }?.let { onScanResult(0, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                if (scanCallback !== this) return
                stopScan()
                failLink("X3 connection is unavailable. We'll retry when it can be reached.")
            }
        }
        scanCallback = callback
        scanner.startScan(
            emptyList<ScanFilter>(),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        handler.postDelayed({
            if (scanCallback === callback && _state.value.phase == LinkPhase.Connecting) {
                Log.i(LogTag, "presence probe found no X3 advertisement; treating link as unavailable, not wedged")
                stopScan()
                failLink("X3 is out of range, asleep, or saving power. We'll reconnect when it becomes available.")
            }
        }, GattPresenceProbeMs)
    }

    private fun unregisterBondReceiver() {
        bondReceiver?.let { receiver -> runCatching { context.unregisterReceiver(receiver) } }
        bondReceiver = null
    }

    suspend fun startSession(start: SessionStart) = sendAwaitingAck(
        MessageType.StartSession,
        PayloadCodec.encodeSessionStart(start),
    )

    suspend fun pauseSession() = sendAwaitingAck(MessageType.PauseSession)
    suspend fun resumeSession() = sendAwaitingAck(MessageType.ResumeSession)
    suspend fun stopSession() = sendAwaitingAck(MessageType.StopSession)
    suspend fun refreshLibrary() = sendAwaitingAck(MessageType.GetLibrary)
    suspend fun acknowledgeReadingStats(sessionId: UInt) =
        sendAwaitingAck(MessageType.AckReadingStats, PayloadCodec.encodeReadingStatsAck(sessionId))
    suspend fun showTicket(ticket: BoardingPassPayload) =
        sendAwaitingAck(MessageType.ShowTicket, PayloadCodec.encodeBoardingPass(ticket))
    suspend fun setRadioPolicy(policy: RadioPolicy) =
        sendAwaitingAck(MessageType.SetRadioPolicy, PayloadCodec.encodeRadioPolicy(policy))
    suspend fun setReaderPolicy(fullRefreshPages: Int) =
        sendAwaitingAck(MessageType.SetReaderPolicy, PayloadCodec.encodeReaderPolicy(fullRefreshPages))
    suspend fun clearTicket() = sendAwaitingAck(MessageType.ClearTicket)
    suspend fun awaitConnected(timeoutMillis: Long = 20_000) {
        withTimeout(timeoutMillis) {
            val link = state.first {
                (it.phase == LinkPhase.Connected && it.capabilities != null) || it.phase == LinkPhase.Error
            }
            if (link.phase == LinkPhase.Error) {
                error(link.message ?: "Could not connect to X3")
            }
        }
    }

    fun isReady(): Boolean = state.value.phase == LinkPhase.Connected && state.value.capabilities != null

    suspend fun deleteLibraryEntries(revision: UInt, paths: List<String>) {
        paths.forEach { path ->
            sendAwaitingAck(MessageType.DeleteLibraryEntries, PayloadCodec.encodeDeleteLibraryEntries(0u, listOf(path)))
        }
        refreshLibrary()
    }

    suspend fun uploadBook(
        fileName: String,
        sizeBytes: Long,
        sha256: ByteArray,
        input: InputStream,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        var begun = false
        requestTransferConnectionPriority(high = true)
        try {
            sendAwaitingAck(
                MessageType.BeginBookUpload,
                PayloadCodec.encodeBookUploadBegin(BookUploadBegin(fileName, sizeBytes, sha256)),
                timeoutMillis = 20_000,
            )
            begun = true
            val chunkBytes = (negotiatedMtu - AttWriteOverheadBytes)
                .coerceIn(MinimumBookChunkBytes, BOOK_UPLOAD_CHUNK_BYTES)
            val buffer = ByteArray(chunkBytes)
            var offset = 0L
            val acknowledgements = ArrayList<CompletableDeferred<Unit>>(BookUploadWindowSize)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                check(offset + count <= sizeBytes) { "Book source grew while it was uploading" }
                acknowledgements += enqueueAwaitingAck(
                    MessageType.BookUploadChunk,
                    PayloadCodec.encodeBookUploadChunk(offset.toInt(), buffer.copyOf(count)),
                )
                offset += count
                onProgress((offset.toFloat() / sizeBytes).coerceIn(0f, 1f))
                if (acknowledgements.size == BookUploadWindowSize) {
                    withTimeout(BookUploadAckTimeoutMs) { acknowledgements.awaitAll() }
                    acknowledgements.clear()
                }
            }
            if (acknowledgements.isNotEmpty()) {
                withTimeout(BookUploadAckTimeoutMs) { acknowledgements.awaitAll() }
            }
            check(offset == sizeBytes) { "Book source changed before upload completed" }
            sendAwaitingAck(MessageType.CommitBookUpload, timeoutMillis = 60_000)
            onProgress(1f)
            refreshLibrary()
        } catch (error: Throwable) {
            if (begun && isReady()) {
                withContext(NonCancellable) {
                    runCatching { sendAwaitingAck(MessageType.AbortBookUpload) }
                }
            }
            throw error
        } finally {
            requestTransferConnectionPriority(high = false)
        }
    }

    suspend fun abortBookUpload() {
        if (isReady()) sendAwaitingAck(MessageType.AbortBookUpload)
    }

    suspend fun flashFirmware(release: FirmwareRelease, file: File) = withContext(Dispatchers.IO) {
        val digest = release.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        sendAwaitingAck(
            MessageType.BeginFirmware,
            PayloadCodec.encodeFirmwareBegin(
                FirmwareBegin(release.model, release.version, release.sizeBytes, digest),
            ),
        )
        RandomAccessFile(file, "r").use { input ->
            val buffer = ByteArray(FIRMWARE_CHUNK_BYTES)
            var offset = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sendAwaitingAck(
                    MessageType.FirmwareChunk,
                    PayloadCodec.encodeFirmwareChunk(offset, buffer.copyOf(count)),
                    timeoutMillis = 20_000,
                )
                offset += count
                _state.value = _state.value.copy(transferProgress = offset.toFloat() / release.sizeBytes)
            }
        }
        sendAwaitingAck(MessageType.CommitFirmware, timeoutMillis = 60_000)
        sendAwaitingAck(MessageType.ApplyFirmware, timeoutMillis = 10_000)
        _state.value = _state.value.copy(transferProgress = 1f, message = "Device is restarting")
        prepareForPeripheralReset()
    }

    private suspend fun sendAwaitingAck(
        type: MessageType,
        payload: ByteArray = byteArrayOf(),
        timeoutMillis: Long = 10_000,
    ) {
        val deferred = enqueueAwaitingAck(type, payload)
        withTimeout(timeoutMillis) { deferred.await() }
    }

    private fun enqueueAwaitingAck(type: MessageType, payload: ByteArray): CompletableDeferred<Unit> =
        checkNotNull(send(type, payload, awaitAck = true))

    @SuppressLint("MissingPermission")
    private fun requestTransferConnectionPriority(high: Boolean) {
        val currentGatt = gatt ?: return
        val priority = if (high) {
            BluetoothGatt.CONNECTION_PRIORITY_HIGH
        } else {
            BluetoothGatt.CONNECTION_PRIORITY_BALANCED
        }
        Log.i(LogTag, "request connection priority=${if (high) "high" else "balanced"}")
        currentGatt.requestConnectionPriority(priority)
    }

    private fun send(type: MessageType, payload: ByteArray, awaitAck: Boolean): CompletableDeferred<Unit>? {
        check(_state.value.phase == LinkPhase.Connected) { "XTEINK is not connected" }
        val id = messageIds.getAndIncrement().toUInt()
        val deferred = if (awaitAck) CompletableDeferred<Unit>().also { pendingAcks[id] = it } else null
        val bytes = EnvelopeCodec.encode(Envelope(messageType = type, messageId = id, payload = payload))
        Log.i(LogTag, "queue send type=$type id=$id bytes=${bytes.size} awaitAck=$awaitAck")
        val uuid = if (
            type == MessageType.FirmwareChunk || type == MessageType.SceneChunk ||
            type == MessageType.BookUploadChunk
        ) {
            UUID.fromString(XTEINK_DATA_UUID)
        } else {
            UUID.fromString(XTEINK_CONTROL_UUID)
        }
        writes += PendingWrite(uuid, bytes)
        drainWrites()
        return deferred
    }

    @SuppressLint("MissingPermission")
    private fun drainWrites() {
        if (writeInFlight) return
        val next = writes.poll() ?: return
        val characteristic = service?.getCharacteristic(next.characteristicUuid) ?: run {
            failLink("Companion characteristic is missing")
            return
        }
        writeInFlight = true
        val currentGatt = gatt ?: run {
            writeInFlight = false
            failLink("XTEINK transport disappeared before write")
            return
        }
        val started = if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(
                characteristic,
                next.value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = next.value
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            writeInFlight = false
            failLink("Could not write to XTEINK")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt === gracefulDisconnectGatt && newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(LogTag, "graceful GATT release completed status=$status")
                gracefulDisconnectCompletion?.complete(Unit)
                return
            }
            if (gatt !== this@BluetoothCompanionClient.gatt) {
                Log.i(LogTag, "ignoring stale GATT callback status=$status state=$newState")
                gatt.close()
                return
            }
            Log.i(LogTag, "GATT state status=$status state=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                val message = if (status == GattConnectionTimeoutStatus) {
                    "X3 connection timed out. Toggle Bluetooth once if it remains stuck, then retry."
                } else {
                    "XTEINK disconnected (Bluetooth status $status)"
                }
                failLink(message)
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                preferAutoConnect = false
                negotiatedMtu = DefaultAttMtu
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.i(LogTag, "services discovered status=$status count=${gatt.services.size}")
            service = gatt.getService(UUID.fromString(XTEINK_SERVICE_UUID))
            if (status != BluetoothGatt.GATT_SUCCESS || service == null) {
                failLink("This firmware has no companion service")
                return
            }
            gatt.requestMtu(RequestedAttMtu)
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(LogTag, "MTU changed mtu=$mtu status=$status")
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DefaultAttMtu
            val events = service?.getCharacteristic(UUID.fromString(XTEINK_EVENTS_UUID)) ?: return
            gatt.setCharacteristicNotification(events, true)
            val descriptor = events.getDescriptor(CLIENT_CONFIG_UUID) ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.i(LogTag, "notifications subscribed status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failLink("Could not subscribe to XTEINK")
                return
            }
            _state.value = _state.value.copy(phase = LinkPhase.Connected, message = null)
            send(
                MessageType.Hello,
                PayloadCodec.encodeHello(supportsRevisionedDeviceStatus = true),
                awaitAck = false,
            )
            send(MessageType.GetStatus, byteArrayOf(), awaitAck = false)
            send(MessageType.GetLibrary, byteArrayOf(), awaitAck = false)
            send(
                MessageType.SetClock,
                PayloadCodec.encodeClock(System.currentTimeMillis() / 1_000L),
                awaitAck = false,
            )
            send(MessageType.GetReadingStats, byteArrayOf(), awaitAck = false)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.i(LogTag, "write complete uuid=${characteristic.uuid} status=$status")
            writeInFlight = false
            if (status == BluetoothGatt.GATT_SUCCESS) drainWrites() else failLink("XTEINK rejected a write ($status)")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = handleEnvelope(value)

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleEnvelope(characteristic.value ?: return)
        }
    }

    private fun handleEnvelope(bytes: ByteArray) {
        runCatching { EnvelopeCodec.decode(bytes) }.onSuccess { envelope ->
            Log.i(LogTag, "received type=${envelope.messageType} id=${envelope.messageId} bytes=${bytes.size}")
            when (envelope.messageType) {
                MessageType.Ack -> pendingAcks.remove(PayloadCodec.decodeAck(envelope.payload))?.complete(Unit)
                MessageType.Nack, MessageType.Error -> {
                    val id = if (envelope.payload.size >= 4) PayloadCodec.decodeAck(envelope.payload) else 0u
                    val message = envelope.payload.drop(4).toByteArray().toString(Charsets.UTF_8).ifBlank { "Device rejected command" }
                    pendingAcks.remove(id)?.completeExceptionally(IllegalStateException(message))
                    _state.value = _state.value.copy(message = message)
                }
                MessageType.Capabilities -> {
                    val capabilities = PayloadCodec.decodeCapabilities(envelope.payload)
                    _state.value = _state.value.copy(capabilities = capabilities)
                }
                MessageType.StatusChanged -> {
                    val status = PayloadCodec.decodeDeviceStatus(envelope.payload)
                    _state.value = _state.value.copy(deviceStatus = status)
                    send(
                        MessageType.AckStatus,
                        PayloadCodec.encodeStatusAck(status.revision),
                        awaitAck = false,
                    )
                }
                MessageType.LibraryPage -> {
                    val page = PayloadCodec.decodeLibraryPage(envelope.payload)
                    if (page.pageIndex == 0 || page.revision != libraryRevision) libraryEntries.clear()
                    libraryRevision = page.revision
                    libraryEntries += page.entries
                    if (page.isLastPage) _libraries.tryEmit(DeviceLibrarySnapshot(page.revision, libraryEntries.toList()))
                }
                MessageType.ReadingStatsChunk -> _readingStats.tryEmit(
                    PayloadCodec.decodeReadingStatsChunk(envelope.payload),
                )
                else -> Unit
            }
        }.onFailure { failLink("Invalid companion response: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val callback = scanCallback ?: return
        if (hasPermissions()) bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(callback)
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun failLink(message: String, requiresBluetoothReset: Boolean = false) {
        Log.w(LogTag, "link failed: $message")
        val failure = IllegalStateException(message)
        pendingAcks.values.forEach { it.completeExceptionally(failure) }
        pendingAcks.clear()
        writes.clear()
        writeInFlight = false
        if (hasPermissions()) runCatching { gatt?.close() }
        gatt = null
        service = null
        _state.value = _state.value.copy(
            phase = LinkPhase.Error,
            message = message,
            transferProgress = null,
            requiresBluetoothReset = requiresBluetoothReset,
        )
    }

    private data class PendingWrite(val characteristicUuid: UUID, val value: ByteArray)
    companion object {
        private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val CompanionDeviceName = "XTEINK Companion"
        private const val LogTag = "XteinkBle"
        private const val DefaultAttMtu = 23
        private const val RequestedAttMtu = 517
        private const val AttWriteOverheadBytes = 3 + 20 + 4
        private const val MinimumBookChunkBytes = 20
        private const val BookUploadWindowSize = 4
        private const val BookUploadAckTimeoutMs = 20_000L
        private const val GattConnectTimeoutMs = 12_000L
        private const val GattRecoverySettleMs = 500L
        private const val GattRecoveryTimeoutMs = 25_000L
        private const val GattPresenceProbeMs = 6_000L
        private const val GattConnectionTimeoutStatus = 147
        private const val GattGracefulDisconnectTimeoutMs = 2_000L
        private const val GattReleaseSettleMs = 750L

        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
