package com.xteink.companion.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnvelopeCodecTest {
    @Test
    fun roundTripPreservesHeaderAndPayload() {
        val original = Envelope(
            messageType = MessageType.BeginScene,
            flags = 0x0003u,
            messageId = 0xF1234567u,
            payload = "scene-01".encodeToByteArray(),
        )

        val decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(original))

        assertEquals(original.protocolVersion, decoded.protocolVersion)
        assertEquals(original.messageType, decoded.messageType)
        assertEquals(original.flags, decoded.flags)
        assertEquals(original.messageId, decoded.messageId)
        assertArrayEquals(original.payload, decoded.payload)
    }

    @Test
    fun rejectsCorruptedPayload() {
        val encoded = EnvelopeCodec.encode(
            Envelope(
                messageType = MessageType.SceneChunk,
                messageId = 7u,
                payload = byteArrayOf(1, 2, 3, 4),
            ),
        )
        encoded[encoded.lastIndex] = 9

        assertThrows(ProtocolException::class.java) {
            EnvelopeCodec.decode(encoded)
        }
    }

    @Test
    fun rejectsLengthMismatch() {
        val encoded = EnvelopeCodec.encode(
            Envelope(
                messageType = MessageType.Hello,
                messageId = 1u,
                payload = byteArrayOf(1),
            ),
        ).copyOf(EnvelopeCodec.HEADER_BYTES)

        assertThrows(ProtocolException::class.java) {
            EnvelopeCodec.decode(encoded)
        }
    }
}
