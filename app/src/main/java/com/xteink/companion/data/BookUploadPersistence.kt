package com.xteink.companion.data

import android.content.Context

/** Shared durable boundary for the activity/ViewModel and transfer service. */
object BookUploadPersistence {
    const val PreferencesName = "xtraordinary_connection"
    const val PendingIdsKey = "pending_book_upload_ids"
    const val MethodKey = "pending_book_upload_method"

    fun clear(context: Context) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .remove(PendingIdsKey)
            .remove(MethodKey)
            .commit()
    }
}
