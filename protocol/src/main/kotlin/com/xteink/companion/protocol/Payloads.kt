package com.xteink.companion.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction

const val XTEINK_SERVICE_UUID = "7e400001-b5a3-f393-e0a9-e50e24dcca9e"
const val XTEINK_CONTROL_UUID = "7e400002-b5a3-f393-e0a9-e50e24dcca9e"
const val XTEINK_DATA_UUID = "7e400003-b5a3-f393-e0a9-e50e24dcca9e"
const val XTEINK_EVENTS_UUID = "7e400004-b5a3-f393-e0a9-e50e24dcca9e"
const val XTEINK_STATUS_UUID = "7e400005-b5a3-f393-e0a9-e50e24dcca9e"
const val MAX_WIRE_PATH_BYTES = 512
// 20-byte envelope + 4-byte offset + 488 bytes = the firmware's 512-byte packet limit. Android 14 and newer
// request a 517-byte ATT MTU, while the client selects a smaller chunk when a
// peer negotiates less. The firmware packet limit remains 512 bytes.
const val FIRMWARE_CHUNK_BYTES = 216
const val BOOK_UPLOAD_CHUNK_BYTES = 488
const val TICKET_BARCODE_CHUNK_BYTES = 488

data class DeviceCapabilities(
    val model: String,
    val firmwareVersion: String,
    val libraryRevision: UInt,
    val supportsFirmwareUpdate: Boolean,
    val ticketPresent: Boolean = false,
    val supportsReaderPolicy: Boolean = false,
    val readerPolicyVersion: Int = if (supportsReaderPolicy) 1 else 0,
    val radioPolicyVersion: Int = 0,
    val ticketPayloadVersion: Int = 0,
)

enum class DeviceActivity(val wireValue: UByte) {
    Awake(0x01u),
    Reading(0x02u),
    ;

    companion object {
        fun fromWireValue(value: UByte): DeviceActivity = entries.firstOrNull {
            it.wireValue == value
        } ?: throw ProtocolException("Unknown device activity: $value")
    }
}

enum class DeviceSyncMode(val wireValue: UByte) {
    Fast(0x01u),
    Slow(0x02u),
    ;

    companion object {
        fun fromWireValue(value: UByte): DeviceSyncMode = entries.firstOrNull {
            it.wireValue == value
        } ?: throw ProtocolException("Unknown device sync mode: $value")
    }
}

data class DeviceStatus(
    val revision: UInt,
    val activity: DeviceActivity,
    val syncMode: DeviceSyncMode,
    val batteryPercentage: Int? = null,
    val charging: Boolean? = null,
)

data class SessionStart(
    val deadlineEpochSeconds: Long,
    val durationSeconds: Int,
    val title: String,
)

data class RadioPolicy(
    val fastWindowMinutes: Int,
    val standbyIntervalSeconds: Int,
    val connectedIntervalMs: Int,
    val sleepAfterMinutes: Int,
)

enum class TicketDisplayMode(val wireValue: UByte) {
    Static(0x00u),
    Live(0x01u),
    ;

    companion object {
        fun fromWireValue(value: UByte): TicketDisplayMode = entries.firstOrNull {
            it.wireValue == value
        } ?: throw ProtocolException("Unknown ticket display mode: $value")
    }
}

data class BoardingPassPayload(
    val mode: TicketDisplayMode,
    val origin: String,
    val destination: String,
    val flight: String,
    val status: String,
    val departureTime: String,
    val arrivalTime: String = "",
    val delayMinutes: Int? = null,
    val gate: String,
    val terminal: String,
    val seat: String,
    val passenger: String,
    val boardingGroup: String,
    val barcodePayload: String,
    val barcodeFormat: String = "QR",
)

data class LibraryEntry(
    val path: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
)

data class LibraryPagePayload(
    val revision: UInt,
    val pageIndex: Int,
    val isLastPage: Boolean,
    val entries: List<LibraryEntry>,
)

