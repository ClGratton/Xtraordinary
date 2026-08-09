package com.xteink.companion.data

import android.content.Context
import com.xteink.companion.protocol.ReadingStatsChunkPayload
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class ReadingPageStat(
    val elapsedMs: Long,
    val words: Int,
    val pageNumber: Int,
) {
    val wordsPerMinute: Int?
        get() = if (words > 0 && elapsedMs > 0) ((words * 60_000L) / elapsedMs).toInt() else null
}

data class ReadingSessionStat(
    val id: UInt,
    val title: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val hasWordCounts: Boolean,
    val pages: List<ReadingPageStat>,
) {
    val durationMs: Long get() = pages.sumOf { it.elapsedMs }
    val wordCount: Int get() = pages.sumOf { it.words }
    val averageWordsPerMinute: Int?
        get() = if (hasWordCounts && durationMs > 0) ((wordCount * 60_000L) / durationMs).toInt() else null

    fun filteredForDisplay(
        minimumPageTimeMs: Long,
        maximumWordsPerMinute: Int = MaximumCredibleWordsPerMinute,
    ): ReadingSessionStat = copy(
        pages = pages.filter { page ->
            val longEnough = page.elapsedMs >= minimumPageTimeMs
            val crediblePace = !hasWordCounts || page.wordsPerMinute?.let { it <= maximumWordsPerMinute } != false
            longEnough && crediblePace
        },
    )

    companion object {
        const val MaximumCredibleWordsPerMinute = 1_000
    }
}

class ReadingStatsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("reading_stats_v1", Context.MODE_PRIVATE)
    private val pending = mutableMapOf<UInt, PendingSession>()

    fun load(): List<ReadingSessionStat> {
        val raw = preferences.getString(SessionsKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { add(array.getJSONObject(it).toSession()) }
            }.sortedByDescending { it.endedAtEpochMs }
        }.getOrDefault(emptyList())
    }

    fun minimumPageSeconds(): Int = preferences.getInt(MinimumPageSecondsKey, DefaultMinimumPageSeconds)
        .takeIf { it in MinimumPageSecondsOptions }
        ?: DefaultMinimumPageSeconds

    fun setMinimumPageSeconds(seconds: Int) {
        preferences.edit().putInt(
            MinimumPageSecondsKey,
            seconds.takeIf { it in MinimumPageSecondsOptions } ?: DefaultMinimumPageSeconds,
        ).apply()
    }

    @Synchronized
    fun deleteSession(sessionId: UInt): ReadingSessionStat? {
        val sessions = load()
        val session = sessions.firstOrNull { it.id == sessionId } ?: return null
        val tombstones = loadTombstones().apply {
            put(session.contentFingerprint(), System.currentTimeMillis())
        }
        val deletedKey = session.contentFingerprint()
        check(saveState(sessions.filterNot { it.contentFingerprint() == deletedKey }, tombstones)) {
            "Could not persist the reading-session deletion"
        }
        return session
    }

    @Synchronized
    fun restoreSession(session: ReadingSessionStat) {
        val sessions = load()
        val key = session.contentFingerprint()
        val tombstones = loadTombstones().apply { remove(key) }
        val restored = (sessions.filterNot { it.contentFingerprint() == key } + session)
            .sortedByDescending { it.endedAtEpochMs }
            .take(MaxSessions)
        check(saveState(restored, tombstones)) { "Could not restore the reading session" }
    }

    @Synchronized
    fun accept(chunk: ReadingStatsChunkPayload): ReadingSessionStat? {
        val existing = load()
        existing.firstOrNull {
            it.id == chunk.sessionId &&
                it.title == chunk.title.ifBlank { "Untitled book" } &&
                it.startedAtEpochMs / 1_000L == chunk.startedEpochSeconds &&
                it.endedAtEpochMs / 1_000L == chunk.endedEpochSeconds &&
                it.pages.size == chunk.totalPages
        }?.let { return it }
        val assembly = pending.getOrPut(chunk.sessionId) {
            PendingSession(
                title = chunk.title,
                startedEpochSeconds = chunk.startedEpochSeconds,
                endedEpochSeconds = chunk.endedEpochSeconds,
                totalPages = chunk.totalPages,
                hasWordCounts = chunk.hasWordCounts,
            )
        }
        if (chunk.startIndex == 0 && assembly.pages.isNotEmpty()) assembly.pages.clear()
        if (chunk.startIndex != assembly.pages.size) return null
        assembly.pages += chunk.samples.map { ReadingPageStat(it.elapsedMs, it.words, it.pageNumber) }
        if (!chunk.isLastChunk || assembly.pages.size != assembly.totalPages) return null

        val activeDuration = assembly.pages.sumOf { it.elapsedMs }
        val receivedAt = System.currentTimeMillis()
        val endedAt = (assembly.endedEpochSeconds * 1_000L).takeIf { it >= ValidEpochMs } ?: receivedAt
        val startedAt = (assembly.startedEpochSeconds * 1_000L).takeIf { it in ValidEpochMs..endedAt }
            ?: (endedAt - activeDuration)
        val session = ReadingSessionStat(
            id = chunk.sessionId,
            title = assembly.title.ifBlank { "Untitled book" },
            startedAtEpochMs = startedAt,
            endedAtEpochMs = endedAt,
            hasWordCounts = assembly.hasWordCounts,
            pages = assembly.pages.toList(),
        )
        if (session.contentFingerprint() in loadTombstones()) {
            pending.remove(chunk.sessionId)
            return session
        }
        val sessions = (existing.filterNot { it.contentFingerprint() == session.contentFingerprint() } + session)
            .sortedByDescending { it.endedAtEpochMs }
            .take(MaxSessions)
        if (!save(sessions)) return null
        pending.remove(chunk.sessionId)
        return session
    }

    /**
     * Cloud backup is intentionally limited to reading history and its display
     * filter. EPUB files, covers, passes, device identifiers, and app settings
     * are not included.
     */
    fun exportCloudJson(): JSONObject = JSONObject().apply {
        put("schema", CloudSchema)
        put("minimumPageSeconds", minimumPageSeconds())
        put("sessions", JSONArray().apply {
            load().forEach { session ->
                put(session.toJson().put("key", session.contentFingerprint()))
            }
        })
        put("deleted", JSONArray().apply {
            loadTombstones().forEach { (key, deletedAt) ->
                put(JSONObject().put("key", key).put("deletedAt", deletedAt))
            }
        })
    }

    /** Merges immutable sessions and returns how many remote sessions were new. */
    @Synchronized
    fun mergeCloudJson(root: JSONObject): Int {
        val schema = root.optInt("schema", -1)
        require(schema in 1..CloudSchema) { "Unsupported reading backup schema" }
        val local = load()
        val byKey = local.associateByTo(linkedMapOf()) { it.contentFingerprint() }
        val tombstones = loadTombstones()
        if (schema >= 2) {
            val remoteDeleted = root.optJSONArray("deleted") ?: JSONArray()
            repeat(remoteDeleted.length()) { index ->
                val deletion = remoteDeleted.getJSONObject(index)
                val key = deletion.optString("key")
                if (key.isNotBlank()) {
                    tombstones[key] = maxOf(tombstones[key] ?: 0L, deletion.optLong("deletedAt", 0L))
                }
            }
        }
        val remote = root.optJSONArray("sessions") ?: JSONArray()
        var added = 0
        repeat(remote.length()) { index ->
            val session = remote.getJSONObject(index).toSession()
            val key = session.contentFingerprint()
            if (key !in byKey && key !in tombstones) {
                byKey[key] = session
                added += 1
            }
        }
        tombstones.keys.forEach(byKey::remove)
        val merged = byKey.values.sortedByDescending { it.endedAtEpochMs }.take(MaxSessions)
        check(saveState(merged, tombstones)) { "Could not persist merged reading history" }
        val remoteMinimum = root.optInt("minimumPageSeconds", minimumPageSeconds())
        if (remoteMinimum in MinimumPageSecondsOptions) setMinimumPageSeconds(remoteMinimum)
        return added
    }

    private fun save(sessions: List<ReadingSessionStat>): Boolean {
        return saveState(sessions, loadTombstones())
    }

    private fun saveState(sessions: List<ReadingSessionStat>, tombstones: Map<String, Long>): Boolean {
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        // X3 deletes a queued session as soon as Android ACKs it. commit() is
        // intentionally synchronous so accept() cannot authorize that ACK
        // until the complete JSON is durably written.
        val deleted = JSONArray()
        tombstones.entries.sortedByDescending { it.value }.take(MaxTombstones).forEach { (key, deletedAt) ->
            deleted.put(JSONObject().put("key", key).put("deletedAt", deletedAt))
        }
        return preferences.edit()
            .putString(SessionsKey, array.toString())
            .putString(DeletedKeysKey, deleted.toString())
            .commit()
    }

    private fun loadTombstones(): MutableMap<String, Long> {
        val raw = preferences.getString(DeletedKeysKey, null) ?: return linkedMapOf()
        return runCatching {
            val array = JSONArray(raw)
            linkedMapOf<String, Long>().apply {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val key = item.optString("key")
                    if (key.isNotBlank()) put(key, item.optLong("deletedAt", 0L))
                }
            }
        }.getOrDefault(linkedMapOf())
    }

    private fun ReadingSessionStat.toJson() = JSONObject().apply {
        put("id", id.toLong())
        put("title", title)
        put("started", startedAtEpochMs)
        put("ended", endedAtEpochMs)
        put("hasWords", hasWordCounts)
        put("pages", JSONArray().apply {
            pages.forEach { page ->
                put(JSONObject().apply {
                    put("elapsed", page.elapsedMs)
                    put("words", page.words)
                    put("page", page.pageNumber)
                })
            }
        })
    }

    private fun ReadingSessionStat.contentFingerprint(): String {
        val canonical = buildString {
            append(title).append('\u0000')
            append(startedAtEpochMs).append('\u0000')
            append(endedAtEpochMs).append('\u0000')
            append(hasWordCounts).append('\u0000')
            pages.forEach { page ->
                append(page.elapsedMs).append(':')
                append(page.words).append(':')
                append(page.pageNumber).append(';')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun JSONObject.toSession(): ReadingSessionStat {
        val pagesJson = getJSONArray("pages")
        val pages = buildList {
            repeat(pagesJson.length()) {
                val page = pagesJson.getJSONObject(it)
                add(ReadingPageStat(page.getLong("elapsed"), page.getInt("words"), page.getInt("page")))
            }
        }
        return ReadingSessionStat(
            id = getLong("id").toUInt(),
            title = getString("title"),
            startedAtEpochMs = getLong("started"),
            endedAtEpochMs = getLong("ended"),
            hasWordCounts = getBoolean("hasWords"),
            pages = pages,
        )
    }

    private data class PendingSession(
        val title: String,
        val startedEpochSeconds: Long,
        val endedEpochSeconds: Long,
        val totalPages: Int,
        val hasWordCounts: Boolean,
        val pages: MutableList<ReadingPageStat> = mutableListOf(),
    )

    private companion object {
        const val SessionsKey = "sessions"
        const val DeletedKeysKey = "deleted_session_keys"
        const val CloudSchema = 2
        const val MinimumPageSecondsKey = "minimum_page_seconds"
        const val MaxSessions = 500
        const val MaxTombstones = 2_000
        const val DefaultMinimumPageSeconds = 5
        val MinimumPageSecondsOptions = setOf(0, 5, 10, 15)
        const val ValidEpochMs = 1_577_836_800_000L // 2020-01-01
    }
}
