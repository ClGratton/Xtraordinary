package com.xteink.companion.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

data class EpubDocumentCandidate(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val modifiedAtEpochMs: Long?,
)

object EpubFolderScanner {
    private const val MaxDocuments = 5_000
    private const val MaxDepth = 12

    fun scan(context: Context, treeUri: Uri): List<EpubDocumentCandidate> {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val directories = ArrayDeque<Pair<String, Int>>().apply { add(rootId to 0) }
        val books = mutableListOf<EpubDocumentCandidate>()
        var visited = 0

        while (directories.isNotEmpty() && visited < MaxDocuments) {
            val (parentId, depth) = directories.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, Projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext() && visited < MaxDocuments) {
                    visited += 1
                    val documentId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val mimeType = cursor.getString(mimeIndex).orEmpty()
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MaxDepth) directories.add(documentId to depth + 1)
                    } else if (mimeType == "application/epub+zip" || name.endsWith(".epub", ignoreCase = true)) {
                        books += EpubDocumentCandidate(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            displayName = name,
                            sizeBytes = cursor.optionalLong(sizeIndex),
                            modifiedAtEpochMs = cursor.optionalLong(modifiedIndex),
                        )
                    }
                }
            }
        }
        return books
    }

    private fun android.database.Cursor.optionalLong(index: Int): Long? =
        if (index >= 0 && !isNull(index)) getLong(index).takeIf { it > 0L } else null

    private val Projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
}
