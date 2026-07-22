package com.xteink.companion.data

import android.content.Context
import androidx.core.content.edit
import com.xteink.companion.ui.ImportedBookUiState
import org.json.JSONArray
import org.json.JSONObject

class BookLibraryRepository(context: Context) {
    private val preferences = context.getSharedPreferences("book_library_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<ImportedBookUiState> {
        val raw = preferences.getString(BooksKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toBook())
                }
            }.sortedByDescending { it.importedAtEpochMs }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun upsertAll(incoming: List<ImportedBookUiState>): List<ImportedBookUiState> {
        val merged = (incoming + load())
            .distinctBy { it.id }
            .sortedByDescending { it.importedAtEpochMs }
        save(merged)
        return merged
    }

    @Synchronized
    fun save(books: List<ImportedBookUiState>) {
        val array = JSONArray()
        books.forEach { array.put(it.toJson()) }
        preferences.edit { putString(BooksKey, array.toString()) }
    }

    fun linkedFolderUri(): String? = preferences.getString(FolderKey, null)

    fun setLinkedFolderUri(uri: String) {
        preferences.edit { putString(FolderKey, uri) }
    }

    private fun ImportedBookUiState.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("fileName", fileName)
        put("sourceUri", sourceUri)
        putNullable("coverPath", coverPath)
        putNullable("language", language)
        putNullable("publisher", publisher)
        putNullable("publishedYear", publishedYear)
        putNullable("isbn", isbn)
        put("subjects", JSONArray(subjects))
        putNullable("fileSizeBytes", fileSizeBytes)
        put("metadataSource", metadataSource)
        put("importedAtEpochMs", importedAtEpochMs)
        putNullable("lastMetadataLookupEpochMs", lastMetadataLookupEpochMs)
        putNullable("fileModifiedAtEpochMs", fileModifiedAtEpochMs)
        put("isOnDevice", isOnDevice)
        putNullable("sourceFolderUri", sourceFolderUri)
    }

    private fun JSONObject.toBook() = ImportedBookUiState(
        id = getString("id"),
        title = getString("title"),
        author = getString("author"),
        fileName = getString("fileName"),
        sourceUri = optString("sourceUri"),
        coverPath = nullableString("coverPath"),
        language = nullableString("language"),
        publisher = nullableString("publisher"),
        publishedYear = nullableInt("publishedYear"),
        isbn = nullableString("isbn"),
        subjects = optJSONArray("subjects")?.let { array ->
            buildList {
                for (index in 0 until array.length()) add(array.getString(index))
            }
        }.orEmpty(),
        fileSizeBytes = nullableLong("fileSizeBytes"),
        metadataSource = optString("metadataSource", "EPUB"),
        importedAtEpochMs = optLong("importedAtEpochMs"),
        lastMetadataLookupEpochMs = nullableLong("lastMetadataLookupEpochMs"),
        fileModifiedAtEpochMs = nullableLong("fileModifiedAtEpochMs"),
        isOnDevice = if (has("isOnDevice")) optBoolean("isOnDevice") else true,
        sourceFolderUri = nullableString("sourceFolderUri"),
    )

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private companion object {
        const val BooksKey = "books"
        const val FolderKey = "linked_folder_uri"
    }
}
