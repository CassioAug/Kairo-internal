package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.ReadingSessionMode
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.buildWordCountByToken
import com.kairo.reader.core.model.countWordsThroughToken
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.data.sessions.ReadingSessionFactory
import com.kairo.reader.data.sessions.ReadingSessionDraft
import com.kairo.reader.data.sessions.ReadingSessionLocation
import com.kairo.reader.data.sessions.ReadingSessionTracker
import com.kairo.reader.ui.rsvp.ReadingPresentationMode
import com.kairo.reader.ui.rsvp.RsvpBookContext
import com.kairo.reader.ui.rsvp.RsvpLayoutBias
import com.kairo.reader.ui.rsvp.RsvpProfileContext
import com.kairo.reader.ui.rsvp.RsvpScreen
import com.kairo.reader.ui.rsvp.RsvpScreenDependencies
import com.kairo.reader.ui.rsvp.RsvpScreenState
import com.kairo.reader.ui.rsvp.RsvpTextStyle
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
    presentationMode: ReadingPresentationMode = ReadingPresentationMode.RSVP,
) {
    val bookId = backStackEntry.arguments?.getString(KairoRoutes.ARG_BOOK_ID) ?: return
    val chapterIndex = backStackEntry.arguments?.getInt(KairoRoutes.ARG_CHAPTER_INDEX) ?: 0
    val startIndex = backStackEntry.arguments?.getInt(KairoRoutes.ARG_TOKEN_INDEX) ?: 0
    val launchTempoMsPerWord =
        backStackEntry.arguments
            ?.getLong(KairoRoutes.ARG_TEMPO_MS)
            ?.takeIf { it > 0L }
    val coroutineScope = rememberCoroutineScope()
    val dispatcherProvider = container.dispatcherProvider
    val resources = LocalResources.current

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
    val routeData =
        rememberRsvpRouteData(
            container = container,
            bookId = bookId,
            chapterIndex = chapterIndex,
            safeStartIndex = safeStartIndex,
        )
    val tokens = routeData.tokens
    val wordCountByToken = remember(tokens) { buildWordCountByToken(tokens) }
    val startResumeCursor =
        savedCurrentResumeCursor
            .takeIf { savedCurrentTokenIndex >= 0 && it >= 0 }
            ?: routeData.savedResumePosition
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
    val sessionStartedAt = remember(bookId, chapterIndex, startIndex) { System.currentTimeMillis() }
    val sessionTracker =
        remember(bookId, chapterIndex, startIndex) {
            ReadingSessionTracker(
                sessionStartedAt,
                initiallyActive =
                playbackIsPlaying &&
                    rsvpLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            )
        }
    var sessionRecorded by remember(bookId, chapterIndex, startIndex) { androidx.compose.runtime.mutableStateOf(false) }
    TrackTimedReadingSessionLifecycle(sessionTracker, rsvpLifecycleOwner, playbackIsPlaying)
    val resolvedRsvpConfig =
        RsvpConfigResolver.resolve(prefs.rsvpConfig, routeData.languageTag)
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
    fun recordSession(endTokenIndex: Int) {
        if (sessionRecorded || tokens.isEmpty()) return
        val endedAt = System.currentTimeMillis()
        sessionTracker.setActive(false, endedAt)
        val safeStart = safeStartIndex.coerceIn(0, tokens.lastIndex)
        val safeEnd = endTokenIndex.coerceIn(0, tokens.lastIndex)
        val wordsRead =
            kotlin.math.abs(
                countWordsThroughToken(tokens, safeEnd) -
                    countWordsThroughToken(tokens, safeStart),
            )
        val session =
            ReadingSessionFactory.create(
                ReadingSessionDraft(
                    bookId = bookIdValue,
                    mode =
                    when (presentationMode) {
                        ReadingPresentationMode.RSVP -> ReadingSessionMode.RSVP
                        ReadingPresentationMode.BIONIC -> ReadingSessionMode.BIONIC
                    },
                    startedAt = sessionStartedAt,
                    endedAt = endedAt,
                    activeDurationMs = sessionTracker.activeDurationMs(endedAt),
                    start = ReadingSessionLocation(chapterIndex, safeStart),
                    end = ReadingSessionLocation(chapterIndex, safeEnd),
                    wordsRead = wordsRead,
                    isWordCountEstimated = false,
                ),
            )
        sessionRecorded = true
        if (session != null) container.recordReadingSession(session)
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
            launchTempoMsPerWord = launchTempoMsPerWord,
            initialIsPlaying = playbackIsPlaying,
            uiPrefs =
            RsvpUiPreferences(
                extremeSpeedUnlocked = prefs.unlockExtremeSpeed,
                readerTheme = prefs.readerTheme,
                focusModeEnabled = focusEnabledInRsvp,
                positioningGridEnabled = prefs.rsvpPositioningGridEnabled,
                positioningGridSnap = prefs.rsvpPositioningGridSnap,
            ),
            textStyle =
            if (presentationMode == ReadingPresentationMode.BIONIC) {
                RsvpTextStyle(
                    fontSizeSp = prefs.bionicReading.fontSizeSp,
                    fontFamily = prefs.rsvpFontFamily,
                    fontWeight = RsvpFontWeight.NORMAL,
                    textBrightness = prefs.bionicReading.textBrightness,
                )
            } else {
                RsvpTextStyle(
                    fontSizeSp = prefs.rsvpFontSizeSp,
                    fontFamily = prefs.rsvpFontFamily,
                    fontWeight = prefs.rsvpFontWeight,
                    textBrightness = prefs.rsvpTextBrightness,
                )
            },
            layoutBias =
            if (presentationMode == ReadingPresentationMode.BIONIC) {
                RsvpLayoutBias(verticalBias = 0f, horizontalBias = 0f)
            } else {
                RsvpLayoutBias(
                    verticalBias = prefs.rsvpVerticalBias,
                    horizontalBias = prefs.rsvpHorizontalBias,
                )
            },
        )
    val rsvpCallbacks =
        buildRsvpRouteCallbacks(
            RsvpRouteCallbackDependencies(
                container = container,
                navController = navController,
                backStackEntry = backStackEntry,
                prefs = prefs,
                bookId = bookId,
                bookIdValue = bookIdValue,
                chapterIndex = chapterIndex,
                chapterCount = routeData.chapterCount,
                tokens = tokens,
                wordCountByToken = wordCountByToken,
                languageTag = routeData.languageTag,
                coroutineScope = coroutineScope,
                resources = resources,
                onShowUserMessage = onShowUserMessage,
                onSessionFinished = ::recordSession,
                onSessionActiveChanged = { active ->
                    sessionTracker.setActive(
                        active &&
                            rsvpLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                        System.currentTimeMillis(),
                    )
                },
                saveRsvpPosition = ::saveRsvpPosition,
            )
        )
    val rsvpDependencies =
        RsvpScreenDependencies(
            frameRepository = container.rsvpFrameRepository,
        )

    RsvpScreen(
        state = rsvpState,
        callbacks = rsvpCallbacks,
        dependencies = rsvpDependencies,
        presentationMode = presentationMode,
        bionicPreferences = prefs.bionicReading,
        tutorialState = tutorialState,
        onTutorialNext = onTutorialNext,
        onTutorialPrevious = onTutorialPrevious,
        onTutorialSkip = onTutorialSkip,
    )
}
