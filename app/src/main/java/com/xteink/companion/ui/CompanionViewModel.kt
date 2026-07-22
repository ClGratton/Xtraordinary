package com.xteink.companion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CompanionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _uiState.update { state ->
                    if (state.focus.phase != FocusPhase.Running) return@update state
                    val nextRemaining = (state.focus.remainingSeconds - 1).coerceAtLeast(0)
                    state.copy(
                        focus = state.focus.copy(
                            remainingSeconds = nextRemaining,
                            phase = if (nextRemaining == 0) FocusPhase.Review else FocusPhase.Running,
                        ),
                    )
                }
            }
        }
    }

    fun setVisualTheme(theme: CompanionVisualTheme) {
        _uiState.update { it.copy(visualTheme = theme) }
    }

    fun setTask(task: String) {
        _uiState.update { state ->
            if (state.focus.phase != FocusPhase.Setup) state
            else state.copy(focus = state.focus.copy(task = task.take(80)))
        }
    }

    fun setDuration(minutes: Int) {
        val bounded = minutes.coerceIn(5, 60)
        _uiState.update { state ->
            if (state.focus.phase != FocusPhase.Setup) state
            else state.copy(
                focus = state.focus.copy(
                    selectedMinutes = bounded,
                    remainingSeconds = bounded * 60,
                ),
            )
        }
    }

    fun startFocus() {
        _uiState.update { state ->
            state.copy(
                surface = CompanionSurface.Focus,
                focus = state.focus.copy(
                    phase = FocusPhase.Running,
                    remainingSeconds = state.focus.selectedMinutes * 60,
                ),
                notice = UiNotice.FocusStartedWithoutX3,
            )
        }
    }

    fun togglePause() {
        _uiState.update { state ->
            val nextPhase = when (state.focus.phase) {
                FocusPhase.Running -> FocusPhase.Paused
                FocusPhase.Paused -> FocusPhase.Running
                else -> state.focus.phase
            }
            state.copy(focus = state.focus.copy(phase = nextPhase))
        }
    }

    fun endFocus() {
        _uiState.update { state ->
            state.copy(
                focus = state.focus.copy(
                    phase = FocusPhase.Setup,
                    remainingSeconds = state.focus.selectedMinutes * 60,
                ),
            )
        }
    }

    fun resetFocus() {
        _uiState.update { state ->
            state.copy(
                surface = CompanionSurface.Focus,
                focus = state.focus.copy(
                    phase = FocusPhase.Setup,
                    remainingSeconds = state.focus.selectedMinutes * 60,
                ),
            )
        }
    }

    fun showTools() {
        _uiState.update { it.copy(surface = CompanionSurface.Tools) }
    }

    fun showRead() {
        _uiState.update { it.copy(surface = CompanionSurface.Read) }
    }

    fun showFocus() {
        _uiState.update { it.copy(surface = CompanionSurface.Focus) }
    }

    fun openPasses() {
        _uiState.update {
            it.copy(surface = CompanionSurface.Tools, toolDestination = ToolDestination.Passes)
        }
    }

    fun showToolHub() {
        _uiState.update { it.copy(toolDestination = ToolDestination.Hub) }
    }

    fun setTicketMode(mode: TicketMode) {
        _uiState.update { it.copy(ticket = it.ticket.copy(mode = mode)) }
    }

    fun selectPass(passId: String) {
        _uiState.update { state ->
            if (state.ticket.passes.none { it.id == passId }) state
            else state.copy(ticket = state.ticket.copy(selectedPassId = passId))
        }
    }

    fun addImportedBook(book: ImportedBookUiState) {
        addImportedBooks(listOf(book))
    }

    fun addImportedBooks(books: List<ImportedBookUiState>) {
        _uiState.update { state ->
            state.copy(
                surface = CompanionSurface.Read,
                read = state.read.copy(
                    books = (books + state.read.books)
                        .distinctBy { it.id }
                        .sortedByDescending { it.importedAtEpochMs },
                ),
            )
        }
    }

    fun restoreBooks(books: List<ImportedBookUiState>) {
        _uiState.update { state ->
            state.copy(
                read = state.read.copy(
                    books = books.sortedByDescending { it.importedAtEpochMs },
                ),
            )
        }
    }

    fun setReadQuery(query: String) {
        _uiState.update { it.copy(read = it.read.copy(query = query.take(80))) }
    }

    fun setReadSort(sort: ReadSort) {
        _uiState.update { it.copy(read = it.read.copy(sort = sort)) }
    }

    fun setOnX3Only(enabled: Boolean) {
        _uiState.update { it.copy(read = it.read.copy(onX3Only = enabled)) }
    }

    fun requestDeleteBooksFromX3(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        _uiState.update { state ->
            if (!state.isX3Connected) {
                state.copy(notice = UiNotice.ConnectX3ToDelete)
            } else {
                state.copy(notice = UiNotice.X3DeleteQueued(bookIds.size))
            }
        }
    }

    fun setImporting(importing: Boolean) {
        _uiState.update { it.copy(read = it.read.copy(importing = importing)) }
    }

    fun setLibrarySyncState(syncing: Boolean, folderLinked: Boolean? = null) {
        _uiState.update { state ->
            state.copy(
                read = state.read.copy(
                    syncing = syncing,
                    folderLinked = folderLinked ?: state.read.folderLinked,
                ),
            )
        }
    }

    fun reportFolderSync(found: Int, added: Int) {
        _uiState.update {
            it.copy(surface = CompanionSurface.Read, notice = UiNotice.FolderSynced(found, added))
        }
    }

    fun reportImportResult(added: Int, duplicates: Int, failed: Int) {
        _uiState.update {
            it.copy(
                surface = CompanionSurface.Read,
                notice = UiNotice.BooksImported(added, duplicates, failed),
            )
        }
    }

    fun reportEpubImportFailure() {
        _uiState.update { it.copy(notice = UiNotice.EpubImportFailed) }
    }

    fun sendTicket() {
        _uiState.update { it.copy(notice = UiNotice.PairBeforeSend) }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(settingsVisible = show) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }
}
