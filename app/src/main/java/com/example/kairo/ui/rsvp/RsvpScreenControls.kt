@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kairo.R

@Composable
internal fun BoxScope.RsvpBottomControls(
    context: RsvpUiContext,
    speedIndicatorText: String,
    controlsModifier: Modifier = Modifier,
) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(CONTROLS_OUTER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = CONTROLS_MAX_WIDTH)
                        .fillMaxWidth(CONTROLS_WIDTH_FRACTION)
                        .clip(RoundedCornerShape(CONTROLS_CORNER_RADIUS))
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = CONTROLS_BACKGROUND_ALPHA),
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = CONTROLS_BORDER_ALPHA),
                            shape = RoundedCornerShape(CONTROLS_CORNER_RADIUS),
                        )
                        .padding(CONTROLS_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RsvpControlsProgress(context)
                Spacer(modifier = Modifier.height(CONTROLS_SPACER))
                RsvpPlaybackControlsRow(
                    context = context,
                    modifier = controlsModifier,
                )
                Spacer(modifier = Modifier.height(CONTROLS_SPACER))
                RsvpPlaybackInfoPills(
                    progressText =
                        stringResource(
                            R.string.rsvp_frame_progress,
                            runtime.frameIndex + 1,
                            context.frameState.frames.size,
                        ),
                    speedText = speedIndicatorText,
                )
                Spacer(modifier = Modifier.height(CONTROLS_HINT_SPACER))
                Text(
                    stringResource(R.string.rsvp_tap_to_resume),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = RESUME_TEXT_ALPHA),
                )
            }
        }
    }
}

@Composable
private fun RsvpPlaybackInfoPills(
    progressText: String,
    speedText: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CONTROLS_INFO_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RsvpPlaybackInfoPill(
            label = stringResource(R.string.rsvp_playback_position_label),
            value = progressText,
            modifier = Modifier.weight(1f),
        )
        RsvpPlaybackInfoPill(
            label = stringResource(R.string.rsvp_playback_speed_label),
            value = speedText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RsvpPlaybackInfoPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(CONTROLS_INFO_PILL_CORNER_RADIUS))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CONTROLS_PILL_BACKGROUND_ALPHA),
                )
                .padding(
                    horizontal = CONTROLS_INFO_PILL_PADDING_HORIZONTAL,
                    vertical = CONTROLS_INFO_PILL_PADDING_VERTICAL,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CONTROLS_PILL_LABEL_ALPHA),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CONTROLS_PILL_VALUE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RsvpControlsProgress(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val progress = (runtime.frameIndex + 1).toFloat() / frames.size.coerceAtLeast(1).toFloat()

    LinearProgressIndicator(
        progress = { progress },
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CONTROLS_PROGRESS_CORNER_RADIUS))
                .height(CONTROLS_PROGRESS_HEIGHT),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PROGRESS_TRACK_ALPHA),
    )
}

@Composable
private fun RsvpPlaybackControlsRow(
    context: RsvpUiContext,
    modifier: Modifier = Modifier,
) {
    val runtime = context.runtime

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CONTROLS_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            runtime.frameIndex = (runtime.frameIndex - 1).coerceAtLeast(0)
            runtime.completed = false
            context.haptics.onFrameStep()
        }) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.content_desc_previous),
                modifier = Modifier.size(SKIP_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        IconButton(
            onClick = {
                if (runtime.isPlaying) {
                    runtime.isPlaying = false
                } else if (!runtime.completed) {
                    resumePlayback(runtime)
                    runtime.showControls = false
                }
                context.haptics.onFrameStep()
            },
            modifier =
            Modifier
                .size(PLAY_BUTTON_SIZE)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                if (runtime.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription =
                stringResource(
                    if (runtime.isPlaying) {
                        R.string.content_desc_pause
                    } else {
                        R.string.content_desc_play
                    },
                ),
                modifier = Modifier.size(PLAY_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }

        IconButton(onClick = {
            advanceFrame(context)
            context.haptics.onFrameStep()
        }) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.content_desc_next),
                modifier = Modifier.size(SKIP_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
