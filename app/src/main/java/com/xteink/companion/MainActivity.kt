package com.xteink.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.xteink.companion.data.BookLibraryRepository
import com.xteink.companion.data.EpubMetadataReader
import com.xteink.companion.data.OpenLibraryMetadataClient
import com.xteink.companion.ui.CompanionViewModel
import com.xteink.companion.ui.CompanionVisualTheme
import com.xteink.companion.ui.X3CompanionApp
import com.xteink.companion.ui.theme.X3CompanionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CompanionViewModel>()
    private lateinit var bookLibrary: BookLibraryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bookLibrary = BookLibraryRepository(this)
        viewModel.restoreBooks(bookLibrary.load())
        refreshMissingBookMetadata()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val epubPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNotEmpty()) importEpubs(uris)
            }
            SideEffect {
                val lightSystemBars = state.visualTheme == CompanionVisualTheme.Expressive
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
            X3CompanionTheme(visualTheme = state.visualTheme) {
                X3CompanionApp(
                    state = state,
                    onSetVisualTheme = viewModel::setVisualTheme,
                    onSetDuration = viewModel::setDuration,
                    onStartFocus = viewModel::startFocus,
                    onTogglePause = viewModel::togglePause,
                    onEndFocus = viewModel::endFocus,
                    onResetFocus = viewModel::resetFocus,
                    onShowTools = viewModel::showTools,
                    onShowRead = viewModel::showRead,
                    onShowFocus = viewModel::showFocus,
                    onSetReadQuery = viewModel::setReadQuery,
                    onSetReadFilter = viewModel::setReadFilter,
                    onOpenEpub = {
                        epubPicker.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/zip",
                                "application/octet-stream",
                            ),
                        )
                    },
                    onOpenPasses = viewModel::openPasses,
                    onShowToolHub = viewModel::showToolHub,
                    onSelectPass = viewModel::selectPass,
                    onSetTicketMode = viewModel::setTicketMode,
                    onSendTicket = viewModel::sendTicket,
                    onShowSettings = viewModel::showSettings,
                    onDismissNotice = viewModel::dismissNotice,
                )
            }
        }
    }

    private fun importEpubs(uris: List<android.net.Uri>) {
        lifecycleScope.launch {
            viewModel.setImporting(true)
            val outcome = withContext(Dispatchers.IO) {
                val existingIds = bookLibrary.load().mapTo(mutableSetOf()) { it.id }
                val newBooks = mutableListOf<com.xteink.companion.ui.ImportedBookUiState>()
                var duplicates = 0
                var failed = 0

                uris.distinct().forEach { uri ->
                    val id = EpubMetadataReader.idForUri(uri)
                    if (id in existingIds) {
                        duplicates += 1
                        return@forEach
                    }
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val parsed = runCatching { EpubMetadataReader.read(this@MainActivity, uri) }.getOrElse {
                        failed += 1
                        return@forEach
                    }
                    val enriched = if (OpenLibraryMetadataClient.shouldEnrich(parsed)) {
                        runCatching { OpenLibraryMetadataClient.enrich(this@MainActivity, parsed) }
                            .getOrElse { parsed.copy(lastMetadataLookupEpochMs = System.currentTimeMillis()) }
                    } else {
                        parsed
                    }
                    newBooks += enriched
                    existingIds += id
                    if (OpenLibraryMetadataClient.shouldEnrich(parsed)) delay(1_100)
                }
                val library = if (newBooks.isEmpty()) bookLibrary.load() else bookLibrary.upsertAll(newBooks)
                ImportOutcome(library, newBooks.size, duplicates, failed)
            }
            viewModel.restoreBooks(outcome.library)
            viewModel.setImporting(false)
            viewModel.reportImportResult(outcome.added, outcome.duplicates, outcome.failed)
        }
    }

    private fun refreshMissingBookMetadata() {
        lifecycleScope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                val books = bookLibrary.load()
                var changed = false
                books.forEach { book ->
                    if (!OpenLibraryMetadataClient.shouldEnrich(book)) return@forEach
                    val enriched = runCatching { OpenLibraryMetadataClient.enrich(this@MainActivity, book) }
                        .getOrElse { book.copy(lastMetadataLookupEpochMs = System.currentTimeMillis()) }
                    bookLibrary.upsertAll(listOf(enriched))
                    changed = true
                    delay(1_100)
                }
                if (changed) bookLibrary.load() else books
            }
            viewModel.restoreBooks(refreshed)
        }
    }

    private data class ImportOutcome(
        val library: List<com.xteink.companion.ui.ImportedBookUiState>,
        val added: Int,
        val duplicates: Int,
        val failed: Int,
    )
}
