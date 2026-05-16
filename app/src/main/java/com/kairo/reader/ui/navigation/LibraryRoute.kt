package com.kairo.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.BookmarkItem
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.library.LibraryBookProgress
import com.kairo.reader.ui.library.LibraryScreen
import com.kairo.reader.ui.library.LibraryTab
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import kotlinx.coroutines.launch

@Composable
internal fun LibraryRoute(
    container: KairoApplication,
    navController: NavHostController,
    books: List<Book>,
    bookmarks: List<BookmarkItem>,
    bookProgress: Map<String, LibraryBookProgress>,
    importState: ImportUiState,
    initialTabRouteValue: String? = null,
    onImportFile: (Uri) -> Unit,
    onImportUrl: (String) -> Unit,
    tutorialState: StartingTutorialOverlayState?,
    onTutorialNext: () -> Unit,
    onTutorialPrevious: () -> Unit,
    onTutorialSkip: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val dispatcherProvider = container.dispatcherProvider
    val initialTab =
        when (initialTabRouteValue?.lowercase()) {
            KairoRoutes.TAB_COMPLETED -> LibraryTab.Completed
            KairoRoutes.TAB_BOOKMARKS -> LibraryTab.Bookmarks
            else -> LibraryTab.Library
        }

    LibraryScreen(
        books = books,
        bookmarks = bookmarks,
        bookProgress = bookProgress,
        initialTab = initialTab,
        importState = importState,
        onOpen = { book ->
            navController.navigate(KairoRoutes.reader(book.id.value))
        },
        onOpenBookmark = { bookId, chapterIndex, tokenIndex ->
            coroutineScope.launch(dispatcherProvider.io) {
                container.readingPositionRepository.savePosition(
                    ReadingPosition(BookId(bookId), chapterIndex, tokenIndex),
                )
            }
            navController.navigate(KairoRoutes.reader(bookId, chapterIndex, tokenIndex))
        },
        onDeleteBookmark = { bookmarkId ->
            coroutineScope.launch { container.bookmarkRepository.delete(bookmarkId) }
        },
        onDeleteBookmarksForBook = { bookId ->
            coroutineScope.launch {
                container.bookmarkRepository.deleteForBook(BookId(bookId))
            }
        },
        onImportFile = onImportFile,
        onImportUrl = onImportUrl,
        onSettings = { navController.navigate(KairoRoutes.SETTINGS) },
        onSetCompleted = { book, isCompleted ->
            coroutineScope.launch {
                container.libraryRepository.setCompleted(book.id.value, isCompleted)
            }
        },
        onDelete = { book ->
            coroutineScope.launch { container.libraryRepository.delete(book.id.value) }
        },
        tutorialState = tutorialState,
        onTutorialNext = onTutorialNext,
        onTutorialPrevious = onTutorialPrevious,
        onTutorialSkip = onTutorialSkip,
    )
}
