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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

data class SettingsHomeActions(
    val onOpenRsvp: () -> Unit,
    val onOpenBionic: () -> Unit,
    val onOpenReader: () -> Unit,
    val onOpenFocus: () -> Unit,
    val onOpenInfo: () -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onOpenLanguage: () -> Unit,
    val onOpenStartingTutorial: () -> Unit,
    val onReset: () -> Unit,
    val onClose: () -> Unit,
)

data class SettingsTutorialActions(val onNext: () -> Unit = {}, val onPrevious: () -> Unit = {}, val onSkip: () -> Unit = {},)

@Composable
fun SettingsHomeScreen(
    actions: SettingsHomeActions,
    tutorialState: StartingTutorialOverlayState? = null,
    tutorialActions: SettingsTutorialActions = SettingsTutorialActions(),
) {
    val context = LocalContext.current
    val languageLabel = resolveLanguageLabel(context, getAppLanguageTag())
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }
    var showResetConfirmation by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            PrimarySettingsRows(actions, languageLabel, tutorialTargets)
            SupportingSettingsRows(actions, tutorialTargets)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_reset_defaults))
            }
            Button(onClick = actions.onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_done))
            }
        }
        tutorialState?.let { overlayState ->
            StartingTutorialOverlay(
                state = overlayState,
                targetBounds = overlayState.step.targetId?.let(tutorialTargets::get),
                onNext = tutorialActions.onNext,
                onPrevious = tutorialActions.onPrevious,
                onSkip = tutorialActions.onSkip,
            )
        }
        if (showResetConfirmation) {
            ResetSettingsDialog(
                onConfirm = {
                    showResetConfirmation = false
                    actions.onReset()
                },
                onDismiss = { showResetConfirmation = false },
            )
        }
    }
}

@Composable
private fun PrimarySettingsRows(
    actions: SettingsHomeActions,
    languageLabel: String,
    tutorialTargets: MutableMap<String, Rect>,
) {
    SettingsNavRow(
        modifier = Modifier.captureTutorialTarget(StartingTutorialTargetIds.SETTINGS_LANGUAGE, tutorialTargets),
        title = stringResource(R.string.settings_language_title),
        subtitle = languageLabel,
        icon = Icons.Default.Language,
        onClick = actions.onOpenLanguage,
    )
    SettingsNavRow(
        modifier = Modifier.captureTutorialTarget(StartingTutorialTargetIds.SETTINGS_RSVP, tutorialTargets),
        title = stringResource(R.string.rsvp_settings_title),
        subtitle = stringResource(R.string.settings_rsvp_subtitle),
        icon = Icons.Default.Settings,
        onClick = actions.onOpenRsvp,
    )
    SettingsNavRow(
        title = stringResource(R.string.bionic_settings_title),
        subtitle = stringResource(R.string.settings_bionic_subtitle),
        icon = Icons.Default.AutoStories,
        onClick = actions.onOpenBionic,
    )
    SettingsNavRow(
        modifier = Modifier.captureTutorialTarget(StartingTutorialTargetIds.SETTINGS_READER, tutorialTargets),
        title = stringResource(R.string.reader_settings_title),
        subtitle = stringResource(R.string.reader_settings_subtitle),
        icon = Icons.Default.Settings,
        onClick = actions.onOpenReader,
    )
}

@Composable
private fun SupportingSettingsRows(
    actions: SettingsHomeActions,
    tutorialTargets: MutableMap<String, Rect>,
) {
    SettingsNavRow(
        modifier = Modifier.captureTutorialTarget(StartingTutorialTargetIds.SETTINGS_FOCUS, tutorialTargets),
        title = stringResource(R.string.focus_settings_title),
        subtitle = stringResource(R.string.focus_settings_subtitle),
        icon = Icons.Default.Settings,
        onClick = actions.onOpenFocus,
    )
    SettingsNavRow(
        title = stringResource(R.string.update_check_title),
        subtitle = stringResource(R.string.update_check_subtitle),
        icon = Icons.Default.Refresh,
        onClick = actions.onCheckForUpdates,
    )
    SettingsNavRow(
        title = stringResource(R.string.info_settings_title),
        subtitle = stringResource(R.string.info_settings_subtitle),
        icon = Icons.Default.Info,
        onClick = actions.onOpenInfo,
    )
    SettingsNavRow(
        modifier = Modifier.captureTutorialTarget(StartingTutorialTargetIds.SETTINGS_TUTORIAL, tutorialTargets),
        title = stringResource(R.string.settings_starting_tutorial_title),
        subtitle = stringResource(R.string.settings_starting_tutorial_subtitle),
        icon = Icons.Default.Info,
        onClick = actions.onOpenStartingTutorial,
    )
}

private fun Modifier.captureTutorialTarget(
    targetId: String,
    targets: MutableMap<String, Rect>,
): Modifier =
    startingTutorialTarget(targetId) { resolvedId, bounds ->
        targets[resolvedId] = bounds
    }

@Composable
private fun ResetSettingsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
        text = { Text(stringResource(R.string.settings_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_reset_defaults))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
