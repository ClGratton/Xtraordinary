package com.xteink.companion.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object EspRomProtocol {
    const val FlashOffset = 0x10000
    const val MaxAppSize = 0x640000
    const val FlashBlockSize = 0x400

    const val Sync = 0x08
    const val FlashBegin = 0x02
    const val FlashData = 0x03
    const val FlashEnd = 0x04
    const val FlashMd5 = 0x13

    private const val DirectionRequest = 0x00
    private const val DirectionResponse = 0x01
    private const val ChecksumMagic = 0xEF
    private const val SlipEnd = 0xC0
    private const val SlipEsc = 0xDB
    private const val SlipEscEnd = 0xDC
    private const val SlipEscEsc = 0xDD

    data class Response(val operation: Int, val value: Long, val data: ByteArray)

    fun syncPayload(): ByteArray = byteArrayOf(0x07, 0x07, 0x12, 0x20) + ByteArray(32) { 0x55 }

    fun flashBeginPayload(size: Int): ByteArray {
        require(size in 1..MaxAppSize) { "Firmware image does not fit the X3 app partition" }
        val blocks = (size + FlashBlockSize - 1) / FlashBlockSize
        return littleEndianInts(size, blocks, FlashBlockSize, FlashOffset, 0)
    }

    fun flashDataPayload(block: ByteArray, sequence: Int): ByteArray {
        require(block.size == FlashBlockSize)
        return littleEndianInts(block.size, sequence, 0, 0) + block
    }

    fun flashEndPayload(): ByteArray = littleEndianInts(1)

    fun flashMd5Payload(size: Int): ByteArray = littleEndianInts(FlashOffset, size, 0, 0)

    fun request(operation: Int, data: ByteArray, checksum: Int = 0): ByteArray {
        require(data.size <= 0xFFFF)
        val packet = ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(DirectionRequest.toByte())
            .put(operation.toByte())
            .putShort(data.size.toShort())
            .putInt(checksum)
            .put(data)
            .array()
        return slipEncode(packet)
    }

    fun parseResponse(packet: ByteArray): Response {
        require(packet.size >= 8) { "Short response from X3 bootloader" }
        val header = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        require(header.get().toInt() and 0xFF == DirectionResponse) { "Unexpected X3 bootloader packet" }
        val operation = header.get().toInt() and 0xFF
        val length = header.short.toInt() and 0xFFFF
        val value = header.int.toLong() and 0xFFFF_FFFFL
        require(packet.size >= 8 + length) { "Incomplete response from X3 bootloader" }
        return Response(operation, value, packet.copyOfRange(8, 8 + length))
    }

    fun checksum(data: ByteArray): Int = data.fold(ChecksumMagic) { value, byte ->
        value xor (byte.toInt() and 0xFF)
    }

    fun slipEncode(packet: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(packet.size + 2)
        output.write(SlipEnd)
        packet.forEach { byte ->
            when (byte.toInt() and 0xFF) {
                SlipEnd -> {
                    output.write(SlipEsc)
                    output.write(SlipEscEnd)
                }
                SlipEsc -> {
                    output.write(SlipEsc)
                    output.write(SlipEscEsc)
                }
                else -> output.write(byte.toInt() and 0xFF)
            }
        }
        output.write(SlipEnd)
        return output.toByteArray()
    }

    fun slipDecode(frame: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(frame.size)
        var escaped = false
        frame.forEach { byte ->
            val value = byte.toInt() and 0xFF
            if (escaped) {
                output.write(
                    when (value) {
                        SlipEscEnd -> SlipEnd
                        SlipEscEsc -> SlipEsc
                        else -> error("Invalid SLIP escape from X3 bootloader")
                    },
                )
                escaped = false
            } else if (value == SlipEsc) {
                escaped = true
            } else {
                output.write(value)
            }
        }
        require(!escaped) { "Incomplete SLIP escape from X3 bootloader" }
        return output.toByteArray()
    }

    private fun littleEndianInts(vararg values: Int): ByteArray =
        ByteBuffer.allocate(values.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putInt)
        }.array()
}
