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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.xteink.companion.data.BookLibraryRepository
import com.xteink.companion.data.BluetoothCompanionClient
import com.xteink.companion.data.EpubMetadataReader
import com.xteink.companion.data.EpubFolderScanner
import com.xteink.companion.data.OpenLibraryMetadataClient
import com.xteink.companion.ui.CompanionViewModel
import com.xteink.companion.ui.CompanionVisualTheme
import com.xteink.companion.ui.X3CompanionApp
import com.xteink.companion.ui.components.SetupScreen
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
        val linkedFolder = bookLibrary.linkedFolderUri()
        viewModel.setLibrarySyncState(syncing = linkedFolder != null, folderLinked = linkedFolder != null)
        if (linkedFolder != null) syncLinkedFolder(showNotice = false) else refreshMissingBookMetadata()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val setupPreferences = remember { getSharedPreferences("xtraordinary_setup", MODE_PRIVATE) }
            var setupComplete by rememberSaveable {
                mutableStateOf(setupPreferences.getBoolean("setup_complete", false))
            }
            var pendingDeviceModel by rememberSaveable { mutableStateOf<String?>(null) }
            val nearbyPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                val model = pendingDeviceModel
                pendingDeviceModel = null
                if (model != null && grants.values.all { it }) viewModel.connectDevice(model)
            }
            val connectDevice: (String) -> Unit = { model ->
                if (viewModel.hasCompanionPermissions()) {
                    viewModel.connectDevice(model)
                } else {
                    pendingDeviceModel = model
                    nearbyPermissionLauncher.launch(BluetoothCompanionClient.requiredPermissions())
                }
            }
            val epubPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNotEmpty()) importEpubs(uris)
            }
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    bookLibrary.setLinkedFolderUri(uri.toString())
                    syncLinkedFolder(showNotice = true)
                }
            }
            SideEffect {
                val lightSystemBars = state.visualTheme == CompanionVisualTheme.Expressive
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
            X3CompanionTheme(visualTheme = state.visualTheme) {
                if (setupComplete) {
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
                        onSetReadSort = viewModel::setReadSort,
                        onSetReadService = viewModel::setReadService,
                        onSetOnX3Only = viewModel::setOnX3Only,
                        onDeleteBooksFromX3 = viewModel::requestDeleteBooksFromX3,
                        onChooseBookFolder = { folderPicker.launch(null) },
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
                        onOpenSetup = {
                            viewModel.showSettings(false)
                            setupPreferences.edit().putBoolean("setup_complete", false).apply()
                            setupComplete = false
                        },
                        onDismissNotice = viewModel::dismissNotice,
                        onConnectDevice = connectDevice,
                        onCheckFirmware = viewModel::checkLatestFirmware,
                        onFlashFirmware = viewModel::flashLatestFirmware,
                    )
                } else {
                    SetupScreen(
                        folderLinked = state.read.folderLinked,
                        device = state.device,
                        isDeviceConnected = state.isX3Connected,
                        onConnectDevice = connectDevice,
                        onChooseBookFolder = { folderPicker.launch(null) },
                        onFinish = {
                            setupPreferences.edit().putBoolean("setup_complete", true).apply()
                            setupComplete = true
                        },
                    )
                }
            }
        }
    }

    private fun syncLinkedFolder(showNotice: Boolean) {
        val folder = bookLibrary.linkedFolderUri()?.let(android.net.Uri::parse) ?: return
        lifecycleScope.launch {
            viewModel.setLibrarySyncState(syncing = true, folderLinked = true)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val candidates = EpubFolderScanner.scan(this@MainActivity, folder)
                    val current = bookLibrary.load()
                    val merged = current.associateByTo(linkedMapOf()) { it.id }
                    val scannedIds = mutableSetOf<String>()
                    var added = 0
                    var failed = 0
                    var enrichmentBudget = 10

                    candidates.forEach { candidate ->
                        val id = EpubMetadataReader.idForUri(candidate.uri)
                        scannedIds += id
                        val existing = merged[id]
                        if (existing != null) {
                            merged[id] = existing.copy(
                                isOnPhone = true,
                                sourceFolderUri = folder.toString(),
                                fileSizeBytes = candidate.sizeBytes ?: existing.fileSizeBytes,
                                fileModifiedAtEpochMs = candidate.modifiedAtEpochMs ?: existing.fileModifiedAtEpochMs,
                            )
                            return@forEach
                        }

                        val parsed = runCatching { EpubMetadataReader.read(this@MainActivity, candidate.uri) }
                            .getOrElse {
                                failed += 1
                                return@forEach
                            }
                            .copy(
                                isOnPhone = true,
                                sourceFolderUri = folder.toString(),
                                fileSizeBytes = candidate.sizeBytes,
                                fileModifiedAtEpochMs = candidate.modifiedAtEpochMs,
                            )
                        val enriched = if (enrichmentBudget > 0 && OpenLibraryMetadataClient.shouldEnrich(parsed)) {
                            enrichmentBudget -= 1
                            runCatching { OpenLibraryMetadataClient.enrich(this@MainActivity, parsed) }
                                .getOrElse { parsed.copy(lastMetadataLookupEpochMs = System.currentTimeMillis()) }
                                .also { delay(1_100) }
                        } else {
                            parsed
                        }
                        merged[id] = enriched
                        added += 1
                    }

                    merged.replaceAll { _, book ->
                        if (book.sourceFolderUri == folder.toString() && book.id !in scannedIds) {
                            book.copy(isOnPhone = false)
                        } else {
                            book
                        }
                    }

                    val library = merged.values.sortedByDescending { it.importedAtEpochMs }
                    bookLibrary.save(library)
                    FolderSyncOutcome(library, candidates.size, added, failed)
                }
            }
            result.onSuccess { outcome ->
                viewModel.restoreBooks(outcome.library)
                viewModel.setLibrarySyncState(syncing = false, folderLinked = true)
                if (showNotice) viewModel.reportFolderSync(outcome.found, outcome.added)
                refreshMissingBookMetadata()
            }.onFailure {
                viewModel.setLibrarySyncState(syncing = false, folderLinked = false)
                viewModel.reportEpubImportFailure()
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

    private data class FolderSyncOutcome(
        val library: List<com.xteink.companion.ui.ImportedBookUiState>,
        val found: Int,
        val added: Int,
        val failed: Int,
    )
}
