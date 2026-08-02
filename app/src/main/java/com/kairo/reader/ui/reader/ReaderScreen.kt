@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
)

package com.kairo.reader.ui.reader

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.TextUtilsCompat
import com.kairo.reader.core.language.BookLanguageResolver
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.TableOfContentsTarget
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.ui.tutorial.StartingTutorialOverlay
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget
import java.util.Locale

private val READER_MIN_BOTTOM_CONTENT_PADDING = 24.dp
private val READER_RSVP_LAUNCHER_CLEARANCE = 96.dp
private const val READER_CHROME_COLLAPSE_SCROLL_PX = 56

/**
 * Main reader screen - can be called directly with ViewModel state.
 *
 * Usage with ViewModel:
 * ```
 * val uiState by viewModel.uiState.collectAsState()
 * ReaderScreen(
 *     book = book,
 *     uiState = uiState,
 *     fontSizeSp = 18f,
 *     invertedScroll = false,
 *     onFocusChange = viewModel::setFocusIndex,
 *     timedReadingMode = TimedReadingMode.RSVP,
 *     onStartTimedReading = { mode, index -> navigateToTimedReading(mode, index) },
 *     onSelectTimedReadingMode = { mode, index -> selectAndNavigate(mode, index) },
 *     onChapterChange = viewModel::loadChapter
 * )
 * ```
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    uiState: ReaderUiState,
    fontSizeSp: Float,
    invertedScroll: Boolean,
    readerTheme: ReaderTheme,
    textBrightness: Float,
    estimatedWpm: Int,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit,
    focusModeEnabled: Boolean,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onAddBookmark: (chapterIndex: Int, tokenIndex: Int, previewText: String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenLibrary: () -> Unit,
    onFocusChange: (Int) -> Unit,
    onPageChange: (pageIndex: Int, focusTokenIndex: Int) -> Unit,
    timedReadingMode: TimedReadingMode,
    onStartTimedReading: (TimedReadingMode, Int) -> Unit,
    onSelectTimedReadingMode: (TimedReadingMode, Int) -> Unit,
    onChapterChange: (Int, Int?) -> Unit,
    onTableOfContentsTargetSelected: (TableOfContentsTarget) -> Unit,
    onViewportMetricsChanged: (fontSizeSp: Float, viewportHeightDp: Int) -> Unit,
    tutorialState: StartingTutorialOverlayState? = null,
    onTutorialNext: () -> Unit = {},
    onTutorialPrevious: () -> Unit = {},
    onTutorialSkip: () -> Unit = {},
) {
    val chapterIndex = uiState.chapterIndex
    val focusIndex = uiState.focusIndex
    val chapter = book.chapters.getOrNull(chapterIndex)
    val coverImage = book.coverImage

    val renderState =
        rememberReaderRenderState(
            chapterIndex = chapterIndex,
            focusIndex = focusIndex,
            pageIndexOverride = uiState.pageIndexOverride,
            coverImage = coverImage,
            chapterData = uiState.chapterData,
        )
    val activeTableOfContentsEntry =
        remember(
            book.tableOfContents,
            chapterIndex,
            focusIndex,
            uiState.chapterData,
        ) {
            resolveActiveTableOfContentsEntry(
                entries = book.tableOfContents,
                chapterIndex = chapterIndex,
                focusIndex = focusIndex,
                chapterData = uiState.chapterData,
            )
        }
    val nonInteractiveChapterLinkTargets =
        remember(book) {
            resolveNonInteractiveChapterLinkTargets(book)
        }
    val contentLayoutDirection =
        remember(book) {
            val languageTag = BookLanguageResolver.resolve(book)
            val locale = languageTag?.let { Locale.forLanguageTag(it) }
            val layoutDirection =
                if (locale == null) {
                    View.LAYOUT_DIRECTION_LTR
                } else {
                    TextUtilsCompat.getLayoutDirectionFromLocale(locale)
                }
            if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
        }
    val onSafeFocusChange =
        remember(renderState.tokens, onFocusChange) {
            { index: Int ->
                if (renderState.tokens.isNotEmpty()) {
                    onFocusChange(renderState.tokens.nearestWordIndex(index))
                }
            }
        }

    val listStateHolder =
        rememberReaderListState(
            listStateKey = renderState.listStateKey,
            focusListIndex = renderState.focusListIndex,
            listItemCount = renderState.listItemCount,
            invertedScroll = invertedScroll,
        )
    val chromeCollapsed by remember(listStateHolder.listState) {
        derivedStateOf {
            val state = listStateHolder.listState
            state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > READER_CHROME_COLLAPSE_SCROLL_PX
        }
    }
    val configuration = LocalConfiguration.current
    val viewportHeightDp = configuration.screenHeightDp
    val compactLandscape =
        configuration.screenWidthDp > configuration.screenHeightDp &&
            configuration.screenHeightDp <= 480
    LaunchedEffect(fontSizeSp, viewportHeightDp) {
        onViewportMetricsChanged(fontSizeSp, viewportHeightDp)
    }

    // Chapter list bottom sheet state
    val showChapterList = remember { mutableStateOf(false) }
    var showReaderMenu by remember { mutableStateOf(false) }
    var showReaderDetails by remember { mutableStateOf(false) }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    var swipeDirection by remember { mutableStateOf<ReaderSwipeDirection?>(null) }
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }
    val tutorialTargetId = tutorialState?.step?.targetId

    LaunchedEffect(tutorialTargetId) {
        if (tutorialState == null) return@LaunchedEffect
        showReaderMenu =
            tutorialTargetId == StartingTutorialTargetIds.READER_MENU_SETTINGS
    }

    val effectiveTimedReadingMode =
        timedReadingModeForReader(
            selectedMode = timedReadingMode,
            tutorialActive = tutorialState != null,
        )
    val isTimedReadingEnabled =
        !uiState.isLoading && renderState.firstWordIndex != -1 && renderState.tokens.isNotEmpty()
    val onStartTimedReadingForToken =
        remember(
            isTimedReadingEnabled,
            renderState.tokens,
            effectiveTimedReadingMode,
            onStartTimedReading,
        ) {
            { tokenIndex: Int ->
                if (isTimedReadingEnabled && renderState.tokens.isNotEmpty()) {
                    onStartTimedReading(
                        effectiveTimedReadingMode,
                        renderState.tokens.nearestWordIndex(tokenIndex),
                    )
                }
            }
        }
    val progressState =
        rememberReaderProgressState(
            ReaderProgressInput(
                safeFocusIndex = renderState.safeFocusIndex,
                totalChapterWords = renderState.totalChapterWords,
                wordCountByToken = renderState.wordCountByToken,
                resolvedPageIndex = renderState.resolvedPageIndex,
                pages = renderState.pages,
                currentPage = renderState.currentPage,
                estimatedWpm = estimatedWpm,
                bookWordCounts = uiState.bookWordCounts,
                chapterIndex = chapterIndex,
                chapterCount = book.chapters.size,
            ),
        )
    val navigationState =
        buildReaderNavigationState(
            pages = renderState.pages,
            isPagedChapter = renderState.isPagedChapter,
            resolvedPageIndex = renderState.resolvedPageIndex,
            chapterIndex = chapterIndex,
            lastChapterIndex = book.chapters.lastIndex,
            onPageChange = { page -> onPageChange(page.index, page.focusTokenIndex) },
            onChapterChange = onChapterChange,
        )
    val bottomInsetPadding = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Bottom
    ).asPaddingValues()
    val bottomInset = bottomInsetPadding.calculateBottomPadding()
    val showTimedReadingLauncher =
        isTimedReadingEnabled &&
            renderState.currentPage?.kind != ChapterPageKind.IMAGE &&
            renderState.currentPage?.kind != ChapterPageKind.BLANK &&
            !showReaderMenu &&
            !showChapterList.value
    val overlayBottomPadding =
        if (showTimedReadingLauncher) {
            if (compactLandscape) {
                76.dp
            } else {
                READER_RSVP_LAUNCHER_CLEARANCE
            }
        } else {
            READER_MIN_BOTTOM_CONTENT_PADDING
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    if (focusModeEnabled) {
                        // In focus mode, do not reserve space for the (hidden) status bar.
                        WindowInsets.displayCutout.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    } else {
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    },
                ).padding(
                    start = if (compactLandscape) 12.dp else 16.dp,
                    end = if (compactLandscape) 12.dp else 16.dp,
                    top =
                    when {
                        compactLandscape -> if (focusModeEnabled) 4.dp else 8.dp
                        focusModeEnabled -> 8.dp
                        else -> 16.dp
                    },
                ),
        ) {
            ReaderHeader(
                state =
                ReaderHeaderState(
                    book = book,
                    chapterIndex = chapterIndex,
                    chapterTitle = sanitizeChapterTitleForDisplay(chapter?.title),
                    tableOfContentsLabel = activeTableOfContentsEntry?.label?.takeIf(String::isNotBlank),
                    coverImage = coverImage,
                    canGoPrev = navigationState.canGoPrevPage,
                    canGoNext = navigationState.canGoNextPage,
                    compactMode = chromeCollapsed,
                    landscapeCompact = compactLandscape,
                    detailsExpanded = showReaderDetails,
                    pageLabel = progressState.pageLabel,
                    progressPercent =
                    if (renderState.totalChapterWords > 0) progressState.progressPercent else null,
                    progressFraction = progressState.progressFraction,
                    etaLabel = progressState.etaLabel,
                    navigationModifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.READER_NAVIGATION) {
                            targetId,
                            bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                    menuModifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.READER_MENU) {
                            targetId,
                            bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                ),
                actions =
                ReaderHeaderActions(
                    onPrev = navigationState.onPrevPage,
                    onNext = navigationState.onNextPage,
                    onOpenLibrary = onOpenLibrary,
                    onShowMenu = { showReaderMenu = !showReaderMenu },
                    onToggleDetails = { showReaderDetails = !showReaderDetails },
                ),
            )
            Spacer(
                modifier =
                Modifier.height(
                    when {
                        showReaderDetails && compactLandscape -> 6.dp
                        showReaderDetails -> 12.dp
                        compactLandscape -> 4.dp
                        else -> 8.dp
                    },
                ),
            )

            CompositionLocalProvider(LocalLayoutDirection provides contentLayoutDirection) {
                ReaderContent(
                    modifier = Modifier.weight(1f),
                    state =
                    ReaderContentState(
                        book = book,
                        chapterIndex = chapterIndex,
                        coverImage = coverImage,
                        isLoading = uiState.isLoading,
                        loadErrorMessage = uiState.chapterLoadError,
                        isCoverChapter = renderState.isCoverChapter,
                        isPagedChapter = renderState.isPagedChapter,
                        resolvedPageIndex = renderState.resolvedPageIndex,
                        fullScreenTitlePageImagePath = renderState.fullScreenTitlePageImagePath,
                        headerCarouselImages = renderState.headerCarouselImages,
                        showHeaderCarousel = renderState.showHeaderCarousel,
                        isBlankPage = renderState.currentPage?.kind == ChapterPageKind.BLANK,
                        displayBlocks = renderState.displayBlocks,
                        listState = listStateHolder.listState,
                        listStateKey = renderState.listStateKey,
                        invertedScroll = invertedScroll,
                        bottomInset = bottomInset,
                        overlayBottomPadding = overlayBottomPadding,
                        focusIndex = focusIndex,
                        fontSizeSp = fontSizeSp,
                        textBrightness = textBrightness,
                        timedReadingMode = effectiveTimedReadingMode,
                        nonInteractiveChapterLinkTargets = nonInteractiveChapterLinkTargets,
                        invertedScrollCommands = listStateHolder.invertedScrollCommands,
                    ),
                    actions =
                    ReaderContentActions(
                        onSafeFocusChange = onSafeFocusChange,
                        onStartTimedReadingForToken = onStartTimedReadingForToken,
                        onPrevPage = navigationState.onPrevPage,
                        onNextPage = navigationState.onNextPage,
                        onSwipePreviewChange = { direction, progress ->
                            swipeDirection = direction
                            swipeProgress = progress
                        },
                        onOpenFullScreenImage = { fullScreenImagePath = it },
                        onChapterSelected = { index -> onChapterChange(index, 0) },
                    ),
                )
            }
        }

        ReaderSwipePageChrome(
            direction = swipeDirection,
            progress = swipeProgress,
            canGoPrev = navigationState.canGoPrevPage,
            canGoNext = navigationState.canGoNextPage,
            modifier = Modifier.fillMaxSize(),
        )

        if (showChapterList.value) {
            BackHandler { showChapterList.value = false }
            ChapterListOverlay(
                book = book,
                currentChapterIndex = chapterIndex,
                currentTableOfContentsEntry = activeTableOfContentsEntry,
                onDismiss = { showChapterList.value = false },
                onTargetSelected = { target ->
                    onTableOfContentsTargetSelected(target)
                    showChapterList.value = false
                },
            )
        }

        if (showReaderMenu) {
            BackHandler { showReaderMenu = false }
            ReaderMenuOverlay(
                state =
                ReaderMenuState(
                    fontSizeSp = fontSizeSp,
                    readerTheme = readerTheme,
                    textBrightness = textBrightness,
                    invertedScroll = invertedScroll,
                    focusModeEnabled = focusModeEnabled,
                    readerSettingsRowModifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.READER_MENU_SETTINGS) {
                            targetId,
                            bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                ),
                actions =
                ReaderMenuActions(
                    onFontSizeChange = onFontSizeChange,
                    onThemeChange = onThemeChange,
                    onTextBrightnessChange = onTextBrightnessChange,
                    onInvertedScrollChange = onInvertedScrollChange,
                    onFocusModeEnabledChange = onFocusModeEnabledChange,
                    onAddBookmark = {
                        if (renderState.tokens.isNotEmpty()) {
                            val safeTokenIndex = renderState.tokens.nearestWordIndex(focusIndex)
                                .coerceIn(0, renderState.tokens.lastIndex)
                            val preview = renderState.tokens.getOrNull(safeTokenIndex)?.text.orEmpty()
                            onAddBookmark(chapterIndex, safeTokenIndex, preview)
                            showReaderMenu = false
                        }
                    },
                    onOpenBookmarks = {
                        showReaderMenu = false
                        onOpenBookmarks()
                    },
                    onShowToc = {
                        showReaderMenu = false
                        showChapterList.value = true
                    },
                    onDismiss = { showReaderMenu = false },
                ),
            )
        }

        AnimatedVisibility(
            visible = showTimedReadingLauncher,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + bottomInset),
        ) {
            ReaderTimedReadingLauncher(
                state =
                ReaderTimedReadingLauncherState(
                    tokens = renderState.tokens,
                    focusIndex = focusIndex,
                    invertedScroll = invertedScroll,
                    listState = listStateHolder.listState,
                    focusListIndex = renderState.focusListIndex,
                    progressFraction = progressState.progressFraction,
                    selectedMode = effectiveTimedReadingMode,
                    modeSelectionEnabled = tutorialState == null,
                ),
                actions =
                ReaderTimedReadingLauncherActions(
                    onFocusChange = onFocusChange,
                    onStartTimedReading = onStartTimedReading,
                    onSelectTimedReadingMode = onSelectTimedReadingMode,
                ),
                modifier =
                Modifier.startingTutorialTarget(StartingTutorialTargetIds.READER_RSVP_LAUNCHER) {
                        targetId,
                        bounds,
                    ->
                    tutorialTargets[targetId] = bounds
                },
            )
        }

        tutorialState?.let { overlayState ->
            StartingTutorialOverlay(
                state = overlayState,
                targetBounds = overlayState.step.targetId?.let(tutorialTargets::get),
                onNext = onTutorialNext,
                onPrevious = onTutorialPrevious,
                onSkip = onTutorialSkip,
            )
        }

        fullScreenImagePath?.let { path ->
            BackHandler { fullScreenImagePath = null }
            FullScreenImageViewer(
                imagePath = path,
                onDismiss = { fullScreenImagePath = null },
            )
        }
    }
}

internal fun timedReadingModeForReader(
    selectedMode: TimedReadingMode,
    tutorialActive: Boolean,
): TimedReadingMode =
    if (tutorialActive) TimedReadingMode.RSVP else selectedMode
