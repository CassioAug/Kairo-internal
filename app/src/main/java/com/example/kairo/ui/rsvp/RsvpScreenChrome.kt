@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kairo.R
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.RsvpProgressBar(context: RsvpUiContext) {
    val runtime = context.runtime
    if (runtime.showControls || runtime.showQuickSettings) return

    val frames = context.frameState.frames
    val progress = (runtime.frameIndex + 1).toFloat() / frames.size.toFloat()
    val progressAlpha = if (runtime.isPlaying) 0.52f else 0.78f

    LinearProgressIndicator(
        progress = { progress },
        modifier =
        Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
                horizontal = PROGRESS_HORIZONTAL_PADDING,
                vertical = PROGRESS_BOTTOM_PADDING,
            )
            .clip(RoundedCornerShape(PROGRESS_CORNER_RADIUS))
            .height(PROGRESS_HEIGHT),
        color = MaterialTheme.colorScheme.primary.copy(alpha = progressAlpha),
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PROGRESS_TRACK_ALPHA),
    )
}

@Composable
internal fun BoxScope.RsvpTopBar(
    context: RsvpUiContext,
    settingsModifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
) {
    val runtime = context.runtime
    val showSettings = runtime.isPositioningMode || !runtime.isPlaying

    Row(
        modifier =
        Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(TOP_BAR_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TOP_BAR_SPACING),
    ) {
        RsvpTopIconButton(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.content_desc_close),
            onClick = { exitAndSavePosition(context) },
            modifier = closeModifier,
        )
    }

    Row(
        modifier =
        Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(TOP_BAR_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TOP_BAR_SPACING),
    ) {
        if (showSettings) {
            RsvpTopIconButton(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.content_desc_settings),
                onClick = {
                    if (runtime.isPositioningMode) {
                        finishPositioning(context, resumeIfWasPlaying = true)
                    } else {
                        runtime.showQuickSettings = !runtime.showQuickSettings
                        if (runtime.showQuickSettings) runtime.showControls = false
                    }
                },
                modifier = settingsModifier,
            )
        }
    }
}

@Composable
private fun RsvpTopIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(TOP_BAR_BUTTON_SIZE)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = TOP_BAR_BUTTON_BACKGROUND_ALPHA),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = TOP_BAR_BUTTON_BORDER_ALPHA),
                    shape = CircleShape,
                ),
    ) {
        Icon(
            imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = TOP_BAR_ICON_ALPHA),
            modifier = Modifier.size(TOP_BAR_ICON_SIZE),
        )
    }
}

@Composable
internal fun BoxScope.RsvpTempoIndicator(
    context: RsvpUiContext,
    indicatorText: String,
) {
    val runtime = context.runtime
    val configuration = LocalConfiguration.current
    val compactLandscape =
        configuration.screenWidthDp > configuration.screenHeightDp &&
            configuration.screenHeightDp <= 480

    AnimatedVisibility(
        visible = runtime.showTempoIndicator && !compactLandscape,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier =
            Modifier
                .padding(top = TEMPO_INDICATOR_TOP_PADDING)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA,
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS),
                ).padding(
                    horizontal = INDICATOR_PADDING_HORIZONTAL,
                    vertical = INDICATOR_PADDING_VERTICAL,
                ),
        ) {
            Text(
                text = indicatorText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun BoxScope.RsvpFontSizeIndicator(context: RsvpUiContext) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showFontSizeIndicator,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier =
            Modifier
                .padding(top = FONT_SIZE_INDICATOR_TOP_PADDING)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA,
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS),
                ).padding(
                    horizontal = INDICATOR_PADDING_HORIZONTAL,
                    vertical = INDICATOR_PADDING_VERTICAL,
                ),
        ) {
            Text(
                text =
                stringResource(
                    R.string.format_sp,
                    runtime.currentFontSizeSp.toInt(),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun BoxScope.RsvpPositioningIndicator(context: RsvpUiContext) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.isPositioningMode,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier =
            Modifier
                .padding(top = TEMPO_INDICATOR_TOP_PADDING)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA,
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS),
                ).clickable { finishPositioning(context, resumeIfWasPlaying = true) }
                .padding(
                    horizontal = POSITIONING_INDICATOR_PADDING_HORIZONTAL,
                    vertical = POSITIONING_INDICATOR_PADDING_VERTICAL,
                ),
        ) {
            Text(
                text = stringResource(R.string.rsvp_positioning_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun BoxScope.RsvpScrubTargetIndicator(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val configuration = LocalConfiguration.current
    val compactLandscape =
        configuration.screenWidthDp > configuration.screenHeightDp &&
            configuration.screenHeightDp <= 480
    if (compactLandscape) return

    val frameCount = frames.size.coerceAtLeast(1)
    val progressPercent =
        (((runtime.frameIndex + 1).toFloat() / frameCount.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(0, 100)

    AnimatedVisibility(
        visible = runtime.isScrubbing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(top = 52.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = INDICATOR_BACKGROUND_ALPHA),
                        RoundedCornerShape(INDICATOR_CORNER_RADIUS),
                    ).padding(
                        horizontal = INDICATOR_PADDING_HORIZONTAL,
                        vertical = INDICATOR_PADDING_VERTICAL,
                    ),
        ) {
            Text(
                text = "${runtime.frameIndex + 1}/$frameCount • $progressPercent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
