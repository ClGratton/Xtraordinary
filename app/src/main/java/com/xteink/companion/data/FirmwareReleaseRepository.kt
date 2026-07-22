package com.xteink.companion.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class FirmwareRelease(
    val model: String,
    val version: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

class FirmwareReleaseRepository(private val context: Context) {
    suspend fun latestFor(model: String): FirmwareRelease = withContext(Dispatchers.IO) {
        val release = JSONObject(getText(LATEST_RELEASE_URL))
        val version = release.getString("tag_name")
        val assets = release.getJSONArray("assets")
        val manifestAsset = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name") == MANIFEST_NAME }
            ?: error("Latest release does not contain $MANIFEST_NAME")
        val manifest = JSONObject(getText(manifestAsset.getString("browser_download_url")))
        val firmwareAssets = manifest.getJSONArray("assets")
        val match = (0 until firmwareAssets.length())
            .map { firmwareAssets.getJSONObject(it) }
            .firstOrNull { it.getString("model").equals(model, ignoreCase = true) }
            ?: error("Latest release has no firmware for $model")
        FirmwareRelease(
            model = match.getString("model"),
            version = manifest.optString("version", version),
            assetName = match.getString("name"),
            downloadUrl = match.optString("url").ifBlank {
                (0 until assets.length()).map { assets.getJSONObject(it) }
                    .first { it.getString("name") == match.getString("name") }
                    .getString("browser_download_url")
            },
            sizeBytes = match.getLong("size"),
            sha256 = match.getString("sha256").lowercase(),
        )
    }

    suspend fun downloadVerified(release: FirmwareRelease): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, release.assetName)
        val connection = open(release.downloadUrl)
        try {
            connection.inputStream.use { input -> target.outputStream().use(input::copyTo) }
        } finally {
            connection.disconnect()
        }
        require(target.length() == release.sizeBytes) { "Firmware download size does not match the manifest" }
        val digest = MessageDigest.getInstance("SHA-256").digest(target.readBytes()).toHex()
        require(digest == release.sha256) { "Firmware SHA-256 does not match the manifest" }
        target
    }

    private fun getText(url: String): String {
        val connection = open(url)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Xtraordinary-Android")
        instanceFollowRedirects = true
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/ClGratton/Xtraordinary/releases/latest"
        private const val MANIFEST_NAME = "firmware-manifest.json"
    }
}
