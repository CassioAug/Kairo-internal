@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.focus.FocusModeSideEffects
import com.kairo.reader.ui.focus.shouldApplyFocusMode
import com.kairo.reader.ui.theme.KairoSnackbarHost
import com.kairo.reader.ui.updates.InAppUpdatePromptEffect
import com.kairo.reader.ui.updates.InAppUpdateUiBindings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class KairoUserMessageController(val hostState: SnackbarHostState, private val coroutineScope: CoroutineScope,) {
    fun show(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        coroutineScope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(
                message = message,
                duration = duration,
            )
        }
    }

    suspend fun showAction(
        message: String,
        actionLabel: String,
        duration: SnackbarDuration,
    ): SnackbarResult {
        hostState.currentSnackbarData?.dismiss()
        return hostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            withDismissAction = true,
            duration = duration,
        )
    }
}

@Composable
internal fun rememberKairoUserMessageController(): KairoUserMessageController {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    return remember(snackbarHostState, coroutineScope) {
        KairoUserMessageController(
            hostState = snackbarHostState,
            coroutineScope = coroutineScope,
        )
    }
}

@Composable
internal fun KairoNavChrome(
    prefs: UserPreferences,
    currentRoute: String?,
    messageController: KairoUserMessageController,
    inAppUpdateUi: InAppUpdateUiBindings,
    content: @Composable () -> Unit,
) {
    FocusModeSideEffects(
        enabled = shouldApplyFocusMode(currentRoute, prefs),
        hideStatusBar = prefs.focusHideStatusBar,
        pauseNotifications = prefs.focusPauseNotifications,
    )
    InAppUpdatePromptEffect(
        bindings = inAppUpdateUi,
        messageController = messageController,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        KairoSnackbarHost(
            hostState = messageController.hostState,
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                ),
        )
    }
}
