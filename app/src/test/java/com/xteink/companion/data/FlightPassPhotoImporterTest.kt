package com.xteink.companion.data

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import kotlin.math.absoluteValue

class FlightPassPhotoImporterTest {
    @Test
    fun parsesWalletStyleWizzLayoutWithoutTreatingLabelsAsData() {
        val pass = FlightPassPhotoImporter.parse(
            recognizedText = """
                BOARDING PASS
                AHO
                VCE
                Flight
                W4
                6762
                6 AUG 2026
                Gate close
                13:05
                Scheduled departure at
                13:35
                Passenger
                Example Passenger
                Sequence / Seat
                0067 / 17B
                Status
                Boarding
            """.trimIndent(),
            barcodePayload = "M1EXAMPLE/PASSENGER",
            barcodeFormat = FlightBarcodeFormat.Pdf417,
            today = LocalDate.of(2026, 8, 10),
        )

        assertEquals("W4 6762", pass.flight)
        assertEquals("13:35", pass.departureTime)
        assertEquals("17B", pass.seat)
        assertEquals("Example Passenger", pass.passenger)
        assertEquals("Departed", pass.status)
        assertEquals("TBD", pass.gate)
    }

    @Test
    fun rendersRealPdf417AsOneBitBmp() {
        val payload = "M1EXAMPLE/PASSENGER EABC123"
        val raster = BarcodeRasterizer.render(payload, FlightBarcodeFormat.Pdf417)
        assertTrue(raster.width in 200..340)
        assertTrue(raster.height in 80..140)
        assertTrue(raster.bmpBytes.copyOfRange(0, 2).contentEquals(byteArrayOf('B'.code.toByte(), 'M'.code.toByte())))
        assertTrue(raster.bmpBytes.size > 1_000)

        val pixels = decodeMonochromeBmp(raster.bmpBytes)
        val decoded = MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(raster.width, raster.height, pixels))),
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.PDF_417),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
        assertEquals(payload, decoded.text)
    }

    private fun decodeMonochromeBmp(bytes: ByteArray): IntArray {
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val pixelOffset = header.getInt(10)
        val width = header.getInt(18)
        val storedHeight = header.getInt(22)
        val height = storedHeight.absoluteValue
        val rowBytes = ((width + 31) / 32) * 4
        return IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val sourceY = if (storedHeight < 0) y else height - 1 - y
            val packed = bytes[pixelOffset + sourceY * rowBytes + x / 8].toInt() and 0xff
            if (((packed shr (7 - x % 8)) and 1) == 0) 0xff000000.toInt() else 0xffffffff.toInt()
        }
    }
}
