package com.xteink.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.xteink.companion.ui.ImportedBookUiState

class OpenLibraryMetadataClientTest {
    @Test
    fun `metadata lookup removes distribution and trailing author tags`() {
        assertEquals(
            listOf(
                "Project Hail Mary",
                "Project Hail Mary (Andy Weir)",
                "Project Hail Mary (Andy Weir) (z-library.sk, 1lib.sk, z-lib.sk)",
            ),
            OpenLibraryMetadataClient.metadataTitleCandidates(
                "Project Hail Mary (Andy Weir) (z-library.sk, 1lib.sk, z-lib.sk)",
            ),
        )
    }

    @Test
    fun `missing cover becomes eligible again after one day`() {
        val attemptedAt = 1_000L
        val book = ImportedBookUiState(
            id = "hail-mary",
            title = "Project Hail Mary",
            author = "On X3",
            fileName = "Project Hail Mary.epub",
            lastMetadataLookupEpochMs = attemptedAt,
        )

        assertFalse(OpenLibraryMetadataClient.shouldEnrich(book, attemptedAt + 24L * 60L * 60L * 1_000L - 1L))
        assertTrue(OpenLibraryMetadataClient.shouldEnrich(book, attemptedAt + 24L * 60L * 60L * 1_000L))
    }
}
