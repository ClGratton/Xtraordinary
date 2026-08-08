package com.xteink.companion.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadCodecTest {
    @Test
    fun deviceStatusRoundTripsWithExactRevision() {
        val expected = DeviceStatus(
            revision = 42u,
            activity = DeviceActivity.Reading,
            syncMode = DeviceSyncMode.Slow,
            batteryPercentage = 73,
            charging = true,
        )

        assertEquals(expected, PayloadCodec.decodeDeviceStatus(PayloadCodec.encodeDeviceStatus(expected)))
        assertEquals(42u, PayloadCodec.decodeAck(PayloadCodec.encodeStatusAck(expected.revision)))
    }

    @Test
    fun legacyDeviceStatusWithoutBatteryStillDecodes() {
        val legacy = byteArrayOf(42, 0, 0, 0, 1, 1)
        val decoded = PayloadCodec.decodeDeviceStatus(legacy)

        assertEquals(42u, decoded.revision)
        assertEquals(null, decoded.batteryPercentage)
        assertEquals(null, decoded.charging)
    }

    @Test
    fun helloAdvertisesRevisionedStatusSupport() {
        assertTrue(PayloadCodec.encodeHello(supportsRevisionedDeviceStatus = true).contentEquals(byteArrayOf(0x01)))
        assertTrue(PayloadCodec.encodeHello(supportsRevisionedDeviceStatus = false).contentEquals(byteArrayOf(0x00)))
    }

    @Test
    fun readerRefreshPolicyUsesLittleEndianPageCount() {
        assertTrue(PayloadCodec.encodeReaderPolicy(15).contentEquals(byteArrayOf(0x0f, 0x00)))
        assertEquals(MessageType.SetReaderPolicy, MessageType.fromWireValue(0x34u))
    }

    @Test
    fun boardingPassRoundTripsWithRealBarcodePayload() {
        val expected = BoardingPassPayload(
            mode = TicketDisplayMode.Live,
            origin = "FCO",
            destination = "JFK",
            flight = "AZ 610",
            status = "Boarding",
            departureTime = "14:50",
            gate = "E31",
            terminal = "3",
            seat = "14A",
            passenger = "CLAUDIO A",
            boardingGroup = "Group 3",
            barcodePayload = "M1GRATTON/CLAUDIO EABC123 FCOJFKAZ 0610 210Y014A0001 100",
        )

        assertEquals(expected, PayloadCodec.decodeBoardingPass(PayloadCodec.encodeBoardingPass(expected)))
    }

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
    fun readingStatsChunkPreservesPageTimingAndWords() {
        val title = "The Left Hand of Darkness".encodeToByteArray()
        val bytes = ByteBuffer.allocate(30 + title.size + 16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(71)
            putLong(1_800_000_000)
            putLong(1_800_001_200)
            putShort(2)
            putShort(0)
            put(1)
            put(1)
            putShort(2)
            putShort(title.size.toShort())
            put(title)
            putInt(45_000)
            putShort(180)
            putShort(12)
            putInt(60_000)
            putShort(220)
            putShort(13)
        }.array()

        val chunk = PayloadCodec.decodeReadingStatsChunk(bytes)
        assertEquals(71u, chunk.sessionId)
        assertEquals("The Left Hand of Darkness", chunk.title)
        assertEquals(2, chunk.samples.size)
        assertEquals(45_000L, chunk.samples.first().elapsedMs)
        assertEquals(180, chunk.samples.first().words)
        assertEquals(13, chunk.samples.last().pageNumber)
        assertTrue(chunk.isLastChunk)
        assertTrue(PayloadCodec.encodeReadingStatsAck(chunk.sessionId).contentEquals(byteArrayOf(71, 0, 0, 0)))
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

    @Test
    fun bookUploadBeginRoundTrips() {
        val expected = BookUploadBegin("Carrying the Fire.epub", 8_765_432, ByteArray(32) { (31 - it).toByte() })
        val actual = PayloadCodec.decodeBookUploadBegin(PayloadCodec.encodeBookUploadBegin(expected))
        assertEquals(expected.fileName, actual.fileName)
        assertEquals(expected.sizeBytes, actual.sizeBytes)
        assertTrue(expected.sha256.contentEquals(actual.sha256))
    }

    @Test
    fun bookUploadChunkKeepsOffsetAndBytes() {
        val payload = PayloadCodec.encodeBookUploadChunk(432, byteArrayOf(1, 2, 3, 4))
        val decoded = PayloadCodec.decodeFirmwareChunk(payload)
        assertEquals(432, decoded.first)
        assertTrue(byteArrayOf(1, 2, 3, 4).contentEquals(decoded.second))
    }
}
