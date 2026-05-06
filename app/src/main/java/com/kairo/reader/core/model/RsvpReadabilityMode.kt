package com.kairo.reader.core.model

import kotlin.math.roundToLong

enum class RsvpReadabilityMode { STANDARD, HIGH_SPEED, EXTREME }

fun readabilityModeForTempoMs(tempoMs: Long): RsvpReadabilityMode =
    when {
        tempoMs <= EXTREME_SPEED_TEMPO_MS -> RsvpReadabilityMode.EXTREME
        tempoMs <= HIGH_SPEED_TEMPO_MS -> RsvpReadabilityMode.HIGH_SPEED
        else -> RsvpReadabilityMode.STANDARD
    }

fun RsvpConfig.readabilityMode(tempoMs: Long = this.tempoMsPerWord): RsvpReadabilityMode =
    readabilityModeForTempoMs(tempoMs)

fun RsvpConfig.effectiveBlinkMode(tempoMs: Long = this.tempoMsPerWord): BlinkMode =
    when {
        blinkMode == BlinkMode.OFF -> BlinkMode.OFF
        readabilityMode(tempoMs) == RsvpReadabilityMode.EXTREME -> BlinkMode.ADAPTIVE
        else -> blinkMode
    }

fun RsvpConfig.prefersSimplifiedOrpDisplay(tempoMs: Long = this.tempoMsPerWord): Boolean =
    false

fun RsvpConfig.speedNarrowingFactor(tempoMs: Long = this.tempoMsPerWord): Double {
    val normalizedTempo = tempoMs.coerceAtLeast(MIN_SPEED_TEMPO_MS_PER_WORD)
    return when (readabilityMode(normalizedTempo)) {
        RsvpReadabilityMode.STANDARD -> 1.0
        RsvpReadabilityMode.HIGH_SPEED -> {
            val fraction =
                ((HIGH_SPEED_TEMPO_MS - normalizedTempo).toDouble() /
                    (HIGH_SPEED_TEMPO_MS - EXTREME_SPEED_TEMPO_MS).toDouble())
                    .coerceIn(0.0, 1.0)
            lerp(1.0, HIGH_SPEED_NARROWING_FACTOR, fraction)
        }
        RsvpReadabilityMode.EXTREME -> {
            val fraction =
                ((EXTREME_SPEED_TEMPO_MS - normalizedTempo).toDouble() /
                    (EXTREME_SPEED_TEMPO_MS - MIN_SPEED_TEMPO_MS_PER_WORD).toDouble())
                    .coerceIn(0.0, 1.0)
            lerp(HIGH_SPEED_NARROWING_FACTOR, EXTREME_NARROWING_FACTOR, fraction)
        }
    }
}

fun RsvpConfig.prefersOrpWindowing(tempoMs: Long = this.tempoMsPerWord): Boolean =
    readabilityMode(tempoMs) != RsvpReadabilityMode.STANDARD

fun RsvpConfig.wordFloorMsForReadability(
    word: Token,
    tempoMs: Long = this.tempoMsPerWord,
): Long {
    val base =
        if (word.isSubwordChunk) {
            longWordMinMs
        } else {
            val letters = word.text.count { it.isLetterOrDigit() }
            if (letters >= longWordChars) longWordMinMs else minWordMs
        }
    val scaledBase = (base.toDouble() * speedNarrowingFactor(tempoMs)).roundToLong()
    val bonus =
        when (readabilityMode(tempoMs)) {
            RsvpReadabilityMode.EXTREME -> if (word.isSubwordChunk) 6L else 2L
            RsvpReadabilityMode.HIGH_SPEED -> if (word.isSubwordChunk) 4L else 1L
            RsvpReadabilityMode.STANDARD -> 0L
        }
    return scaledBase + bonus
}

private const val HIGH_SPEED_TEMPO_MS = 82L
private const val EXTREME_SPEED_TEMPO_MS = 62L
private const val MIN_SPEED_TEMPO_MS_PER_WORD = 3L
private const val HIGH_SPEED_NARROWING_FACTOR = 0.82
private const val EXTREME_NARROWING_FACTOR = 0.64

private fun lerp(start: Double, end: Double, fraction: Double): Double =
    start + ((end - start) * fraction)
