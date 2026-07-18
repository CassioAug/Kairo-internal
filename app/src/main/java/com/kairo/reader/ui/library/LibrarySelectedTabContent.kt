package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget

@Composable
internal fun LibrarySelectedTabContent(
    state: LibraryTabContentState,
    actions: LibraryTabContentActions,
    tutorialTargets: MutableMap<String, Rect>,
) {
    val selectedTab = state.selectedTab
    val libraryBooks = state.libraryBooks
    val completedBooks = state.completedBooks
    val bookmarks = state.bookmarks
    val bookProgress = state.bookProgress
    val compactLandscape = state.compactLandscape
    val importState = ImportUiState(isImporting = state.isImporting)
    val onOpen = actions.onOpen
    val onSetCompleted = actions.onSetCompleted
    val onOpenBookmark = actions.onOpenBookmark
    val onDeleteBookmark = actions.onDeleteBookmark
    val launchBookImport = actions.onLaunchBookImport
    if (selectedTab == LibraryTab.Library) {
        if (!compactLandscape) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.library_add_content_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ImportSourceCard(
                        icon = Icons.Default.Book,
                        label = stringResource(R.string.library_source_book),
                        supportingText = stringResource(R.string.library_source_book_hint),
                        onClick = launchBookImport,
                        enabled = !importState.isImporting,
                        modifier =
                        Modifier
                            .weight(1f)
                            .startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_IMPORT) {
                                    targetId,
                                    bounds,
                                ->
                                tutorialTargets[targetId] = bounds
                            },
                    )
                    ImportSourceCard(
                        icon = Icons.Default.Link,
                        label = stringResource(R.string.library_source_link),
                        supportingText = stringResource(R.string.library_source_link_hint),
                        onClick = { actions.onShowReadLinkDialog() },
                        enabled = !importState.isImporting,
                        modifier = Modifier.weight(1f),
                    )
                    ImportSourceCard(
                        icon = Icons.AutoMirrored.Filled.TextSnippet,
                        label = stringResource(R.string.library_source_text),
                        supportingText = stringResource(R.string.library_source_text_hint),
                        onClick = { actions.onShowAddTextDialog() },
                        enabled = !importState.isImporting,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 6.dp else 8.dp),
        ) {
            items(libraryBooks, key = { it.id.value }) { book ->
                LibraryCard(
                    book = book,
                    progress = bookProgress[book.id.value],
                    onOpen = onOpen,
                    onSetCompleted = onSetCompleted,
                    onRequestDelete = { actions.onRequestDelete(it) },
                    compactLandscape = compactLandscape,
                )
            }
        }
    } else if (selectedTab == LibraryTab.Completed) {
        if (completedBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.library_no_completed_books),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 6.dp else 8.dp),
            ) {
                items(completedBooks, key = { it.id.value }) { book ->
                    LibraryCard(
                        book = book,
                        progress = bookProgress[book.id.value],
                        onOpen = onOpen,
                        onSetCompleted = onSetCompleted,
                        onRequestDelete = { actions.onRequestDelete(it) },
                        compactLandscape = compactLandscape,
                    )
                }
            }
        }
    } else {
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.library_no_bookmarks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val grouped =
                remember(bookmarks) {
                    bookmarks
                        .groupBy { it.book.id.value }
                        .values
                        .map { group ->
                            val firstItem = group.first()
                            group.sortedByDescending { it.bookmark.createdAt } to firstItem
                        }.sortedBy { (_, firstItem) -> firstItem.book.title.lowercase() }
                }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "bookmarks_summary") {
                    BookmarksSummaryRow(
                        bookmarkCount = bookmarks.size,
                    )
                }
                grouped.forEach { (group, firstItem) ->
                    item(key = "header_${firstItem.book.id.value}") {
                        BookmarkBookHeader(
                            book = firstItem.book,
                            bookmarkCount = group.size,
                            onClearBookmarks = { actions.onRequestClearBookmarks(firstItem.book) },
                        )
                    }
                    items(
                        items = group,
                        key = { it.bookmark.id },
                    ) { item ->
                        BookmarkRow(
                            item = item,
                            onOpenBookmark = onOpenBookmark,
                            onDeleteBookmark = onDeleteBookmark,
                        )
                    }
                }
            }
        }
    }
}
