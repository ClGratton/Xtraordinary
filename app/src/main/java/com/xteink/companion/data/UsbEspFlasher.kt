package com.xteink.companion.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque

enum class UsbFlashPhase {
    Disconnected,
    PermissionRequired,
    Ready,
    EnteringBootloader,
    Erasing,
    Writing,
    Verifying,
    Restarting,
    Complete,
    Error,
}

data class UsbFlashState(
    val phase: UsbFlashPhase = UsbFlashPhase.Disconnected,
    val deviceDetected: Boolean = false,
    val progress: Float? = null,
    val message: String? = null,
)

class UsbEspFlasher(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _state = MutableStateFlow(UsbFlashState())
    val state: StateFlow<UsbFlashState> = _state.asStateFlow()
    private var permissionResult: CompletableDeferred<Boolean>? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PermissionAction -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    permissionResult?.complete(granted)
                    permissionResult = null
                    refresh()
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> refresh()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(PermissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    fun refresh() {
        val device = findDevice()
        if (device == null) {
            if (_state.value.phase !in ActivePhases) {
                _state.value = UsbFlashState()
            }
            return
        }
        if (_state.value.phase in ActivePhases) return
        val granted = usbManager.hasPermission(device)
        _state.value = UsbFlashState(
            phase = if (granted) UsbFlashPhase.Ready else UsbFlashPhase.PermissionRequired,
            deviceDetected = true,
            message = if (granted) "X3 connected by USB" else "X3 connected by USB · access required",
        )
    }

    suspend fun flash(file: File) = withContext(Dispatchers.IO) {
        val image = file.readBytes()
        require(image.isNotEmpty() && image[0].toInt() and 0xFF == 0xE9) {
            "The downloaded file is not an ESP32-C3 application image"
        }
        require(image.size <= EspRomProtocol.MaxAppSize) {
            "Firmware image does not fit the X3 app partition"
        }
        val device = awaitPermission()
        _state.value = UsbFlashState(
            phase = UsbFlashPhase.EnteringBootloader,
            deviceDetected = true,
            progress = 0f,
            message = "Entering X3 bootloader…",
        )
        runCatching {
            RomConnection.open(usbManager, device).use { connection ->
                connection.enterBootloaderAndSync()
                _state.value = _state.value.copy(
                    phase = UsbFlashPhase.Erasing,
                    message = "Preparing the X3 flash…",
                )
                connection.flashBegin(image.size)
                _state.value = _state.value.copy(
                    phase = UsbFlashPhase.Writing,
                    message = "Installing firmware from this phone…",
                )
                connection.writeImage(image) { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
                connection.flashEnd()
                _state.value = _state.value.copy(
                    phase = UsbFlashPhase.Verifying,
                    progress = 1f,
                    message = "Verifying firmware on the X3…",
                )
                connection.verifyMd5(image)
                _state.value = _state.value.copy(
                    phase = UsbFlashPhase.Restarting,
                    message = "Firmware verified · restarting X3…",
                )
                connection.hardReset()
            }
        }.onSuccess {
            _state.value = UsbFlashState(
                phase = UsbFlashPhase.Complete,
                deviceDetected = findDevice() != null,
                progress = 1f,
                message = "Firmware installed · X3 is restarting",
            )
        }.onFailure { error ->
            _state.value = UsbFlashState(
                phase = UsbFlashPhase.Error,
                deviceDetected = findDevice() != null,
                message = error.message ?: "USB firmware installation failed",
            )
            throw error
        }.getOrThrow()
    }

    private suspend fun awaitPermission(): UsbDevice {
        val device = findDevice() ?: error("Connect the X3 to this phone with a USB data cable")
        if (usbManager.hasPermission(device)) return device
        _state.value = UsbFlashState(
            phase = UsbFlashPhase.PermissionRequired,
            deviceDetected = true,
            message = "Allow Xtraordinary to access the connected X3",
        )
        val deferred = CompletableDeferred<Boolean>()
        permissionResult = deferred
        val intent = Intent(PermissionAction).setPackage(appContext.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        usbManager.requestPermission(device, pendingIntent)
        val granted = withTimeout(PermissionTimeoutMs) { deferred.await() }
        require(granted && usbManager.hasPermission(device)) { "USB access to the X3 was not granted" }
        return device
    }

    private fun findDevice(): UsbDevice? = usbManager.deviceList.values.firstOrNull {
        it.vendorId == EspressifVendorId && it.productId == EspUsbJtagSerialProductId
    }

    override fun close() {
        runCatching { appContext.unregisterReceiver(receiver) }
        permissionResult?.cancel()
        permissionResult = null
    }

    private class RomConnection(
        private val connection: UsbDeviceConnection,
        private val controlInterface: UsbInterface,
        private val dataInterface: UsbInterface,
        private val input: UsbEndpoint,
        private val output: UsbEndpoint,
    ) : Closeable {
        private var dtr = false
        private var rts = false
        private val receivedBytes = ArrayDeque<Int>()

        fun enterBootloaderAndSync() {
            setLineCoding()
            var lastError: Throwable? = null
            repeat(7) {
                runCatching {
                    usbJtagSerialReset()
                    drainInput()
                    command(EspRomProtocol.Sync, EspRomProtocol.syncPayload(), timeoutMs = 1_000)
                }.onSuccess { return }.onFailure { lastError = it }
            }
            throw IllegalStateException(
                "Could not enter the X3 bootloader. Keep the cable connected and try again.",
                lastError,
            )
        }

        fun flashBegin(size: Int) {
            command(
                EspRomProtocol.FlashBegin,
                EspRomProtocol.flashBeginPayload(size),
                timeoutMs = 180_000,
            )
        }

        fun writeImage(image: ByteArray, onProgress: (Float) -> Unit) {
            val blockCount = (image.size + EspRomProtocol.FlashBlockSize - 1) / EspRomProtocol.FlashBlockSize
            repeat(blockCount) { sequence ->
                val start = sequence * EspRomProtocol.FlashBlockSize
                val end = minOf(start + EspRomProtocol.FlashBlockSize, image.size)
                val block = ByteArray(EspRomProtocol.FlashBlockSize) { 0xFF.toByte() }
                image.copyInto(block, endIndex = end, startIndex = start)
                val payload = EspRomProtocol.flashDataPayload(block, sequence)
                var lastError: Throwable? = null
                for (attempt in 0 until 3) {
                    val result = runCatching {
                        command(
                            EspRomProtocol.FlashData,
                            payload,
                            EspRomProtocol.checksum(block),
                            timeoutMs = 5_000,
                        )
                    }
                    if (result.isSuccess) {
                        lastError = null
                        break
                    }
                    lastError = result.exceptionOrNull()
                    if (attempt == 2) throw lastError!!
                }
                if (sequence % 8 == 0 || sequence == blockCount - 1) {
                    onProgress((sequence + 1).toFloat() / blockCount)
                }
            }
        }

        fun flashEnd() {
            command(EspRomProtocol.FlashEnd, EspRomProtocol.flashEndPayload(), timeoutMs = 5_000)
        }

        fun verifyMd5(image: ByteArray) {
            val expected = MessageDigest.getInstance("MD5").digest(image).toHex()
            val response = command(
                EspRomProtocol.FlashMd5,
                EspRomProtocol.flashMd5Payload(image.size),
                responseDataBytes = 32,
                timeoutMs = 60_000,
            )
            val actual = response.data.copyOfRange(0, 32).toString(Charsets.US_ASCII).lowercase()
            require(actual == expected) { "The firmware written to X3 did not pass verification" }
        }

        fun hardReset() {
            setControlLines(dtr = false, rts = true)
            Thread.sleep(200)
            setControlLines(dtr = false, rts = false)
            Thread.sleep(200)
        }

        private fun command(
            operation: Int,
            data: ByteArray,
            checksum: Int = 0,
            responseDataBytes: Int = 0,
            timeoutMs: Int,
        ): EspRomProtocol.Response {
            writeAll(EspRomProtocol.request(operation, data, checksum))
            repeat(100) {
                val response = EspRomProtocol.parseResponse(readSlipPacket(timeoutMs))
                if (response.operation != operation) return@repeat
                require(response.data.size >= responseDataBytes + 2) {
                    "Incomplete X3 bootloader response for command 0x${operation.toString(16)}"
                }
                val status = response.data[responseDataBytes].toInt() and 0xFF
                val reason = response.data[responseDataBytes + 1].toInt() and 0xFF
                require(status == 0) {
                    "X3 bootloader rejected command 0x${operation.toString(16)} (reason $reason)"
                }
                return response
            }
            error("X3 bootloader did not answer command 0x${operation.toString(16)}")
        }

        private fun usbJtagSerialReset() {
            setControlLines(dtr = dtr, rts = false)
            setControlLines(dtr = false, rts = rts)
            Thread.sleep(100)
            setControlLines(dtr = true, rts = rts)
            setControlLines(dtr = dtr, rts = false)
            Thread.sleep(100)
            setControlLines(dtr = dtr, rts = true)
            setControlLines(dtr = false, rts = rts)
            setControlLines(dtr = dtr, rts = true)
            Thread.sleep(100)
            setControlLines(dtr = false, rts = rts)
            setControlLines(dtr = dtr, rts = false)
        }

        private fun setLineCoding() {
            val lineCoding = byteArrayOf(
                0x00, 0xC2.toByte(), 0x01, 0x00, // 115200 little-endian
                0x00, // one stop bit
                0x00, // no parity
                0x08, // eight data bits
            )
            val result = connection.controlTransfer(
                UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_OUT,
                CdcSetLineCoding,
                0,
                controlInterface.id,
                lineCoding,
                lineCoding.size,
                UsbTimeoutMs,
            )
            require(result == lineCoding.size) { "Could not configure the X3 USB serial interface" }
        }

        private fun setControlLines(dtr: Boolean, rts: Boolean) {
            this.dtr = dtr
            this.rts = rts
            val value = (if (dtr) 1 else 0) or (if (rts) 2 else 0)
            val result = connection.controlTransfer(
                UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_OUT,
                CdcSetControlLineState,
                value,
                controlInterface.id,
                null,
                0,
                UsbTimeoutMs,
            )
            require(result >= 0) { "Could not reset the X3 into firmware mode" }
        }

        private fun writeAll(bytes: ByteArray) {
            var offset = 0
            while (offset < bytes.size) {
                val written = connection.bulkTransfer(
                    output,
                    bytes,
                    offset,
                    bytes.size - offset,
                    UsbTimeoutMs,
                )
                require(written > 0) { "USB write to X3 failed" }
                offset += written
            }
        }

        private fun readSlipPacket(timeoutMs: Int): ByteArray {
            val deadline = System.currentTimeMillis() + timeoutMs
            val frame = ByteArrayOutputStream()
            val buffer = ByteArray(input.maxPacketSize.coerceAtLeast(64))
            var started = false
            while (System.currentTimeMillis() < deadline) {
                if (receivedBytes.isEmpty()) {
                    val remaining = (deadline - System.currentTimeMillis()).coerceAtMost(250).toInt()
                    val count = connection.bulkTransfer(input, buffer, buffer.size, remaining)
                    if (count <= 0) continue
                    for (index in 0 until count) receivedBytes.addLast(buffer[index].toInt() and 0xFF)
                }
                while (receivedBytes.isNotEmpty()) {
                    val value = receivedBytes.removeFirst()
                    if (!started) {
                        if (value == SlipEnd) {
                            started = true
                            frame.reset()
                        }
                    } else if (value == SlipEnd) {
                        if (frame.size() > 0) return EspRomProtocol.slipDecode(frame.toByteArray())
                    } else {
                        frame.write(value)
                    }
                }
            }
            error("Timed out waiting for the X3 bootloader")
        }

        private fun drainInput() {
            val buffer = ByteArray(input.maxPacketSize.coerceAtLeast(64))
            receivedBytes.clear()
            while (connection.bulkTransfer(input, buffer, buffer.size, 10) > 0) Unit
        }

        override fun close() {
            runCatching { connection.releaseInterface(dataInterface) }
            runCatching { connection.releaseInterface(controlInterface) }
            connection.close()
        }

        companion object {
            fun open(manager: UsbManager, device: UsbDevice): RomConnection {
                val control = (0 until device.interfaceCount)
                    .map(device::getInterface)
                    .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_COMM }
                    ?: error("X3 USB control interface is missing")
                val data = (0 until device.interfaceCount)
                    .map(device::getInterface)
                    .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }
                    ?: error("X3 USB serial interface is missing")
                val input = (0 until data.endpointCount)
                    .map(data::getEndpoint)
                    .firstOrNull { it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
                    ?: error("X3 USB input endpoint is missing")
                val output = (0 until data.endpointCount)
                    .map(data::getEndpoint)
                    .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
                    ?: error("X3 USB output endpoint is missing")
                val connection = manager.openDevice(device) ?: error("Could not open the X3 USB connection")
                require(connection.claimInterface(control, true) && connection.claimInterface(data, true)) {
                    connection.close()
                    "Could not claim the X3 USB serial interface"
                }
                return RomConnection(connection, control, data, input, output)
            }
        }
    }

    companion object {
        private const val EspressifVendorId = 0x303A
        private const val EspUsbJtagSerialProductId = 0x1001
        private const val PermissionAction = "com.xteink.companion.USB_PERMISSION"
        private const val PermissionTimeoutMs = 30_000L
        private const val UsbTimeoutMs = 5_000
        private const val CdcSetLineCoding = 0x20
        private const val CdcSetControlLineState = 0x22
        private const val SlipEnd = 0xC0
        private val ActivePhases = setOf(
            UsbFlashPhase.EnteringBootloader,
            UsbFlashPhase.Erasing,
            UsbFlashPhase.Writing,
            UsbFlashPhase.Verifying,
            UsbFlashPhase.Restarting,
        )
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
