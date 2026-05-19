package com.kairo.reader.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.ui.reader.ReaderScreen
import com.kairo.reader.ui.reader.ReaderViewModel
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState

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
        produceState<ReaderBookLoadState>(
            initialValue = ReaderBookLoadState.Loading,
            bookId,
        ) {
            value =
                runCatching { container.bookRepository.getBook(BookId(bookId)) }
                    .fold(
                        onSuccess = { book -> ReaderBookLoadState.Loaded(book) },
                        onFailure = { ReaderBookLoadState.Missing },
                    )
        }
    val book =
        when (val state = bookState.value) {
            is ReaderBookLoadState.Loaded -> state.book
            ReaderBookLoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return
            }
            ReaderBookLoadState.Missing -> {
                ReaderMissingBookState(
                    onOpenLibrary = {
                        navController.navigate(KairoRoutes.LIBRARY) {
                            popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
                return
            }
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

    LaunchedEffect(
        rsvpResultChapterIndex,
        safeRsvpResultIndex,
        rsvpResultResumeCursor,
        uiState.chapterIndex,
        uiState.chapterData,
    ) {
        if (rsvpResultChapterIndex >= 0 && safeRsvpResultIndex >= 0) {
            if (rsvpResultChapterIndex != uiState.chapterIndex) {
                readerViewModel.loadChapter(rsvpResultChapterIndex, safeRsvpResultIndex)
                return@LaunchedEffect
            }
            val chapterData = uiState.chapterData ?: return@LaunchedEffect
            val safeTargetIndex =
                if (chapterData.tokens.isNotEmpty()) {
                    chapterData.tokens.nearestWordIndex(safeRsvpResultIndex)
                        .coerceIn(0, chapterData.tokens.lastIndex)
                } else {
                    safeRsvpResultIndex
                }
            if (safeTargetIndex != uiState.focusIndex) {
                readerViewModel.applyFocusIndex(safeTargetIndex)
            }
            val wordIndex = resolveWordIndex(chapterData.wordCountByToken, safeTargetIndex)
            readerPositionSaver.saveImmediate(
                ReadingPosition(
                    BookId(bookId),
                    rsvpResultChapterIndex,
                    safeTargetIndex,
                    wordIndex,
                    rsvpResumeCursor = rsvpResultResumeCursor,
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
    val readerCallbacks =
        buildReaderRouteCallbacks(
            ReaderRouteCallbackDependencies(
                container = container,
                navController = navController,
                prefs = prefs,
                bookId = bookId,
                bookIdValue = BookId(bookId),
                dispatcherProvider = dispatcherProvider,
                coroutineScope = coroutineScope,
                lifecycleScope = lifecycleOwner.lifecycleScope,
                resources = resources,
                uiState = uiState,
                effectiveUiState = effectiveUiState,
                readerViewModel = readerViewModel,
                readerPositionSaver = readerPositionSaver,
                getLastExplicitFocusIndex = { lastExplicitFocusIndex },
                setLastExplicitFocusIndex = { lastExplicitFocusIndex = it },
                onShowUserMessage = onShowUserMessage,
            )
        )

    BackHandler(enabled = !tutorialActive) {
        readerCallbacks.onOpenLibrary()
    }

    ReaderScreen(
        book = book,
        uiState = effectiveUiState,
        fontSizeSp = prefs.readerFontSizeSp,
        invertedScroll = prefs.invertedScroll,
        readerTheme = prefs.readerTheme,
        textBrightness = prefs.readerTextBrightness,
        estimatedWpm = readerEstimatedWpm,
        onFontSizeChange = readerCallbacks.onFontSizeChange,
        onThemeChange = readerCallbacks.onThemeChange,
        onTextBrightnessChange = readerCallbacks.onTextBrightnessChange,
        onInvertedScrollChange = readerCallbacks.onInvertedScrollChange,
        focusModeEnabled = focusEnabledInReader,
        onFocusModeEnabledChange = readerCallbacks.onFocusModeEnabledChange,
        onAddBookmark = readerCallbacks.onAddBookmark,
        onOpenBookmarks = readerCallbacks.onOpenBookmarks,
        onOpenLibrary = readerCallbacks.onOpenLibrary,
        onFocusChange = readerCallbacks.onFocusChange,
        onPageChange = readerCallbacks.onPageChange,
        onStartRsvp = readerCallbacks.onStartRsvp,
        onChapterChange = readerCallbacks.onChapterChange,
        onViewportMetricsChanged = readerCallbacks.onViewportMetricsChanged,
        tutorialState = tutorialState,
        onTutorialNext = onTutorialNext,
        onTutorialPrevious = onTutorialPrevious,
        onTutorialSkip = onTutorialSkip,
    )
}

private sealed interface ReaderBookLoadState {
    data object Loading : ReaderBookLoadState
    data class Loaded(val book: Book) : ReaderBookLoadState
    data object Missing : ReaderBookLoadState
}

@Composable
private fun ReaderMissingBookState(onOpenLibrary: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.reader_missing_book_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.reader_missing_book_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenLibrary) {
                Text(text = stringResource(R.string.action_return_to_library))
            }
        }
    }
}
