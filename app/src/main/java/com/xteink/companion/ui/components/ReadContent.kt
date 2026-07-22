package com.xteink.companion.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.ui.ImportedBookUiState
import com.xteink.companion.ui.ReadSort
import com.xteink.companion.ui.ReadService
import com.xteink.companion.ui.ReadUiState
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadContent(
    state: ReadUiState,
    isX3Connected: Boolean,
    connectedDeviceModel: String?,
    onSetQuery: (String) -> Unit,
    onSetSort: (ReadSort) -> Unit,
    onSetService: (ReadService) -> Unit,
    onSetOnX3Only: (Boolean) -> Unit,
    onChooseBookFolder: () -> Unit,
    onOpenEpub: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteBooksFromX3: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedBookIds: Set<String> = emptySet(),
) {
    val haptics = LocalHapticFeedback.current
    val deviceLabel = connectedDeviceModel ?: stringResource(R.string.xteink_device_short)
    var selectedBookIds by remember(initialSelectedBookIds) { mutableStateOf(initialSelectedBookIds) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showServiceReminder by rememberSaveable { mutableStateOf(true) }
    val visibleBooks = remember(state.books, state.query, state.sort, state.service, state.onX3Only) {
        val terms = state.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val matching = state.books.filter { book ->
            val haystack = listOfNotNull(
                book.title,
                book.author,
                book.publisher,
                book.language,
                book.isbn,
                book.fileName,
            ).joinToString(" ").lowercase()
            terms.all(haystack::contains) &&
                (!state.onX3Only || book.isOnX3) &&
                (state.service != ReadService.LocalEpub || book.metadataSource.startsWith("EPUB", ignoreCase = true))
        }
        when (state.sort) {
            ReadSort.Recent -> matching.sortedByDescending {
                it.fileModifiedAtEpochMs ?: it.importedAtEpochMs
            }
            ReadSort.Name -> matching.sortedBy { it.title.lowercase(Locale.ROOT) }
            ReadSort.Size -> matching.sortedWith(
                compareByDescending<ImportedBookUiState> { it.fileSizeBytes != null }
                    .thenByDescending { it.fileSizeBytes ?: 0L },
            )
        }
    }
    val visibleIds = visibleBooks.mapTo(linkedSetOf()) { it.id }
    val selectedOnX3Ids = selectedBookIds.filterTo(linkedSetOf()) { id ->
        state.books.any { it.id == id && it.isOnX3 }
    }

    LaunchedEffect(state.books) {
        selectedBookIds = selectedBookIds.intersect(state.books.mapTo(hashSetOf()) { it.id })
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 106.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.read_title), style = MaterialTheme.typography.headlineLarge)
                        if (state.syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                stringResource(
                                    R.string.book_and_device_count,
                                    state.books.size,
                                    state.books.count { it.isOnX3 },
                                    deviceLabel,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.read_library_subtitle, deviceLabel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (selectedBookIds.isNotEmpty()) {
                            LibrarySelectionSummary(
                                selectedCount = selectedBookIds.size,
                                allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all(selectedBookIds::contains),
                                onSelectAll = {
                                    selectedBookIds = selectedBookIds + visibleIds
                                },
                            )
                        } else {
                            AssistChip(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onChooseBookFolder()
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            when {
                                                state.syncing -> R.string.syncing_library
                                                state.folderLinked -> R.string.folder_linked
                                                else -> R.string.choose_book_folder
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSetQuery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    placeholder = { Text(stringResource(R.string.search_your_library)) },
                    singleLine = true,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortFilterMenu(selected = state.sort, onSelected = onSetSort)
                    ServiceFilterMenu(selected = state.service, onSelected = onSetService)
                    FilterChip(
                        selected = state.onX3Only,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onSetOnX3Only(!state.onX3Only)
                        },
                        label = { Text(stringResource(R.string.on_device, deviceLabel)) },
                    )
                }
            }
            if (visibleBooks.isEmpty()) {
                item { LibraryEmptyState(hasBooks = state.books.isNotEmpty()) }
            } else {
                items(visibleBooks, key = { it.id }) { book ->
                    ImportedBookCard(
                        book = book,
                        deviceLabel = deviceLabel,
                        selectionMode = selectedBookIds.isNotEmpty(),
                        selected = book.id in selectedBookIds,
                        onClick = {
                            if (selectedBookIds.isNotEmpty()) {
                                selectedBookIds = selectedBookIds.toggle(book.id)
                            }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedBookIds = selectedBookIds + book.id
                        },
                    )
                }
            }
            if (showServiceReminder) {
                item {
                    ServiceAccountsReminderCard(
                        onOpenSettings = onOpenSettings,
                        onDismiss = { showServiceReminder = false },
                    )
                }
            }
        }

        val selectionMode = selectedBookIds.isNotEmpty()
        val canDelete = selectedOnX3Ids.isNotEmpty()
        LibraryBottomActions(
            selectionMode = selectionMode,
            importing = state.importing,
            canDelete = canDelete,
            importDescription = stringResource(R.string.import_epubs),
            deleteDescription = stringResource(R.string.delete_selected_from_device, deviceLabel),
            onClearSelection = { selectedBookIds = emptySet() },
            onImport = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onOpenEpub()
            },
            onDelete = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                if (isX3Connected) showDeleteConfirmation = true
                else onDeleteBooksFromX3(selectedOnX3Ids)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_books_from_device_title, deviceLabel)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.delete_books_from_device_body,
                        selectedOnX3Ids.size,
                        selectedOnX3Ids.size,
                        deviceLabel,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDeleteBooksFromX3(selectedOnX3Ids)
                        selectedBookIds = emptySet()
                        showDeleteConfirmation = false
                    },
                ) { Text(stringResource(R.string.delete_from_device, deviceLabel)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LibraryBottomActions(
    selectionMode: Boolean,
    importing: Boolean,
    canDelete: Boolean,
    importDescription: String,
    deleteDescription: String,
    onClearSelection: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val splitProgress by animateFloatAsState(
        targetValue = if (selectionMode) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "library action mitosis",
    )
    val actionCorner by animateDpAsState(
        targetValue = if (selectionMode) 34.dp else 22.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "library action corner",
    )
    val actionContainer by animateColorAsState(
        targetValue = if (selectionMode) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primary,
        animationSpec = tween(220),
        label = "library action container",
    )
    val actionContent by animateColorAsState(
        targetValue = if (selectionMode) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimary,
        animationSpec = tween(220),
        label = "library action content",
    )
    val actionEnabled = if (selectionMode) canDelete else !importing

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        val clearWidth = 112.dp
        val splitTravel = maxWidth - (clearWidth / 2) - 34.dp
        if (splitProgress > 0.001f) {
            FilledTonalButton(
                onClick = onClearSelection,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(clearWidth)
                    .height(60.dp)
                    .graphicsLayer {
                        alpha = splitProgress
                        scaleX = 0.70f + (0.30f * splitProgress)
                        scaleY = 0.88f + (0.12f * splitProgress)
                        translationX = splitTravel.toPx() * (1f - splitProgress)
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    },
                shape = RoundedCornerShape(22.dp),
            ) {
                Text(stringResource(R.string.clear_all), maxLines = 1)
            }
        }

        Surface(
            onClick = if (selectionMode) onDelete else onImport,
            enabled = actionEnabled,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(68.dp)
                .alpha(if (selectionMode && !canDelete) 0.45f else 1f)
                .semantics {
                    contentDescription = if (selectionMode) deleteDescription else importDescription
                    if (!actionEnabled) disabled()
                },
            shape = RoundedCornerShape(actionCorner),
            color = actionContainer,
            contentColor = actionContent,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (importing && !selectionMode) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = actionContent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    ImportBookIcon(
                        color = actionContent,
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - splitProgress
                            scaleX = 1f - (0.18f * splitProgress)
                            scaleY = scaleX
                        },
                    )
                    TrashIcon(
                        color = actionContent,
                        modifier = Modifier.graphicsLayer {
                            alpha = splitProgress
                            scaleX = 0.82f + (0.18f * splitProgress)
                            scaleY = scaleX
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySelectionSummary(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.books_selected, selectedCount),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(if (allVisibleSelected) R.string.all_selected else R.string.select_all),
            style = MaterialTheme.typography.labelLarge,
            color = if (allVisibleSelected) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
            modifier = if (allVisibleSelected) Modifier else Modifier.clickable(onClick = onSelectAll),
        )
    }
}

@Composable
private fun TrashIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(28.dp)) {
        val stroke = 1.9.dp.toPx()
        drawLine(color, Offset(size.width * 0.22f, size.height * 0.29f), Offset(size.width * 0.78f, size.height * 0.29f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.19f), Offset(size.width * 0.60f, size.height * 0.19f), stroke, StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.28f, size.height * 0.36f),
            size = Size(size.width * 0.44f, size.height * 0.43f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawLine(color, Offset(size.width * 0.44f, size.height * 0.45f), Offset(size.width * 0.44f, size.height * 0.70f), stroke * 0.8f, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.56f, size.height * 0.45f), Offset(size.width * 0.56f, size.height * 0.70f), stroke * 0.8f, StrokeCap.Round)
    }
}

