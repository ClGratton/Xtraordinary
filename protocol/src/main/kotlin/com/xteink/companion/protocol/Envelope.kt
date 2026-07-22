package com.xteink.companion.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

const val PROTOCOL_VERSION: UByte = 1u
const val MAX_PAYLOAD_BYTES: Int = 8 * 1024 * 1024

enum class MessageType(val wireValue: UByte) {
    Hello(0x01u),
    Capabilities(0x02u),
    GetStatus(0x03u),
    BeginScene(0x10u),
    SceneChunk(0x11u),
    CommitScene(0x12u),
    ActivateScene(0x13u),
    StartSession(0x20u),
    PauseSession(0x21u),
    ResumeSession(0x22u),
    StopSession(0x23u),
    PinSceneAndSleep(0x30u),
    Ack(0x80u),
    Nack(0x81u),
    ButtonEvent(0x82u),
    StatusChanged(0x83u),
    Error(0xFFu),
    ;

    companion object {
        fun fromWireValue(value: UByte): MessageType = entries.firstOrNull {
            it.wireValue == value
        } ?: throw ProtocolException("Unknown message type: $value")
    }
}
data class Envelope(
    val protocolVersion: UByte = PROTOCOL_VERSION,
    val messageType: MessageType,
    val flags: UShort = 0u,
    val messageId: UInt,
    val payload: ByteArray = byteArrayOf(),
)

class ProtocolException(message: String) : IllegalArgumentException(message)

object EnvelopeCodec {
    private val magic = byteArrayOf('X'.code.toByte(), '3'.code.toByte(), 'C'.code.toByte(), 'P'.code.toByte())
    const val HEADER_BYTES = 20

    fun encode(envelope: Envelope): ByteArray {
        require(envelope.payload.size <= MAX_PAYLOAD_BYTES) {
            "Payload exceeds $MAX_PAYLOAD_BYTES bytes"
        }

        return ByteBuffer.allocate(HEADER_BYTES + envelope.payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put(magic)
                put(envelope.protocolVersion.toByte())
                put(envelope.messageType.wireValue.toByte())
                putShort(envelope.flags.toShort())
                putInt(envelope.messageId.toInt())
                putInt(envelope.payload.size)
                putInt(crc32(envelope.payload).toInt())
                put(envelope.payload)
            }
            .array()
    }

    fun decode(bytes: ByteArray): Envelope {
        if (bytes.size < HEADER_BYTES) {
            throw ProtocolException("Envelope is shorter than the $HEADER_BYTES-byte header")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val actualMagic = ByteArray(magic.size).also(buffer::get)
        if (!actualMagic.contentEquals(magic)) {
            throw ProtocolException("Invalid envelope magic")
        }

        val protocolVersion = buffer.get().toUByte()
        val messageType = MessageType.fromWireValue(buffer.get().toUByte())
        val flags = buffer.short.toUShort()
        val messageId = buffer.int.toUInt()
        val payloadLength = buffer.int
        val expectedCrc = buffer.int.toUInt()

        if (payloadLength !in 0..MAX_PAYLOAD_BYTES) {
            throw ProtocolException("Invalid payload length: $payloadLength")
        }
        if (buffer.remaining() != payloadLength) {
            throw ProtocolException(
                "Payload length mismatch: header says $payloadLength, envelope contains ${buffer.remaining()}",
            )
        }

        val payload = ByteArray(payloadLength).also(buffer::get)
        val actualCrc = crc32(payload)
        if (actualCrc != expectedCrc) {
            throw ProtocolException("Payload CRC mismatch")
        }

        return Envelope(
            protocolVersion = protocolVersion,
            messageType = messageType,
            flags = flags,
            messageId = messageId,
            payload = payload,
        )
    }

    fun crc32(bytes: ByteArray): UInt = CRC32().run {
        update(bytes)
        value.toUInt()
    }
}
