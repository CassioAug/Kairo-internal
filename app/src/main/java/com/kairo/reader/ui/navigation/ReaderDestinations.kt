package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

internal fun NavGraphBuilder.readerDestinations(dependencies: KairoNavGraphDependencies) {
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
        estimatedWpm = dependencies.pace.estimatedWpm,
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