data class ReadingPageSamplePayload(
    val elapsedMs: Long,
    val words: Int,
    val pageNumber: Int,
)

data class ReadingStatsChunkPayload(
    val sessionId: UInt,
    val startedEpochSeconds: Long,
    val endedEpochSeconds: Long,
    val totalPages: Int,
    val startIndex: Int,
    val isLastChunk: Boolean,
    val hasWordCounts: Boolean,
    val title: String,
    val samples: List<ReadingPageSamplePayload>,
)

data class FirmwareBegin(
    val model: String,
    val version: String,
    val sizeBytes: Long,
    val sha256: ByteArray,
) {
    init {
        require(sha256.size == 32) { "Firmware SHA-256 must contain 32 bytes" }
    }
}

data class BookUploadBegin(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: ByteArray,
) {
    init {
        require(sizeBytes in 1..(128L * 1024L * 1024L)) { "Book size is outside the supported range" }
        require(sha256.size == 32) { "Book SHA-256 must contain 32 bytes" }
    }
}

object PayloadCodec {
    fun encodeHello(supportsRevisionedDeviceStatus: Boolean): ByteArray = byteArrayOf(
        if (supportsRevisionedDeviceStatus) 0x01 else 0x00,
    )

    fun encodeDeviceStatus(value: DeviceStatus): ByteArray = writer(8) {
        putInt(value.revision.toInt())
        put(value.activity.wireValue.toByte())
        put(value.syncMode.wireValue.toByte())
        if (value.batteryPercentage != null && value.charging != null) {
            put(value.batteryPercentage.coerceIn(0, 100).toByte())
            put(if (value.charging) 1 else 0)
        }
    }

    fun decodeDeviceStatus(bytes: ByteArray): DeviceStatus {
        require(bytes.size == 6 || bytes.size == 8) { "Device status payload must contain 6 or 8 bytes" }
        return reader(bytes) {
            DeviceStatus(
                revision = int.toUInt(),
                activity = DeviceActivity.fromWireValue(get().toUByte()),
                syncMode = DeviceSyncMode.fromWireValue(get().toUByte()),
                batteryPercentage = if (remaining() >= 2) get().toInt() and 0xff else null,
                charging = if (remaining() >= 1) get().toInt() != 0 else null,
            )
        }
    }

    fun encodeStatusAck(revision: UInt): ByteArray =
        ByteBuffer.allocate(4).little().putInt(revision.toInt()).array()

    fun encodeClock(epochSeconds: Long): ByteArray =
        ByteBuffer.allocate(8).little().putLong(epochSeconds).array()

    fun encodeReadingStatsAck(sessionId: UInt): ByteArray =
        ByteBuffer.allocate(4).little().putInt(sessionId.toInt()).array()

    fun encodeRadioPolicy(value: RadioPolicy): ByteArray = writer(8) {
        putShort(value.fastWindowMinutes.coerceIn(1, 30).toShort())
        putShort(value.standbyIntervalSeconds.coerceIn(10, 300).toShort())
        putShort(value.connectedIntervalMs.coerceIn(500, 4_000).toShort())
        putShort(value.sleepAfterMinutes.coerceIn(2, 60).toShort())
    }

    fun encodeReaderPolicy(fullRefreshPages: Int, powerButtonHoldMs: Int? = null): ByteArray =
        ByteBuffer.allocate(if (powerButtonHoldMs == null) 2 else 4).little().apply {
            putShort(fullRefreshPages.toShort())
            powerButtonHoldMs?.let { putShort(it.toShort()) }
        }.array()

    fun encodeInteractiveLease(seconds: Int): ByteArray =
        ByteBuffer.allocate(2).little().putShort(seconds.coerceIn(2, 120).toShort()).array()

