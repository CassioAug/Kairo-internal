package com.kairo.reader.core.model

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
    fun userPreferencesStartWithSaneFontSizes() {
        val defaults = UserPreferences()

        assertEquals(18f, defaults.readerFontSizeSp, 0.0f)
        assertEquals(38f, defaults.rsvpFontSizeSp, 0.0f)
    }

    @Test
    fun comprehensionProfilesUseConservativePhraseChunking() {
        val chunkedProfiles = setOf(RsvpProfile.NARRATIVE, RsvpProfile.FLOW, RsvpProfile.STUDY)
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertEquals(
                "Unexpected phrase chunking default for ${profile.name}",
                profile in chunkedProfiles,
                config.enablePhraseChunking,
            )
        }
    }

    @Test
    fun builtInProfilesChooseContextByReadingIntent() {
        assertEquals(
            RsvpContextAssistMode.OFF,
            RsvpProfile.SPRINT.defaultConfig().contextAssistMode,
        )
        assertEquals(
            RsvpContextAssistMode.FULL_CLAUSE,
            RsvpProfile.NARRATIVE.defaultConfig().contextAssistMode,
        )
        assertEquals(
            RsvpContextAssistMode.FULL_CLAUSE,
            RsvpProfile.STUDY.defaultConfig().contextAssistMode,
        )
        assertEquals(
            RsvpContextAssistMode.PREVIOUS_WORDS,
            RsvpProfile.BALANCED.defaultConfig().contextAssistMode,
        )
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
    fun balancedProfileStartsWithBreathablePunctuation() {
        val config = RsvpProfile.BALANCED.defaultConfig()

        assertTrue("Expected comma breath to be noticeable", config.commaPauseMs >= 165L)
        assertTrue("Expected semicolon breath to exceed commas", config.semicolonPauseMs >= 260L)
        assertTrue("Expected full stops to settle", config.periodPauseMs >= 330L)
        assertTrue("Expected expressive sentence marks to settle", config.sentenceEndPauseMs >= 350L)
        assertTrue("Expected paragraphs to create a real reset", config.paragraphPauseMs >= 430L)
        assertTrue("Expected global punctuation breathing above neutral", config.punctuationPauseFactor >= 1.08)
        assertTrue("Expected dialogue punctuation to stay readable", config.dialoguePunctuationScale >= 0.94)
        assertFalse("Expected first-run parentheticals to stay readable", config.useParentheticalAside)
    }

    @Test
    fun builtInProfilesLeanIntoPunctuationWithoutLosingTheirSpeedShape() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue("Expected readable comma breath for ${profile.name}", config.commaPauseMs >= 120L)
            assertTrue(
                "Expected semicolon to be closer to a stop than a comma for ${profile.name}",
                config.semicolonPauseMs >= (config.commaPauseMs * 1.45).toLong(),
            )
            assertTrue(
                "Expected ellipses/sentence timing to have a real floor for ${profile.name}",
                config.minPauseScale >= 0.82,
            )
            assertTrue(
                "Expected punctuation breathing to be above neutral for ${profile.name}",
                config.punctuationPauseFactor >= 1.04,
            )
            assertTrue(
                "Expected dialogue punctuation not to collapse for ${profile.name}",
                config.dialoguePunctuationScale >= 0.92,
            )
            assertFalse(
                "Expected parentheticals to remain readable by default for ${profile.name}",
                config.useParentheticalAside,
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
                config.dialoguePunctuationScale in 0.90..1.0,
            )
            assertTrue(
                "Expected paragraph strength to hold natural breaks for ${profile.name}",
                config.paragraphPauseMultiplier in 1.10..1.75,
            )
            assertTrue(
                "Expected page breaks to remain stronger than paragraph breaks for ${profile.name}",
                config.pageBreakPauseMultiplier > config.paragraphPauseMultiplier + 1.5,
            )
            assertTrue(
                "Expected ORP highlight on by default for ${profile.name}",
                config.orpHighlightEnabled,
            )
            assertTrue(
                "Expected punctuation breathing to be intentionally profiled for ${profile.name}",
                config.punctuationPauseFactor in 1.0..1.25,
            )
            assertTrue(
                "Expected phrase chunks to stay short for ${profile.name}",
                config.maxWordsPerUnit == 2,
            )
            assertTrue(
                "Expected opt-in phrase chunk character budget to stay compact for ${profile.name}",
                config.maxCharsPerUnit in 12..15,
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
