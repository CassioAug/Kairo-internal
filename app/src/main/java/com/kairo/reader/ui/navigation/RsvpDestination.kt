package com.kairo.reader.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

internal fun NavGraphBuilder.rsvpDestination(dependencies: KairoNavGraphDependencies) {
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
