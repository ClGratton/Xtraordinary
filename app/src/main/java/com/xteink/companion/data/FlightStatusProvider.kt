package com.xteink.companion.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FlightIdentity(
    val flightNumber: String,
    val operatingDate: String,
    val origin: String,
) {
    val isComplete: Boolean
        get() = flightNumber.isNotBlank() && operatingDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
            origin.matches(Regex("[A-Z]{3}"))

    fun normalized(): FlightIdentity = copy(
        flightNumber = flightNumber.filterNot(Char::isWhitespace).uppercase(),
        origin = origin.uppercase(),
    )
}

data class FlightStatusSnapshot(
    val identity: FlightIdentity,
    val status: String,
    val departureTime: String,
    val arrivalTime: String,
    val gate: String,
    val terminal: String,
    val delayMinutes: Int?,
    val observedAtEpochMs: Long,
    val providerName: String,
)

interface FlightStatusProvider {
    val isConfigured: Boolean
    suspend fun latest(identity: FlightIdentity): FlightStatusSnapshot?
}

object DisabledFlightStatusProvider : FlightStatusProvider {
    override val isConfigured = false
    override suspend fun latest(identity: FlightIdentity): FlightStatusSnapshot? = null
}

/**
 * Calls a project-owned proxy. Provider credentials never belong in the APK.
 *
 * Contract: GET ?flight=...&date=YYYY-MM-DD&origin=AAA returns one JSON object
 * containing the same identity plus status, departureTime, arrivalTime, gate,
 * terminal, delayMinutes, observedAtEpochMs, and provider.
 */
class ProxyFlightStatusProvider(private val endpoint: String) : FlightStatusProvider {
    override val isConfigured: Boolean = endpoint.startsWith("https://")

    override suspend fun latest(identity: FlightIdentity): FlightStatusSnapshot? = withContext(Dispatchers.IO) {
        val requested = identity.normalized()
        if (!isConfigured || !requested.isComplete) return@withContext null
        val uri = Uri.parse(endpoint).buildUpon()
            .appendQueryParameter("flight", requested.flightNumber)
            .appendQueryParameter("date", requested.operatingDate)
            .appendQueryParameter("origin", requested.origin)
            .build()
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val text = connection.inputStream.bufferedReader().use { it.readText().take(MaxResponseChars) }
            parseSnapshot(JSONObject(text), requested)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MaxResponseChars = 64 * 1024

        internal fun parseSnapshot(value: JSONObject, requested: FlightIdentity): FlightStatusSnapshot? {
            val returned = FlightIdentity(
                flightNumber = value.optString("flightNumber", requested.flightNumber),
                operatingDate = value.optString("operatingDate", requested.operatingDate),
                origin = value.optString("origin", requested.origin),
            ).normalized()
            if (!matchesIdentity(requested, returned)) return null
            val observedAt = value.optLong("observedAtEpochMs", 0L)
            if (observedAt <= 0L) return null
            return FlightStatusSnapshot(
                identity = returned,
                status = value.optString("status").take(24),
                departureTime = value.optString("departureTime").take(16),
                arrivalTime = value.optString("arrivalTime").take(16),
                gate = value.optString("gate").take(8),
                terminal = value.optString("terminal").take(8),
                delayMinutes = if (value.has("delayMinutes") && !value.isNull("delayMinutes")) {
                    value.optInt("delayMinutes").coerceIn(-1_440, 1_440)
                } else {
                    null
                },
                observedAtEpochMs = observedAt,
                providerName = value.optString("provider", "Flight status provider").take(40),
            )
        }

        internal fun matchesIdentity(requested: FlightIdentity, returned: FlightIdentity): Boolean =
            requested.normalized() == returned.normalized()
    }
}

object FlightStatusRefreshPolicy {
    const val NormalIntervalMs = 5 * 60_000L
    private val FailureIntervalsMs = longArrayOf(5 * 60_000L, 10 * 60_000L, 20 * 60_000L, 30 * 60_000L)

    fun delayAfterFailure(consecutiveFailures: Int): Long =
        FailureIntervalsMs[(consecutiveFailures - 1).coerceIn(0, FailureIntervalsMs.lastIndex)]
}
