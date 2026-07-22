package com.xteink.companion.data

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File

internal object BookCoverCache {
    private const val MaxCoverBytes = 8_000_000

    fun save(context: Context, bookId: String, bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MaxCoverBytes) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val directory = File(context.filesDir, "book-covers").apply { mkdirs() }
        val destination = File(directory, "$bookId.cover")
        val temporary = File(directory, "$bookId.cover.tmp")
        temporary.outputStream().use { it.write(bytes) }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        return destination.absolutePath
    }
}
