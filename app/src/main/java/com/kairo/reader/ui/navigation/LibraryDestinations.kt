package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

internal fun NavGraphBuilder.libraryDestinations(dependencies: KairoNavGraphDependencies) {
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
        selectedWpm = dependencies.pace.selectedWpm,
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
