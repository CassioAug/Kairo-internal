package com.example.kairo.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpProfileDefaultsTest {
    @Test
    fun userPreferencesDefaultsUseBalancedProfile() {
        val defaults = UserPreferences()
        val balanced = RsvpProfile.BALANCED.defaultConfig()

        assertEquals(RsvpProfileIds.builtIn(RsvpProfile.BALANCED), defaults.rsvpSelectedProfileId)
        assertEquals(balanced, defaults.rsvpConfig)
        assertEquals(balanced.tempoMsPerWord, defaults.rsvpTempoMsPerWord)
    }

    @Test
    fun builtInProfilesHavePhraseChunkingOffByDefault() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertFalse(
                "Expected phrase chunking off for ${profile.name}",
                config.enablePhraseChunking,
            )
        }
    }

    @Test
    fun builtInProfilesEnablePunctuationLandingByDefault() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue(
                "Expected punctuation landing on for ${profile.name}",
                config.usePunctuationLandingHold,
            )
        }
    }

    @Test
    fun builtInProfilesMaintainReadablePauseHierarchy() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue("Expected comma pause for ${profile.name}", config.commaPauseMs > 0L)
            assertTrue(
                "Expected sentence pauses to exceed comma pauses for ${profile.name}",
                config.periodPauseMs > config.commaPauseMs &&
                    config.sentenceEndPauseMs > config.commaPauseMs,
            )
            assertTrue(
                "Expected expressive sentence marks to hold at least as long as periods for ${profile.name}",
                config.sentenceEndPauseMs >= config.periodPauseMs,
            )
            assertTrue(
                "Expected semicolons to sit between commas and periods for ${profile.name}",
                config.semicolonPauseMs > config.commaPauseMs &&
                    config.semicolonPauseMs < config.periodPauseMs,
            )
            assertTrue(
                "Expected colons and dashes to create clause holds for ${profile.name}",
                config.colonPauseMs > config.commaPauseMs &&
                    config.dashPauseMs > config.commaPauseMs,
            )
            assertTrue(
                "Expected soft punctuation to remain lighter than clause punctuation for ${profile.name}",
                config.quotePauseMs < config.commaPauseMs &&
                    config.parenthesesPauseMs < config.semicolonPauseMs,
            )
            assertTrue(
                "Expected paragraph pauses to exceed sentence pauses for ${profile.name}",
                config.paragraphPauseMs > config.sentenceEndPauseMs,
            )
            assertTrue(
                "Expected long-word floor to exceed base floor for ${profile.name}",
                config.longWordMinMs > config.minWordMs,
            )
            assertTrue(
                "Expected pause floor to stay within a readable range for ${profile.name}",
                config.minPauseScale in 0.65..0.9,
            )
        }
    }

    @Test
    fun builtInProfilesTuneNaturalReadingControls() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue("Expected focal stress on for ${profile.name}", config.useFocalStress)
            assertTrue(
                "Expected supporting words to compress gently for ${profile.name}",
                config.focalSupportCompression in 0.86..0.98,
            )
            assertTrue(
                "Expected anticipatory landing on for ${profile.name}",
                config.useAnticipatoryLanding,
            )
            assertTrue(
                "Expected landing boost to stay subtle for ${profile.name}",
                config.anticipatoryLandingBoost in 1.03..1.12,
            )
            assertTrue(
                "Expected dialogue punctuation to stay readable for ${profile.name}",
                config.dialoguePunctuationScale in 0.78..0.98,
            )
            assertTrue(
                "Expected paragraph strength to hold natural breaks for ${profile.name}",
                config.paragraphPauseMultiplier in 1.10..1.65,
            )
            assertTrue(
                "Expected page breaks to remain stronger than paragraph breaks for ${profile.name}",
                config.pageBreakPauseMultiplier > config.paragraphPauseMultiplier + 1.5,
            )
            assertTrue(
                "Expected punctuation breathing to be intentionally profiled for ${profile.name}",
                config.punctuationPauseFactor in 0.9..1.2,
            )
        }

        val naturalProfiles =
            RsvpProfile.entries
                .map { profile ->
                    val config = profile.defaultConfig()
                    listOf(
                        config.punctuationPauseFactor,
                        config.focalSupportCompression,
                        config.anticipatoryLandingBoost,
                        config.dialoguePunctuationScale,
                        config.parentheticalAsideMultiplier,
                        config.paragraphPauseMultiplier,
                        config.pageBreakPauseMultiplier,
                    )
                }.toSet()

        assertEquals(
            "Expected every built-in profile to carry its own natural-reading shape",
            RsvpProfile.entries.size,
            naturalProfiles.size,
        )
    }

    @Test
    fun builtInProfilesDefineDistinctProsodyStrengths() {
        val distinctProsodyStrengths =
            RsvpProfile.entries
                .map { it.defaultConfig().prosodyStrength }
                .toSet()
        assertTrue(
            "Expected profiles to provide meaningful cadence variation",
            distinctProsodyStrengths.size >= 4,
        )
    }
}
