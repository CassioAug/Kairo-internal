package com.kairo.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.importing.rememberImportCoordinator
import com.kairo.reader.ui.tutorial.rememberStartingTutorialCoordinator

@Suppress("FunctionNaming")
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

    val paceState =
        rememberKairoNavPaceState(
            config = prefs.rsvpConfig,
            dispatcherProvider = dispatcherProvider,
        )
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
            kairoNavGraph(
                KairoNavGraphDependencies(
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    pace = paceState,
                    importCoordinator = importCoordinator,
                    tutorialCoordinator = tutorialCoordinator,
                    onShowUserMessage = { message -> messageController.show(message) },
                )
            )
        }
    }
}
