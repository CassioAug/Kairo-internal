package com.kairo.reader.ui.updates

internal data class InAppUpdateUiBindings(
    val prompt: InAppUpdatePrompt?,
    val onAction: (InAppUpdateAction) -> Unit,
    val onDismiss: (InAppUpdatePrompt) -> Unit,
    val onCheckForUpdates: (onResult: (InAppUpdateCheckResult) -> Unit) -> Unit,
)
