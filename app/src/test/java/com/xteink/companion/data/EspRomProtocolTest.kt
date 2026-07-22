package com.xteink.companion.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EspRomProtocolTest {
    @Test
    fun slipRoundTripEscapesProtocolDelimiters() {
        val packet = byteArrayOf(0x00, 0xC0.toByte(), 0xDB.toByte(), 0x55)
        val encoded = EspRomProtocol.slipEncode(packet)

        assertEquals(0xC0, encoded.first().toInt() and 0xFF)
        assertEquals(0xC0, encoded.last().toInt() and 0xFF)
        assertArrayEquals(packet, EspRomProtocol.slipDecode(encoded.copyOfRange(1, encoded.lastIndex)))
    }

    @Test
    fun flashBeginTargetsFirstX3ApplicationSlot() {
        val size = 5_416_128
        val payload = ByteBuffer.wrap(EspRomProtocol.flashBeginPayload(size)).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(size, payload.int)
        assertEquals((size + 0x3FF) / 0x400, payload.int)
        assertEquals(0x400, payload.int)
        assertEquals(0x10000, payload.int)
        assertEquals(0, payload.int)
    }

    @Test
    fun requestAndResponseUseEspRomWireHeaders() {
        val request = EspRomProtocol.request(EspRomProtocol.Sync, byteArrayOf(1, 2, 3))
        val decodedRequest = EspRomProtocol.slipDecode(request.copyOfRange(1, request.lastIndex))
        assertArrayEquals(
            byteArrayOf(0, EspRomProtocol.Sync.toByte(), 3, 0, 0, 0, 0, 0, 1, 2, 3),
            decodedRequest,
        )

        val rawResponse = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .put(1)
            .put(EspRomProtocol.Sync.toByte())
            .putShort(4)
            .putInt(0x1234)
            .put(byteArrayOf(0, 0, 0, 0))
            .array()
        val response = EspRomProtocol.parseResponse(rawResponse)
        assertEquals(EspRomProtocol.Sync, response.operation)
        assertEquals(0x1234, response.value)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), response.data)
    }
}
