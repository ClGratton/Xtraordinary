package com.xteink.companion.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class ImportedFlightPass(
    val id: String,
    val origin: String,
    val destination: String,
    val flight: String,
    val status: String,
    val departureTime: String,
    val gate: String,
    val terminal: String,
    val seat: String,
    val passenger: String,
    val boardingGroup: String,
    val barcodePayload: String,
    val source: String,
)

object FlightPassImporter {
    private const val MaxJsonBytes = 512 * 1024

    fun read(context: Context, uri: Uri): ImportedFlightPass {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val path = uri.lastPathSegment.orEmpty()
        return if (mime == "application/vnd.apple.pkpass" || path.endsWith(".pkpass", ignoreCase = true)) {
            readPkPass(context, uri)
        } else {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                readBounded(input, MaxJsonBytes).toString(Charsets.UTF_8)
            } ?: error("Could not open the selected flight pass")
            readText(text)
        }
    }

    private fun readPkPass(context: Context, uri: Uri): ImportedFlightPass {
        val passJson = context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use zipUse@{ zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "pass.json") {
                        return@zipUse readBounded(zip, MaxJsonBytes).toString(Charsets.UTF_8)
                    }
                }
                null
            }
        } ?: error("This .pkpass file has no pass.json")
        return fromPkPass(JSONObject(passJson))
    }

    fun readText(raw: String): ImportedFlightPass {
        require(raw.toByteArray(Charsets.UTF_8).size <= MaxJsonBytes) { "The flight-pass data is too large" }
        val text = raw.trim()
        val saveToken = text.substringAfter("/gp/v/save/", missingDelimiterValue = "")
            .substringBeforeAny('?', '#', '\n', '\r')
        if (saveToken.isNotBlank()) {
            val jwtPayload = saveToken.split('.').getOrNull(1) ?: error("The Google Wallet save link is incomplete")
            val decoded = Base64.decode(Uri.decode(jwtPayload), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return fromGoogleWallet(JSONObject(decoded.toString(Charsets.UTF_8)), "Google Wallet save link")
        }

        val json = JSONObject(text)
        return if (json.has("flightObjects") || json.optJSONObject("payload")?.has("flightObjects") == true) {
            fromGoogleWallet(json, "Google Wallet flight object")
        } else {
            fromGoogleFlightObject(json, "Google Wallet flight object")
        }
    }

    private fun fromGoogleWallet(jwt: JSONObject, source: String): ImportedFlightPass {
        val payload = jwt.optJSONObject("payload") ?: jwt
        val objects = payload.optJSONArray("flightObjects") ?: error("No flight object was found in this Wallet payload")
        val flightObject = objects.optJSONObject(0) ?: error("The Wallet flight object is empty")
        return fromGoogleFlightObject(flightObject, source)
    }

    private fun fromGoogleFlightObject(value: JSONObject, source: String): ImportedFlightPass {
        val classReference = value.optJSONObject("classReference") ?: JSONObject()
        val header = classReference.optJSONObject("flightHeader") ?: value.optJSONObject("flightHeader") ?: JSONObject()
        val carrier = header.optJSONObject("carrier") ?: JSONObject()
        val boarding = value.optJSONObject("boardingAndSeatingInfo") ?: JSONObject()
        val barcode = value.optJSONObject("barcode") ?: JSONObject()
        val origin = classReference.optJSONObject("origin") ?: value.optJSONObject("origin") ?: JSONObject()
        val destination =
            classReference.optJSONObject("destination") ?: value.optJSONObject("destination") ?: JSONObject()
        val flightNumber = header.optString("flightNumber").ifBlank { value.optString("flightNumber") }
        val carrierCode = carrier.optString("carrierIataCode")
        val barcodePayload = barcode.optString("value").ifBlank { barcode.optString("alternateText") }
        require(barcodePayload.isNotBlank()) { "This Wallet object has no barcode payload" }

        return ImportedFlightPass(
            id = value.optString("id").ifBlank { "wallet-${barcodePayload.hashCode()}" },
            origin = origin.optString("airportIataCode").ifBlank { "---" }.uppercase().take(3),
            destination = destination.optString("airportIataCode").ifBlank { "---" }.uppercase().take(3),
            flight = listOf(carrierCode, flightNumber).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Flight" }.take(16),
            status = classReference.optString("flightStatus").ifBlank { value.optString("state", "Imported") }.take(24),
            departureTime = classReference.optString("localScheduledDepartureDateTime")
                .ifBlank { value.optString("localScheduledDepartureDateTime") }
                .takeLast(16),
            gate = boarding.optString("gate")
                .ifBlank { boarding.optString("boardingGate") }
                .ifBlank { header.optString("gate") }
                .ifBlank { "TBD" }
                .take(8),
            terminal = boarding.optString("terminal")
                .ifBlank { boarding.optString("boardingTerminal") }
                .ifBlank { header.optString("terminal") }
                .take(8),
            seat = boarding.optString("seatNumber").ifBlank { "TBD" }.take(8),
            passenger = value.optString("passengerName").ifBlank { "Passenger" }.take(40),
            boardingGroup = boarding.optString("boardingGroup")
                .ifBlank { boarding.optString("boardingBoardingGroup") }
                .take(24),
            barcodePayload = barcodePayload.take(256),
            source = "Imported $source",
        )
    }

    private fun fromPkPass(value: JSONObject): ImportedFlightPass {
        val boardingPass = value.optJSONObject("boardingPass") ?: error("This pass is not a boarding pass")
        val fields = linkedMapOf<String, String>()
        listOf("headerFields", "primaryFields", "secondaryFields", "auxiliaryFields", "backFields").forEach { name ->
            val array = boardingPass.optJSONArray(name) ?: JSONArray()
            for (index in 0 until array.length()) {
                val field = array.optJSONObject(index) ?: continue
                val key = field.optString("key").lowercase()
                val rendered = field.opt("value")?.toString().orEmpty()
                if (key.isNotBlank() && rendered.isNotBlank()) fields[key] = rendered
            }
        }
        val barcode = value.optJSONArray("barcodes")?.optJSONObject(0) ?: value.optJSONObject("barcode") ?: JSONObject()
        val barcodePayload = barcode.optString("message")
        require(barcodePayload.isNotBlank()) { "This .pkpass has no barcode message" }

        fun field(vararg keys: String): String = keys.firstNotNullOfOrNull { fields[it] }.orEmpty()
        return ImportedFlightPass(
            id = value.optString("serialNumber").ifBlank { "pkpass-${barcodePayload.hashCode()}" },
            origin = field("origin", "from", "departure").ifBlank { "---" }.uppercase().take(3),
            destination = field("destination", "to", "arrival").ifBlank { "---" }.uppercase().take(3),
            flight = field("flight", "flightnumber", "flight_number").ifBlank { value.optString("description", "Flight") }.take(16),
            status = field("status").ifBlank { "Imported" }.take(24),
            departureTime = field("departure", "departuretime", "boardingtime", "date").take(16),
            gate = field("gate").ifBlank { "TBD" }.take(8),
            terminal = field("terminal").take(8),
            seat = field("seat").ifBlank { "TBD" }.take(8),
            passenger = field("passenger", "passengername", "name").ifBlank { "Passenger" }.take(40),
            boardingGroup = field("group", "boardinggroup", "boarding_group").take(24),
            barcodePayload = barcodePayload.take(256),
            source = "Imported .pkpass",
        )
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "The flight-pass data is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val end = delimiters.map { indexOf(it) }.filter { it >= 0 }.minOrNull() ?: length
        return substring(0, end)
    }
}
