package com.xteink.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatsFilterTest {
    @Test
    fun filtersShortVisitsAndImpossiblePaceWithoutChangingRawHistory() {
        val session = ReadingSessionStat(
            id = 1u,
            title = "Test",
            startedAtEpochMs = 0,
            endedAtEpochMs = 20_000,
            hasWordCounts = true,
            pages = listOf(
                ReadingPageStat(elapsedMs = 2_000, words = 20, pageNumber = 1),
                ReadingPageStat(elapsedMs = 6_000, words = 120, pageNumber = 2),
                ReadingPageStat(elapsedMs = 12_000, words = 80, pageNumber = 3),
            ),
        )

        val filtered = session.filteredForDisplay(minimumPageTimeMs = 5_000)

        assertEquals(listOf(3), filtered.pages.map { it.pageNumber })
        assertEquals(3, session.pages.size)
    }

    @Test
    fun bitmapSessionsOnlyApplyTheMinimumTimeFilter() {
        val session = ReadingSessionStat(
            id = 2u,
            title = "Bitmap",
            startedAtEpochMs = 0,
            endedAtEpochMs = 12_000,
            hasWordCounts = false,
            pages = listOf(
                ReadingPageStat(elapsedMs = 4_000, words = 0, pageNumber = 1),
                ReadingPageStat(elapsedMs = 8_000, words = 0, pageNumber = 2),
            ),
        )

        assertEquals(listOf(2), session.filteredForDisplay(5_000).pages.map { it.pageNumber })
    }
}
