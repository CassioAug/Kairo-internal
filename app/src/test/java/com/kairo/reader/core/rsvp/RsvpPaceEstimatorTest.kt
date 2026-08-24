package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPaceEstimatorTest {
    @Test
    fun lowerTempoProducesHigherEstimatedWpm() {
        val slow = RsvpPaceEstimator.estimateWpm(RsvpConfig(tempoMsPerWord = 160L))
        val fast = RsvpPaceEstimator.estimateWpm(RsvpConfig(tempoMsPerWord = 90L))

        assertTrue("Expected fast($fast) > slow($slow)", fast > slow)
    }

    @Test
    fun paceCacheIdentitySeparatesLegacyFromEnglishScoredStrategy() {
        val config =
            RsvpConfig(
                enablePhraseChunking = true,
                maxWordsPerUnit = 3,
                maxCharsPerUnit = 24,
            )
        val legacyOptions = RsvpPaceEstimationOptions.LEGACY
        val scoredOptions =
            RsvpPaceEstimationOptions(
                sampleLanguagePolicy = RsvpLanguagePolicy.ENGLISH,
                segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
            )
        val legacy =
            RsvpEstimatedReadingPace.estimateWpm(
                config = config,
                paceOptions = legacyOptions,
            )
        val scored =
            RsvpEstimatedReadingPace.estimateWpm(
                config = config,
                paceOptions = scoredOptions,
            )

        assertTrue(legacy > 0)
        assertTrue(scored > 0)
        assertNotEquals(
            EstimatedWpmCacheKey(config, legacyOptions, targetLanguageTag = "en"),
            EstimatedWpmCacheKey(config, scoredOptions, targetLanguageTag = "en"),
        )
    }

    @Test
    fun nonEnglishSamplePolicyDoesNotApplyEnglishScoringToEnglishSample() {
        val config =
            RsvpConfig(
                enablePhraseChunking = true,
                maxWordsPerUnit = 3,
                maxCharsPerUnit = 24,
            )
        val legacy = RsvpPaceEstimator.estimateWpm(config)
        val ineligibleScored =
            RsvpPaceEstimator.estimateWpm(
                config,
                RsvpPaceEstimationOptions(
                    sampleLanguagePolicy = RsvpLanguagePolicy.DEFAULT_NON_ENGLISH,
                    segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
                ),
            )

        assertEquals(legacy, ineligibleScored)
    }
}
