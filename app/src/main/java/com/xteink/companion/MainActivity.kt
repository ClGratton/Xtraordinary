package com.xteink.companion

import android.accounts.Account
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
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
import com.xteink.companion.data.FlightPassImporter
import com.xteink.companion.data.FlightPassPhotoImporter
import com.xteink.companion.data.CloudBackupState
import com.xteink.companion.data.FirmwareSource
import com.xteink.companion.data.GoogleDriveReadingSync
import com.xteink.companion.data.OpenLibraryMetadataClient
import com.xteink.companion.monetization.DistributionMonetizationGateway
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.xteink.companion.ui.CompanionViewModel
import com.xteink.companion.ui.CompanionColorMode
import com.xteink.companion.ui.CompanionColorModeBoundary
import com.xteink.companion.ui.X3CompanionApp
import com.xteink.companion.ui.components.SetupScreen
import com.xteink.companion.ui.theme.X3CompanionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PrepareX3ResetAction = "com.xteink.companion.action.PREPARE_X3_RESET"
private const val ReadX3CrashReportAction = "com.xteink.companion.action.READ_X3_CRASH_REPORT"
private const val ReadX3DiagnosticsAction = "com.xteink.companion.action.READ_X3_DIAGNOSTICS"
private const val X3MaintenanceLeaseAction = "com.xteink.companion.action.X3_MAINTENANCE_LEASE"
private const val DeployLogTag = "XteinkDeploy"
private const val FlightImportLogTag = "FlightPassImport"
private const val GoogleBackupLogTag = "GoogleBackup"

