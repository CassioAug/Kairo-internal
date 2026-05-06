package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPaceEstimatorTest {
    @Test
    fun lowerTempoProducesHigherEstimatedWpm() {
        val slow = RsvpPaceEstimator.estimateWpm(RsvpConfig(tempoMsPerWord = 160L))
        val fast = RsvpPaceEstimator.estimateWpm(RsvpConfig(tempoMsPerWord = 90L))

        assertTrue("Expected fast($fast) > slow($slow)", fast > slow)
    }
}
