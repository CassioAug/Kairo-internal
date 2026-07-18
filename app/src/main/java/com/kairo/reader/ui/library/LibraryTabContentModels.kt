package com.kairo.reader.ui.library

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookmarkItem

data class LibraryTabContentState(
    val selectedTab: LibraryTab,
    val libraryBooks: List<Book>,
    val completedBooks: List<Book>,
    val bookmarks: List<BookmarkItem>,
    val bookProgress: Map<String, LibraryBookProgress>,
    val compactLandscape: Boolean,
    val isImporting: Boolean,
)

data class LibraryTabContentActions(
    val onOpen: (Book) -> Unit,
    val onSetCompleted: (Book, Boolean) -> Unit,
    val onRequestDelete: (Book) -> Unit,
    val onOpenBookmark: (String, Int, Int) -> Unit,
    val onDeleteBookmark: (String) -> Unit,
    val onRequestClearBookmarks: (Book) -> Unit,
    val onLaunchBookImport: () -> Unit,
    val onShowReadLinkDialog: () -> Unit,
    val onShowAddTextDialog: () -> Unit,
)
