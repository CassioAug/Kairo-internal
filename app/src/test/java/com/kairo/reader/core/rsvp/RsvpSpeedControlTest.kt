package com.kairo.reader.core.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpSpeedControlTest {
    @Test
    fun bandForTempoMs_usesNaturalWpmThresholds() {
        assertEquals(
            RsvpSpeedControl.SpeedBand.VERY_SLOW,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 300L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.SLOW,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 200L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.STEADY,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 150L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.FAST,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 100L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.VERY_FAST,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 60L, extremeUnlocked = false),
        )
    }

    @Test
    fun steadySliderRegionUsesComfortableBaselineTempo() {
        val tempoAt28 =
            RsvpSpeedControl.tempoForSpeed(
                speed = 28f,
                minTempoMsPerWord = RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD,
                maxTempoMsPerWord = RsvpSpeedControl.MAX_TEMPO_MS_PER_WORD,
            )
        val tempoAt30 =
            RsvpSpeedControl.tempoForSpeed(
                speed = 30f,
                minTempoMsPerWord = RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD,
                maxTempoMsPerWord = RsvpSpeedControl.MAX_TEMPO_MS_PER_WORD,
            )

        assertEquals(160L, tempoAt28)
        assertEquals(151L, tempoAt30)
        assertTrue(60_000.0 / tempoAt28 in 350.0..425.0)
        assertTrue(60_000.0 / tempoAt30 in 350.0..425.0)
    }

    @Test
    fun legacyCurveMigrationPreservesDisplayedSliderPosition() {
        val legacyTempo =
            RsvpSpeedControl.tempoForSpeed(
                speed = 28f,
                minTempoMsPerWord = RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD,
                maxTempoMsPerWord = 240L,
            )
        val migratedTempo =
            RsvpSpeedControl.recalibrateLegacyTempoMs(
                tempoMsPerWord = legacyTempo,
                minTempoMsPerWord = RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD,
            )
        val migratedSpeed =
            RsvpSpeedControl.speedForTempoMs(
                tempoMsPerWord = migratedTempo,
                minTempoMsPerWord = RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD,
                maxTempoMsPerWord = RsvpSpeedControl.MAX_TEMPO_MS_PER_WORD,
            )

        assertEquals(28, RsvpSpeedControl.displaySpeed(migratedSpeed))
        assertEquals(160L, migratedTempo)
    }

    @Test
    fun bandForTempoMs_onlyShowsExtremeWhenUnlocked() {
        assertEquals(
            RsvpSpeedControl.SpeedBand.VERY_FAST,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 20L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.EXTREME,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 20L, extremeUnlocked = true),
        )
    }
}
