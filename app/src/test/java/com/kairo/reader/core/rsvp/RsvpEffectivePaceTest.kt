package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
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

    @Test
    fun frameFloorMs_usesEffectiveTempoForPunctuationFloor() {
        val frame =
            RsvpFrame(
                tokens =
                listOf(
                    Token(text = "Wait", type = TokenType.WORD),
                    Token(text = ".", type = TokenType.PUNCTUATION),
                ),
                durationMs = 1L,
                originalTokenIndex = 0,
            )
        val config = RsvpConfig(tempoMsPerWord = 150L)
        val effectiveTempoMs = 60L

        assertEquals(
            frameFloorMs(
                frame = frame,
                config = config.copy(tempoMsPerWord = effectiveTempoMs),
                effectiveTempoMs = effectiveTempoMs,
            ),
            frameFloorMs(
                frame = frame,
                config = config,
                effectiveTempoMs = effectiveTempoMs,
            ),
        )
    }

    @Test
    fun estimateChapterPreviewWpm_doesNotDoubleCountSplitWordFrames() {
        val config =
            RsvpConfig(
                tempoMsPerWord = 100L,
                minWordMs = 1L,
                longWordMinMs = 1L,
            )
        val frames =
            listOf(
                RsvpFrame(
                    tokens = listOf(Token(text = "self-", type = TokenType.WORD)),
                    durationMs = 100L,
                    originalTokenIndex = 4,
                ),
                RsvpFrame(
                    tokens = listOf(Token(text = "aware", type = TokenType.WORD)),
                    durationMs = 100L,
                    originalTokenIndex = 4,
                ),
            )

        assertEquals(
            300,
            RsvpEstimatedReadingPace.estimateChapterPreviewWpm(
                config = config,
                frames = frames,
                baseTempoMsPerWord = 100L,
                sessionTempoMsPerWord = null,
            ),
        )
    }

    @Test
    fun estimateChapterPreviewWpm_countsMultiWordFrames() {
        val config =
            RsvpConfig(
                tempoMsPerWord = 100L,
                minWordMs = 1L,
                longWordMinMs = 1L,
            )
        val frames =
            listOf(
                RsvpFrame(
                    tokens =
                    listOf(
                        Token(text = "in", type = TokenType.WORD),
                        Token(text = "time", type = TokenType.WORD),
                    ),
                    durationMs = 200L,
                    originalTokenIndex = 0,
                ),
            )

        assertEquals(
            600,
            RsvpEstimatedReadingPace.estimateChapterPreviewWpm(
                config = config,
                frames = frames,
                baseTempoMsPerWord = 100L,
                sessionTempoMsPerWord = null,
            ),
        )
    }
}
