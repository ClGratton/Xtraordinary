package com.xteink.companion.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadCodecTest {
    @Test
    fun sessionRoundTrips() {
        val expected = SessionStart(1_800_000_000, 1_500, "Deep work")
        assertEquals(expected, PayloadCodec.decodeSessionStart(PayloadCodec.encodeSessionStart(expected)))
    }

    @Test
    fun libraryPageRoundTrips() {
        val expected = LibraryPagePayload(
            revision = 9u,
            pageIndex = 2,
            isLastPage = true,
            entries = listOf(LibraryEntry("/Books/Dune.epub", 42_000, 1_700_000_000)),
        )
        assertEquals(expected, PayloadCodec.decodeLibraryPage(PayloadCodec.encodeLibraryPage(expected)))
    }

    @Test
    fun firmwareBeginRoundTrips() {
        val expected = FirmwareBegin("X3", "v0.2.0", 4_000_000, ByteArray(32) { it.toByte() })
        val actual = PayloadCodec.decodeFirmwareBegin(PayloadCodec.encodeFirmwareBegin(expected))
        assertEquals(expected.model, actual.model)
        assertEquals(expected.version, actual.version)
        assertEquals(expected.sizeBytes, actual.sizeBytes)
        assertTrue(expected.sha256.contentEquals(actual.sha256))
    }
}
