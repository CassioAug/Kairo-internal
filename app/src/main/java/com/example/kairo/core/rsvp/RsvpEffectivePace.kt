package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import kotlin.math.roundToInt

object RsvpEffectivePace {
    fun estimateWpm(
        config: RsvpConfig,
        sessionTempoMsPerWord: Long? = null,
        fallbackEstimatedWpm: Int = 0,
    ): Int =
        wpmForTempoMsPerWord(sessionTempoMsPerWord ?: config.tempoMsPerWord)
            ?: fallbackEstimatedWpm.coerceAtLeast(0)

    fun adjustEstimatedWpm(
        baseEstimatedWpm: Int,
        baseTempoMsPerWord: Long,
        sessionTempoMsPerWord: Long?,
    ): Int =
        wpmForTempoMsPerWord(sessionTempoMsPerWord)
            ?: wpmForTempoMsPerWord(baseTempoMsPerWord)
            ?: baseEstimatedWpm.coerceAtLeast(0)

    private fun wpmForTempoMsPerWord(tempoMsPerWord: Long?): Int? =
        tempoMsPerWord
            ?.takeIf { it > 0L }
            ?.let { tempoMs -> (60_000.0 / tempoMs.toDouble()).roundToInt().coerceAtLeast(1) }
}
