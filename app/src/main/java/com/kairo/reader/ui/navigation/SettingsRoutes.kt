package com.kairo.reader.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.settings.FocusSettingsScreen
import com.kairo.reader.ui.settings.InfoSettingsScreen
import com.kairo.reader.ui.settings.LanguageSettingsScreen
import com.kairo.reader.ui.settings.ReaderSettingsScreen
import com.kairo.reader.ui.settings.RsvpSettingsScreen
import com.kairo.reader.ui.settings.SettingsHomeScreen
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun NavGraphBuilder.settingsRoutes(
    container: KairoApplication,
    navController: NavHostController,
    prefs: UserPreferences,
    coroutineScope: CoroutineScope,
    tutorialState: StartingTutorialOverlayState?,
    onOpenStartingTutorial: () -> Unit,
    onTutorialNext: () -> Unit,
    onTutorialPrevious: () -> Unit,
    onTutorialSkip: () -> Unit,
) {
    composable(KairoRoutes.SETTINGS) {
        SettingsHomeScreen(
            onOpenLanguage = {
                navController.navigate(KairoRoutes.SETTINGS_LANGUAGE)
            },
            onOpenRsvp = { navController.navigate(KairoRoutes.SETTINGS_RSVP) },
            onOpenReader = { navController.navigate(KairoRoutes.SETTINGS_READER) },
            onOpenFocus = { navController.navigate(KairoRoutes.SETTINGS_FOCUS) },
            onOpenInfo = { navController.navigate(KairoRoutes.SETTINGS_INFO) },
            onOpenStartingTutorial = onOpenStartingTutorial,
            onReset = {
                coroutineScope.launch {
                    container.preferencesRepository.reset()
                }
            },
            onClose = { navController.popBackStack() },
            tutorialState = tutorialState,
            onTutorialNext = onTutorialNext,
            onTutorialPrevious = onTutorialPrevious,
            onTutorialSkip = onTutorialSkip,
        )
    }

    composable(KairoRoutes.SETTINGS_LANGUAGE) {
        LanguageSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(KairoRoutes.SETTINGS_INFO) {
        InfoSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(KairoRoutes.SETTINGS_RSVP) {
        RsvpSettingsScreen(
            preferences = prefs,
            onSelectRsvpProfile = { profileId ->
                coroutineScope.launch {
                    container.preferencesRepository.selectRsvpProfile(profileId)
                }
            },
            onSaveRsvpProfile = { name, config ->
                coroutineScope.launch {
                    container.preferencesRepository.saveRsvpCustomProfile(name, config)
                }
            },
            onDeleteRsvpProfile = { profileId ->
                coroutineScope.launch {
                    container.preferencesRepository.deleteRsvpCustomProfile(profileId)
                }
            },
            onRsvpTempoMsPerWordChange = { tempoMsPerWord ->
                coroutineScope.launch {
                    container.preferencesRepository.updateRsvpTempoMsPerWord(tempoMsPerWord)
                }
            },
            onRsvpConfigChange = { config ->
                coroutineScope.launch {
                    container.preferencesRepository.updateRsvpConfig { config }
                }
            },
            onUnlockExtremeSpeedChange = { enabled ->
                coroutineScope.launch {
                    container.preferencesRepository.updateUnlockExtremeSpeed(enabled)
                }
            },
            onRsvpFontSizeChange = { size ->
                coroutineScope.launch {
                    container.preferencesRepository.updateRsvpFontSize(size)
                }
            },
            onRsvpTextBrightnessChange = { brightness ->
                coroutineScope.launch {
                    container.preferencesRepository.updateRsvpTextBrightness(brightness)
                }
            },
            onRsvpFontWeightChange = { weight ->
                coroutineScope.launch {
                    container.preferencesRepository.updateRsvpFontWeight(weight)
                }
            },
            onRsvpFontFamilyChange = { family ->
                coroutineScope.launch {
                    container.preferencesRepository.updateRsvpFontFamily(family)
                }
            },
            onRsvpVerticalBiasChange = { bias ->
                coroutineScope.launch {
                    container.preferencesRepository.updateRsvpVerticalBias(bias)
                }
            },
            onRsvpHorizontalBiasChange = { bias ->
                coroutineScope.launch {
                    container.preferencesRepository.updateRsvpHorizontalBias(bias)
                }
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable(KairoRoutes.SETTINGS_READER) {
        ReaderSettingsScreen(
            preferences = prefs,
            onFontSizeChange = { size ->
                coroutineScope.launch { container.preferencesRepository.updateFontSize(size) }
            },
            onThemeChange = { theme ->
                coroutineScope.launch {
                    container.preferencesRepository.updateTheme(theme.name)
                }
            },
            onTextBrightnessChange = { brightness ->
                coroutineScope.launch {
                    container.preferencesRepository.updateReaderTextBrightness(brightness)
                }
            },
            onInvertedScrollChange = { enabled ->
                coroutineScope.launch {
                    container.preferencesRepository.updateInvertedScroll(enabled)
                }
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable(KairoRoutes.SETTINGS_FOCUS) {
        FocusSettingsScreen(
            preferences = prefs,
            onFocusModeEnabledChange = { enabled ->
                coroutineScope.launch {
                    container.preferencesRepository.updateFocusModeEnabled(enabled)
                }
            },
            onFocusHideStatusBarChange = { enabled ->
                coroutineScope.launch {
                    container.preferencesRepository.updateFocusHideStatusBar(enabled)
                }
            },
            onFocusPauseNotificationsChange = { enabled ->
                coroutineScope.launch {
                    container.preferencesRepository.updateFocusPauseNotifications(enabled)
                }
            },
            onFocusApplyInReaderChange = { enabled ->
                coroutineScope.launch {
                    container.preferencesRepository.updateFocusApplyInReader(enabled)
                }
            },
            onFocusApplyInRsvpChange = { enabled ->
                coroutineScope.launch {
                    container.preferencesRepository.updateFocusApplyInRsvp(enabled)
                }
            },
            onBack = { navController.popBackStack() },
        )
    }
}
