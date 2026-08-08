package com.xteink.companion.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class CloudBackupState(
    val enabled: Boolean = false,
    val accountEmail: String? = null,
    val accountName: String? = null,
    val lastSyncEpochMs: Long? = null,
    val syncing: Boolean = false,
    val needsAuthorization: Boolean = false,
    val message: String? = null,
)

data class GoogleUserInfo(val email: String, val name: String?)

data class CloudSyncResult(
    val account: GoogleUserInfo,
    val remoteSessionsAdded: Int,
    val syncedAtEpochMs: Long,
)

class GoogleDriveReadingSync(
    context: Context,
    private val readingStats: ReadingStatsRepository = ReadingStatsRepository(context),
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun loadState(): CloudBackupState = CloudBackupState(
        enabled = preferences.getBoolean(KeyEnabled, false),
        accountEmail = preferences.getString(KeyEmail, null),
        accountName = preferences.getString(KeyName, null),
        lastSyncEpochMs = preferences.getLong(KeyLastSync, 0L).takeIf { it > 0L },
    )

    fun consentAccepted(): Boolean =
        preferences.getInt(KeyConsentVersion, 0) == ConsentVersion

    fun recordConsent() {
        preferences.edit()
            .putInt(KeyConsentVersion, ConsentVersion)
            .putLong(KeyConsentAt, System.currentTimeMillis())
            .apply()
    }

    fun clearLocalConnection() {
        preferences.edit()
            .remove(KeyEnabled)
            .remove(KeyEmail)
            .remove(KeyName)
            .remove(KeyLastSync)
            .apply()
    }

    fun sync(accessToken: String): CloudSyncResult {
        val account = fetchUserInfo(accessToken)
        val remoteFileId = findBackupFile(accessToken)
        val remoteSessionsAdded = if (remoteFileId != null) {
            val remote = requestJson(
                url = "$DriveFilesUrl/$remoteFileId?alt=media",
                accessToken = accessToken,
            )
            readingStats.mergeCloudJson(remote)
        } else {
            0
        }

        val payload = readingStats.exportCloudJson().apply {
            put("updatedAt", System.currentTimeMillis())
        }
        if (remoteFileId == null) {
            createBackupFile(accessToken, payload)
        } else {
            request(
                method = "PATCH",
                url = "$DriveUploadUrl/$remoteFileId?uploadType=media",
                accessToken = accessToken,
                contentType = JsonMime,
                body = payload.toString().toByteArray(StandardCharsets.UTF_8),
            )
        }

        val syncedAt = System.currentTimeMillis()
        check(
            preferences.edit()
                .putBoolean(KeyEnabled, true)
                .putString(KeyEmail, account.email)
                .putString(KeyName, account.name)
                .putLong(KeyLastSync, syncedAt)
                .commit(),
        ) { "Could not persist Google backup state" }
        return CloudSyncResult(account, remoteSessionsAdded, syncedAt)
    }

    fun deleteCloudBackup(accessToken: String) {
        findBackupFile(accessToken)?.let { fileId ->
            request(
                method = "DELETE",
                url = "$DriveFilesUrl/$fileId",
                accessToken = accessToken,
            )
        }
        clearLocalConnection()
    }

    private fun fetchUserInfo(accessToken: String): GoogleUserInfo {
        val json = requestJson(UserInfoUrl, accessToken)
        return GoogleUserInfo(
            email = json.getString("email"),
            name = json.optString("name").takeIf { it.isNotBlank() },
        )
    }

    private fun findBackupFile(accessToken: String): String? {
        val encodedName = URLEncoder.encode("name = '$BackupFileName'", StandardCharsets.UTF_8.name())
        val json = requestJson(
            url = "$DriveFilesUrl?spaces=appDataFolder&q=$encodedName&fields=files(id,name)&pageSize=10",
            accessToken = accessToken,
        )
        val files = json.optJSONArray("files") ?: JSONArray()
        return if (files.length() == 0) null else files.getJSONObject(0).getString("id")
    }

    private fun createBackupFile(accessToken: String, payload: JSONObject) {
        val boundary = "xtraordinary-${System.currentTimeMillis()}"
        val metadata = JSONObject()
            .put("name", BackupFileName)
            .put("parents", JSONArray().put("appDataFolder"))
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: $JsonMime\r\n\r\n")
            append(payload).append("\r\n")
            append("--$boundary--\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        request(
            method = "POST",
            url = "$DriveUploadUrl?uploadType=multipart&fields=id",
            accessToken = accessToken,
            contentType = "multipart/related; boundary=$boundary",
            body = body,
        )
    }

    private fun requestJson(url: String, accessToken: String): JSONObject =
        JSONObject(request("GET", url, accessToken))

    private fun request(
        method: String,
        url: String,
        accessToken: String,
        contentType: String? = null,
        body: ByteArray? = null,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", JsonMime)
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Google Drive request failed ($status): $response")
            response
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val ConsentVersion = 1
        val Scopes = listOf(
            "https://www.googleapis.com/auth/drive.appdata",
            "openid",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
        )

        private const val PreferencesName = "google_reading_backup_v1"
        private const val KeyEnabled = "enabled"
        private const val KeyEmail = "email"
        private const val KeyName = "name"
        private const val KeyLastSync = "last_sync"
        private const val KeyConsentVersion = "consent_version"
        private const val KeyConsentAt = "consent_at"
        private const val BackupFileName = "xtraordinary-reading-v1.json"
        private const val JsonMime = "application/json"
        private const val DriveFilesUrl = "https://www.googleapis.com/drive/v3/files"
        private const val DriveUploadUrl = "https://www.googleapis.com/upload/drive/v3/files"
        private const val UserInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo"
    }
}
