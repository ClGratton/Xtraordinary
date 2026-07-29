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
)

enum class DeviceActivity(val wireValue: UByte) {
    Awake(0u),
    Reading(1u),
    Focus(2u),
    Transfer(3u),
    Sleeping(4u),
    Booting(5u),
    ;

    companion object {
        fun fromWireValue(value: UByte): DeviceActivity =
            entries.firstOrNull { it.wireValue == value }
                ?: throw ProtocolException("Unknown device activity: $value")
    }
}

enum class DeviceSyncMode(val wireValue: UByte) {
    Fast(0u),
    Slow(1u),
    Off(2u),
    ;

    companion object {
        fun fromWireValue(value: UByte): DeviceSyncMode =
            entries.firstOrNull { it.wireValue == value }
                ?: throw ProtocolException("Unknown device sync mode: $value")
    }
}

data class PowerSyncConfig(
    val normalPollSeconds: Int = 15,
    val slowPollSeconds: Int = 10 * 60,
    val sleepTimeoutMinutes: Int = 5,
) {
    init {
        require(normalPollSeconds in 10..120) { "Normal sync interval must be between 10 and 120 seconds" }
        require(slowPollSeconds in 60..30 * 60) { "Reading sync interval must be between 1 and 30 minutes" }
        require(sleepTimeoutMinutes in 1..5) { "Sleep timeout must be between 1 and 5 minutes" }
    }
}

data class DeviceStatus(
    val revision: UInt,
    val activity: DeviceActivity,
    val syncMode: DeviceSyncMode,
    val powerConfig: PowerSyncConfig,
)

data class SessionStart(
    val deadlineEpochSeconds: Long,
    val durationSeconds: Int,
    val title: String,
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
    }

    fun decodeCapabilities(bytes: ByteArray): DeviceCapabilities = reader(bytes) {
        DeviceCapabilities(utf8(24), utf8(48), int.toUInt(), get().toInt() != 0)
    }

    fun encodePowerSyncConfig(value: PowerSyncConfig): ByteArray = writer(9) {
        putInt(value.normalPollSeconds)
        putInt(value.slowPollSeconds)
        put(value.sleepTimeoutMinutes.toByte())
    }

    fun decodePowerSyncConfig(bytes: ByteArray): PowerSyncConfig = reader(bytes) {
        PowerSyncConfig(
            normalPollSeconds = int,
            slowPollSeconds = int,
            sleepTimeoutMinutes = get().toInt() and 0xff,
        )
    }

    fun encodeDeviceStatus(value: DeviceStatus): ByteArray = writer(15) {
        putInt(value.revision.toInt())
        put(value.activity.wireValue.toByte())
        put(value.syncMode.wireValue.toByte())
        putInt(value.powerConfig.normalPollSeconds)
        putInt(value.powerConfig.slowPollSeconds)
        put(value.powerConfig.sleepTimeoutMinutes.toByte())
    }

    fun decodeDeviceStatus(bytes: ByteArray): DeviceStatus = reader(bytes) {
        require(bytes.size == 15) { "Device status payload must be 15 bytes" }
        DeviceStatus(
            revision = int.toUInt(),
            activity = DeviceActivity.fromWireValue(get().toUByte()),
            syncMode = DeviceSyncMode.fromWireValue(get().toUByte()),
            powerConfig = PowerSyncConfig(
                normalPollSeconds = int,
                slowPollSeconds = int,
                sleepTimeoutMinutes = get().toInt() and 0xff,
            ),
        )
    }

    fun encodeStatusConfirmation(revision: UInt): ByteArray =
        ByteBuffer.allocate(4).little().putInt(revision.toInt()).array()

    fun decodeStatusConfirmation(bytes: ByteArray): UInt {
        require(bytes.size == 4) { "Status confirmation payload must be 4 bytes" }
        return ByteBuffer.wrap(bytes).little().int.toUInt()
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
