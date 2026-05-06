@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.ui.tutorial.StartingTutorialOverlay
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget

@Composable
fun SettingsHomeScreen(
    onOpenRsvp: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenStartingTutorial: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    tutorialState: StartingTutorialOverlayState? = null,
    onTutorialNext: () -> Unit = {},
    onTutorialPrevious: () -> Unit = {},
    onTutorialSkip: () -> Unit = {},
) {
    val context = LocalContext.current
    val languageLabel = resolveLanguageLabel(context, getAppLanguageTag())
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

            SettingsNavRow(
                modifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.SETTINGS_LANGUAGE) {
                        targetId,
                        bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                title = stringResource(R.string.settings_language_title),
                subtitle = languageLabel,
                icon = Icons.Default.Language,
                onClick = onOpenLanguage,
            )

            SettingsNavRow(
                modifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.SETTINGS_RSVP) {
                        targetId,
                        bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                title = stringResource(R.string.rsvp_settings_title),
                subtitle = stringResource(R.string.settings_rsvp_subtitle),
                icon = Icons.Default.Settings,
                onClick = onOpenRsvp,
            )
            SettingsNavRow(
                modifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.SETTINGS_READER) {
                        targetId,
                        bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                title = stringResource(R.string.reader_settings_title),
                subtitle = stringResource(R.string.reader_settings_subtitle),
                icon = Icons.Default.Settings,
                onClick = onOpenReader,
            )
            SettingsNavRow(
                modifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.SETTINGS_FOCUS) {
                        targetId,
                        bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                title = stringResource(R.string.focus_settings_title),
                subtitle = stringResource(R.string.focus_settings_subtitle),
                icon = Icons.Default.Settings,
                onClick = onOpenFocus,
            )
            SettingsNavRow(
                modifier =
                    Modifier.startingTutorialTarget(StartingTutorialTargetIds.SETTINGS_TUTORIAL) {
                        targetId,
                        bounds,
                        ->
                        tutorialTargets[targetId] = bounds
                    },
                title = stringResource(R.string.settings_starting_tutorial_title),
                subtitle = stringResource(R.string.settings_starting_tutorial_subtitle),
                icon = Icons.Default.Info,
                onClick = onOpenStartingTutorial,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_reset_defaults))
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_done))
            }
        }
        tutorialState?.let { overlayState ->
            StartingTutorialOverlay(
                state = overlayState,
                targetBounds = overlayState.step.targetId?.let(tutorialTargets::get),
                onNext = onTutorialNext,
                onPrevious = onTutorialPrevious,
                onSkip = onTutorialSkip,
            )
        }
    }
}
