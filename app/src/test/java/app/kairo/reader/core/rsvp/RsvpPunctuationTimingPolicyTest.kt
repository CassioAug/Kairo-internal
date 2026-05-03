package app.kairo.reader.core.rsvp

import app.kairo.reader.core.model.Token
import app.kairo.reader.core.model.TokenType
import app.kairo.reader.core.model.RsvpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPunctuationTimingPolicyTest {
    @Test
    fun periodResolvesToSentenceEnd() {
        val tier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("."),
                prevWord = word("Hello"),
                nextToken = word("there"),
            )

        assertEquals(RsvpPunctuationTier.SENTENCE_END, tier)
    }

    @Test
    fun commaResolvesToClauseBreak() {
        val tier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("again"),
            )

        assertEquals(RsvpPunctuationTier.CLAUSE_BREAK, tier)
    }

    @Test
    fun quotesResolveToSoftSeparator() {
        val tier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("\""),
                prevWord = word("said"),
                nextToken = word("hello"),
            )

        assertEquals(RsvpPunctuationTier.SOFT_SEPARATOR, tier)
    }

    @Test
    fun decimalPointsResolveToNone() {
        val tier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("."),
                prevWord = word("3"),
                nextToken = word("14"),
            )

        assertEquals(RsvpPunctuationTier.NONE, tier)
    }

    @Test
    fun multilingualSentenceMarksResolveToSentenceEnd() {
        val cjkTier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("。"),
                prevWord = word("待って"),
                nextToken = word("次"),
            )
        val arabicTier =
            RsvpPunctuationTimingPolicy.resolveTier(
                token = punctuation("؟"),
                prevWord = word("مرحبا"),
                nextToken = word("التالي"),
            )

        assertEquals(RsvpPunctuationTier.SENTENCE_END, cjkTier)
        assertEquals(RsvpPunctuationTier.SENTENCE_END, arabicTier)
    }

    @Test
    fun multilingualSentenceMarksReceiveSentencePauseTiming() {
        val timing =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation("。"),
                prevWord = word("待って"),
                nextToken = word("次"),
                config =
                    RsvpConfig(
                        sentenceEndPauseMs = 220L,
                        minPauseScale = 0.6,
                    ),
            )

        assertTrue(timing.baseMs > 0.0)
        assertTrue(timing.floorMs > 0.0)
    }

    @Test
    fun punctuationTimingNarrowsAtHigherSpeed() {
        val slow =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("again"),
                config =
                    RsvpConfig(
                        tempoMsPerWord = 120L,
                        commaPauseMs = 100L,
                    ),
            )
        val fast =
            RsvpPunctuationTimingPolicy.resolvePauseTiming(
                token = punctuation(","),
                prevWord = word("Wait"),
                nextToken = word("again"),
                config =
                    RsvpConfig(
                        tempoMsPerWord = 55L,
                        commaPauseMs = 100L,
                    ),
            )

        assertTrue(fast.baseMs < slow.baseMs)
        assertTrue(fast.floorMs < slow.floorMs)
    }

    @Test
    fun sentenceEndContourDampensTailLiftWhenLandingIsActive() {
        val contour =
            RsvpPunctuationTimingPolicy.resolveBoundaryContour(
                token = punctuation("."),
                prevWord = word("Hello"),
                nextToken = word("Now"),
            )

        assertTrue(contour.landingHoldWeight > 0.0)
        assertTrue(contour.tailLiftWeight > 0.0)
        assertTrue(
            "Expected landing settle to temper the tail lift a little",
            contour.tailLiftWeight < (1.26 * 0.92),
        )
    }

    private fun word(text: String) = Token(text = text, type = TokenType.WORD)

    private fun punctuation(text: String) = Token(text = text, type = TokenType.PUNCTUATION)
}
