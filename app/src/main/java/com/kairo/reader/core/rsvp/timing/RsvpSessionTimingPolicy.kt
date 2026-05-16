package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.rsvp.engine.MIN_FRAME_MS
import kotlin.math.min

internal object RsvpSessionTimingPolicy {
    fun applyInitialSessionRamps(
        frames: MutableList<RsvpFrame>,
        config: RsvpConfig,
    ) {
        if (frames.isEmpty()) return

        val total = frames.size
        val rampUp = min(config.rampUpFrames.coerceAtLeast(0), total / 2)
        for (i in 0 until rampUp) {
            frames[i] =
                frames[i].copy(
                    durationMs = (frames[i].durationMs * rampUpMultiplier(i, rampUp)).toLong(),
                )
        }

        frames[0] =
            frames[0].copy(
                durationMs = addNonNegativeDelay(frames[0].durationMs, config.startDelayMs)
                    .coerceAtLeast(MIN_FRAME_MS),
            )

        val rampDown = min(config.rampDownFrames.coerceAtLeast(0), total / 2)
        val start = total - rampDown
        for (i in start until total) {
            frames[i] =
                frames[i].copy(
                    durationMs = (frames[i].durationMs * rampDownMultiplier(i - start, rampDown)).toLong(),
                )
        }

        frames[frames.lastIndex] =
            frames.last().copy(
                durationMs = addNonNegativeDelay(frames.last().durationMs, config.endDelayMs)
                    .coerceAtLeast(MIN_FRAME_MS),
            )
    }

    fun resumeRampMultiplier(
        config: RsvpConfig,
        frameIndex: Int,
        rampStartIndex: Int,
    ): Double {
        val rampFrames = config.rampUpFrames
        if (rampStartIndex <= 0 || rampStartIndex < rampFrames) return 1.0
        val offset = frameIndex - rampStartIndex
        if (rampFrames <= 0 || offset < 0 || offset >= rampFrames) return 1.0
        return rampUpMultiplier(offset, rampFrames)
    }

    fun resumeDelayMs(
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

    private fun rampUpMultiplier(
        offset: Int,
        rampFrames: Int,
    ): Double {
        val progress = offset.toDouble() / rampFrames.coerceAtLeast(1).toDouble()
        return 1.35 - (0.35 * progress)
    }

    private fun rampDownMultiplier(
        offset: Int,
        rampFrames: Int,
    ): Double {
        val progress = offset.toDouble() / rampFrames.coerceAtLeast(1).toDouble()
        return 1.0 + (0.25 * progress)
    }

    private fun addNonNegativeDelay(
        durationMs: Long,
        delayMs: Long,
    ): Long {
        val safeDelay = delayMs.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - durationMs < safeDelay) {
            Long.MAX_VALUE
        } else {
            durationMs + safeDelay
        }
    }
}
