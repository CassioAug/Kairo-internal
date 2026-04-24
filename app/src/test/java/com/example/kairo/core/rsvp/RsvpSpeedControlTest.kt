package com.example.kairo.core.rsvp

import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpSpeedControlTest {
    @Test
    fun bandForTempoMs_usesNaturalWpmThresholds() {
        assertEquals(
            RsvpSpeedControl.SpeedBand.VERY_SLOW,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 240L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.SLOW,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 150L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.STEADY,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 115L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.FAST,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 80L, extremeUnlocked = false),
        )
        assertEquals(
            RsvpSpeedControl.SpeedBand.VERY_FAST,
            RsvpSpeedControl.bandForTempoMs(tempoMsPerWord = 50L, extremeUnlocked = false),
        )
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
