package com.xteink.companion.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BarcodeRaster(
    val bmpBytes: ByteArray,
    val width: Int,
    val height: Int,
)

/** Produces the same real barcode raster for the phone preview and X3. */
object BarcodeRasterizer {
    private const val MatrixSize = 320
    // 380 px uses the portrait X3 width without crowding the frame and can be
    // doubled exactly to 760 px in the rotated scanner view.
    private const val LinearWidth = 380
    private const val Pdf417Height = 140
    private const val LinearHeight = 100

    fun render(payload: String, format: FlightBarcodeFormat): BarcodeRaster {
        require(payload.isNotBlank()) { "The boarding-pass barcode is empty" }
        val zxingFormat = format.toZxingFormat()
            ?: error("${format.displayName} barcode rendering is not supported")
        val linear = format.isLinear
        val width = if (linear) LinearWidth else MatrixSize
        val height = when (format) {
            FlightBarcodeFormat.Pdf417 -> Pdf417Height
            else -> if (linear) LinearHeight else MatrixSize
        }
        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            EncodeHintType.MARGIN to if (linear) 12 else 4,
        )
        val matrix = MultiFormatWriter().encode(payload, zxingFormat, width, height, hints)
        return BarcodeRaster(matrix.toMonochromeBmp(), matrix.width, matrix.height)
    }

    private fun FlightBarcodeFormat.toZxingFormat(): BarcodeFormat? = when (this) {
        FlightBarcodeFormat.Qr -> BarcodeFormat.QR_CODE
        FlightBarcodeFormat.Aztec -> BarcodeFormat.AZTEC
        FlightBarcodeFormat.Pdf417 -> BarcodeFormat.PDF_417
        FlightBarcodeFormat.DataMatrix -> BarcodeFormat.DATA_MATRIX
        FlightBarcodeFormat.Code128 -> BarcodeFormat.CODE_128
        FlightBarcodeFormat.Code39 -> BarcodeFormat.CODE_39
        FlightBarcodeFormat.Code93 -> BarcodeFormat.CODE_93
        FlightBarcodeFormat.Codabar -> BarcodeFormat.CODABAR
        FlightBarcodeFormat.Ean13 -> BarcodeFormat.EAN_13
        FlightBarcodeFormat.Ean8 -> BarcodeFormat.EAN_8
        FlightBarcodeFormat.Itf -> BarcodeFormat.ITF
        FlightBarcodeFormat.UpcA -> BarcodeFormat.UPC_A
        FlightBarcodeFormat.UpcE -> BarcodeFormat.UPC_E
        FlightBarcodeFormat.Unknown -> null
    }

    private fun BitMatrix.toMonochromeBmp(): ByteArray {
        val rowBytes = ((width + 31) / 32) * 4
        val pixelBytes = rowBytes * height
        val headerBytes = 14 + 40 + 8
        val output = ByteArrayOutputStream(headerBytes + pixelBytes)
        val header = ByteBuffer.allocate(headerBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte())
            put('M'.code.toByte())
            putInt(headerBytes + pixelBytes)
            putShort(0.toShort())
            putShort(0.toShort())
            putInt(headerBytes)
            putInt(40)
            putInt(width)
            putInt(-height) // top-down rows
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(0)
            putInt(pixelBytes)
            putInt(2_835)
            putInt(2_835)
            putInt(2)
            putInt(2)
            // Palette index 0 = black, index 1 = white.
            put(byteArrayOf(0, 0, 0, 0, -1, -1, -1, 0))
        }.array()
        output.write(header)
        val row = ByteArray(rowBytes) { 0xff.toByte() }
        for (y in 0 until height) {
            row.fill(0xff.toByte())
            for (x in 0 until width) {
                if (get(x, y)) {
                    val byteIndex = x / 8
                    row[byteIndex] = (row[byteIndex].toInt() and (1 shl (7 - x % 8)).inv()).toByte()
                }
            }
            output.write(row)
        }
        return output.toByteArray()
    }
}

val FlightBarcodeFormat.isLinear: Boolean
    get() = this == FlightBarcodeFormat.Pdf417 ||
        this == FlightBarcodeFormat.Code128 ||
        this == FlightBarcodeFormat.Code39 ||
        this == FlightBarcodeFormat.Code93 ||
        this == FlightBarcodeFormat.Codabar ||
        this == FlightBarcodeFormat.Ean13 ||
        this == FlightBarcodeFormat.Ean8 ||
        this == FlightBarcodeFormat.Itf ||
        this == FlightBarcodeFormat.UpcA ||
        this == FlightBarcodeFormat.UpcE
