package com.xteink.companion.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val XTEINK_SERVICE_UUID = "7e400001-b5a3-f393-e0a9-e50e24dcca9e"
const val XTEINK_CONTROL_UUID = "7e400002-b5a3-f393-e0a9-e50e24dcca9e"
const val XTEINK_DATA_UUID = "7e400003-b5a3-f393-e0a9-e50e24dcca9e"
const val XTEINK_EVENTS_UUID = "7e400004-b5a3-f393-e0a9-e50e24dcca9e"
const val XTEINK_STATUS_UUID = "7e400005-b5a3-f393-e0a9-e50e24dcca9e"
const val MAX_WIRE_PATH_BYTES = 512
// 20-byte envelope + 4-byte offset + 216 bytes = 240, safely inside a
// negotiated 247-byte ATT MTU (244-byte value).
const val FIRMWARE_CHUNK_BYTES = 216

data class DeviceCapabilities(
    val model: String,
    val firmwareVersion: String,
    val libraryRevision: UInt,
    val supportsFirmwareUpdate: Boolean,
    val ticketPresent: Boolean = false,
    val supportsReaderPolicy: Boolean = false,
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
    val slowIntervalMs: Int,
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
    val gate: String,
    val terminal: String,
    val seat: String,
    val passenger: String,
    val boardingGroup: String,
    val barcodePayload: String,
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

    fun encodeRadioPolicy(value: RadioPolicy): ByteArray = writer(6) {
        putShort(value.fastWindowMinutes.coerceIn(1, 30).toShort())
        putShort(value.slowIntervalMs.coerceIn(500, 4_000).toShort())
        putShort(value.sleepAfterMinutes.coerceIn(2, 60).toShort())
    }

    fun encodeReaderPolicy(fullRefreshPages: Int): ByteArray =
        ByteBuffer.allocate(2).little().putShort(fullRefreshPages.toShort()).array()

    fun encodeBoardingPass(value: BoardingPassPayload): ByteArray = writer(384) {
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
        put(if (value.supportsReaderPolicy) 1 else 0)
    }

    fun decodeCapabilities(bytes: ByteArray): DeviceCapabilities = reader(bytes) {
        DeviceCapabilities(
            model = utf8(24),
            firmwareVersion = utf8(48),
            libraryRevision = int.toUInt(),
            supportsFirmwareUpdate = get().toInt() != 0,
            ticketPresent = remaining() > 0 && get().toInt() != 0,
            supportsReaderPolicy = remaining() > 0 && get().toInt() != 0,
        )
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
