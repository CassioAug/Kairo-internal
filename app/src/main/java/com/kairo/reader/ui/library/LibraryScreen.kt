@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MagicNumber")

package com.kairo.reader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.ui.format.formatShortDurationMinutes
import com.kairo.reader.ui.tutorial.StartingTutorialOverlay
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    books: List<Book>,
    bookmarks: List<BookmarkItem>,
    bookProgress: Map<String, LibraryBookProgress>,
    initialTab: LibraryTab = LibraryTab.Library,
    importState: ImportUiState = ImportUiState(),
    onOpen: (Book) -> Unit,
    onOpenBookmark: (bookId: String, chapterIndex: Int, tokenIndex: Int) -> Unit,
    onDeleteBookmark: (bookmarkId: String) -> Unit,
    onDeleteBookmarksForBook: (bookId: String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onImportUrl: (String) -> Unit,
    onImportText: (TextImportRequest) -> Unit = {},
    onSettings: () -> Unit,
    onSetCompleted: (Book, Boolean) -> Unit,
    onDelete: (Book) -> Unit,
    tutorialState: StartingTutorialOverlayState? = null,
    onTutorialNext: () -> Unit = {},
    onTutorialPrevious: () -> Unit = {},
    onTutorialSkip: () -> Unit = {},
) {
    // File picker launcher for EPUB/MOBI files
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let { onImportFile(it) }
        }
    val configuration = LocalConfiguration.current
    val compactLandscape =
        configuration.screenWidthDp > configuration.screenHeightDp &&
            configuration.screenHeightDp <= 480
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.ordinal) }
    var pendingDeleteBook by remember { mutableStateOf<Book?>(null) }
    var pendingClearBookmarkBook by remember { mutableStateOf<Book?>(null) }
    var showReadLinkDialog by rememberSaveable { mutableStateOf(false) }
    var linkInput by rememberSaveable { mutableStateOf("") }
    var showAddTextDialog by rememberSaveable { mutableStateOf(false) }
    var textImportTitle by rememberSaveable { mutableStateOf("") }
    var textImportContent by rememberSaveable { mutableStateOf("") }
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }
    val libraryBooks = remember(books) { books.filterNot { it.isCompleted } }
    val completedBooks = remember(books) { books.filter { it.isCompleted } }
    val launchBookImport = {
        filePickerLauncher.launch(
            arrayOf(
                "application/epub+zip",
                "application/x-mobipocket-ebook",
                "application/octet-stream",
                "*/*",
            ),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(
                    horizontal = if (compactLandscape) 12.dp else 16.dp,
                    vertical = if (compactLandscape) 8.dp else 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 8.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.library_title),
                        style =
                            if (compactLandscape) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                    )
                    Text(
                        stringResource(R.string.library_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (compactLandscape && selectedTab == LibraryTab.Library.ordinal) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImportBookButton(
                            onClick = launchBookImport,
                            enabled = !importState.isImporting,
                            compact = true,
                            modifier =
                                Modifier.startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_IMPORT) {
                                    targetId,
                                    bounds,
                                    ->
                                    tutorialTargets[targetId] = bounds
                                },
                        )
                        ReadFromLinkButton(
                            onClick = { showReadLinkDialog = true },
                            enabled = !importState.isImporting,
                            compact = true,
                        )
                        AddTextButton(
                            onClick = { showAddTextDialog = true },
                            enabled = !importState.isImporting,
                            compact = true,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onSettings,
                    modifier =
                        Modifier.startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_SETTINGS) {
                            targetId,
                            bounds,
                            ->
                            tutorialTargets[targetId] = bounds
                        },
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.content_desc_settings),
                    )
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.LIBRARY_TABS) {
                        targetId,
                        bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
            ) {
                Tab(
                    selected = selectedTab == LibraryTab.Library.ordinal,
                    onClick = { selectedTab = LibraryTab.Library.ordinal },
                    text = { Text(stringResource(R.string.library_tab_library)) },
                )
                Tab(
                    selected = selectedTab == LibraryTab.Completed.ordinal,
                    onClick = { selectedTab = LibraryTab.Completed.ordinal },
                    text = { Text(stringResource(R.string.library_tab_completed)) },
                )
                Tab(
                    selected = selectedTab == LibraryTab.Bookmarks.ordinal,
                    onClick = { selectedTab = LibraryTab.Bookmarks.ordinal },
                    text = { Text(stringResource(R.string.library_tab_bookmarks)) },
                )
            }

            if (selectedTab == LibraryTab.Library.ordinal) {
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
                            onClick = { showReadLinkDialog = true },
                            enabled = !importState.isImporting,
                            modifier = Modifier.weight(1f),
                        )
                            ImportSourceCard(
                                icon = Icons.Default.TextSnippet,
                                label = stringResource(R.string.library_source_text),
                                supportingText = stringResource(R.string.library_source_text_hint),
                                onClick = { showAddTextDialog = true },
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
                            onRequestDelete = { pendingDeleteBook = it },
                            compactLandscape = compactLandscape,
                        )
                    }
                }
            } else if (selectedTab == LibraryTab.Completed.ordinal) {
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
                                onRequestDelete = { pendingDeleteBook = it },
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
                                    onClearBookmarks = { pendingClearBookmarkBook = firstItem.book },
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
        ImportProgressOverlay(state = importState)
        tutorialState?.let { overlayState ->
            StartingTutorialOverlay(
                state = overlayState,
                targetBounds = overlayState.step.targetId?.let(tutorialTargets::get),
                onNext = onTutorialNext,
                onPrevious = onTutorialPrevious,
                onSkip = onTutorialSkip,
            )
        }
    }

    pendingDeleteBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBook = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.library_delete_message, book.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(book)
                        pendingDeleteBook = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBook = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingClearBookmarkBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingClearBookmarkBook = null },
            title = { Text(stringResource(R.string.library_bookmark_clear_book_title)) },
            text = {
                Text(
                    stringResource(R.string.library_bookmark_clear_book_message, book.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBookmarksForBook(book.id.value)
                        pendingClearBookmarkBook = null
                    },
                ) { Text(stringResource(R.string.library_bookmark_clear_book_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearBookmarkBook = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showReadLinkDialog) {
        ReadFromLinkDialog(
            value = linkInput,
            onValueChange = { linkInput = it },
            onDismiss = { showReadLinkDialog = false },
            onSubmit = {
                val submitted = linkInput.trim()
                if (submitted.isNotBlank()) {
                    onImportUrl(submitted)
                    linkInput = ""
                    showReadLinkDialog = false
                }
            },
        )
    }

    if (showAddTextDialog) {
        val defaultTitle = stringResource(R.string.library_text_default_title)
        AddTextDialog(
            title = textImportTitle,
            content = textImportContent,
            onTitleChange = { textImportTitle = it },
            onContentChange = { textImportContent = it },
            onDismiss = { showAddTextDialog = false },
            onSubmit = {
                val submittedContent = textImportContent.trim()
                if (submittedContent.isNotBlank()) {
                    onImportText(
                        TextImportRequest(
                            content = submittedContent,
                            title = textImportTitle.trim().ifBlank { defaultTitle },
                        )
                    )
                    textImportTitle = ""
                    textImportContent = ""
                    showAddTextDialog = false
                }
            },
        )
    }
}

enum class LibraryTab { Library, Completed, Bookmarks }

@Composable
private fun ImportBookButton(
    onClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.library_import_button),
            )
        }
        return
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp),
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            stringResource(R.string.library_import_button),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReadFromLinkButton(
    onClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(
                Icons.Default.Link,
                contentDescription = stringResource(R.string.library_read_from_link_button),
            )
        }
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp),
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            stringResource(R.string.library_read_from_link_button),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddTextButton(
    onClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(
                Icons.Default.TextSnippet,
                contentDescription = stringResource(R.string.library_text_import_button),
            )
        }
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            Icons.Default.TextSnippet,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp),
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            stringResource(R.string.library_text_import_button),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImportSourceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    supportingText: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 92.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReadFromLinkDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_read_from_link_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.library_read_from_link_label)) },
                placeholder = {
                    Text(stringResource(R.string.library_read_from_link_placeholder))
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.library_read_from_link_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun LibraryCard(
    book: Book,
    progress: LibraryBookProgress?,
    onOpen: (Book) -> Unit,
    onSetCompleted: (Book, Boolean) -> Unit,
    onRequestDelete: (Book) -> Unit,
    compactLandscape: Boolean = false,
) {
    val context = LocalContext.current
    val authorSeparator = stringResource(R.string.list_separator)
    var actionsExpanded by remember { mutableStateOf(false) }
    val deleteActionDescription = stringResource(R.string.content_desc_delete_book)
    val completedActionDescription =
        stringResource(
            if (book.isCompleted) {
                R.string.content_desc_move_book_to_library
            } else {
                R.string.content_desc_mark_book_completed
            },
        )
    Card(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(book) },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(if (compactLandscape) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compactLandscape) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Book cover or placeholder
            BookCover(
                coverImage = book.coverImage,
                title = book.title,
                cacheKey = book.id.value,
                modifier =
                    Modifier.size(
                        width = if (compactLandscape) 48.dp else 60.dp,
                        height = if (compactLandscape) 72.dp else 90.dp,
                    ),
            )

            // Book info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 2.dp else 4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compactLandscape) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.authors.isNotEmpty()) {
                    Text(
                        text = book.authors.joinToString(authorSeparator),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text =
                    pluralStringResource(
                        R.plurals.library_chapter_count,
                        book.chapters.size,
                        book.chapters.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                progress?.let { stats ->
                    val eta =
                        if (stats.remainingMinutes != null) {
                            stringResource(
                                R.string.library_time_left,
                                formatShortDurationMinutes(context, stats.remainingMinutes),
                            )
                        } else {
                            null
                        }
                    val percentCompleteLabel =
                        stringResource(
                            R.string.library_percent_complete,
                            stats.percentComplete,
                        )
                    val label =
                        if (eta != null) {
                            percentCompleteLabel + stringResource(R.string.meta_separator) + eta
                        } else {
                            percentCompleteLabel
                        }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (book.isCompleted) {
                    CompletedStatusPill()
                }
            }

            Box {
                IconButton(onClick = { actionsExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.content_desc_book_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        modifier = Modifier.semantics {
                            contentDescription = completedActionDescription
                        },
                        text = { Text(completedActionDescription) },
                        leadingIcon = {
                            Icon(
                                imageVector =
                                    if (book.isCompleted) {
                                        Icons.Default.Refresh
                                    } else {
                                        Icons.Default.Done
                                    },
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onSetCompleted(book, !book.isCompleted)
                        },
                    )
                    DropdownMenuItem(
                        modifier = Modifier.semantics {
                            contentDescription = deleteActionDescription
                        },
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onRequestDelete(book)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedStatusPill() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Done,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = stringResource(R.string.library_completed_status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun BookmarksSummaryRow(
    bookmarkCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.library_bookmark_count,
                        bookmarkCount,
                        bookmarkCount,
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.library_bookmarks_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BookmarkBookHeader(
    book: Book,
    bookmarkCount: Int,
    onClearBookmarks: () -> Unit,
) {
    val authorSeparator = stringResource(R.string.list_separator)
    val clearBookmarksDescription =
        stringResource(R.string.content_desc_delete_book_bookmarks, book.title)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            coverImage = book.coverImage,
            title = book.title,
            cacheKey = book.id.value,
            modifier = Modifier.size(width = 34.dp, height = 50.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val authors = book.authors.joinToString(authorSeparator)
            val count =
                pluralStringResource(
                    R.plurals.library_bookmark_count,
                    bookmarkCount,
                    bookmarkCount,
                )
            if (authors.isNotBlank()) {
                Text(
                    text = authors + stringResource(R.string.meta_separator) + count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onClearBookmarks,
            modifier =
                Modifier.semantics {
                    contentDescription = clearBookmarksDescription
                },
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.library_bookmark_clear_book))
        }
    }
}

@Composable
private fun BookmarkRow(
    item: BookmarkItem,
    onOpenBookmark: (bookId: String, chapterIndex: Int, tokenIndex: Int) -> Unit,
    onDeleteBookmark: (bookmarkId: String) -> Unit,
) {
    val bookmark = item.bookmark
    val book = item.book
    val chapterCount = item.chapterCount.coerceAtLeast(1)
    val percent =
        remember(bookmark.chapterIndex, chapterCount) {
            (((bookmark.chapterIndex + 1).toFloat() / chapterCount.toFloat()) * 100f)
                .roundToInt()
                .coerceIn(0, 100)
        }

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable {
                onOpenBookmark(book.id.value, bookmark.chapterIndex, bookmark.tokenIndex)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        R.string.library_bookmark_progress,
                        bookmark.chapterIndex + 1,
                        chapterCount,
                        percent,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = bookmark.previewText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.content_desc_delete_bookmark),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookCover(
    coverImage: ByteArray?,
    title: String,
    cacheKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (coverImage != null && coverImage.isNotEmpty()) {
        val coverDescription =
            stringResource(R.string.content_desc_cover_of_title, title)
        AsyncImage(
            model =
            remember(coverImage, cacheKey) {
                ImageRequest
                    .Builder(context)
                    .data(coverImage)
                    .memoryCacheKey("book_cover_$cacheKey")
                    .crossfade(false)
                    .build()
            },
            contentDescription = coverDescription,
            modifier = modifier.clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        PlaceholderCover(modifier = modifier)
    }
}

@Composable
private fun PlaceholderCover(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Book,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp),
        )
    }
}
