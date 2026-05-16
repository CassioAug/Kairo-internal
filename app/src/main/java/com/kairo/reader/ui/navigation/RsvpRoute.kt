package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.buildWordCountByToken
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.ui.rsvp.RsvpBookContext
import com.kairo.reader.ui.rsvp.RsvpBookmarkCallbacks
import com.kairo.reader.ui.rsvp.RsvpLayoutBias
import com.kairo.reader.ui.rsvp.RsvpPlaybackCallbacks
import com.kairo.reader.ui.rsvp.RsvpPreferenceCallbacks
import com.kairo.reader.ui.rsvp.RsvpProfileContext
import com.kairo.reader.ui.rsvp.RsvpScreen
import com.kairo.reader.ui.rsvp.RsvpScreenCallbacks
import com.kairo.reader.ui.rsvp.RsvpScreenDependencies
import com.kairo.reader.ui.rsvp.RsvpScreenState
import com.kairo.reader.ui.rsvp.RsvpTextStyle
import com.kairo.reader.ui.rsvp.RsvpThemeCallbacks
import com.kairo.reader.ui.rsvp.RsvpUiCallbacks
import com.kairo.reader.ui.rsvp.RsvpUiPreferences
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import kotlinx.coroutines.launch

@Composable
internal fun RsvpRoute(
    backStackEntry: NavBackStackEntry,
    container: KairoApplication,
    navController: NavHostController,
    prefs: UserPreferences,
    tutorialState: StartingTutorialOverlayState?,
    onShowUserMessage: (String) -> Unit,
    onTutorialNext: () -> Unit,
    onTutorialPrevious: () -> Unit,
    onTutorialSkip: () -> Unit,
) {
    val bookId = backStackEntry.arguments?.getString(KairoRoutes.ARG_BOOK_ID) ?: return
    val chapterIndex = backStackEntry.arguments?.getInt(KairoRoutes.ARG_CHAPTER_INDEX) ?: 0
    val startIndex = backStackEntry.arguments?.getInt(KairoRoutes.ARG_TOKEN_INDEX) ?: 0
    val coroutineScope = rememberCoroutineScope()
    val dispatcherProvider = container.dispatcherProvider
    val resources = LocalResources.current
    val launchSnapshotTokens =
        remember(bookId, chapterIndex) {
            RsvpLaunchSnapshotStore.tokensFor(bookId, chapterIndex)
        }
    val tokensState =
        produceState(
            initialValue = launchSnapshotTokens,
            bookId,
            chapterIndex,
        ) {
            val loadedTokens =
                runCatching {
                    container.tokenRepository.getTokens(BookId(bookId), chapterIndex)
                }.getOrElse { emptyList() }
            if (loadedTokens.isNotEmpty() || value.isEmpty()) {
                value = loadedTokens
            }
        }
    val tokens = tokensState.value
    val wordCountByToken = remember(tokens) { buildWordCountByToken(tokens) }

    val focusEnabledInRsvp = prefs.focusModeEnabled && prefs.focusApplyInRsvp
    val rsvpLifecycleOwner = LocalLifecycleOwner.current
    val bookIdValue = BookId(bookId)
    val savedCurrentTokenIndex =
        remember(backStackEntry) {
            backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_CURRENT_TOKEN_INDEX] ?: -1
        }
    val savedCurrentResumeCursor =
        remember(backStackEntry) {
            backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_CURRENT_RESUME_CURSOR] ?: -1
        }
    val safeStartIndex =
        savedCurrentTokenIndex
            .takeIf { it >= 0 }
            ?: startIndex.coerceAtLeast(0)
    val chapterCountState =
        produceState(
            initialValue = chapterIndex + 1,
            bookId,
        ) {
            value =
                runCatching {
                    container.bookRepository.getBook(bookIdValue).chapters.size
                }.getOrDefault(chapterIndex + 1)
        }
    val savedResumePositionState =
        produceState<ReadingPosition?>(
            initialValue = null,
            bookId,
            chapterIndex,
            safeStartIndex,
        ) {
            value = container.readingPositionRepository.getPosition(bookIdValue)
        }
    val startResumeCursor =
        savedCurrentResumeCursor
            .takeIf { savedCurrentTokenIndex >= 0 && it >= 0 }
            ?: savedResumePositionState.value
                ?.takeIf {
                    it.chapterIndex == chapterIndex &&
                        it.tokenIndex == safeStartIndex &&
                        it.rsvpResumeCursor >= 0
                }?.rsvpResumeCursor
            ?: -1
    val playbackIsPlayingFlow =
        remember(backStackEntry) {
            backStackEntry.savedStateHandle.getStateFlow(
                KairoSavedStateKeys.RSVP_PLAYBACK_IS_PLAYING,
                true,
            )
        }
    val playbackIsPlaying by playbackIsPlayingFlow.collectAsState(initial = true)
    val languageTagState =
        produceState<String?>(
            initialValue = null,
            bookId,
        ) {
            value =
                runCatching { container.bookRepository.getBookLanguageTag(bookIdValue) }
                    .getOrNull()
        }
    val resolvedRsvpConfig =
        RsvpConfigResolver.resolve(prefs.rsvpConfig, languageTagState.value)
    fun saveRsvpPosition(
        targetChapterIndex: Int,
        targetTokenIndex: Int,
        targetWordIndex: Int,
        targetResumeCursor: Int,
    ) {
        rsvpLifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
            container.readingPositionRepository.savePosition(
                ReadingPosition(
                    bookIdValue,
                    targetChapterIndex,
                    targetTokenIndex,
                    targetWordIndex,
                    rsvpResumeCursor = targetResumeCursor,
                ),
            )
        }
    }

    val rsvpState =
        RsvpScreenState(
            book =
                RsvpBookContext(
                    bookId = bookIdValue,
                    chapterIndex = chapterIndex,
                    tokens = tokens,
                    startIndex = safeStartIndex,
                    startResumeCursor = startResumeCursor,
                    sessionStartIndex = startIndex.coerceAtLeast(0),
                ),
            profile =
                RsvpProfileContext(
                    config = resolvedRsvpConfig,
                    selectedProfileId = prefs.rsvpSelectedProfileId,
                    customProfiles = prefs.rsvpCustomProfiles,
                ),
            initialIsPlaying = playbackIsPlaying,
            uiPrefs =
                RsvpUiPreferences(
                    extremeSpeedUnlocked = prefs.unlockExtremeSpeed,
                    readerTheme = prefs.readerTheme,
                    focusModeEnabled = focusEnabledInRsvp,
                ),
            textStyle =
                RsvpTextStyle(
                    fontSizeSp = prefs.rsvpFontSizeSp,
                    fontFamily = prefs.rsvpFontFamily,
                    fontWeight = prefs.rsvpFontWeight,
                    textBrightness = prefs.rsvpTextBrightness,
                ),
            layoutBias =
                RsvpLayoutBias(
                    verticalBias = prefs.rsvpVerticalBias,
                    horizontalBias = prefs.rsvpHorizontalBias,
                ),
        )
    val rsvpCallbacks =
        RsvpScreenCallbacks(
            bookmarks =
                RsvpBookmarkCallbacks(
                    onAddBookmark = { tokenIndex, previewText ->
                        coroutineScope.launch {
                            val id = "$bookId:$chapterIndex:$tokenIndex"
                            container.bookmarkRepository.add(
                                Bookmark(
                                    id = id,
                                    bookId = bookIdValue,
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
                        navController.navigate(KairoRoutes.libraryBookmarks()) {
                            popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                        }
                    },
                ),
            playback =
                RsvpPlaybackCallbacks(
                    onFinished = { resumePoint ->
                        val returnTarget =
                            resolveRsvpReturnTarget(
                                resumePoint = resumePoint,
                                currentChapterIndex = chapterIndex,
                                chapterCount = chapterCountState.value,
                                currentChapterTokens = tokens,
                            )
                        val wordIndex =
                            if (returnTarget.chapterIndex == chapterIndex) {
                                resolveWordIndex(wordCountByToken, returnTarget.tokenIndex)
                            } else {
                                0
                            }
                        saveRsvpPosition(
                            targetChapterIndex = returnTarget.chapterIndex,
                            targetTokenIndex = returnTarget.tokenIndex,
                            targetWordIndex = wordIndex,
                            targetResumeCursor = returnTarget.resumeCursor,
                        )
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX,
                                returnTarget.chapterIndex,
                            )
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX,
                                returnTarget.tokenIndex,
                            )
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR,
                                returnTarget.resumeCursor,
                            )
                        navController.popBackStack()
                    },
                    onPositionChanged = { resumePoint ->
                        val safeIndex =
                            if (tokens.isNotEmpty()) {
                                resumePoint.tokenIndex.coerceIn(0, tokens.lastIndex)
                            } else {
                                0
                            }
                        backStackEntry.savedStateHandle[
                            KairoSavedStateKeys.RSVP_CURRENT_TOKEN_INDEX
                        ] =
                            safeIndex
                        backStackEntry.savedStateHandle[
                            KairoSavedStateKeys.RSVP_CURRENT_RESUME_CURSOR
                        ] =
                            resumePoint.resumeCursor
                        val wordIndex = resolveWordIndex(wordCountByToken, safeIndex)
                        saveRsvpPosition(
                            targetChapterIndex = chapterIndex,
                            targetTokenIndex = safeIndex,
                            targetWordIndex = wordIndex,
                            targetResumeCursor = resumePoint.resumeCursor,
                        )
                    },
                    onTempoChange = { tempoMsPerWord ->
                        val baseTempoMs =
                            RsvpConfigResolver.toBaseTempoMs(
                                tempoMsPerWord,
                                languageTagState.value,
                            )
                        coroutineScope.launch {
                            container.preferencesRepository.updateRsvpTempoMsPerWord(baseTempoMs)
                        }
                    },
                    onExit = { resumePoint ->
                        val resumeIndex =
                            if (tokens.isNotEmpty()) {
                                resumePoint.tokenIndex.coerceIn(0, tokens.lastIndex)
                            } else {
                                resumePoint.tokenIndex.coerceAtLeast(0)
                            }
                        val wordIndex = resolveWordIndex(wordCountByToken, resumeIndex)
                        saveRsvpPosition(
                            targetChapterIndex = chapterIndex,
                            targetTokenIndex = resumeIndex,
                            targetWordIndex = wordIndex,
                            targetResumeCursor = resumePoint.resumeCursor,
                        )
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX, chapterIndex)
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX, resumeIndex)
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR,
                                resumePoint.resumeCursor,
                            )
                        navController.popBackStack()
                    },
                    onOpenLibrary = { resumePoint ->
                        val resumeIndex =
                            if (tokens.isNotEmpty()) {
                                resumePoint.tokenIndex.coerceIn(0, tokens.lastIndex)
                            } else {
                                resumePoint.tokenIndex.coerceAtLeast(0)
                            }
                        val wordIndex = resolveWordIndex(wordCountByToken, resumeIndex)
                        saveRsvpPosition(
                            targetChapterIndex = chapterIndex,
                            targetTokenIndex = resumeIndex,
                            targetWordIndex = wordIndex,
                            targetResumeCursor = resumePoint.resumeCursor,
                        )
                        navController.navigate(KairoRoutes.LIBRARY) {
                            popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onPlaybackStateChanged = { isPlaying ->
                        backStackEntry.savedStateHandle[
                            KairoSavedStateKeys.RSVP_PLAYBACK_IS_PLAYING
                        ] =
                            isPlaying
                    },
                ),
            preferences =
                RsvpPreferenceCallbacks(
                    onExtremeSpeedUnlockedChange = { enabled ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateUnlockExtremeSpeed(enabled)
                        }
                    },
                    onSelectProfile = { profileId ->
                        coroutineScope.launch {
                            container.preferencesRepository.selectRsvpProfile(profileId)
                        }
                    },
                    onSaveCustomProfile = { name, config ->
                        coroutineScope.launch {
                            container.preferencesRepository.saveRsvpCustomProfile(name, config)
                        }
                    },
                    onDeleteCustomProfile = { profileId ->
                        coroutineScope.launch {
                            container.preferencesRepository.deleteRsvpCustomProfile(profileId)
                        }
                    },
                    onRsvpConfigChange = { updated ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateRsvpConfig { updated }
                        }
                    },
                ),
            ui =
                RsvpUiCallbacks(
                    onFocusModeEnabledChange = { enabled ->
                        coroutineScope.launch {
                            if (enabled) {
                                if (!prefs.focusModeEnabled) {
                                    container.preferencesRepository.updateFocusModeEnabled(true)
                                }
                                container.preferencesRepository.updateFocusApplyInRsvp(true)
                            } else {
                                container.preferencesRepository.updateFocusApplyInRsvp(false)
                            }
                        }
                    },
                    onRsvpFontSizeChange = { size ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateRsvpFontSize(size)
                        }
                    },
                    onRsvpTextBrightnessChange = { brightness ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateRsvpTextBrightness(brightness)
                        }
                    },
                    onRsvpFontWeightChange = { weight ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateRsvpFontWeight(weight)
                        }
                    },
                    onRsvpFontFamilyChange = { family ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateRsvpFontFamily(family)
                        }
                    },
                ),
            theme =
                RsvpThemeCallbacks(
                    onThemeChange = { theme ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateTheme(theme.name)
                        }
                    },
                    onVerticalBiasChange = { bias ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateRsvpVerticalBias(bias)
                        }
                    },
                    onHorizontalBiasChange = { bias ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateRsvpHorizontalBias(bias)
                        }
                    },
                ),
        )
    val rsvpDependencies =
        RsvpScreenDependencies(
            frameRepository = container.rsvpFrameRepository,
        )

    RsvpScreen(
        state = rsvpState,
        callbacks = rsvpCallbacks,
        dependencies = rsvpDependencies,
        tutorialState = tutorialState,
        onTutorialNext = onTutorialNext,
        onTutorialPrevious = onTutorialPrevious,
        onTutorialSkip = onTutorialSkip,
    )
}
