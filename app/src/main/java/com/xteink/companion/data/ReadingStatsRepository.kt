package com.xteink.companion.data

import android.content.Context
import com.xteink.companion.protocol.ReadingStatsChunkPayload
import org.json.JSONArray
import org.json.JSONObject

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
    fun accept(chunk: ReadingStatsChunkPayload): ReadingSessionStat? {
        val existing = load()
        existing.firstOrNull { it.id == chunk.sessionId }?.let { return it }
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
        val sessions = (existing.filterNot { it.id == session.id } + session)
            .sortedByDescending { it.endedAtEpochMs }
            .take(MaxSessions)
        if (!save(sessions)) return null
        pending.remove(chunk.sessionId)
        return session
    }

    private fun save(sessions: List<ReadingSessionStat>): Boolean {
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        // X3 deletes a queued session as soon as Android ACKs it. commit() is
        // intentionally synchronous so accept() cannot authorize that ACK
        // until the complete JSON is durably written.
        return preferences.edit().putString(SessionsKey, array.toString()).commit()
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
        const val MinimumPageSecondsKey = "minimum_page_seconds"
        const val MaxSessions = 500
        const val DefaultMinimumPageSeconds = 5
        val MinimumPageSecondsOptions = setOf(0, 5, 10, 15)
        const val ValidEpochMs = 1_577_836_800_000L // 2020-01-01
    }
}