@Composable
private fun SortFilterMenu(
    selected: ReadSort,
    onSelected: (ReadSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(
        when (selected) {
            ReadSort.Recent -> R.string.sort_recent
            ReadSort.Name -> R.string.sort_name
            ReadSort.Size -> R.string.sort_size
        },
    )
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(stringResource(R.string.sort_by, selectedLabel)) },
            trailingIcon = { Text("▾") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReadSort.entries.forEach { sort ->
                val label = stringResource(
                    when (sort) {
                        ReadSort.Recent -> R.string.sort_recent
                        ReadSort.Name -> R.string.sort_name
                        ReadSort.Size -> R.string.sort_size
                    },
                )
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ImportBookIcon(
    color: Color = MaterialTheme.colorScheme.onPrimary,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(31.dp)) {
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.10f, size.height * 0.15f),
            size = Size(size.width * 0.56f, size.height * 0.72f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.23f, size.height * 0.16f),
            end = Offset(size.width * 0.23f, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.70f, size.height * 0.37f),
            end = Offset(size.width * 0.98f, size.height * 0.37f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.84f, size.height * 0.23f),
            end = Offset(size.width * 0.84f, size.height * 0.51f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LibraryEmptyState(hasBooks: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookOutlineIcon(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(if (hasBooks) R.string.no_matching_books else R.string.library_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (hasBooks) R.string.change_library_filters else R.string.library_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedBookCard(
    book: ImportedBookUiState,
    deviceLabel: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap = remember(book.coverPath) {
        book.coverPath?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (book.isOnPhone || book.isOnX3) 1f else 0.58f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .fillMaxHeight(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.book_cover_description, book.title),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            BookOutlineIcon(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp),
                    ) {
                        SelectionCircle(selected = selected)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        book.publishedYear?.toString(),
                        book.language?.uppercase(Locale.ROOT),
                        book.publisher,
                    ).joinToString(" · ").ifBlank { book.fileName },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (book.isOnX3) {
                        book.x3Path?.let { path ->
                            stringResource(R.string.on_device_at, deviceLabel, path)
                        } ?: stringResource(R.string.on_device, deviceLabel)
                    } else if (book.isOnPhone) {
                        stringResource(R.string.phone_only)
                    } else {
                        stringResource(R.string.book_unavailable)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SelectionCircle(selected: Boolean) {
    val fill = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val mark = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(28.dp)) {
        drawCircle(
            color = if (selected) fill else Color.Transparent,
            style = if (selected) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.dp.toPx()),
        )
        if (!selected) drawCircle(color = outline, style = Stroke(2.dp.toPx()))
        if (selected) {
            val stroke = 2.dp.toPx()
            drawLine(mark, Offset(size.width * 0.27f, size.height * 0.52f), Offset(size.width * 0.44f, size.height * 0.68f), stroke, StrokeCap.Round)
            drawLine(mark, Offset(size.width * 0.44f, size.height * 0.68f), Offset(size.width * 0.75f, size.height * 0.34f), stroke, StrokeCap.Round)
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

@Composable
private fun ServiceFilterMenu(
    selected: ReadService,
    onSelected: (ReadService) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    stringResource(
                        if (selected == ReadService.All) R.string.service_filter else R.string.local_epub,
                    ),
                )
            },
            trailingIcon = { Text("▾") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_services)) },
                onClick = {
                    onSelected(ReadService.All)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.local_epub)) },
                onClick = {
                    onSelected(ReadService.LocalEpub)
                    expanded = false
                },
            )
            HorizontalDivider()
            listOf(
                R.string.service_play_books,
                R.string.service_kindle,
                R.string.service_kobo,
                R.string.service_gutenberg,
            ).forEach { serviceName ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(serviceName))
                            Text(stringResource(R.string.not_linked))
                        }
                    },
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun ServiceAccountsReminderCard(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        onClick = onOpenSettings,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_library_services),
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(
                stringResource(R.string.link_accounts_card_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    R.string.service_play_books_short,
                    R.string.service_kindle,
                    R.string.service_kobo,
                    R.string.service_gutenberg_short,
                ).forEach { serviceName ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            stringResource(serviceName),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
