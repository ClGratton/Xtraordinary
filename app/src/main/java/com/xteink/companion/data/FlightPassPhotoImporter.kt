package com.xteink.companion.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FlightPassPhotoImporter {
    suspend fun read(context: Context, uri: Uri): ImportedFlightPass {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        // Airline Wallet passes use a mix of Aztec, PDF417, QR and Code 128.
        val scanner = BarcodeScanning.getClient()
        return try {
            val recognized = recognizer.process(image).awaitResult().text
            val barcode = scanner.process(image).awaitResult()
                .mapNotNull(::toDetectedBarcode)
                .maxByOrNull { it.priority }
            require(recognized.isNotBlank()) { "No ticket text was found in this photo" }
            require(barcode != null) {
                "No boarding-pass barcode was found. Use the full-resolution Wallet screenshot."
            }
            parse(recognized, barcode.payload, barcode.format)
        } finally {
            recognizer.close()
            scanner.close()
        }
    }

    internal fun parse(
        recognizedText: String,
        barcodePayload: String,
        barcodeFormat: FlightBarcodeFormat = FlightBarcodeFormat.Unknown,
        today: LocalDate = LocalDate.now(),
    ): ImportedFlightPass {
        val lines = recognizedText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val upperLines = lines.map { it.uppercase(Locale.US) }
        val routeLine = upperLines.firstOrNull { line ->
            IataRegex.findAll(line).map { it.value }.distinct().count() >= 2
        }
        val routeCodes = routeLine?.let { IataRegex.findAll(it).map { match -> match.value }.distinct().toList() }
            .orEmpty()
            .filterNot { it in IgnoredIataWords }
        val allCodes = upperLines.asSequence()
            .flatMap { IataRegex.findAll(it).map { match -> match.value } }
            .filterNot { it in IgnoredIataWords }
            .distinct()
            .toList()
        val origin = (routeCodes.getOrNull(0) ?: allCodes.getOrNull(0) ?: "---").take(3)
        val destination = (routeCodes.getOrNull(1) ?: allCodes.firstOrNull { it != origin } ?: "---").take(3)
        val flight = findFlightNumber(upperLines) ?: "Flight"
        val departureTime = valueAfterAdjacentLabel(upperLines, "SCHEDULED DEPARTURE AT")
            ?.let { TimeRegex.find(it)?.value }
            ?: upperLines.firstNotNullOfOrNull { line ->
                if (line.contains("GATE CLOSE")) null else TimeRegex.find(line)?.value
            }.orEmpty()
        val flightDate = findFlightDate(upperLines, today)
        val status = if (flightDate?.isBefore(today) == true) {
            "Departed"
        } else {
            upperLines.firstNotNullOfOrNull(::explicitStatus) ?: "Imported"
        }

        return ImportedFlightPass(
            id = "photo-${barcodePayload.hashCode()}",
            origin = origin,
            destination = destination,
            flight = flight.take(16),
            status = status.take(24),
            departureTime = departureTime.take(16),
            gate = valueAfterLabel(upperLines, "GATE")?.takeUnless { it.equals("CLOSE", true) } ?: "TBD",
            terminal = valueAfterLabel(upperLines, "TERMINAL").orEmpty(),
            seat = findSeat(lines) ?: "TBD",
            passenger = valueAfterLabel(lines, "PASSENGER")
                ?: valueAfterAdjacentLabel(lines, "PASSENGER")
                ?: valueAfterLabel(lines, "NAME")
                ?: valueAfterAdjacentLabel(lines, "NAME")
                ?: "Passenger",
            boardingGroup = valueAfterLabel(lines, "BOARDING GROUP")
                ?: valueAfterLabel(lines, "GROUP")
                ?: "",
            barcodePayload = barcodePayload.take(256),
            barcodeFormat = barcodeFormat,
            source = "Imported from photo · extracted on device",
        )
    }

    private fun toDetectedBarcode(barcode: Barcode): DetectedBarcode? {
        // Preserve the decoded bytes represented by ML Kit. Trailing spaces are
        // meaningful padding in some BCBP payloads and must not be normalized.
        val payload = barcode.rawValue.orEmpty()
        if (payload.isBlank()) return null
        val format = when (barcode.format) {
            Barcode.FORMAT_QR_CODE -> FlightBarcodeFormat.Qr
            Barcode.FORMAT_AZTEC -> FlightBarcodeFormat.Aztec
            Barcode.FORMAT_PDF417 -> FlightBarcodeFormat.Pdf417
            Barcode.FORMAT_DATA_MATRIX -> FlightBarcodeFormat.DataMatrix
            Barcode.FORMAT_CODE_128 -> FlightBarcodeFormat.Code128
            Barcode.FORMAT_CODE_39 -> FlightBarcodeFormat.Code39
            Barcode.FORMAT_CODE_93 -> FlightBarcodeFormat.Code93
            Barcode.FORMAT_CODABAR -> FlightBarcodeFormat.Codabar
            Barcode.FORMAT_EAN_13 -> FlightBarcodeFormat.Ean13
            Barcode.FORMAT_EAN_8 -> FlightBarcodeFormat.Ean8
            Barcode.FORMAT_ITF -> FlightBarcodeFormat.Itf
            Barcode.FORMAT_UPC_A -> FlightBarcodeFormat.UpcA
            Barcode.FORMAT_UPC_E -> FlightBarcodeFormat.UpcE
            else -> FlightBarcodeFormat.Unknown
        }
        val area = barcode.boundingBox?.let { it.width().coerceAtLeast(0) * it.height().coerceAtLeast(0) } ?: 0
        val formatWeight = when (format) {
            FlightBarcodeFormat.Aztec, FlightBarcodeFormat.Pdf417, FlightBarcodeFormat.Qr -> 1_000_000
            else -> 0
        }
        return DetectedBarcode(payload, format, formatWeight + area)
    }

    private data class DetectedBarcode(
        val payload: String,
        val format: FlightBarcodeFormat,
        val priority: Int,
    )

    private fun valueAfterLabel(lines: List<String>, label: String): String? {
        val pattern = Regex("(?i)\\b${Regex.escape(label)}\\b\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9 .'-]{0,39})")
        return lines.firstNotNullOfOrNull { line ->
            pattern.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun valueAfterAdjacentLabel(lines: List<String>, label: String): String? {
        val index = lines.indexOfFirst { it.trim().equals(label, ignoreCase = true) }
        if (index < 0) return null
        return lines.getOrNull(index + 1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun findFlightNumber(lines: List<String>): String? {
        lines.forEachIndexed { index, line ->
            if (CarrierCodeRegex.matches(line)) {
                val number = lines.getOrNull(index + 1)?.let(FlightDigitsRegex::matchEntire)?.value
                if (number != null) return "$line $number"
            }
        }
        return lines.firstNotNullOfOrNull { line ->
            FlightRegex.findAll(line).firstOrNull { it.groupValues[1] !in IgnoredCarrierCodes }
                ?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
        }
    }

    private fun findSeat(lines: List<String>): String? {
        valueAfterLabel(lines, "SEAT")?.let { value ->
            SeatRegex.find(value)?.value?.let { return it.uppercase(Locale.US) }
        }
        val labelIndex = lines.indexOfFirst { it.contains("SEAT", ignoreCase = true) }
        if (labelIndex < 0) return null
        for (index in (labelIndex + 1)..(labelIndex + 2)) {
            val line = lines.getOrNull(index) ?: continue
            SeatRegex.find(line.substringAfterLast('/').trim())?.value?.let {
                return it.uppercase(Locale.US)
            }
        }
        return null
    }

    private fun explicitStatus(line: String): String? {
        val normalized = line.trim()
        if (normalized == "BOARDING PASS" || normalized == "BOARDING CARD") return null
        return StatusWords.firstOrNull { normalized == it || normalized == "STATUS $it" }
            ?.lowercase()
            ?.replaceFirstChar(Char::uppercase)
    }

    private fun findFlightDate(lines: List<String>, today: LocalDate): LocalDate? {
        val formatter = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
        for (line in lines) {
            val match = DateRegex.find(line) ?: continue
            val year = match.groupValues[3].toIntOrNull() ?: today.year
            val dateText = "${match.groupValues[1]} ${match.groupValues[2].lowercase().replaceFirstChar(Char::uppercase)} $year"
            try {
                return LocalDate.parse(dateText, formatter)
            } catch (_: DateTimeParseException) {
                // Continue after OCR tokens that merely resemble a date.
            }
        }
        return null
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

    private val IataRegex = Regex("\\b[A-Z]{3}\\b")
    private val FlightRegex = Regex("\\b([A-Z][A-Z0-9]|[0-9][A-Z])\\s*([0-9]{1,4}[A-Z]?)\\b")
    private val CarrierCodeRegex = Regex("[A-Z0-9]{2}")
    private val FlightDigitsRegex = Regex("[0-9]{1,4}[A-Z]?")
    private val SeatRegex = Regex("\\b[0-9]{1,3}[A-Z]\\b", RegexOption.IGNORE_CASE)
    private val DateRegex = Regex("\\b([0-3]?\\d)\\s+(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:\\s+(20\\d{2}))?\\b")
    private val TimeRegex = Regex("\\b(?:[01]?\\d|2[0-3])[:.]([0-5]\\d)(?:\\s?[AP]M)?\\b")
    private val StatusWords = listOf("BOARDING", "ON TIME", "DELAYED", "CANCELLED", "DEPARTED", "GATE CLOSED", "SCHEDULED")
    private val IgnoredCarrierCodes = setOf("AT", "TO", "NO", "IN", "ON")
    private val IgnoredIataWords = setOf("GATE", "SEAT", "NAME", "DATE", "TIME", "FROM", "BOARDING", "PASSENGER")
}
