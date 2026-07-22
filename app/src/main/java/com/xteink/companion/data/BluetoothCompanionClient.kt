package com.xteink.companion.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.xteink.companion.protocol.DeviceCapabilities
import com.xteink.companion.protocol.Envelope
import com.xteink.companion.protocol.EnvelopeCodec
import com.xteink.companion.protocol.FIRMWARE_CHUNK_BYTES
import com.xteink.companion.protocol.FirmwareBegin
import com.xteink.companion.protocol.LibraryEntry
import com.xteink.companion.protocol.MessageType
import com.xteink.companion.protocol.PayloadCodec
import com.xteink.companion.protocol.SessionStart
import com.xteink.companion.protocol.XTEINK_CONTROL_UUID
import com.xteink.companion.protocol.XTEINK_DATA_UUID
import com.xteink.companion.protocol.XTEINK_EVENTS_UUID
import com.xteink.companion.protocol.XTEINK_SERVICE_UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
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
    val message: String? = null,
    val transferProgress: Float? = null,
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

    private val _state = MutableStateFlow(CompanionLinkState())
    val state: StateFlow<CompanionLinkState> = _state.asStateFlow()
    private val _libraries = MutableSharedFlow<DeviceLibrarySnapshot>(extraBufferCapacity = 2)
    val libraries: SharedFlow<DeviceLibrarySnapshot> = _libraries.asSharedFlow()

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun connect(model: String) {
        if (!hasPermissions()) {
            _state.value = CompanionLinkState(LinkPhase.Error, model, message = "Nearby devices permission is required")
            return
        }
        disconnect()
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = CompanionLinkState(LinkPhase.Error, model, message = "Bluetooth is turned off")
            return
        }
        _state.value = CompanionLinkState(LinkPhase.Scanning, model, message = "Searching nearby")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                stopScan()
                _state.value = _state.value.copy(phase = LinkPhase.Connecting, message = "Connecting")
                gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }

            override fun onScanFailed(errorCode: Int) {
                _state.value = _state.value.copy(phase = LinkPhase.Error, message = "Bluetooth scan failed ($errorCode)")
            }
        }
        scanCallback = callback
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(XTEINK_SERVICE_UUID)).build()
        val scanner = adapter.bluetoothLeScanner ?: run {
            _state.value = CompanionLinkState(LinkPhase.Error, model, message = "Bluetooth LE is unavailable")
            return
        }
        scanner.startScan(
            listOf(filter),
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
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        service = null
        writes.clear()
        writeInFlight = false
        pendingAcks.values.forEach { it.cancel() }
        pendingAcks.clear()
        _state.value = CompanionLinkState()
    }

    suspend fun startSession(start: SessionStart) = sendAwaitingAck(
        MessageType.StartSession,
        PayloadCodec.encodeSessionStart(start),
    )

    suspend fun pauseSession() = sendAwaitingAck(MessageType.PauseSession)
    suspend fun resumeSession() = sendAwaitingAck(MessageType.ResumeSession)
    suspend fun stopSession() = sendAwaitingAck(MessageType.StopSession)
    suspend fun refreshLibrary() = sendAwaitingAck(MessageType.GetLibrary)

    suspend fun deleteLibraryEntries(revision: UInt, paths: List<String>) {
        paths.forEach { path ->
            sendAwaitingAck(MessageType.DeleteLibraryEntries, PayloadCodec.encodeDeleteLibraryEntries(0u, listOf(path)))
        }
        refreshLibrary()
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
        send(MessageType.ApplyFirmware, byteArrayOf(), awaitAck = false)
        _state.value = _state.value.copy(transferProgress = 1f, message = "Device is restarting")
    }

    private suspend fun sendAwaitingAck(
        type: MessageType,
        payload: ByteArray = byteArrayOf(),
        timeoutMillis: Long = 10_000,
    ) {
        val deferred = send(type, payload, awaitAck = true) ?: return
        withTimeout(timeoutMillis) { deferred.await() }
    }

    private fun send(type: MessageType, payload: ByteArray, awaitAck: Boolean): CompletableDeferred<Unit>? {
        check(_state.value.phase == LinkPhase.Connected) { "XTEINK is not connected" }
        val id = messageIds.getAndIncrement().toUInt()
        val deferred = if (awaitAck) CompletableDeferred<Unit>().also { pendingAcks[id] = it } else null
        val bytes = EnvelopeCodec.encode(Envelope(messageType = type, messageId = id, payload = payload))
        val uuid = if (type == MessageType.FirmwareChunk || type == MessageType.SceneChunk) {
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
        val currentGatt = gatt ?: return
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
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                failLink("XTEINK disconnected")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            service = gatt.getService(UUID.fromString(XTEINK_SERVICE_UUID))
            if (status != BluetoothGatt.GATT_SUCCESS || service == null) {
                failLink("This firmware has no companion service")
                return
            }
            gatt.requestMtu(247)
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
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
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failLink("Could not subscribe to XTEINK")
                return
            }
            _state.value = _state.value.copy(phase = LinkPhase.Connected, message = null)
            send(MessageType.Hello, byteArrayOf(), awaitAck = false)
            send(MessageType.GetStatus, byteArrayOf(), awaitAck = false)
            send(MessageType.GetLibrary, byteArrayOf(), awaitAck = false)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
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
            when (envelope.messageType) {
                MessageType.Ack -> pendingAcks.remove(PayloadCodec.decodeAck(envelope.payload))?.complete(Unit)
                MessageType.Nack, MessageType.Error -> {
                    val id = if (envelope.payload.size >= 4) PayloadCodec.decodeAck(envelope.payload) else 0u
                    val message = envelope.payload.drop(4).toByteArray().toString(Charsets.UTF_8).ifBlank { "Device rejected command" }
                    pendingAcks.remove(id)?.completeExceptionally(IllegalStateException(message))
                    _state.value = _state.value.copy(message = message)
                }
                MessageType.Capabilities, MessageType.StatusChanged -> {
                    val capabilities = PayloadCodec.decodeCapabilities(envelope.payload)
                    _state.value = _state.value.copy(capabilities = capabilities)
                }
                MessageType.LibraryPage -> {
                    val page = PayloadCodec.decodeLibraryPage(envelope.payload)
                    if (page.pageIndex == 0 || page.revision != libraryRevision) libraryEntries.clear()
                    libraryRevision = page.revision
                    libraryEntries += page.entries
                    if (page.isLastPage) _libraries.tryEmit(DeviceLibrarySnapshot(page.revision, libraryEntries.toList()))
                }
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

    private fun failLink(message: String) {
        _state.value = _state.value.copy(phase = LinkPhase.Error, message = message, transferProgress = null)
    }

    private data class PendingWrite(val characteristicUuid: UUID, val value: ByteArray)
    companion object {
        private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
