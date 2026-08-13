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
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.xteink.companion.protocol.BookUploadBegin
import com.xteink.companion.protocol.Envelope
import com.xteink.companion.protocol.EnvelopeCodec
import com.xteink.companion.protocol.MessageType
import com.xteink.companion.protocol.PayloadCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.InputStream
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/** Book transfer over the ESP32-C3 USB Serial/JTAG bulk endpoints. */
class UsbBookTransfer(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val messageIds = AtomicInteger(1)
    private var permissionResult: CompletableDeferred<Boolean>? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PermissionAction) {
                permissionResult?.complete(
                    intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false),
                )
                permissionResult = null
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(PermissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun isDeviceDetected(): Boolean = findDevice() != null

    suspend fun uploadBook(
        fileName: String,
        sizeBytes: Long,
        sha256: ByteArray,
        input: InputStream,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val startedAtMs = SystemClock.elapsedRealtime()
        var phase = "detect"
        var offset = 0L
        try {
            val detected = findDevice() ?: error("Connect the X3 with a USB data cable")
            Log.i(LogTag, "detected file=$fileName bytes=$sizeBytes device=${detected.deviceName}")
            phase = "permission"
            val device = awaitPermission(detected)
            phase = "open"
            UsbSerialConnection.open(usbManager, device).use { connection ->
                connection.prepare()
                Log.i(LogTag, "opened file=$fileName device=${device.deviceName}")
                var begun = false
                try {
                    phase = "begin"
                    val begin = nextEnvelope(
                        MessageType.BeginBookUpload,
                        PayloadCodec.encodeBookUploadBegin(BookUploadBegin(fileName, sizeBytes, sha256)),
                    )
                    connection.send(begin, BeginTimeoutMs)
                    Log.i(LogTag, "begin ack file=$fileName messageId=${begin.messageId}")
                    begun = true
                    phase = "chunk"
                    val buffer = ByteArray(UsbBookChunkBytes)
                    var nextProgressLogAt = ProgressLogBytes
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        check(offset + count <= sizeBytes) { "Book source grew while it was uploading" }
                        connection.send(
                            nextEnvelope(
                                MessageType.BookUploadChunk,
                                PayloadCodec.encodeBookUploadChunk(offset.toInt(), buffer.copyOf(count)),
                            ),
                            ChunkTimeoutMs,
                        )
                        offset += count
                        onProgress((offset.toFloat() / sizeBytes).coerceIn(0f, 1f))
                        if (offset >= nextProgressLogAt || offset == sizeBytes) {
                            Log.i(LogTag, "progress file=$fileName bytes=$offset/$sizeBytes")
                            while (nextProgressLogAt <= offset) nextProgressLogAt += ProgressLogBytes
                        }
                    }
                    check(offset == sizeBytes) { "Book source changed before upload completed" }
                    phase = "commit"
                    val commit = nextEnvelope(MessageType.CommitBookUpload)
                    connection.send(commit, CommitTimeoutMs)
                    Log.i(LogTag, "commit ack file=$fileName messageId=${commit.messageId}")
                    onProgress(1f)
                } catch (error: Throwable) {
                    if (begun) {
                        withContext(NonCancellable) {
                            runCatching {
                                val abort = nextEnvelope(MessageType.AbortBookUpload)
                                connection.send(abort, AbortTimeoutMs)
                                Log.i(LogTag, "abort ack file=$fileName messageId=${abort.messageId}")
                            }.onFailure { abortError ->
                                Log.w(LogTag, "abort failed file=$fileName", abortError)
                            }
                        }
                    }
                    throw error
                }
            }
            val elapsedMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(1L)
            val bytesPerSecond = sizeBytes * 1_000L / elapsedMs
            Log.i(LogTag, "complete file=$fileName elapsedMs=$elapsedMs bytesPerSecond=$bytesPerSecond")
        } catch (error: Throwable) {
            Log.e(LogTag, "failed phase=$phase file=$fileName offset=$offset/$sizeBytes", error)
            throw error
        }
    }

    private fun nextEnvelope(type: MessageType, payload: ByteArray = byteArrayOf()): Envelope = Envelope(
        messageType = type,
        messageId = messageIds.getAndIncrement().toUInt(),
        payload = payload,
    )

    private suspend fun awaitPermission(device: UsbDevice): UsbDevice {
        if (usbManager.hasPermission(device)) {
            Log.i(LogTag, "permission already granted device=${device.deviceName}")
            return device
        }
        val deferred = CompletableDeferred<Boolean>()
        permissionResult = deferred
        val intent = Intent(PermissionAction).setPackage(appContext.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        Log.i(LogTag, "permission requested device=${device.deviceName}")
        usbManager.requestPermission(device, pendingIntent)
        val granted = withTimeout(PermissionTimeoutMs) { deferred.await() }
        require(granted && usbManager.hasPermission(device)) { "USB access to the X3 was not granted" }
        Log.i(LogTag, "permission granted device=${device.deviceName}")
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

    private class UsbSerialConnection(
        private val connection: UsbDeviceConnection,
        private val controlInterface: UsbInterface,
        private val dataInterface: UsbInterface,
        private val input: UsbEndpoint,
        private val output: UsbEndpoint,
    ) : Closeable {
        private val incomingLine = StringBuilder()
        private val completedLines = ArrayDeque<String>()

        fun prepare() {
            val lineCoding = byteArrayOf(
                0x00, 0xC2.toByte(), 0x01, 0x00,
                0x00,
                0x00,
                0x08,
            )
            val result = connection.controlTransfer(
                UsbConstants.USB_TYPE_CLASS or UsbRecipientInterface or UsbConstants.USB_DIR_OUT,
                CdcSetLineCoding,
                0,
                controlInterface.id,
                lineCoding,
                lineCoding.size,
                UsbIoTimeoutMs,
            )
            require(result == lineCoding.size) { "Could not configure the X3 USB link" }
            drainInput()
        }

        suspend fun send(envelope: Envelope, timeoutMs: Long) {
            val encoded = EnvelopeCodec.encode(envelope)
            val line = buildString(CommandPrefix.length + encoded.size * 2 + 1) {
                append(CommandPrefix)
                encoded.forEach { append("%02x".format(it.toInt() and 0xff)) }
                append('\n')
            }.toByteArray(Charsets.US_ASCII)
            writeAll(line)
            awaitReply(envelope.messageId, timeoutMs)
            // The ESP32-C3 USB Serial/JTAG endpoint ACKs through its TX ISR. An
            // immediate host OUT after that ACK can arrive during the endpoint
            // turnaround and disappear even though the prior command completed.
            // Keep this at the request/ACK transport boundary so Begin, chunks,
            // Commit, and Abort all share the same pacing contract.
            delay(UsbReplyTurnaroundGuardMs)
        }

        private suspend fun awaitReply(messageId: UInt, timeoutMs: Long) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                val remaining = (deadline - System.currentTimeMillis()).coerceAtMost(UsbReadSliceMs).toInt()
                val line = readLine(remaining) ?: continue
                when (line) {
                    "USB_BOOK_ACK:$messageId" -> return
                    "USB_BOOK_NACK:$messageId" -> error("X3 rejected the USB book transfer")
                }
            }
            error("Timed out waiting for the X3 USB transfer")
        }

        private fun readLine(timeoutMs: Int): String? {
            completedLines.pollFirst()?.let { return it }
            val buffer = ByteArray(input.maxPacketSize.coerceAtLeast(64))
            val count = connection.bulkTransfer(input, buffer, buffer.size, timeoutMs)
            if (count <= 0) return null
            for (index in 0 until count) {
                when (val value = buffer[index].toInt() and 0xff) {
                    '\n'.code -> {
                        completedLines.addLast(incomingLine.toString().trimEnd('\r'))
                        incomingLine.clear()
                    }
                    else -> if (incomingLine.length < MaxLineBytes) incomingLine.append(value.toChar())
                }
            }
            return completedLines.pollFirst()
        }

        private fun writeAll(bytes: ByteArray) {
            var offset = 0
            while (offset < bytes.size) {
                val written = connection.bulkTransfer(
                    output,
                    bytes,
                    offset,
                    bytes.size - offset,
                    UsbIoTimeoutMs,
                )
                require(written > 0) { "USB write to X3 failed" }
                offset += written
            }
        }

        private fun drainInput() {
            val buffer = ByteArray(input.maxPacketSize.coerceAtLeast(64))
            incomingLine.clear()
            completedLines.clear()
            while (connection.bulkTransfer(input, buffer, buffer.size, 10) > 0) Unit
        }

        override fun close() {
            runCatching { connection.releaseInterface(dataInterface) }
            runCatching { connection.releaseInterface(controlInterface) }
            connection.close()
        }

        companion object {
            fun open(manager: UsbManager, device: UsbDevice): UsbSerialConnection {
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
                    .firstOrNull {
                        it.direction == UsbConstants.USB_DIR_IN &&
                            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    } ?: error("X3 USB input endpoint is missing")
                val output = (0 until data.endpointCount)
                    .map(data::getEndpoint)
                    .firstOrNull {
                        it.direction == UsbConstants.USB_DIR_OUT &&
                            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    } ?: error("X3 USB output endpoint is missing")
                val connection = manager.openDevice(device) ?: error("Could not open the X3 USB connection")
                require(connection.claimInterface(control, true) && connection.claimInterface(data, true)) {
                    connection.close()
                    "Could not claim the X3 USB interface"
                }
                return UsbSerialConnection(connection, control, data, input, output)
            }
        }
    }

    companion object {
        private const val EspressifVendorId = 0x303A
        private const val EspUsbJtagSerialProductId = 0x1001
        private const val PermissionAction = "com.xteink.companion.USB_BOOK_PERMISSION"
        private const val PermissionTimeoutMs = 30_000L
        private const val BeginTimeoutMs = 15_000L
        private const val ChunkTimeoutMs = 5_000L
        private const val CommitTimeoutMs = 90_000L
        private const val AbortTimeoutMs = 5_000L
        private const val UsbIoTimeoutMs = 5_000
        private const val UsbReadSliceMs = 250L
        private const val UsbReplyTurnaroundGuardMs = 20L
        private const val UsbRecipientInterface = 0x01
        private const val CdcSetLineCoding = 0x20
        private const val CommandPrefix = "CMD:USB_BOOK:"
        private const val MaxLineBytes = 4 * 1024
        // BLE can carry the protocol maximum directly. CDC hex expansion doubles
        // each byte, so retain ample RX-queue headroom across sustained frames.
        private const val UsbBookChunkBytes = 240
        private const val ProgressLogBytes = 64L * 1024L
        private const val LogTag = "XteinkUsbBook"
    }
}
