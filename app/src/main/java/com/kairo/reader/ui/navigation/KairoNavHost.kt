package com.kairo.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.rsvp.RsvpEffectivePace
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import com.kairo.reader.ui.importing.rememberImportCoordinator
import com.kairo.reader.ui.tutorial.rememberStartingTutorialCoordinator
import kotlinx.coroutines.withContext

@Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")
@Composable
internal fun KairoNavHost(
    container: KairoApplication,
    prefs: UserPreferences,
    externalImportUri: Uri?,
    externalArticleUrl: String?,
    onExternalImportUriConsumed: (Uri) -> Unit,
    onExternalArticleUrlConsumed: (String) -> Unit,
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val dispatcherProvider = container.dispatcherProvider
    val messageController = rememberKairoUserMessageController()

    val importCoordinator =
        rememberImportCoordinator(
            container = container,
            navController = navController,
            externalImportUri = externalImportUri,
            externalArticleUrl = externalArticleUrl,
            onExternalImportUriConsumed = onExternalImportUriConsumed,
            onExternalArticleUrlConsumed = onExternalArticleUrlConsumed,
            onShowUserMessage = { message, duration ->
                messageController.show(message, duration)
            },
        )

    val selectedWpm by produceState(initialValue = 0, prefs.rsvpConfig) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEffectivePace.estimateWpm(prefs.rsvpConfig)
            }
    }
    val estimatedWpm by produceState(initialValue = selectedWpm, prefs.rsvpConfig, selectedWpm) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEstimatedReadingPace.estimateWpm(
                    config = prefs.rsvpConfig,
                    fallbackEstimatedWpm = selectedWpm,
                )
            }
    }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val tutorialCoordinator =
        rememberStartingTutorialCoordinator(
            container = container,
            navController = navController,
            prefs = prefs,
            currentRoute = currentRoute,
            externalImportUri = externalImportUri,
            externalArticleUrl = externalArticleUrl,
            isImporting = importCoordinator.state.isImporting,
        )
    KairoNavChrome(
        prefs = prefs,
        currentRoute = currentRoute,
        messageController = messageController,
    ) {
        NavHost(navController = navController, startDestination = KairoRoutes.LIBRARY) {
            composable(KairoRoutes.LIBRARY) {
                LibraryRoute(
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    selectedWpm = selectedWpm,
                    importState = importCoordinator.state,
                    onImportFile = importCoordinator.importFile,
                    onImportUrl = importCoordinator.importUrl,
                    tutorialState = tutorialCoordinator.libraryState,
                    onTutorialNext = tutorialCoordinator.next,
                    onTutorialPrevious = tutorialCoordinator.previous,
                    onTutorialSkip = tutorialCoordinator.skip,
                )
            }

            composable(
                route = KairoRoutes.LIBRARY_WITH_TAB,
                arguments =
                    listOf(
                        navArgument(KairoRoutes.ARG_LIBRARY_TAB) {
                            type = NavType.StringType
                            defaultValue = KairoRoutes.TAB_LIBRARY
                        },
                    ),
            ) { backStackEntry ->
                val tab =
                    backStackEntry.arguments?.getString(KairoRoutes.ARG_LIBRARY_TAB)
                        ?: KairoRoutes.TAB_LIBRARY
                LibraryRoute(
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    selectedWpm = selectedWpm,
                    importState = importCoordinator.state,
                    initialTabRouteValue = tab,
                    onImportFile = importCoordinator.importFile,
                    onImportUrl = importCoordinator.importUrl,
                    tutorialState = tutorialCoordinator.libraryState,
                    onTutorialNext = tutorialCoordinator.next,
                    onTutorialPrevious = tutorialCoordinator.previous,
                    onTutorialSkip = tutorialCoordinator.skip,
                )
            }

            composable(
                route = KairoRoutes.READER,
                arguments =
                    listOf(
                        navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
                    ),
            ) { backStackEntry ->
                ReaderRoute(
                    backStackEntry = backStackEntry,
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    estimatedWpm = estimatedWpm,
                    tutorialActive = tutorialCoordinator.active,
                    tutorialState = tutorialCoordinator.readerState,
                    onShowUserMessage = { message -> messageController.show(message) },
                    onTutorialNext = tutorialCoordinator.next,
                    onTutorialPrevious = tutorialCoordinator.previous,
                    onTutorialSkip = tutorialCoordinator.skip,
                )
            }

            composable(
                route = KairoRoutes.READER_WITH_POSITION,
                arguments =
                    listOf(
                        navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
                        navArgument(KairoRoutes.ARG_CHAPTER_INDEX) { type = NavType.IntType },
                        navArgument(KairoRoutes.ARG_TOKEN_INDEX) { type = NavType.IntType },
                    ),
            ) { backStackEntry ->
                ReaderRoute(
                    backStackEntry = backStackEntry,
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    estimatedWpm = estimatedWpm,
                    tutorialActive = tutorialCoordinator.active,
                    tutorialState = tutorialCoordinator.readerState,
                    initialChapterIndex =
                        backStackEntry.arguments?.getInt(KairoRoutes.ARG_CHAPTER_INDEX) ?: 0,
                    initialTokenIndex =
                        backStackEntry.arguments?.getInt(KairoRoutes.ARG_TOKEN_INDEX) ?: 0,
                    onShowUserMessage = { message -> messageController.show(message) },
                    onTutorialNext = tutorialCoordinator.next,
                    onTutorialPrevious = tutorialCoordinator.previous,
                    onTutorialSkip = tutorialCoordinator.skip,
                )
            }

            composable(
                route = KairoRoutes.RSVP,
                arguments =
                    listOf(
                        navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
                        navArgument(KairoRoutes.ARG_CHAPTER_INDEX) { type = NavType.IntType },
                        navArgument(KairoRoutes.ARG_TOKEN_INDEX) { type = NavType.IntType },
                        navArgument(KairoRoutes.ARG_TEMPO_MS) {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
            ) { backStackEntry ->
                RsvpRoute(
                    backStackEntry = backStackEntry,
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    tutorialState = tutorialCoordinator.rsvpState,
                    onShowUserMessage = { message -> messageController.show(message) },
                    onTutorialNext = tutorialCoordinator.next,
                    onTutorialPrevious = tutorialCoordinator.previous,
                    onTutorialSkip = tutorialCoordinator.skip,
                )
            }

            settingsRoutes(
                container = container,
                navController = navController,
                prefs = prefs,
                coroutineScope = coroutineScope,
                tutorialState = tutorialCoordinator.settingsState,
                onOpenStartingTutorial = tutorialCoordinator.start,
                onTutorialNext = tutorialCoordinator.next,
                onTutorialPrevious = tutorialCoordinator.previous,
                onTutorialSkip = tutorialCoordinator.skip,
            )
        }
    }
}
