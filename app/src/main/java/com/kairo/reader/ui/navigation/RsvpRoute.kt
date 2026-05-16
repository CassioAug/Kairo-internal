package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.buildWordCountByToken
import com.kairo.reader.core.rsvp.RsvpConfigResolver
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
) {
    val bookId = backStackEntry.arguments?.getString(KairoRoutes.ARG_BOOK_ID) ?: return
    val chapterIndex = backStackEntry.arguments?.getInt(KairoRoutes.ARG_CHAPTER_INDEX) ?: 0
    val startIndex = backStackEntry.arguments?.getInt(KairoRoutes.ARG_TOKEN_INDEX) ?: 0
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
        tutorialState = tutorialState,
        onTutorialNext = onTutorialNext,
        onTutorialPrevious = onTutorialPrevious,
        onTutorialSkip = onTutorialSkip,
    )
}
