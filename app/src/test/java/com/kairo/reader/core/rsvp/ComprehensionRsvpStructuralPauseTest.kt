package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensionRsvpStructuralPauseTest : ComprehensionRsvpTestBase() {
    @Test
    fun blinkSeparationDoesNotSplitMultiWordChunkFrames() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 80L,
                blinkMode = BlinkMode.SUBTLE,
                enablePhraseChunking = true,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 16,
                sentenceEndPauseMs = 0L,
                useClausePausing = false,
            )
        val tokens = listOf(w("calm"), w("water"), w("wild"), w("winds"))

        val frames = engine.generateFrames(tokens, 0, config)

        assertEquals(2, frames.size)
        assertTrue(frames.all { frame -> frame.tokens.any { it.type == TokenType.WORD } })
        assertTrue(frames.all { frame -> frame.tokens.count { it.type == TokenType.WORD } == 2 })
    }

    @Test
    fun pageBreakAddsMeaningfulPause() {
        val config = stableConfig.copy(tempoMsPerWord = 60L)

        val frames = engine.generateFrames(
            tokens = listOf(w("Hello"), pageBreak(), w("Next")),
            startIndex = 0,
            config = config
        )
        assertTrue("Expected at least 3 frames (word + break + word)", frames.size >= 3)

        val breakFrame = frames[1]
        assertTrue(
            "Expected break frame to contain no WORD tokens",
            breakFrame.tokens.none {
                it.type ==
                    TokenType.WORD
            }
        )
        assertEquals(listOf(" "), breakFrame.tokens.map { it.text })
        assertTrue("Expected page break pause to be meaningfully longer", breakFrame.durationMs >= 700L)
    }

    @Test
    fun pageBreakPauseMultiplierControlsBlankFrameDuration() {
        val baseConfig = stableConfig.copy(
            tempoMsPerWord = 60L,
            paragraphPauseMs = 240L,
            pageBreakPauseMultiplier = 2.0,
        )
        val spaciousConfig = baseConfig.copy(pageBreakPauseMultiplier = 4.0)
        val tokens = listOf(w("Hello"), pageBreak(), w("Next"))

        val baseBreakFrame = engine.generateFrames(tokens, 0, baseConfig)[1]
        val spaciousBreakFrame = engine.generateFrames(tokens, 0, spaciousConfig)[1]

        assertTrue(
            "Expected page break multiplier to lengthen the blank frame",
            spaciousBreakFrame.durationMs > baseBreakFrame.durationMs + 300L,
        )
    }

    @Test
    fun blinkSeparationInsertsBlankFramesBetweenWords() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 80L,
                blinkMode = BlinkMode.SUBTLE,
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
                lengthExponent = 1.0,
                sentenceEndPauseMs = 0L,
                useClausePausing = false,
            )

        val tokens = listOf(w("test"), w("test"))

        val withoutBlink = engine.generateFrames(tokens, 0, config.copy(blinkMode = BlinkMode.OFF))
        val withBlink = engine.generateFrames(tokens, 0, config)

        assertEquals(2, withoutBlink.size)
        assertEquals(3, withBlink.size)
        assertTrue(withBlink[1].tokens.none { it.type == TokenType.WORD })
        assertTrue(withBlink[1].durationMs in 16L..22L)
        assertEquals(withoutBlink.sumOf { it.durationMs }, withBlink.sumOf { it.durationMs })
    }

    @Test
    fun structuralPauseAddsExtraTimeToBreakFrames() {
        val config =
            stableConfig.copy(
                tempoMsPerWord = 200L,
                paragraphPauseMs = 0L,
                startDelayMs = 0L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
            )

        val tokens =
            listOf(
                w("Hello"),
                Token(text = "\n", type = TokenType.PARAGRAPH_BREAK, pauseAfterMs = 200L),
                w("Next"),
            )

        val frames = engine.generateFrames(tokens, 0, config)
        val breakFrame =
            frames.firstOrNull { it.tokens.none { t -> t.type == TokenType.WORD } }
                ?: error("Expected a break frame")

        assertTrue("Expected break frame to include extra pause", breakFrame.durationMs >= 340L)
    }
}
