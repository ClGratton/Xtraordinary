package com.xteink.companion.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xteink.companion.R
import com.xteink.companion.ui.ImportedBookUiState
import com.xteink.companion.ui.ReadSort
import com.xteink.companion.ui.ReadUiState
import java.util.Locale

@Composable
fun ReadContent(
    state: ReadUiState,
    onSetQuery: (String) -> Unit,
    onSetSort: (ReadSort) -> Unit,
    onSetOnDeviceOnly: (Boolean) -> Unit,
    onChooseBookFolder: () -> Unit,
    onOpenEpub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val visibleBooks = remember(state.books, state.query, state.sort, state.onDeviceOnly) {
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
            terms.all(haystack::contains) && (!state.onDeviceOnly || book.isOnDevice)
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
                                pluralStringResource(R.plurals.book_count, state.books.size, state.books.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.read_library_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    Text(
                        stringResource(R.string.sort_books),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ReadSort.entries.forEach { sort ->
                        val selected = state.sort == sort
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSetSort(sort)
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (sort) {
                                            ReadSort.Recent -> R.string.sort_recent
                                            ReadSort.Name -> R.string.sort_name
                                            ReadSort.Size -> R.string.sort_size
                                        },
                                    ),
                                )
                            },
                        )
                    }
                    FilterChip(
                        selected = state.onDeviceOnly,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onSetOnDeviceOnly(!state.onDeviceOnly)
                        },
                        label = { Text(stringResource(R.string.on_device)) },
                    )
                }
            }
            if (visibleBooks.isEmpty()) {
                item { LibraryEmptyState(hasBooks = state.books.isNotEmpty()) }
            } else {
                items(visibleBooks, key = { it.id }) { book -> ImportedBookCard(book) }
            }
            item { ResearchSourcesCard() }
        }

        val importDescription = stringResource(R.string.import_epubs)
        FloatingActionButton(
            onClick = {
                if (!state.importing) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onOpenEpub()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .size(68.dp)
                .semantics { contentDescription = importDescription },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(22.dp),
        ) {
            if (state.importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                ImportBookIcon()
            }
        }
    }
}

@Composable
private fun ImportBookIcon() {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(31.dp)) {
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

@Composable
private fun ImportedBookCard(book: ImportedBookUiState) {
    val bitmap = remember(book.coverPath) {
        book.coverPath?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (book.isOnDevice) 1f else 0.58f),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .width(62.dp)
                    .fillMaxHeight(),
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
                    if (book.isOnDevice) {
                        listOfNotNull(
                            book.isbn?.let { "ISBN $it" },
                            book.fileSizeBytes?.let(::formatFileSize),
                        ).joinToString(" · ").ifBlank {
                            stringResource(R.string.metadata_from, book.metadataSource)
                        }
                    } else {
                        stringResource(R.string.not_on_device)
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
private fun ResearchSourcesCard() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.research_and_sources), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.planned),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.research_and_sources_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Play Books", "Kindle", "Kobo", "Gutenberg").forEach { source ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge) {
                        Text(
                            source,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
