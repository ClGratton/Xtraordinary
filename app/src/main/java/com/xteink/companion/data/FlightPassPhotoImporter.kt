package com.xteink.companion.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FlightPassPhotoImporter {
    suspend fun read(context: Context, uri: Uri): ImportedFlightPass {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_DATA_MATRIX,
                )
                .build(),
        )
        return try {
            val recognized = recognizer.process(image).awaitResult().text
            val barcode = scanner.process(image).awaitResult().firstNotNullOfOrNull { it.rawValue }
                ?.trim()
                .orEmpty()
            require(recognized.isNotBlank()) { "No ticket text was found in this photo" }
            require(barcode.isNotBlank()) { "No scannable ticket code was found in this photo" }
            parse(recognized, barcode)
        } finally {
            recognizer.close()
            scanner.close()
        }
    }

    internal fun parse(recognizedText: String, barcodePayload: String): ImportedFlightPass {
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
        val flightMatch = upperLines.firstNotNullOfOrNull { FlightRegex.find(it) }
        val flight = flightMatch?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: "Flight"
        val departureTime = upperLines.firstNotNullOfOrNull { TimeRegex.find(it)?.value }.orEmpty()
        val status = upperLines.firstOrNull { line -> StatusWords.firstOrNull(line::contains) != null }
            ?.let { line -> StatusWords.first(line::contains).lowercase().replaceFirstChar(Char::uppercase) }
            ?: "Imported"

        return ImportedFlightPass(
            id = "photo-${barcodePayload.hashCode()}",
            origin = origin,
            destination = destination,
            flight = flight.take(16),
            status = status.take(24),
            departureTime = departureTime.take(16),
            gate = valueAfterLabel(upperLines, "GATE") ?: "TBD",
            terminal = valueAfterLabel(upperLines, "TERMINAL").orEmpty(),
            seat = valueAfterLabel(upperLines, "SEAT") ?: "TBD",
            passenger = valueAfterLabel(lines, "PASSENGER")
                ?: valueAfterLabel(lines, "NAME")
                ?: "Passenger",
            boardingGroup = valueAfterLabel(lines, "BOARDING GROUP")
                ?: valueAfterLabel(lines, "GROUP")
                ?: "",
            barcodePayload = barcodePayload.take(256),
            source = "Imported from photo · extracted on device",
        )
    }

    private fun valueAfterLabel(lines: List<String>, label: String): String? {
        val pattern = Regex("(?i)\\b${Regex.escape(label)}\\b\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9 .'-]{0,39})")
        return lines.firstNotNullOfOrNull { line ->
            pattern.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

    private val IataRegex = Regex("\\b[A-Z]{3}\\b")
    private val FlightRegex = Regex("\\b([A-Z][A-Z0-9]|[0-9][A-Z])\\s*([0-9]{1,4}[A-Z]?)\\b")
    private val TimeRegex = Regex("\\b(?:[01]?\\d|2[0-3])[:.]([0-5]\\d)(?:\\s?[AP]M)?\\b")
    private val StatusWords = listOf("BOARDING", "ON TIME", "DELAYED", "CANCELLED", "DEPARTED", "GATE CLOSED")
    private val IgnoredIataWords = setOf("GATE", "SEAT", "NAME", "DATE", "TIME", "FROM", "BOARDING", "PASSENGER")
}