internal fun googleAuthorizationFailureMessage(statusCode: Int?): String = when (statusCode) {
    CommonStatusCodes.DEVELOPER_ERROR -> "Google backup isn't configured for this build"
    CommonStatusCodes.CANCELED -> "Google backup authorization was canceled"
    else -> "Google backup was not authorized"
}

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CompanionViewModel>()
    private lateinit var bookLibrary: BookLibraryRepository
    private lateinit var googleReadingSync: GoogleDriveReadingSync
    private val monetizationGateway by lazy { DistributionMonetizationGateway() }
    private var externalResetPreparationRequested = false

    private enum class CloudAction { Sync, Delete }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monetizationGateway.start(this)
        enableEdgeToEdge()
        bookLibrary = BookLibraryRepository(this)
        googleReadingSync = GoogleDriveReadingSync(this)
        viewModel.restoreBooks(bookLibrary.load())
        val linkedFolder = bookLibrary.linkedFolderUri()
        viewModel.setLibrarySyncState(syncing = linkedFolder != null, folderLinked = linkedFolder != null)
        if (linkedFolder != null) syncLinkedFolder(showNotice = false) else refreshMissingBookMetadata()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val monetizationState by monetizationGateway.state.collectAsStateWithLifecycle()
            val setupPreferences = remember { getSharedPreferences("xtraordinary_setup", MODE_PRIVATE) }
            var setupComplete by rememberSaveable {
                mutableStateOf(setupPreferences.getBoolean("setup_complete", false))
            }
            var pendingDeviceModel by rememberSaveable { mutableStateOf<String?>(null) }
            var pendingLocalFirmwareModel by rememberSaveable { mutableStateOf<String?>(null) }
            var cloudState by remember { mutableStateOf(googleReadingSync.loadState()) }
            var cloudConsentAccepted by rememberSaveable {
                mutableStateOf(googleReadingSync.consentAccepted())
            }
            var pendingCloudAction by rememberSaveable { mutableStateOf(CloudAction.Sync) }

            fun setCloudMessage(message: String, needsAuthorization: Boolean = false) {
                cloudState = cloudState.copy(
                    syncing = false,
                    needsAuthorization = needsAuthorization,
                    message = message,
                )
            }

            fun finishCloudAction(accessToken: String) {
                val action = pendingCloudAction
                cloudState = cloudState.copy(syncing = true, message = null)
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            when (action) {
                                CloudAction.Sync -> googleReadingSync.sync(accessToken)
                                CloudAction.Delete -> {
                                    googleReadingSync.deleteCloudBackup(accessToken)
                                    null
                                }
                            }
                        }
                    }.onSuccess { result ->
                        if (action == CloudAction.Sync) {
                            viewModel.reloadReadingStats()
                            cloudState = googleReadingSync.loadState().copy(
                                message = if ((result?.remoteSessionsAdded ?: 0) > 0) {
                                    "Synced · restored ${result?.remoteSessionsAdded} sessions"
                                } else {
                                    "Reading history is up to date"
                                },
                            )
                        } else {
                            val email = cloudState.accountEmail
                            cloudState = CloudBackupState(message = "Cloud backup deleted and Google disconnected")
                            if (email != null) {
                                val revoke = RevokeAccessRequest.builder()
                                    .setAccount(Account(email, "com.google"))
                                    .setScopes(GoogleDriveReadingSync.Scopes.map(::Scope))
                                    .build()
                                Identity.getAuthorizationClient(this@MainActivity).revokeAccess(revoke)
                            }
                        }
                    }.onFailure { error ->
                        setCloudMessage(error.message ?: "Google backup could not be updated", needsAuthorization = true)
                    }
                }
            }

            val googleAuthorizationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { activityResult ->
                runCatching {
                    Identity.getAuthorizationClient(this@MainActivity)
                        .getAuthorizationResultFromIntent(activityResult.data)
                }.onSuccess { result ->
                    result.accessToken?.let(::finishCloudAction)
                        ?: setCloudMessage("Google did not return an access token", needsAuthorization = true)
                }.onFailure { error ->
                    val statusCode = (error as? ApiException)?.statusCode
                    Log.w(
                        GoogleBackupLogTag,
                        "Authorization failed: ${error.javaClass.simpleName}, status=$statusCode",
                    )
                    setCloudMessage(
                        googleAuthorizationFailureMessage(statusCode),
                        needsAuthorization = true,
                    )
                }
            }

            fun requestGoogleAuthorization(action: CloudAction, interactive: Boolean) {
                pendingCloudAction = action
                cloudState = cloudState.copy(syncing = true, message = null)
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(GoogleDriveReadingSync.Scopes.map(::Scope))
                    .build()
                Identity.getAuthorizationClient(this@MainActivity).authorize(request)
                    .addOnSuccessListener { result ->
                        when {
                            result.hasResolution() && interactive -> {
                                cloudState = cloudState.copy(syncing = false)
                                googleAuthorizationLauncher.launch(
                                    IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build(),
                                )
                            }
                            result.hasResolution() -> setCloudMessage(
                                "Reconnect Google in Settings to resume backup",
                                needsAuthorization = true,
                            )
                            result.accessToken != null -> finishCloudAction(result.accessToken!!)
                            else -> setCloudMessage("Google did not return an access token", needsAuthorization = true)
                        }
                    }
                    .addOnFailureListener { error ->
                        setCloudMessage(error.message ?: "Google backup is unavailable", needsAuthorization = true)
                    }
            }
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
            val flightPassPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) importFlightPass(uri)
            }
            val flightPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) importFlightPassPhoto(uri)
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
            var themeDisplayedPosition by rememberSaveable { mutableFloatStateOf(state.colorMode.ordinal.toFloat()) }
            LaunchedEffect(state.colorMode) {
                themeDisplayedPosition = state.colorMode.ordinal.toFloat()
            }
            SideEffect {
                val lightSystemBars = CompanionColorModeBoundary.modeForDisplayedPosition(
                    state.colorMode,
                    themeDisplayedPosition,
                ) == CompanionColorMode.Light
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
            val localFirmwarePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                val model = pendingLocalFirmwareModel
                pendingLocalFirmwareModel = null
                if (uri != null && model != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    viewModel.prepareLocalFirmware(model, uri)
                }
            }
            val checkFirmware: (String, FirmwareSource) -> Unit = { model, source ->
                if (source == FirmwareSource.LocalFile) {
                    pendingLocalFirmwareModel = model
                    localFirmwarePicker.launch(arrayOf("application/octet-stream", "application/x-binary"))
                } else {
                    viewModel.checkFirmware(model, source)
                }
            }
            val readingHistorySignature = state.readingStats.sessions.joinToString("|") {
                "${it.id}:${it.endedAtEpochMs}:${it.pages.size}"
            }
            LaunchedEffect(cloudState.enabled, readingHistorySignature) {
                if (cloudState.enabled && !cloudState.syncing) {
                    requestGoogleAuthorization(CloudAction.Sync, interactive = false)
                }
            }
            X3CompanionTheme(
                visualTheme = state.visualTheme,
                colorMode = state.colorMode,
                colorModeProgress = themeDisplayedPosition,
            ) {
                if (setupComplete) {
                    X3CompanionApp(
                        state = state,
                        onSetVisualTheme = viewModel::setVisualTheme,
                        onSetColorMode = viewModel::setColorMode,
                        onThemeDisplayedPosition = { themeDisplayedPosition = it },
                        onSetRadioPolicy = viewModel::setRadioPolicy,
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
                        onSetReadLocation = viewModel::setReadLocation,
                        onUploadBooksToX3 = viewModel::requestUploadBooksToX3,
                        onCancelBookUpload = viewModel::cancelBookUpload,
                        onDismissDirectBookUploadOffer = viewModel::dismissDirectBookUploadOffer,
                        onDeleteBooksFromX3 = viewModel::requestDeleteBooksFromX3,
                        onChooseBookFolder = { folderPicker.launch(null) },
                        onOpenEpub = {
                            epubPicker.launch(
                                arrayOf("application/epub+zip"),
                            )
                        },
                        onOpenPasses = viewModel::openPasses,
                        onOpenStats = viewModel::openStats,
                        onSetReadingStatsView = viewModel::setReadingStatsView,
                        onSelectReadingSession = viewModel::selectReadingSession,
                        onDeleteReadingSession = viewModel::deleteReadingSession,
                        onUndoReadingSessionDeletion = viewModel::undoReadingSessionDeletion,
                        onSetMinimumReadingPageSeconds = viewModel::setMinimumReadingPageSeconds,
                        onShowToolHub = viewModel::showToolHub,
                        onSelectPass = viewModel::selectPass,
                        onSetTicketMode = viewModel::setTicketMode,
                        onSendTicket = viewModel::sendTicket,
                        onRemoveTicket = viewModel::removeTicketFromX3,
                        onImportPhoto = {
                            flightPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onImportWalletLink = ::importFlightPassLink,
                        onImportPassFile = {
                            flightPassPicker.launch(
                                arrayOf(
                                    "application/vnd.apple.pkpass",
                                    "application/json",
                                    "text/plain",
                                ),
                            )
                        },
                        onShowSettings = viewModel::showSettings,
                        onOpenSetup = {
                            viewModel.showSettings(false)
                            setupPreferences.edit().putBoolean("setup_complete", false).apply()
                            setupComplete = false
                        },
                        onDismissNotice = viewModel::dismissNotice,
                        onConnectDevice = connectDevice,
                        onCheckFirmware = checkFirmware,
                        onFlashFirmware = viewModel::flashLatestFirmware,
                        onResetX3Setup = viewModel::resetX3SetupOverUsb,
                        cloudBackupState = cloudState,
                        onSyncGoogleBackup = {
                            googleReadingSync.recordConsent()
                            cloudConsentAccepted = true
                            requestGoogleAuthorization(CloudAction.Sync, interactive = true)
                        },
                        onDeleteGoogleBackup = {
                            requestGoogleAuthorization(CloudAction.Delete, interactive = true)
                        },
                        monetizationState = monetizationState,
                        onBuyAdFree = { monetizationGateway.buy(this@MainActivity) },
                        onRestorePurchase = monetizationGateway::restore,
                        onAdPrivacyOptions = { monetizationGateway.showPrivacyOptions(this@MainActivity) },
                        onOpenCommunitySource = {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    android.net.Uri.parse(BuildConfig.COMMUNITY_SOURCE_URL),
                                ),
                            )
                        },
                    )
                } else {
                    SetupScreen(
                        folderLinked = state.read.folderLinked,
                        device = state.device,
                        isDeviceTransportConnected = state.isX3TransportConnected,
                        onConnectDevice = connectDevice,
                        onCheckFirmware = checkFirmware,
                        onFlashFirmware = viewModel::flashLatestFirmware,
                        cloudBackupState = cloudState,
                        cloudConsentAccepted = cloudConsentAccepted,
                        onCloudConsentChanged = { cloudConsentAccepted = it },
                        onConnectGoogle = {
                            googleReadingSync.recordConsent()
                            requestGoogleAuthorization(CloudAction.Sync, interactive = true)
                        },
                        onChooseBookFolder = { folderPicker.launch(null) },
                        onFinish = {
                            setupPreferences.edit().putBoolean("setup_complete", true).apply()
                            setupComplete = true
                        },
                    )
                }
            }
        }
        if (!handleDiagnosticIntent(intent) && !handleExternalResetIntent(intent) && !handleMaintenanceIntent(intent)) handleSharedFlightPass(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleDiagnosticIntent(intent) && !handleExternalResetIntent(intent) && !handleMaintenanceIntent(intent)) handleSharedFlightPass(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!externalResetPreparationRequested) viewModel.onAppForegrounded()
    }

    override fun onStop() {
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    private fun handleExternalResetIntent(intent: Intent): Boolean {
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!isDebuggable || intent.action != PrepareX3ResetAction) {
            externalResetPreparationRequested = false
            return false
        }
        externalResetPreparationRequested = true
        lifecycleScope.launch {
            runCatching { viewModel.prepareForExternalDeviceReset() }
                .onSuccess { Log.i(DeployLogTag, "PERIPHERAL_RESET_READY") }
                .onFailure { Log.e(DeployLogTag, "PERIPHERAL_RESET_FAILED", it) }
        }
        return true
    }

    private fun handleDiagnosticIntent(intent: Intent): Boolean {
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!isDebuggable || intent.action !in setOf(ReadX3CrashReportAction, ReadX3DiagnosticsAction)) return false
        // Keep normal foreground reconnect and durable USB work from racing the
        // diagnostic's exclusive serial request.
        externalResetPreparationRequested = true
        lifecycleScope.launch {
            val diagnostics = intent.action == ReadX3DiagnosticsAction
            runCatching {
                if (diagnostics) viewModel.readUsbDiagnostics() else viewModel.readUsbCrashReport()
            }.onSuccess {
                val label = if (diagnostics) "X3_DIAGNOSTICS" else "X3_CRASH_REPORT"
                Log.i(DeployLogTag, "${label}_START")
                it.lineSequence().forEach { line -> Log.i(DeployLogTag, line) }
                Log.i(DeployLogTag, "${label}_END")
            }.onFailure {
                val label = if (diagnostics) "X3_DIAGNOSTICS" else "X3_CRASH_REPORT"
                Log.e(DeployLogTag, "${label}_FAILED", it)
            }
        }
        return true
    }

    private fun handleMaintenanceIntent(intent: Intent): Boolean {
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!isDebuggable || intent.action != X3MaintenanceLeaseAction) return false
        externalResetPreparationRequested = true
        val seconds = intent.getIntExtra("seconds", 0).coerceIn(0, 600)
        lifecycleScope.launch {
            runCatching {
                if (seconds == 0) viewModel.endMaintenanceLease()
                else viewModel.beginMaintenanceLease(seconds)
            }.onSuccess {
                Log.i(DeployLogTag, if (seconds == 0) "X3_MAINTENANCE_RESTORED" else "X3_MAINTENANCE_ACTIVE seconds=$seconds")
            }.onFailure { Log.e(DeployLogTag, "X3_MAINTENANCE_FAILED", it) }
        }
        return true
    }

    private fun importFlightPass(uri: android.net.Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { FlightPassImporter.read(this@MainActivity, uri) }
            }
            result.onSuccess(viewModel::importFlightPass).onFailure(viewModel::reportFlightPassImportFailure)
        }
    }

    override fun onDestroy() {
        monetizationGateway.close()
        super.onDestroy()
    }

    private fun importFlightPassPhoto(uri: android.net.Uri) {
        lifecycleScope.launch {
            val result = runCatching { FlightPassPhotoImporter.read(this@MainActivity, uri) }
            result.onSuccess { pass ->
                Log.i(FlightImportLogTag, "Imported ${pass.barcodeFormat.displayName} pass from photo")
                viewModel.importFlightPass(pass)
            }.onFailure { error ->
                Log.w(FlightImportLogTag, "Photo import failed: ${error.message}")
                viewModel.reportFlightPassImportFailure(error)
            }
        }
    }

    private fun importFlightPassLink(link: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { FlightPassImporter.readText(link) }
            }
            result.onSuccess(viewModel::importFlightPass).onFailure(viewModel::reportFlightPassImportFailure)
        }
    }

    private fun handleSharedFlightPass(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        when {
            intent.type?.startsWith("image/") == true -> {
                @Suppress("DEPRECATION")
                val image = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: return
                importFlightPassPhoto(image)
            }
            intent.type == "text/plain" -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (sharedText.isNotBlank()) importFlightPassLink(sharedText)
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
                                .getOrElse { parsed }
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
                runCatching {
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
                                .getOrElse { parsed }
                        } else {
                            parsed
                        }
                        newBooks += enriched
                        existingIds += id
                        if (OpenLibraryMetadataClient.shouldEnrich(parsed)) delay(1_100)
                    }
                    val library = if (newBooks.isEmpty()) bookLibrary.load() else bookLibrary.upsertAll(newBooks)
                    ImportOutcome(library, newBooks.mapTo(linkedSetOf()) { it.id }, duplicates, failed)
                }
            }
            viewModel.setImporting(false)
            outcome.onSuccess { imported ->
                viewModel.restoreBooks(imported.library)
                viewModel.reportImportResult(imported.added, imported.duplicates, imported.failed)
                viewModel.offerImportedBooksForDirectUpload(imported.importedBookIds)
            }.onFailure {
                viewModel.reportEpubImportFailure()
            }
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
                        .getOrElse { book }
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
        val importedBookIds: Set<String>,
        val duplicates: Int,
        val failed: Int,
    ) {
        val added: Int get() = importedBookIds.size
    }

    private data class FolderSyncOutcome(
        val library: List<com.xteink.companion.ui.ImportedBookUiState>,
        val found: Int,
        val added: Int,
        val failed: Int,
    )
}
