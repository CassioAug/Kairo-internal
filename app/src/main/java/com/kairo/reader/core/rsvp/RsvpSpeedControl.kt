package com.kairo.reader.core.rsvp

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object RsvpSpeedControl {
    const val MIN_SPEED = 0f
    const val MAX_SPEED = 100f
    const val SAFE_MIN_TEMPO_MS_PER_WORD = 20L
    const val EXTREME_MIN_TEMPO_MS_PER_WORD = 3L
    const val MAX_TEMPO_MS_PER_WORD = 360L
    const val SPEED_CURVE_VERSION = 2

    enum class SpeedBand {
        VERY_SLOW,
        SLOW,
        STEADY,
        FAST,
        VERY_FAST,
        EXTREME,
    }

    fun speedForTempoMs(
        tempoMsPerWord: Long,
        minTempoMsPerWord: Long,
        maxTempoMsPerWord: Long,
    ): Float {
        val fastTempo = minTempoMsPerWord.coerceAtLeast(1L)
        val slowTempo = maxTempoMsPerWord.coerceAtLeast(fastTempo)
        val clampedTempo = tempoMsPerWord.coerceIn(fastTempo, slowTempo)
        if (fastTempo == slowTempo) return MAX_SPEED

        val minLog = ln(fastTempo.toDouble())
        val maxLog = ln(slowTempo.toDouble())
        val normalized =
            1.0 - ((ln(clampedTempo.toDouble()) - minLog) / (maxLog - minLog))
        return (normalized * MAX_SPEED).toFloat().coerceIn(MIN_SPEED, MAX_SPEED)
    }

    fun tempoForSpeed(
        speed: Float,
        minTempoMsPerWord: Long,
        maxTempoMsPerWord: Long,
    ): Long {
        val fastTempo = minTempoMsPerWord.coerceAtLeast(1L)
        val slowTempo = maxTempoMsPerWord.coerceAtLeast(fastTempo)
        if (fastTempo == slowTempo) return fastTempo

        val normalized = (speed / MAX_SPEED).coerceIn(0f, 1f).toDouble()
        val minLog = ln(fastTempo.toDouble())
        val maxLog = ln(slowTempo.toDouble())
        val tempoLog = maxLog + ((minLog - maxLog) * normalized)
        return exp(tempoLog).roundToLong().coerceIn(fastTempo, slowTempo)
    }

    fun displaySpeed(speed: Float): Int = speed.roundToInt().coerceIn(0, 100)

    /**
     * Keeps a saved slider position stable while moving it onto the more readable speed curve.
     * Version 1 used a 240ms slow endpoint, which made the 28-30 region roughly 500-525 WPM.
     */
    fun recalibrateLegacyTempoMs(
        tempoMsPerWord: Long,
        minTempoMsPerWord: Long,
    ): Long {
        val legacySpeed =
            speedForTempoMs(
                tempoMsPerWord = tempoMsPerWord,
                minTempoMsPerWord = minTempoMsPerWord,
                maxTempoMsPerWord = LEGACY_MAX_TEMPO_MS_PER_WORD,
            )
        return tempoForSpeed(
            // Saved tempos were already rounded on the legacy curve. Re-map the slider value the
            // user actually saw instead of carrying that sub-step rounding error into the new curve.
            speed = displaySpeed(legacySpeed).toFloat(),
            minTempoMsPerWord = minTempoMsPerWord,
            maxTempoMsPerWord = MAX_TEMPO_MS_PER_WORD,
        )
    }

    fun bandForSpeed(
        speed: Float,
        extremeUnlocked: Boolean,
    ): SpeedBand {
        val minTempoMsPerWord =
            if (extremeUnlocked) {
                EXTREME_MIN_TEMPO_MS_PER_WORD
            } else {
                SAFE_MIN_TEMPO_MS_PER_WORD
            }
        return bandForTempoMs(
            tempoMsPerWord =
                tempoForSpeed(
                    speed = speed,
                    minTempoMsPerWord = minTempoMsPerWord,
                    maxTempoMsPerWord = MAX_TEMPO_MS_PER_WORD,
                ),
            extremeUnlocked = extremeUnlocked,
        )
    }

    fun bandForTempoMs(
        tempoMsPerWord: Long,
        extremeUnlocked: Boolean,
    ): SpeedBand {
        val selectedWpm = MS_PER_MINUTE / tempoMsPerWord.coerceAtLeast(1L).toDouble()
        return when {
            extremeUnlocked && selectedWpm >= EXTREME_MIN_WPM -> SpeedBand.EXTREME
            selectedWpm >= VERY_FAST_MIN_WPM -> SpeedBand.VERY_FAST
            selectedWpm >= FAST_MIN_WPM -> SpeedBand.FAST
            selectedWpm >= STEADY_MIN_WPM -> SpeedBand.STEADY
            selectedWpm >= SLOW_MIN_WPM -> SpeedBand.SLOW
            else -> SpeedBand.VERY_SLOW
        }
    }

    private const val MS_PER_MINUTE = 60_000.0
    private const val SLOW_MIN_WPM = 250
    private const val STEADY_MIN_WPM = 350
    private const val FAST_MIN_WPM = 550
    private const val VERY_FAST_MIN_WPM = 850
    private const val EXTREME_MIN_WPM = 1_800
    private const val LEGACY_MAX_TEMPO_MS_PER_WORD = 240L
}
