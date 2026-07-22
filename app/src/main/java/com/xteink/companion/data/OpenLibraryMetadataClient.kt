package com.xteink.companion.data

import android.content.Context
import com.xteink.companion.ui.ImportedBookUiState
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OpenLibraryMetadataClient {
    private const val LookupCooldownMs = 7L * 24L * 60L * 60L * 1_000L
    private const val MaxResponseBytes = 1_000_000
    private const val MaxCoverBytes = 8_000_000
    private const val UserAgent = "Xtraordinary/0.1 (https://github.com/ClGratton/Xtraordinary)"

    fun shouldEnrich(book: ImportedBookUiState, now: Long = System.currentTimeMillis()): Boolean {
        val missing = book.coverPath == null || book.author == UnknownAuthor || book.publisher == null ||
            book.language == null || book.publishedYear == null
        val cooldownExpired = book.lastMetadataLookupEpochMs?.let { now - it >= LookupCooldownMs } ?: true
        return missing && cooldownExpired
    }

    fun enrich(context: Context, book: ImportedBookUiState): ImportedBookUiState {
        val attemptedAt = System.currentTimeMillis()
        val titleQuery = book.title.ifBlank { book.fileName.substringBeforeLast('.') }
        if (titleQuery.isBlank()) return book.copy(lastMetadataLookupEpochMs = attemptedAt)

        val endpoint = buildString {
            append("https://openlibrary.org/search.json?limit=1")
            append("&fields=title,author_name,publisher,first_publish_year,language,cover_i,subject,isbn")
            append("&title=")
            append(encode(titleQuery))
            if (book.author != UnknownAuthor && book.author.isNotBlank()) {
                append("&author=")
                append(encode(book.author))
            }
        }
        val root = JSONObject(readText(endpoint, MaxResponseBytes))
        val document = root.optJSONArray("docs")?.optJSONObject(0)
            ?: return book.copy(lastMetadataLookupEpochMs = attemptedAt)

        val author = document.firstString("author_name")
        val publisher = document.firstString("publisher")
        val language = document.firstString("language")
        val isbn = document.firstString("isbn")
        val publishedYear = document.optInt("first_publish_year").takeIf { it > 0 }
        val subjects = document.optJSONArray("subject")?.let { array ->
            buildList {
                for (index in 0 until minOf(array.length(), 3)) add(array.getString(index))
            }
        }.orEmpty()
        val coverPath = if (book.coverPath != null) {
            book.coverPath
        } else {
            document.optLong("cover_i").takeIf { it > 0 }?.let { coverId ->
                runCatching {
                    BookCoverCache.save(
                        context,
                        book.id,
                        readBytes("https://covers.openlibrary.org/b/id/$coverId-M.jpg", MaxCoverBytes),
                    )
                }.getOrNull()
            }
        }
        val changed = (book.author == UnknownAuthor && !author.isNullOrBlank()) ||
            (book.publisher == null && publisher != null) ||
            (book.language == null && language != null) ||
            (book.publishedYear == null && publishedYear != null) ||
            (book.isbn == null && isbn != null) ||
            (book.subjects.isEmpty() && subjects.isNotEmpty()) ||
            (book.coverPath == null && coverPath != null)

        return book.copy(
            author = if (book.author == UnknownAuthor) author ?: book.author else book.author,
            publisher = book.publisher ?: publisher,
            language = book.language ?: language,
            publishedYear = book.publishedYear ?: publishedYear,
            isbn = book.isbn ?: isbn,
            subjects = if (book.subjects.isEmpty()) subjects else book.subjects,
            coverPath = coverPath,
            metadataSource = if (changed) "EPUB + Open Library" else book.metadataSource,
            lastMetadataLookupEpochMs = attemptedAt,
        )
    }

    private fun readText(url: String, maxBytes: Int): String = readBytes(url, maxBytes).toString(Charsets.UTF_8)

    private fun readBytes(url: String, maxBytes: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 7_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", UserAgent)
        return try {
            require(connection.responseCode in 200..299) { "Metadata service returned ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "Metadata response is unexpectedly large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.firstString(key: String): String? =
        optJSONArray(key)?.optString(0)?.takeIf { it.isNotBlank() }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    const val UnknownAuthor = "Unknown author"
}
