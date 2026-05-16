package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.importing.ImportCoordinator
import com.kairo.reader.ui.tutorial.StartingTutorialCoordinator
import kotlinx.coroutines.CoroutineScope

internal class KairoNavGraphDependencies(
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val selectedWpm: Int,
    val estimatedWpm: Int,
    val importCoordinator: ImportCoordinator,
    val tutorialCoordinator: StartingTutorialCoordinator,
    val settingsCoroutineScope: CoroutineScope,
    val onShowUserMessage: (String) -> Unit,
)

internal fun NavGraphBuilder.kairoNavGraph(dependencies: KairoNavGraphDependencies) {
    libraryDestinations(dependencies)
    readerDestinations(dependencies)
    rsvpDestination(dependencies)
    settingsRoutes(
        container = dependencies.container,
        navController = dependencies.navController,
        prefs = dependencies.prefs,
        coroutineScope = dependencies.settingsCoroutineScope,
        tutorialState = dependencies.tutorialCoordinator.settingsState,
        onOpenStartingTutorial = dependencies.tutorialCoordinator.start,
        onTutorialNext = dependencies.tutorialCoordinator.next,
        onTutorialPrevious = dependencies.tutorialCoordinator.previous,
        onTutorialSkip = dependencies.tutorialCoordinator.skip,
    )
}

private fun NavGraphBuilder.libraryDestinations(dependencies: KairoNavGraphDependencies) {
    composable(KairoRoutes.LIBRARY) {
        KairoLibraryDestination(dependencies)
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
        KairoLibraryDestination(
            dependencies = dependencies,
            initialTabRouteValue =
                backStackEntry.arguments?.getString(KairoRoutes.ARG_LIBRARY_TAB)
                    ?: KairoRoutes.TAB_LIBRARY,
        )
    }
}

@Composable
private fun KairoLibraryDestination(
    dependencies: KairoNavGraphDependencies,
    initialTabRouteValue: String? = null,
) {
    LibraryRoute(
        container = dependencies.container,
        navController = dependencies.navController,
        prefs = dependencies.prefs,
        selectedWpm = dependencies.selectedWpm,
        importState = dependencies.importCoordinator.state,
        initialTabRouteValue = initialTabRouteValue,
        onImportFile = dependencies.importCoordinator.importFile,
        onImportUrl = dependencies.importCoordinator.importUrl,
        tutorialState = dependencies.tutorialCoordinator.libraryState,
        onTutorialNext = dependencies.tutorialCoordinator.next,
        onTutorialPrevious = dependencies.tutorialCoordinator.previous,
        onTutorialSkip = dependencies.tutorialCoordinator.skip,
    )
}

private fun NavGraphBuilder.readerDestinations(dependencies: KairoNavGraphDependencies) {
    composable(
        route = KairoRoutes.READER,
        arguments =
            listOf(
                navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
            ),
    ) { backStackEntry ->
        KairoReaderDestination(
            dependencies = dependencies,
            backStackEntry = backStackEntry,
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
        KairoReaderDestination(
            dependencies = dependencies,
            backStackEntry = backStackEntry,
            initialChapterIndex =
                backStackEntry.arguments?.getInt(KairoRoutes.ARG_CHAPTER_INDEX) ?: 0,
            initialTokenIndex =
                backStackEntry.arguments?.getInt(KairoRoutes.ARG_TOKEN_INDEX) ?: 0,
        )
    }
}

@Composable
private fun KairoReaderDestination(
    dependencies: KairoNavGraphDependencies,
    backStackEntry: NavBackStackEntry,
    initialChapterIndex: Int? = null,
    initialTokenIndex: Int? = null,
) {
    ReaderRoute(
        backStackEntry = backStackEntry,
        container = dependencies.container,
        navController = dependencies.navController,
        prefs = dependencies.prefs,
        estimatedWpm = dependencies.estimatedWpm,
        tutorialActive = dependencies.tutorialCoordinator.active,
        tutorialState = dependencies.tutorialCoordinator.readerState,
        initialChapterIndex = initialChapterIndex,
        initialTokenIndex = initialTokenIndex,
        onShowUserMessage = dependencies.onShowUserMessage,
        onTutorialNext = dependencies.tutorialCoordinator.next,
        onTutorialPrevious = dependencies.tutorialCoordinator.previous,
        onTutorialSkip = dependencies.tutorialCoordinator.skip,
    )
}

private fun NavGraphBuilder.rsvpDestination(dependencies: KairoNavGraphDependencies) {
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
            container = dependencies.container,
            navController = dependencies.navController,
            prefs = dependencies.prefs,
            tutorialState = dependencies.tutorialCoordinator.rsvpState,
            onShowUserMessage = dependencies.onShowUserMessage,
            onTutorialNext = dependencies.tutorialCoordinator.next,
            onTutorialPrevious = dependencies.tutorialCoordinator.previous,
            onTutorialSkip = dependencies.tutorialCoordinator.skip,
        )
    }
}
