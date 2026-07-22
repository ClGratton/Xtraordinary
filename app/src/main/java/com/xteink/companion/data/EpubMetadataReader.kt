package com.xteink.companion.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.xteink.companion.ui.ImportedBookUiState
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object EpubMetadataReader {
    private const val ContainerPath = "META-INF/container.xml"
    private const val MaxMetadataBytes = 2_000_000
    private const val MaxCoverBytes = 8_000_000

    fun read(context: Context, uri: Uri): ImportedBookUiState {
        val fileName = queryDisplayName(context, uri) ?: "Imported book.epub"
        val containerXml = readZipText(context, uri, ContainerPath)
        val packagePath = parsePackagePath(containerXml)
        val packageXml = readZipText(context, uri, packagePath)
        val packageData = parsePackage(packageXml)
        val bookId = idForUri(uri)
        val coverPath = packageData.coverHref?.let { href ->
            val coverEntry = resolveRelativeEntry(packagePath, href)
            runCatching {
                BookCoverCache.save(context, bookId, readZipBytes(context, uri, coverEntry, MaxCoverBytes))
            }.getOrNull()
        }

        return ImportedBookUiState(
            id = bookId,
            title = packageData.title.ifBlank { fileName.substringBeforeLast('.') },
            author = packageData.author.ifBlank { OpenLibraryMetadataClient.UnknownAuthor },
            fileName = fileName,
            sourceUri = uri.toString(),
            coverPath = coverPath,
            language = packageData.language.ifBlank { null },
            publisher = packageData.publisher.ifBlank { null },
            publishedYear = packageData.publishedYear,
            isbn = packageData.isbn,
            fileSizeBytes = queryFileSize(context, uri),
            importedAtEpochMs = System.currentTimeMillis(),
        )
    }

    fun idForUri(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }

    private fun readZipText(context: Context, uri: Uri, expectedPath: String): String {
        return readZipBytes(context, uri, expectedPath, MaxMetadataBytes).toString(Charsets.UTF_8)
    }

    private fun readZipBytes(context: Context, uri: Uri, expectedPath: String, limit: Int): ByteArray {
        val normalizedExpectedPath = expectedPath.trimStart('/')
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.trimStart('/') == normalizedExpectedPath) {
                        return readCurrentEntry(zip, limit)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        error("EPUB metadata entry is missing: $normalizedExpectedPath")
    }

    private fun readCurrentEntry(zip: ZipInputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "EPUB entry is unexpectedly large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun parsePackagePath(xml: String): String {
        val parser = safeParser(xml)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
                    ?: error("EPUB rootfile has no full-path")
            }
            parser.next()
        }
        error("EPUB container has no rootfile")
    }

    private fun parsePackage(xml: String): PackageData {
        val parser = safeParser(xml)
        var title = ""
        var author = ""
        var language = ""
        var publisher = ""
        var publishedYear: Int? = null
        var isbn: String? = null
        var coverId: String? = null
        val manifestItems = mutableListOf<ManifestItem>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':').lowercase()) {
                    "title" -> if (title.isBlank()) title = parser.nextText().trim()
                    "creator" -> if (author.isBlank()) author = parser.nextText().trim()
                    "language" -> if (language.isBlank()) language = parser.nextText().trim()
                    "publisher" -> if (publisher.isBlank()) publisher = parser.nextText().trim()
                    "date" -> if (publishedYear == null) {
                        publishedYear = parser.nextText().trim().take(4).toIntOrNull()
                    }
                    "identifier" -> {
                        val value = parser.nextText().trim()
                        val compact = value.replace(Regex("[^0-9Xx]"), "")
                        if (isbn == null && compact.length in setOf(10, 13)) isbn = compact
                    }
                    "meta" -> if (parser.getAttributeValue(null, "name") == "cover") {
                        coverId = parser.getAttributeValue(null, "content")
                    }
                    "item" -> manifestItems += ManifestItem(
                        id = parser.getAttributeValue(null, "id").orEmpty(),
                        href = parser.getAttributeValue(null, "href").orEmpty(),
                        mediaType = parser.getAttributeValue(null, "media-type").orEmpty(),
                        properties = parser.getAttributeValue(null, "properties").orEmpty(),
                    )
                }
            }
            parser.next()
        }
        val coverHref = manifestItems.firstOrNull { "cover-image" in it.properties.split(' ') }?.href
            ?: manifestItems.firstOrNull { it.id == coverId }?.href
            ?: manifestItems.firstOrNull {
                it.mediaType.startsWith("image/") && it.href.contains("cover", ignoreCase = true)
            }?.href
        return PackageData(title, author, language, publisher, publishedYear, isbn, coverHref)
    }

    private fun resolveRelativeEntry(packagePath: String, href: String): String {
        val cleanHref = href.substringBefore('#').substringBefore('?').replace("%20", " ")
        val directory = packagePath.substringBeforeLast('/', missingDelimiterValue = "")
        val parts = (if (directory.isBlank()) cleanHref else "$directory/$cleanHref").split('/')
        val resolved = mutableListOf<String>()
        parts.forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
                else -> resolved += part
            }
        }
        return resolved.joinToString("/")
    }

    private fun safeParser(xml: String): XmlPullParser {
        return Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(StringReader(xml))
        }
    }

    private data class PackageData(
        val title: String,
        val author: String,
        val language: String,
        val publisher: String,
        val publishedYear: Int?,
        val isbn: String?,
        val coverHref: String?,
    )

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )
}
