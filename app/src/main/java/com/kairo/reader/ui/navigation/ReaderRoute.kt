package com.kairo.reader.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.ui.reader.ReaderScreen
import com.kairo.reader.ui.reader.ReaderViewModel
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ReaderRoute(
    backStackEntry: NavBackStackEntry,
    container: KairoApplication,
    navController: NavHostController,
    prefs: UserPreferences,
    estimatedWpm: Int,
    tutorialActive: Boolean,
    tutorialState: StartingTutorialOverlayState?,
    initialChapterIndex: Int? = null,
    initialTokenIndex: Int? = null,
    onShowUserMessage: (String) -> Unit,
    onTutorialNext: () -> Unit,
    onTutorialPrevious: () -> Unit,
    onTutorialSkip: () -> Unit,
) {
    val bookId = backStackEntry.arguments?.getString(KairoRoutes.ARG_BOOK_ID) ?: return
    val dispatcherProvider = container.dispatcherProvider
    val coroutineScope = rememberCoroutineScope()
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val readerPositionSaver =
        remember(bookId, lifecycleOwner) {
            ReaderPositionSaver(
                scope = lifecycleOwner.lifecycleScope,
                repository = container.readingPositionRepository,
                saveDispatcher = dispatcherProvider.io,
            )
        }

    val bookState =
        produceState<Book?>(
            initialValue = null,
            bookId,
        ) {
            value = runCatching { container.bookRepository.getBook(BookId(bookId)) }.getOrNull()
        }
    val book = bookState.value
    if (book == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val readerViewModel: ReaderViewModel =
        viewModel(
            factory =
                ReaderViewModel.factory(
                    container.bookRepository,
                    container.tokenRepository,
                    dispatcherProvider,
                ),
        )
    val uiState by readerViewModel.uiState.collectAsState()

    val rsvpResultFlow =
        remember(backStackEntry) {
            backStackEntry.savedStateHandle.getStateFlow(
                KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX,
                -1,
            )
        }
    val rsvpResultIndex by rsvpResultFlow.collectAsState(initial = -1)
    val rsvpResultChapterFlow =
        remember(backStackEntry) {
            backStackEntry.savedStateHandle.getStateFlow(
                KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX,
                -1,
            )
        }
    val rsvpResultChapterIndex by rsvpResultChapterFlow.collectAsState(initial = -1)
    val rsvpResumeCursorFlow =
        remember(backStackEntry) {
            backStackEntry.savedStateHandle.getStateFlow(
                KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR,
                -1,
            )
        }
    val rsvpResultResumeCursor by rsvpResumeCursorFlow.collectAsState(initial = -1)
    val safeRsvpResultIndex =
        if (rsvpResultIndex >= 0) {
            val tokens = uiState.chapterData?.tokens
            if (rsvpResultChapterIndex == uiState.chapterIndex &&
                tokens != null &&
                tokens.isNotEmpty()
            ) {
                tokens.nearestWordIndex(rsvpResultIndex)
            } else {
                rsvpResultIndex.coerceAtLeast(0)
            }
        } else {
            rsvpResultIndex
        }
    val effectiveUiState =
        if (safeRsvpResultIndex >= 0 &&
            rsvpResultChapterIndex == uiState.chapterIndex
        ) {
            uiState.copy(focusIndex = safeRsvpResultIndex)
        } else {
            uiState
        }

    LaunchedEffect(rsvpResultChapterIndex, safeRsvpResultIndex, rsvpResultResumeCursor) {
        if (rsvpResultChapterIndex >= 0 && safeRsvpResultIndex >= 0) {
            if (rsvpResultChapterIndex != uiState.chapterIndex) {
                readerViewModel.loadChapter(rsvpResultChapterIndex, safeRsvpResultIndex)
            } else if (safeRsvpResultIndex != uiState.focusIndex) {
                readerViewModel.applyFocusIndex(safeRsvpResultIndex)
            }
            val wordIndex =
                if (rsvpResultChapterIndex == uiState.chapterIndex) {
                    resolveWordIndex(
                        uiState.chapterData?.wordCountByToken,
                        safeRsvpResultIndex,
                    )
                } else {
                    0
                }
            val resumeCursor =
                if (rsvpResultChapterIndex == uiState.chapterIndex) {
                    rsvpResultResumeCursor
                } else {
                    -1
                }
            readerPositionSaver.saveImmediate(
                ReadingPosition(
                    BookId(bookId),
                    rsvpResultChapterIndex,
                    safeRsvpResultIndex,
                    wordIndex,
                    rsvpResumeCursor = resumeCursor,
                ),
            )
            backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX] = -1
            backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX] = -1
            backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR] = -1
        }
    }

    var hasInitialized by rememberSaveable { mutableStateOf(false) }
    val restoresSavedPositionOnInitialLoad = initialChapterIndex == null || initialTokenIndex == null

    LaunchedEffect(book) {
        if (!hasInitialized || uiState.chapterData == null) {
            val savedPosition =
                if (restoresSavedPositionOnInitialLoad || hasInitialized) {
                    container.readingPositionRepository.getPosition(BookId(bookId))
                } else {
                    null
                }
            val initialChapter = savedPosition?.chapterIndex ?: initialChapterIndex ?: 0
            val initialFocus = savedPosition?.tokenIndex ?: initialTokenIndex ?: 0
            readerViewModel.loadBook(book, initialChapter, initialFocus)
            hasInitialized = true
        }
    }

    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED && hasInitialized) {
            if (rsvpResultChapterIndex >= 0 && safeRsvpResultIndex >= 0) {
                return@LaunchedEffect
            }
            val savedPosition = container.readingPositionRepository.getPosition(BookId(bookId))
            if (savedPosition != null && savedPosition.chapterIndex == uiState.chapterIndex) {
                val tokens = uiState.chapterData?.tokens
                if (tokens != null && savedPosition.tokenIndex != uiState.focusIndex) {
                    readerViewModel.applyFocusIndex(
                        savedPosition.tokenIndex.coerceIn(0, tokens.lastIndex)
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.chapterIndex, uiState.chapterData) {
        if (!hasInitialized) return@LaunchedEffect
        val tokens = uiState.chapterData?.tokens ?: return@LaunchedEffect
        if (tokens.isEmpty()) return@LaunchedEffect
        val safeIndex = tokens.nearestWordIndex(uiState.focusIndex).coerceIn(0, tokens.lastIndex)
        val wordIndex = resolveWordIndex(uiState.chapterData?.wordCountByToken, safeIndex)
        readerPositionSaver.saveDebounced(
            ReadingPosition(
                BookId(bookId),
                uiState.chapterIndex,
                safeIndex,
                wordIndex,
            ),
        )
    }

    val resolvedRsvpConfig = RsvpConfigResolver.resolve(prefs.rsvpConfig, book.languageTag)
    val readerEstimatedWpm =
        rememberReaderEstimatedWpm(
            baseConfig = resolvedRsvpConfig,
            fallbackEstimatedWpm = estimatedWpm,
            dispatcherProvider = dispatcherProvider,
            languageTag = book.languageTag,
        )
    LaunchedEffect(
        uiState.chapterIndex,
        uiState.chapterData,
        uiState.focusIndex,
        resolvedRsvpConfig,
    ) {
        if (!hasInitialized) return@LaunchedEffect
        val chapterData = uiState.chapterData ?: return@LaunchedEffect
        if (chapterData.tokens.isEmpty()) return@LaunchedEffect
        val safeStartIndex =
            chapterData.tokens.nearestWordIndex(uiState.focusIndex)
                .coerceIn(0, chapterData.tokens.lastIndex)
        container.rsvpFrameRepository.prefetchFrames(
            BookId(bookId),
            uiState.chapterIndex,
            resolvedRsvpConfig,
            startIndex = safeStartIndex,
        )
    }

    val focusEnabledInReader = prefs.focusModeEnabled && prefs.focusApplyInReader
    var lastExplicitFocusIndex by remember(bookId) { mutableIntStateOf(-1) }
    fun buildCurrentReaderPosition(tokenIndex: Int = effectiveUiState.focusIndex): ReadingPosition? {
        val chapterData = effectiveUiState.chapterData ?: return null
        val tokens = chapterData.tokens
        if (tokens.isEmpty()) return null
        val safeIndex = tokens.nearestWordIndex(tokenIndex).coerceIn(0, tokens.lastIndex)
        val wordIndex = resolveWordIndex(chapterData.wordCountByToken, safeIndex)
        return ReadingPosition(
            BookId(bookId),
            effectiveUiState.chapterIndex,
            safeIndex,
            wordIndex,
        )
    }

    fun saveCurrentReaderPosition() {
        buildCurrentReaderPosition()?.let(readerPositionSaver::saveImmediate)
    }

    fun navigateReaderToLibrary() {
        val position =
            lastExplicitFocusIndex
                .takeIf { it >= 0 }
                ?.let(::buildCurrentReaderPosition)
                ?: buildCurrentReaderPosition()
        lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
            if (position != null) {
                readerPositionSaver.saveImmediateAndJoin(position)
            }
            withContext(Dispatchers.Main) {
                navController.navigate(KairoRoutes.LIBRARY) {
                    popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    BackHandler(enabled = !tutorialActive) {
        navigateReaderToLibrary()
    }

    ReaderScreen(
        book = book,
        uiState = effectiveUiState,
        fontSizeSp = prefs.readerFontSizeSp,
        invertedScroll = prefs.invertedScroll,
        readerTheme = prefs.readerTheme,
        textBrightness = prefs.readerTextBrightness,
        estimatedWpm = readerEstimatedWpm,
        onFontSizeChange = { size ->
            coroutineScope.launch {
                container.preferencesRepository.updateFontSize(size)
            }
        },
        onThemeChange = { theme ->
            coroutineScope.launch {
                container.preferencesRepository.updateTheme(theme.name)
            }
        },
        onTextBrightnessChange = { brightness ->
            coroutineScope.launch {
                container.preferencesRepository.updateReaderTextBrightness(brightness)
            }
        },
        onInvertedScrollChange = { enabled ->
            coroutineScope.launch {
                container.preferencesRepository.updateInvertedScroll(enabled)
            }
        },
        focusModeEnabled = focusEnabledInReader,
        onFocusModeEnabledChange = { enabled ->
            coroutineScope.launch {
                if (enabled) {
                    if (!prefs.focusModeEnabled) {
                        container.preferencesRepository.updateFocusModeEnabled(true)
                    }
                    container.preferencesRepository.updateFocusApplyInReader(true)
                } else {
                    container.preferencesRepository.updateFocusApplyInReader(false)
                }
            }
        },
        onAddBookmark = { chapterIndex, tokenIndex, previewText ->
            coroutineScope.launch {
                val id = "$bookId:$chapterIndex:$tokenIndex"
                container.bookmarkRepository.add(
                    Bookmark(
                        id = id,
                        bookId = BookId(bookId),
                        chapterIndex = chapterIndex,
                        tokenIndex = tokenIndex,
                        previewText = previewText,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                onShowUserMessage(resources.getString(R.string.toast_bookmark_added))
            }
        },
        onOpenBookmarks = {
            saveCurrentReaderPosition()
            navController.navigate(KairoRoutes.libraryBookmarks())
        },
        onOpenLibrary = ::navigateReaderToLibrary,
        onFocusChange = { newFocusIndex ->
            lastExplicitFocusIndex = newFocusIndex
            readerViewModel.setFocusIndex(newFocusIndex)
            val wordIndex = resolveWordIndex(uiState.chapterData?.wordCountByToken, newFocusIndex)
            readerPositionSaver.saveDebounced(
                ReadingPosition(
                    BookId(bookId),
                    uiState.chapterIndex,
                    newFocusIndex,
                    wordIndex,
                ),
            )
        },
        onStartRsvp = { start ->
            RsvpLaunchSnapshotStore.put(
                bookId = bookId,
                chapterIndex = uiState.chapterIndex,
                tokens = uiState.chapterData?.tokens.orEmpty(),
            )
            val wordIndex = resolveWordIndex(uiState.chapterData?.wordCountByToken, start)
            lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                val existingPosition =
                    container.readingPositionRepository.getPosition(BookId(bookId))
                val resumeCursor =
                    existingPosition
                        ?.takeIf {
                            it.chapterIndex == uiState.chapterIndex &&
                                it.tokenIndex == start
                        }?.rsvpResumeCursor ?: -1
                readerPositionSaver.saveImmediateAndJoin(
                    ReadingPosition(
                        BookId(bookId),
                        uiState.chapterIndex,
                        start,
                        wordIndex,
                        rsvpResumeCursor = resumeCursor,
                    ),
                )
                withContext(Dispatchers.Main) {
                    navController.navigate(
                        KairoRoutes.rsvp(
                            bookId = bookId,
                            chapterIndex = uiState.chapterIndex,
                            tokenIndex = start,
                        )
                    )
                }
            }
        },
        onChapterChange = { newIndex, focusIndex ->
            readerViewModel.loadChapter(newIndex, focusIndex)
        },
        onViewportMetricsChanged = { resolvedFontSizeSp, viewportHeightDp ->
            readerViewModel.updatePaginationMetrics(resolvedFontSizeSp, viewportHeightDp)
        },
        tutorialState = tutorialState,
        onTutorialNext = onTutorialNext,
        onTutorialPrevious = onTutorialPrevious,
        onTutorialSkip = onTutorialSkip,
    )
}
