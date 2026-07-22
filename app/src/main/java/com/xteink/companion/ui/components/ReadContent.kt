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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.xteink.companion.ui.ReadFilter
import com.xteink.companion.ui.ReadUiState
import java.util.Locale

@Composable
fun ReadContent(
    state: ReadUiState,
    onSetQuery: (String) -> Unit,
    onSetFilter: (ReadFilter) -> Unit,
    onOpenEpub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val filteredBooks = remember(state.books, state.query, state.filter) {
        val terms = state.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        state.books.filter { book ->
            val haystack = listOfNotNull(
                book.title,
                book.author,
                book.publisher,
                book.language,
                book.isbn,
                book.fileName,
            ).joinToString(" ").lowercase()
            val matchesQuery = terms.all(haystack::contains)
            val matchesFilter = when (state.filter) {
                ReadFilter.All -> true
                ReadFilter.WithCover -> book.coverPath != null
                ReadFilter.NeedsDetails -> book.coverPath == null || book.author == "Unknown author" ||
                    book.publisher == null || book.language == null
            }
            matchesQuery && matchesFilter
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 94.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(stringResource(R.string.read_title), style = MaterialTheme.typography.headlineLarge)
                        Text(
                            pluralStringResource(R.plurals.book_count, state.books.size, state.books.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.read_library_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                ) {
                    ReadFilter.entries.forEach { filter ->
                        val selected = state.filter == filter
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSetFilter(filter)
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (filter) {
                                            ReadFilter.All -> R.string.filter_all
                                            ReadFilter.WithCover -> R.string.filter_with_covers
                                            ReadFilter.NeedsDetails -> R.string.filter_needs_details
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            if (filteredBooks.isEmpty()) {
                item {
                    LibraryEmptyState(hasBooks = state.books.isNotEmpty())
                }
            } else {
                items(filteredBooks, key = { it.id }) { book ->
                    ImportedBookCard(book)
                }
            }
            item { ResearchSourcesCard() }
        }

        val importDescription = stringResource(R.string.import_epubs)
        SmallFloatingActionButton(
            onClick = {
                if (!state.importing) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onOpenEpub()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .semantics { contentDescription = importDescription },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.large,
        ) {
            if (state.importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
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
    Canvas(modifier = Modifier.size(28.dp)) {
        val stroke = 1.9.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
            size = Size(size.width * 0.55f, size.height * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.24f, size.height * 0.17f),
            end = Offset(size.width * 0.24f, size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.72f, size.height * 0.36f),
            end = Offset(size.width * 0.96f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.84f, size.height * 0.24f),
            end = Offset(size.width * 0.84f, size.height * 0.48f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LibraryEmptyState(hasBooks: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
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
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier
                    .width(76.dp)
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
                            modifier = Modifier.size(34.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
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
                    listOfNotNull(
                        book.isbn?.let { "ISBN $it" },
                        book.fileSizeBytes?.let(::formatFileSize),
                    ).joinToString(" · ").ifBlank { stringResource(R.string.local_epub) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.metadata_from, book.metadataSource),
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
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
