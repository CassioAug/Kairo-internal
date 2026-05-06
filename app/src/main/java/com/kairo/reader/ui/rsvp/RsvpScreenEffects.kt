@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.rsvp.frameFloorMs
import com.kairo.reader.core.rsvp.shouldSkipBlinkFrame
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

@Composable
internal fun RsvpPositionSaveEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book

    LaunchedEffect(runtime.frameIndex, context.frameState.isLoading) {
        if (!shouldSyncPositionFromFrameState(context.frameState)) return@LaunchedEffect
        val currentIndex = resolveCurrentTokenIndex(frames, runtime.frameIndex, book.startIndex)
        val currentResumeCursor =
            resolveCurrentResumeCursor(
                frames = frames,
                frameIndex = runtime.frameIndex,
                fallbackCursor = book.startResumeCursor.takeIf { it >= 0 } ?: -1,
            )
        runtime.currentTokenIndex = currentIndex
        runtime.currentResumeCursor = currentResumeCursor
        val now = SystemClock.elapsedRealtime()
        val shouldSave =
            now - runtime.lastPositionSaveMs >= POSITION_SAVE_INTERVAL_MS ||
                runtime.frameIndex == frames.lastIndex
        if (shouldSave) {
            runtime.lastPositionSaveMs = now
            context.callbacks.playback.onPositionChanged(currentResumePoint(context))
        }
    }
}

@Composable
internal fun RsvpFrameAlignmentEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames

    LaunchedEffect(frames, context.frameState.isLoading) {
        if (!shouldSyncPositionFromFrameState(context.frameState)) return@LaunchedEffect
        runtime.frameIndex = alignFrameIndex(frames, runtime.currentTokenIndex, runtime.currentResumeCursor)
    }
}

internal fun shouldSyncPositionFromFrameState(frameState: RsvpFrameLoadState): Boolean =
    frameState.frames.isNotEmpty() && !frameState.isLoading

@Composable
internal fun RsvpSessionResetEffect(
    context: RsvpUiContext,
    sessionKey: String,
    autoPlay: Boolean,
) {
    val runtime = context.runtime
    val book = context.state.book
    val profile = context.state.profile
    val textStyle = context.state.textStyle
    val layoutBias = context.state.layoutBias
    val frames = context.frameState.frames

    var lastSessionKey by rememberSaveable { mutableStateOf(sessionKey) }

    LaunchedEffect(sessionKey) {
        if (lastSessionKey == sessionKey) return@LaunchedEffect
        lastSessionKey = sessionKey
        runtime.currentTokenIndex = book.startIndex
        runtime.currentResumeCursor = book.startResumeCursor.takeIf { it >= 0 } ?: -1
        runtime.frameIndex = alignFrameIndex(frames, book.startIndex, runtime.currentResumeCursor)
        runtime.rampStartFrameIndex = runtime.frameIndex
        runtime.scheduledFrameIndex = -1
        runtime.nextFrameAtMs = 0L
        runtime.isPlaying = autoPlay
        runtime.completed = false
        runtime.currentTempoMsPerWord =
            context.state.launchTempoMsPerWord ?: profile.config.tempoMsPerWord
        runtime.currentFontSizeSp = textStyle.fontSizeSp
        runtime.currentFontWeight = textStyle.fontWeight
        runtime.currentFontFamily = textStyle.fontFamily
        runtime.currentTextBrightness = textStyle.textBrightness
        runtime.currentVerticalBias = layoutBias.verticalBias
        runtime.currentHorizontalBias = layoutBias.horizontalBias
        runtime.dragStartBias = layoutBias.verticalBias
        runtime.dragStartHorizontalBias = layoutBias.horizontalBias
        runtime.isPositioningMode = false
        runtime.isAdjustingPosition = false
        runtime.isScrubbing = false
        runtime.isExiting = false
        runtime.dragAxis = RsvpDragAxis.NONE
        runtime.dragAccumulator = ZERO_FLOAT
        runtime.dragAccumulatorX = ZERO_FLOAT
        runtime.showQuickSettings = false
        runtime.showControls = false
    }
}

