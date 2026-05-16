package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.effectiveBlinkMode
import com.kairo.reader.core.model.isMidSentencePunctuation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

internal fun applySessionRamps(
    frames: MutableList<RsvpFrame>,
    config: RsvpConfig,
) {
    if (frames.isEmpty()) return

    val total = frames.size
    val rampUp = min(config.rampUpFrames.coerceAtLeast(0), total / 2)
    for (i in 0 until rampUp) {
        val progress = i.toDouble() / rampUp.coerceAtLeast(1)
        val multiplier = 1.35 - (0.35 * progress)
        frames[i] = frames[i].copy(durationMs = (frames[i].durationMs * multiplier).toLong())
    }

    frames[0] =
        frames[0].copy(
            durationMs = addNonNegativeDelay(frames[0].durationMs, config.startDelayMs)
                .coerceAtLeast(MIN_FRAME_MS),
        )

    val rampDown = min(config.rampDownFrames.coerceAtLeast(0), total / 2)
    val start = total - rampDown
    for (i in start until total) {
        val progress = (i - start).toDouble() / rampDown.coerceAtLeast(1)
        val multiplier = 1.0 + (0.25 * progress)
        frames[i] = frames[i].copy(durationMs = (frames[i].durationMs * multiplier).toLong())
    }

    frames[frames.lastIndex] =
        frames.last().copy(
            durationMs = addNonNegativeDelay(frames.last().durationMs, config.endDelayMs)
                .coerceAtLeast(MIN_FRAME_MS),
        )
}


internal fun addNonNegativeDelay(
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


internal fun applyBlinkSeparation(
    frames: MutableList<RsvpFrame>,
    config: RsvpConfig,
) {
    val blinkMode = config.effectiveBlinkMode()
    // Early exit if blink mode is disabled - no processing needed
    if (blinkMode == BlinkMode.OFF) return
    if (frames.size < 2) return

    val strength = speedStrength(config.tempoMsPerWord.toDouble())
    if (strength < BLINK_START_STRENGTH) return
    val normalizedStrength =
        ((strength - BLINK_START_STRENGTH) / (1.0 - BLINK_START_STRENGTH))
            .coerceIn(0.0, 1.0)
    val easedStrength = normalizedStrength * normalizedStrength
    val targetBlinkMs =
        (MIN_BLINK_MS.toDouble() + (BLINK_EXTRA_MS * easedStrength))
            .roundToLong()
            .coerceIn(MIN_BLINK_MS, MAX_BLINK_MS)

    val blinkToken = Token(text = " ", type = TokenType.PUNCTUATION)
    val output = ArrayList<RsvpFrame>(frames.size * 2)

    for (i in frames.indices) {
        val frame = frames[i]
        val next = frames.getOrNull(i + 1)
        val hasWord = frame.tokens.any { it.type == TokenType.WORD }
        val nextTokens = next?.tokens.orEmpty()
        val nextHasWord = nextTokens.any { it.type == TokenType.WORD }
        val wordCount = frame.tokens.count { it.type == TokenType.WORD }
        val nextWordCount = nextTokens.count { it.type == TokenType.WORD }

        if (hasWord && nextHasWord) {
            // Keep chunked phrase units visually stable by avoiding injected blink frames
            // between multi-word frames.
            if (wordCount != 1 || nextWordCount != 1) {
                output += frame
                continue
            }
            val firstWord = frame.tokens.firstOrNull { it.type == TokenType.WORD }
            if (firstWord == null) {
                output += frame
                continue
            }
            val nextWord = nextTokens.firstOrNull { it.type == TokenType.WORD }
            if (nextWord != null &&
                frame.tokens.none { it.type == TokenType.PUNCTUATION } &&
                shouldPreferHold(firstWord, nextWord)
            ) {
                output += frame
                continue
            }
            if (isHardBoundary(frame.tokens, nextWord)) {
                output += frame
                continue
            }
            val floorMs = max(wordFloorMs(firstWord, config), MIN_FRAME_MS)
            val maxBlink = (frame.durationMs - floorMs).coerceAtLeast(0L)
            val punctuationFactor = blinkPunctuationFactor(frame.tokens)
            val weight =
                when (blinkMode) {
                    BlinkMode.SUBTLE -> punctuationFactor
                    BlinkMode.ADAPTIVE -> {
                        val ease = (wordEase(firstWord) + wordEase(nextWord ?: firstWord)) * 0.5
                        if (ease >= ADAPTIVE_EASE_THRESHOLD) punctuationFactor else 0.0
                    }
                    BlinkMode.OFF -> 0.0
                }
            val blinkMs = min((targetBlinkMs * weight).roundToLong(), maxBlink)
            if (blinkMs >= MIN_BLINK_MS) {
                output +=
                    frame.copy(
                        durationMs = (frame.durationMs - blinkMs).coerceAtLeast(MIN_FRAME_MS)
                    )
                output +=
                    RsvpFrame(
                        tokens = listOf(blinkToken),
                        durationMs = blinkMs,
                        originalTokenIndex = frame.originalTokenIndex,
                        resumeCursor = frame.resumeCursor,
                        nextOriginalTokenIndex = frame.nextOriginalTokenIndex,
                    )
                continue
            }
        }

        output += frame
    }

    frames.clear()
    frames.addAll(output)
}


internal fun blinkPunctuationFactor(tokens: List<Token>): Double {
    val hasMidPause =
        tokens.any { token ->
            val ch = token.text.firstOrNull() ?: return@any false
            token.type == TokenType.PUNCTUATION && isMidSentencePunctuation(ch)
        }
    return if (hasMidPause) 0.55 else 1.0
}
