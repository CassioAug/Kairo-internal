package com.example.kairo.core.rsvp

import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
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
