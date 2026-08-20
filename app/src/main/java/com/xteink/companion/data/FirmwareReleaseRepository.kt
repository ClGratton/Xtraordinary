package com.xteink.companion.data

import android.content.Context
import android.net.Uri
import com.xteink.companion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class FirmwareSource {
    Xtraordinary,
    LocalFile,
    XteinkStock,
    CrossPoint,
    CrossInk,
}

data class FirmwareRelease(
    val model: String,
    val source: FirmwareSource,
    val version: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

class FirmwareReleaseRepository(private val context: Context) {
    suspend fun latestFor(
        model: String,
        source: FirmwareSource = FirmwareSource.Xtraordinary,
    ): FirmwareRelease = withContext(Dispatchers.IO) {
        requireFirmwareSourceDistributionAllowed(
            source = source,
            xteinkOemFirmwareAllowed = BuildConfig.XTEINK_OEM_FIRMWARE_ALLOWED,
        )
        fixedRelease(model, source)?.let { return@withContext it }
        when (source) {
            FirmwareSource.Xtraordinary -> latestXtraordinary(model)
            FirmwareSource.LocalFile -> error("Choose a local firmware file first")
            FirmwareSource.XteinkStock -> error("Missing fixed XTEINK stock release")
            FirmwareSource.CrossPoint -> latestGitHubAsset(
                model = model,
                source = source,
                releaseUrl = CROSSPOINT_RELEASE_URL,
            ) { name -> name == "firmware.bin" }
            FirmwareSource.CrossInk -> latestGitHubAsset(
                model = model,
                source = source,
                releaseUrl = CROSSINK_RELEASE_URL,
            ) { name -> name.startsWith("firmware-tiny-") && name.endsWith(".bin") }
        }
    }

    suspend fun localFor(model: String, uri: Uri): FirmwareRelease = withContext(Dispatchers.IO) {
        require(model.equals("X3", ignoreCase = true)) {
            "Local firmware installation is currently available for X3 only"
        }
        val selectedFile = File(context.cacheDir, "selected-local-x3.bin")
        context.contentResolver.openInputStream(uri)?.use { input ->
            selectedFile.outputStream().use(input::copyTo)
        } ?: error("The selected firmware file could not be opened")
        val image = selectedFile.readBytes()
        val version = inspectLocalX3Firmware(image)
        val digest = MessageDigest.getInstance("SHA-256").digest(image).toHex()
        FirmwareRelease(
            model = "X3",
            source = FirmwareSource.LocalFile,
            version = version,
            assetName = "local-xtraordinary-x3.bin",
            downloadUrl = selectedFile.toURI().toString(),
            sizeBytes = image.size.toLong(),
            sha256 = digest,
        )
    }

    private fun fixedRelease(model: String, source: FirmwareSource): FirmwareRelease? {
        val spec = FIXED_RELEASES[source] ?: return null
        require(model.equals(spec.model, ignoreCase = true)) {
            "$source firmware is currently available for ${spec.model} only"
        }
        return FirmwareRelease(
            model = spec.model,
            source = source,
            version = spec.version,
            assetName = spec.assetName,
            downloadUrl = spec.downloadUrl,
            sizeBytes = spec.sizeBytes,
            sha256 = spec.sha256,
        )
    }

    private fun latestXtraordinary(model: String): FirmwareRelease {
        val release = JSONObject(getText(XTRAORDINARY_RELEASE_URL))
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
        return FirmwareRelease(
            model = match.getString("model"),
            source = FirmwareSource.Xtraordinary,
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

    private fun latestGitHubAsset(
        model: String,
        source: FirmwareSource,
        releaseUrl: String,
        matchesAsset: (String) -> Boolean,
    ): FirmwareRelease {
        require(model.equals("X3", ignoreCase = true)) { "$source firmware is currently available for X3 only" }
        val release = JSONObject(getText(releaseUrl))
        val version = release.getString("tag_name")
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { matchesAsset(it.getString("name")) }
            ?: error("Latest $source release has no compatible X3 firmware")
        val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
        require(digest.matches(Regex("[0-9a-f]{64}"))) {
            "GitHub did not provide a SHA-256 digest for the latest $source firmware"
        }
        return FirmwareRelease(
            model = "X3",
            source = source,
            version = version,
            assetName = asset.getString("name"),
            downloadUrl = asset.getString("browser_download_url"),
            sizeBytes = asset.getLong("size"),
            sha256 = digest,
        )
    }

    suspend fun downloadVerified(release: FirmwareRelease): File = withContext(Dispatchers.IO) {
        val targetName = if (release.source == FirmwareSource.LocalFile) {
            "verified-${release.assetName}"
        } else {
            release.assetName
        }
        val target = File(context.cacheDir, targetName)
        val sourceUri = Uri.parse(release.downloadUrl)
        when (sourceUri.scheme) {
            "file" -> File(requireNotNull(sourceUri.path)).inputStream().use { input ->
                target.outputStream().use(input::copyTo)
            }
            "content" -> context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use(input::copyTo)
            } ?: error("The selected firmware file could not be reopened")
            else -> {
                val connection = open(release.downloadUrl)
                try {
                    connection.inputStream.use { input -> target.outputStream().use(input::copyTo) }
                } finally {
                    connection.disconnect()
                }
            }
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
        private data class FixedReleaseSpec(
            val model: String,
            val version: String,
            val assetName: String,
            val downloadUrl: String,
            val sizeBytes: Long,
            val sha256: String,
        )

        private val FIXED_RELEASES = mapOf(
            FirmwareSource.XteinkStock to FixedReleaseSpec(
                model = "X3",
                version = "XT V5.1.6 EN",
                assetName = "V5.1.6-X3-EN-PROD-0304_.bin",
                downloadUrl = "https://overseas-upload-file-api.oss-ap-southeast-1.aliyuncs.com/" +
                    "uploads/b9dced8c-45d7-4a2b-a13a-aed22e9e0bc0/2026/03/19/" +
                    "V5.1.6-X3-EN-PROD-0304_.bin",
                sizeBytes = 6_412_240L,
                sha256 = "49926e09526a0201688ea6ac1936a8e62588f66dcd06297f2a011114ede42525",
            ),
        )

        private const val XTRAORDINARY_RELEASE_URL =
            "https://api.github.com/repos/ClGratton/Xtraordinary/releases/latest"
        private const val CROSSPOINT_RELEASE_URL =
            "https://api.github.com/repos/crosspoint-reader/crosspoint-reader/releases/latest"
        private const val CROSSINK_RELEASE_URL =
            "https://api.github.com/repos/uxjulia/CrossInk/releases/latest"
        private const val MANIFEST_NAME = "firmware-manifest.json"
    }
}

internal fun requireFirmwareSourceDistributionAllowed(
    source: FirmwareSource,
    xteinkOemFirmwareAllowed: Boolean,
) {
    require(source != FirmwareSource.XteinkStock || xteinkOemFirmwareAllowed) {
        "XTEINK OEM recovery is disabled in public builds until distribution permission is recorded"
    }
}

internal fun inspectLocalX3Firmware(image: ByteArray): String {
    require(image.isNotEmpty() && image[0].toInt() and 0xFF == 0xE9) {
        "The selected file is not an ESP32-C3 application image"
    }
    require(image.size <= EspRomProtocol.MaxAppSize) {
        "Firmware image does not fit the X3 app partition"
    }
    val searchable = image.toString(Charsets.ISO_8859_1)
    return Regex("xtraordinary-v[0-9A-Za-z][0-9A-Za-z.-]*")
        .find(searchable)
        ?.value
        ?: error("The selected file is not Xtraordinary X3 firmware")
}
