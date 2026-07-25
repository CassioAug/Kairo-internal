package com.kairo.reader.ui.updates

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.kairo.reader.R
import com.kairo.reader.ui.navigation.KairoUserMessageController

@Composable
internal fun InAppUpdatePromptEffect(
    bindings: InAppUpdateUiBindings,
    messageController: KairoUserMessageController,
) {
    val prompt = bindings.prompt
    if (prompt == null) {
        return
    }

    val message =
        stringResource(
            when (prompt) {
                InAppUpdatePrompt.UPDATE_AVAILABLE -> R.string.update_available_message
                InAppUpdatePrompt.READY_TO_RESTART -> R.string.update_ready_message
            }
        )
    val actionLabel =
        stringResource(
            when (prompt) {
                InAppUpdatePrompt.UPDATE_AVAILABLE -> R.string.update_available_action
                InAppUpdatePrompt.READY_TO_RESTART -> R.string.update_ready_action
            }
        )
    val duration =
        when (prompt) {
            InAppUpdatePrompt.UPDATE_AVAILABLE -> SnackbarDuration.Long
            InAppUpdatePrompt.READY_TO_RESTART -> SnackbarDuration.Indefinite
        }

    LaunchedEffect(prompt, message, actionLabel, duration) {
        val result =
            messageController.showAction(
                message = message,
                actionLabel = actionLabel,
                duration = duration,
            )
        if (result == SnackbarResult.ActionPerformed) {
            bindings.onAction(
                when (prompt) {
                    InAppUpdatePrompt.UPDATE_AVAILABLE -> InAppUpdateAction.START_UPDATE
                    InAppUpdatePrompt.READY_TO_RESTART -> InAppUpdateAction.RESTART_TO_INSTALL
                }
            )
        } else {
            bindings.onDismiss(prompt)
        }
    }
}