@Composable
internal fun RsvpPlaybackLoopEffect(
    context: RsvpUiContext,
    enabled: Boolean,
) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val tempoScale = context.timing.tempoScale
    val config = context.state.profile.config

    LaunchedEffect(enabled, runtime.isPlaying, runtime.frameIndex, runtime.completed, frames, tempoScale) {
        if (!enabled) return@LaunchedEffect
        if (!runtime.isPlaying || runtime.completed) return@LaunchedEffect
        if (runtime.frameIndex >= frames.size) return@LaunchedEffect
        val frame = frames[runtime.frameIndex]
        val effectiveTempoMs =
            effectivePlaybackTempoMs(
                baseTempoMs = context.frameState.baseTempoMs,
                tempoScale = tempoScale,
            )
        if (shouldSkipBlinkFrame(frame, config, effectiveTempoMs, tempoScale)) {
            if (runtime.frameIndex >= frames.lastIndex) {
                if (shouldCompleteAtLoadedFrameBoundary(context)) {
                    completePlayback(context)
                } else {
                    holdAtLoadingFrameBoundary(context)
                }
            } else {
                runtime.frameIndex += 1
            }
            return@LaunchedEffect
        }
        val rampMultiplier =
            rampMultiplier(config, runtime.frameIndex, runtime.rampStartFrameIndex)
        val resumeDelayMs =
            resumeDelayMs(config, runtime.frameIndex, runtime.rampStartFrameIndex)
        val frameMs =
            (frame.durationMs * rampMultiplier)
                .roundToLong()
                .coerceAtLeast(MIN_FRAME_DELAY_MS)
        var scaledMs =
            ((frameMs + resumeDelayMs) * tempoScale)
                .roundToLong()
                .coerceAtLeast(MIN_FRAME_DELAY_MS)
        val floorMs = frameFloorMs(frame, config, effectiveTempoMs)
        if (scaledMs < floorMs) {
            scaledMs = floorMs
        }
        val now = SystemClock.elapsedRealtime()
        val chained = runtime.scheduledFrameIndex == runtime.frameIndex - 1
        val overshootMs =
            if (chained && runtime.nextFrameAtMs > 0L) {
                (now - runtime.nextFrameAtMs).coerceAtLeast(0L)
            } else {
                0L
            }
        val candidateTarget =
            if (chained && runtime.nextFrameAtMs > 0L) {
                runtime.nextFrameAtMs + scaledMs
            } else {
                now + scaledMs
            }
        val softenedCatchUp =
            if (overshootMs > 0L) {
                (overshootMs * (1.0 - CATCH_UP_FACTOR)).roundToLong()
            } else {
                0L
            }
        val targetMs =
            if (candidateTarget < now) {
                now + scaledMs
            } else {
                candidateTarget + softenedCatchUp
            }
        runtime.scheduledFrameIndex = runtime.frameIndex
        runtime.nextFrameAtMs = targetMs
        val delayMs = (targetMs - now).coerceAtLeast(MIN_FRAME_DELAY_MS)
        delay(delayMs)
        if (runtime.frameIndex == frames.lastIndex) {
            if (shouldCompleteAtLoadedFrameBoundary(context)) {
                completePlayback(context)
            } else {
                holdAtLoadingFrameBoundary(context)
            }
        } else {
            runtime.frameIndex += 1
        }
    }
}

internal fun shouldCompleteAtLoadedFrameBoundary(context: RsvpUiContext): Boolean =
    context.frameState.frames.isNotEmpty() &&
        context.runtime.frameIndex >= context.frameState.frames.lastIndex &&
        !context.frameState.isLoading

internal fun effectivePlaybackTempoMs(
    baseTempoMs: Long,
    tempoScale: Double,
): Long =
    (baseTempoMs.coerceAtLeast(1L) * tempoScale)
        .roundToLong()
        .coerceAtLeast(1L)

internal fun holdAtLoadingFrameBoundary(context: RsvpUiContext) {
    val runtime = context.runtime
    val frame = context.frameState.frames.getOrNull(runtime.frameIndex)
    if (frame != null) {
        runtime.currentTokenIndex = frame.nextOriginalTokenIndex
        runtime.currentResumeCursor = -1
    }
    runtime.scheduledFrameIndex = -1
    runtime.nextFrameAtMs = 0L
}

private fun rampMultiplier(
    config: RsvpConfig,
    frameIndex: Int,
    rampStartIndex: Int,
): Double {
    val rampFrames = config.rampUpFrames
    if (rampStartIndex <= 0 || rampStartIndex < rampFrames) return 1.0
    val offset = frameIndex - rampStartIndex
    if (rampFrames <= 0 || offset < 0 || offset >= rampFrames) return 1.0
    val progress = offset.toDouble() / rampFrames.toDouble().coerceAtLeast(1.0)
    return 1.35 - (0.35 * progress)
}

private fun resumeDelayMs(
    config: RsvpConfig,
    frameIndex: Int,
    rampStartIndex: Int,
): Long {
    if (rampStartIndex <= 0 ||
        rampStartIndex < config.rampUpFrames ||
        frameIndex != rampStartIndex
    ) {
        return 0L
    }
    return config.startDelayMs
}

private const val CATCH_UP_FACTOR = 0.25

@Composable
internal fun RsvpAutoHideControlsEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showControls, runtime.isPlaying) {
        if (runtime.showControls && runtime.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            runtime.showControls = false
        }
    }
}

@Composable
internal fun RsvpAutoHideTempoIndicatorEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showTempoIndicator) {
        if (runtime.showTempoIndicator) {
            delay(TEMPO_INDICATOR_HIDE_DELAY_MS)
            runtime.showTempoIndicator = false
        }
    }
}

@Composable
internal fun RsvpAutoHideFontSizeIndicatorEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showFontSizeIndicator) {
        if (runtime.showFontSizeIndicator) {
            delay(FONT_SIZE_INDICATOR_HIDE_DELAY_MS)
            runtime.showFontSizeIndicator = false
        }
    }
}
