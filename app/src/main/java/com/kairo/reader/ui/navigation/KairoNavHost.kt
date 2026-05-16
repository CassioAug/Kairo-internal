package com.kairo.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.rsvp.RsvpEffectivePace
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import com.kairo.reader.ui.importing.rememberImportCoordinator
import com.kairo.reader.ui.tutorial.rememberStartingTutorialCoordinator
import kotlinx.coroutines.withContext

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
            kairoNavGraph(
                KairoNavGraphDependencies(
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    selectedWpm = selectedWpm,
                    estimatedWpm = estimatedWpm,
                    importCoordinator = importCoordinator,
                    tutorialCoordinator = tutorialCoordinator,
                    settingsCoroutineScope = coroutineScope,
                    onShowUserMessage = { message -> messageController.show(message) },
                )
            )
        }
    }
}