    fun encodeTicketBarcodeBegin(sizeBytes: Int): ByteArray {
        require(sizeBytes in 1..(64 * 1024)) { "Ticket barcode image is outside the supported range" }
        return ByteBuffer.allocate(4).little().putInt(sizeBytes).array()
    }

    fun encodeTicketBarcodeChunk(offset: Int, data: ByteArray): ByteArray = writer(4 + data.size) {
        require(offset >= 0) { "Ticket barcode offset must be non-negative" }
        require(data.isNotEmpty() && data.size <= TICKET_BARCODE_CHUNK_BYTES) { "Ticket barcode chunk is invalid" }
        putInt(offset)
        put(data)
    }

    fun encodeBoardingPass(value: BoardingPassPayload, payloadVersion: Int = 1): ByteArray = writer(496) {
        put(value.mode.wireValue.toByte())
        putUtf8(value.origin, 3)
        putUtf8(value.destination, 3)
        putUtf8(value.flight, 16)
        putUtf8(value.status, 24)
        putUtf8(value.departureTime, 16)
        putUtf8(value.gate, 8)
        putUtf8(value.terminal, 8)
        putUtf8(value.seat, 8)
        putUtf8(value.passenger, 40)
        putUtf8(value.boardingGroup, 24)
        putUtf8(value.barcodePayload, 256)
        putUtf8(value.barcodeFormat, 16)
        if (payloadVersion >= 2) {
            putUtf8(value.arrivalTime, 16)
            putShort((value.delayMinutes ?: Short.MIN_VALUE.toInt()).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
    }

    fun decodeBoardingPass(bytes: ByteArray): BoardingPassPayload = reader(bytes) {
        BoardingPassPayload(
            mode = TicketDisplayMode.fromWireValue(get().toUByte()),
            origin = utf8(3),
            destination = utf8(3),
            flight = utf8(16),
            status = utf8(24),
            departureTime = utf8(16),
            gate = utf8(8),
            terminal = utf8(8),
            seat = utf8(8),
            passenger = utf8(40),
            boardingGroup = utf8(24),
            barcodePayload = utf8(256),
            barcodeFormat = if (remaining() > 0) utf8(16) else "QR",
            arrivalTime = if (remaining() > 0) utf8(16) else "",
            delayMinutes = if (remaining() >= 2) short.toInt().takeUnless { it == Short.MIN_VALUE.toInt() } else null,
        )
    }

    fun encodeSessionStart(value: SessionStart): ByteArray = writer(18 + value.title.length) {
        putLong(value.deadlineEpochSeconds)
        putInt(value.durationSeconds)
        putUtf8(value.title, 160)
    }

    fun decodeSessionStart(bytes: ByteArray): SessionStart = reader(bytes) {
        SessionStart(long, int, utf8(160))
    }

    fun encodeCapabilities(value: DeviceCapabilities): ByteArray = writer(32) {
        putUtf8(value.model, 24)
        putUtf8(value.firmwareVersion, 48)
        putInt(value.libraryRevision.toInt())
        put(if (value.supportsFirmwareUpdate) 1 else 0)
        put(if (value.ticketPresent) 1 else 0)
        put(value.readerPolicyVersion.coerceIn(0, 255).toByte())
        put(value.radioPolicyVersion.coerceIn(0, 255).toByte())
        put(value.ticketPayloadVersion.coerceIn(0, 255).toByte())
    }

    fun decodeCapabilities(bytes: ByteArray): DeviceCapabilities {
        // Current X3 firmware uses length-prefixed UTF-8 fields. Older companion
        // firmware used the original fixed 24/48-byte fields; identify that
        // layout from its impossible first length and decode it explicitly.
        val currentLength = if (bytes.size >= 2) {
            ByteBuffer.wrap(bytes).little().short.toInt() and 0xffff
        } else {
            Int.MAX_VALUE
        }
        return if (currentLength <= 24 && bytes.size >= 2 + currentLength + 2) {
            reader(bytes) {
                decodeCapabilitiesFields(utf8(24), utf8(48))
            }
        } else {
            decodeLegacyCapabilities(bytes)
        }
    }

    private fun ByteBuffer.decodeCapabilitiesFields(model: String, firmwareVersion: String): DeviceCapabilities {
        val libraryRevision = int.toUInt()
        val supportsFirmwareUpdate = get().toInt() != 0
        val ticketPresent = remaining() > 0 && get().toInt() != 0
        val readerPolicyVersion = if (remaining() > 0) get().toInt() and 0xff else 0
        val radioPolicyVersion = if (remaining() > 0) get().toInt() and 0xff else 0
        val ticketPayloadVersion = if (remaining() > 0) get().toInt() and 0xff else 0
        return DeviceCapabilities(
            model, firmwareVersion, libraryRevision, supportsFirmwareUpdate, ticketPresent,
            readerPolicyVersion > 0, readerPolicyVersion, radioPolicyVersion, ticketPayloadVersion,
        )
    }

    private fun decodeLegacyCapabilities(bytes: ByteArray): DeviceCapabilities {
        require(bytes.size >= 24 + 48 + 5) { "Legacy capabilities payload is truncated" }
        return ByteBuffer.wrap(bytes).little().run {
            val model = fixedUtf8(24)
            val firmwareVersion = fixedUtf8(48)
            decodeCapabilitiesFields(model, firmwareVersion)
        }
    }

    private fun ByteBuffer.fixedUtf8(width: Int): String {
        val field = ByteArray(width).also(::get)
        val end = field.indexOf(0).takeIf { it >= 0 } ?: field.size
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(field, 0, end)).toString()
    }

    fun encodeLibraryPage(value: LibraryPagePayload): ByteArray = writer(64 + value.entries.sumOf { it.path.length }) {
        putInt(value.revision.toInt())
        putShort(value.pageIndex.toShort())
        put(if (value.isLastPage) 1 else 0)
        putShort(value.entries.size.toShort())
        value.entries.forEach {
            putUtf8(it.path, MAX_WIRE_PATH_BYTES)
            putLong(it.sizeBytes)
            putLong(it.modifiedEpochSeconds)
        }
    }

    fun decodeLibraryPage(bytes: ByteArray): LibraryPagePayload = reader(bytes) {
        val revision = int.toUInt()
        val pageIndex = short.toInt() and 0xffff
        val last = get().toInt() != 0
        val count = short.toInt() and 0xffff
        require(count <= 128) { "Library page entry count is not bounded" }
        LibraryPagePayload(
            revision,
            pageIndex,
            last,
            List(count) { LibraryEntry(utf8(MAX_WIRE_PATH_BYTES), long, long) },
        )
    }

    fun decodeReadingStatsChunk(bytes: ByteArray): ReadingStatsChunkPayload = reader(bytes) {
        val sessionId = int.toUInt()
        val started = long
        val ended = long
        val totalPages = short.toInt() and 0xffff
        val startIndex = short.toInt() and 0xffff
        val last = get().toInt() != 0
        val hasWords = get().toInt() != 0
        val count = short.toInt() and 0xffff
        require(totalPages <= 2048 && count <= 32 && startIndex + count <= totalPages) {
            "Reading stats chunk is not bounded"
        }
        val title = utf8(96)
        ReadingStatsChunkPayload(
            sessionId = sessionId,
            startedEpochSeconds = started,
            endedEpochSeconds = ended,
            totalPages = totalPages,
            startIndex = startIndex,
            isLastChunk = last,
            hasWordCounts = hasWords,
            title = title,
            samples = List(count) {
                ReadingPageSamplePayload(
                    elapsedMs = int.toLong() and 0xffffffffL,
                    words = short.toInt() and 0xffff,
                    pageNumber = short.toInt() and 0xffff,
                )
            },
        )
    }

    fun encodeDeleteLibraryEntries(revision: UInt, paths: List<String>): ByteArray = writer(8 + paths.sumOf { it.length }) {
        require(paths.size <= 64) { "Too many library paths" }
        putInt(revision.toInt())
        putShort(paths.size.toShort())
        paths.forEach { putUtf8(it, MAX_WIRE_PATH_BYTES) }
    }

    fun decodeDeleteLibraryEntries(bytes: ByteArray): Pair<UInt, List<String>> = reader(bytes) {
        val revision = int.toUInt()
        val count = short.toInt() and 0xffff
        require(count <= 64) { "Too many library paths" }
        revision to List(count) { utf8(MAX_WIRE_PATH_BYTES) }
    }

    fun encodeBookUploadBegin(value: BookUploadBegin): ByteArray = writer(224) {
        putUtf8(value.fileName, 180)
        putLong(value.sizeBytes)
        put(value.sha256)
    }

    fun decodeBookUploadBegin(bytes: ByteArray): BookUploadBegin = reader(bytes) {
        BookUploadBegin(utf8(180), long, ByteArray(32).also(::get))
    }

    fun encodeBookUploadChunk(offset: Int, data: ByteArray): ByteArray = writer(4 + data.size) {
        require(data.size <= BOOK_UPLOAD_CHUNK_BYTES) { "Book chunk is too large" }
        putInt(offset)
        put(data)
    }

    fun encodeFirmwareBegin(value: FirmwareBegin): ByteArray = writer(96) {
        putUtf8(value.model, 24)
        putUtf8(value.version, 48)
        putLong(value.sizeBytes)
        put(value.sha256)
    }

    fun decodeFirmwareBegin(bytes: ByteArray): FirmwareBegin = reader(bytes) {
        FirmwareBegin(utf8(24), utf8(48), long, ByteArray(32).also(::get))
    }

    fun encodeFirmwareChunk(offset: Int, data: ByteArray): ByteArray = writer(4 + data.size) {
        require(data.size <= FIRMWARE_CHUNK_BYTES) { "Firmware chunk is too large" }
        putInt(offset)
        put(data)
    }

    fun decodeFirmwareChunk(bytes: ByteArray): Pair<Int, ByteArray> = reader(bytes) {
        val offset = int
        offset to ByteArray(remaining()).also(::get)
    }

    fun encodeAck(messageId: UInt): ByteArray = ByteBuffer.allocate(4).little().putInt(messageId.toInt()).array()
    fun decodeAck(bytes: ByteArray): UInt {
        require(bytes.size >= 4) { "ACK payload is truncated" }
        return ByteBuffer.wrap(bytes).little().int.toUInt()
    }

    private inline fun writer(capacityHint: Int, block: ByteBuffer.() -> Unit): ByteArray {
        val buffer = ByteBuffer.allocate((capacityHint * 4 + 4096).coerceAtMost(MAX_PAYLOAD_BYTES)).little()
        buffer.block()
        return buffer.array().copyOf(buffer.position())
    }

    private inline fun <T> reader(bytes: ByteArray, block: ByteBuffer.() -> T): T = try {
        ByteBuffer.wrap(bytes).little().block()
    } catch (error: java.nio.BufferUnderflowException) {
        throw ProtocolException("Payload is truncated")
    }

    private fun ByteBuffer.putUtf8(value: String, maxBytes: Int) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= maxBytes) { "UTF-8 field exceeds $maxBytes bytes" }
        putShort(encoded.size.toShort())
        put(encoded)
    }

    private fun ByteBuffer.utf8(maxBytes: Int): String {
        val size = short.toInt() and 0xffff
        require(size <= maxBytes && size <= remaining()) { "Invalid UTF-8 field length" }
        return ByteArray(size).also(::get).toString(Charsets.UTF_8)
    }

    private fun ByteBuffer.little(): ByteBuffer = order(ByteOrder.LITTLE_ENDIAN)
}
