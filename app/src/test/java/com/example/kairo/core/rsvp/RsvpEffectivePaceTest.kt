package com.example.kairo.core.rsvp

import com.example.kairo.core.model.RsvpConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpEffectivePaceTest {
    @Test
    fun adjustEstimatedWpm_slowerSessionTempoLowersEstimatedWpm() {
        val adjusted =
            RsvpEffectivePace.adjustEstimatedWpm(
                baseEstimatedWpm = 600,
                baseTempoMsPerWord = 100L,
                sessionTempoMsPerWord = 150L,
            )

        assertEquals(400, adjusted)
    }

    @Test
    fun adjustEstimatedWpm_invalidSessionTempoFallsBackToBaseEstimate() {
        val adjusted =
            RsvpEffectivePace.adjustEstimatedWpm(
                baseEstimatedWpm = 600,
                baseTempoMsPerWord = 100L,
                sessionTempoMsPerWord = 0L,
            )

        assertEquals(600, adjusted)
    }

    @Test
    fun estimateWpm_sessionTempoOverrideMatchesReaderScaling() {
        val config = RsvpConfig(tempoMsPerWord = 110L)

        val base = RsvpEffectivePace.estimateWpm(config)
        val adjusted =
            RsvpEffectivePace.estimateWpm(
                config = config,
                sessionTempoMsPerWord = 165L,
                fallbackEstimatedWpm = base,
            )

        assertEquals(545, base)
        assertEquals(364, adjusted)
    }
}
