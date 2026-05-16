package com.kairo.reader.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.importing.ImportCoordinator
import com.kairo.reader.ui.tutorial.StartingTutorialCoordinator

internal class KairoNavGraphDependencies(
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val pace: KairoNavPaceState,
    val importCoordinator: ImportCoordinator,
    val tutorialCoordinator: StartingTutorialCoordinator,
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
        tutorialState = dependencies.tutorialCoordinator.settingsState,
        onOpenStartingTutorial = dependencies.tutorialCoordinator.start,
        onTutorialNext = dependencies.tutorialCoordinator.next,
        onTutorialPrevious = dependencies.tutorialCoordinator.previous,
        onTutorialSkip = dependencies.tutorialCoordinator.skip,
    )
}
