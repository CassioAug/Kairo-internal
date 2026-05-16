package com.kairo.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.library.LibraryBookProgress
import com.kairo.reader.ui.library.LibraryScreen
import com.kairo.reader.ui.library.LibraryTab
import com.kairo.reader.ui.library.buildLibraryEstimatedWpmByBookId
import com.kairo.reader.ui.library.buildLibraryProgress
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LibraryRoute(
    container: KairoApplication,
    navController: NavHostController,
    prefs: UserPreferences,
    selectedWpm: Int,
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
    val books by container.libraryRepository.observeLibrary().collectAsState(initial = emptyList())
    val bookmarks by container.bookmarkRepository.observeBookmarks().collectAsState(
        initial = emptyList()
    )
    val positions by container.readingPositionRepository.observePositions().collectAsState(
        initial = emptyList()
    )
    val libraryEstimatedWpmByBook by produceState<Map<String, Int>>(
        initialValue = emptyMap(),
        books,
        prefs.rsvpConfig,
        selectedWpm,
    ) {
        value =
            withContext(dispatcherProvider.default) {
                buildLibraryEstimatedWpmByBookId(
                    books = books,
                    config = prefs.rsvpConfig,
                    fallbackEstimatedWpm = selectedWpm,
                )
            }
    }
    val bookProgress by produceState<Map<String, LibraryBookProgress>>(
        initialValue = emptyMap(),
        books,
        positions,
        libraryEstimatedWpmByBook,
    ) {
        value =
            withContext(dispatcherProvider.io) {
                buildLibraryProgress(
                    books = books,
                    positions = positions,
                    estimatedWpmByBookId = libraryEstimatedWpmByBook,
                )
            }
    }
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
