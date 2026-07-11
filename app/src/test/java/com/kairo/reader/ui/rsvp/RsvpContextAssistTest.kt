package com.kairo.reader.ui.rsvp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpContextAssistTest {
    @Test
    fun guideBandHeightReservesSymmetricSpaceAroundFocusText() {
        val textHeight = 45.dp

        val bandHeight = orpGuideBandHeight(textHeight, guideThickness = 1f)

        assertEquals(79.dp, bandHeight)
    }

    @Test
    fun focusEnvelopeRangeStaysStableWithinEachFrameBlock() {
        assertEquals(0 until 12, resolveContextEnvelopeFrameRange(0, 30, blockSize = 6))
        assertEquals(0 until 12, resolveContextEnvelopeFrameRange(5, 30, blockSize = 6))
        assertEquals(6 until 18, resolveContextEnvelopeFrameRange(6, 30, blockSize = 6))
        assertEquals(6 until 18, resolveContextEnvelopeFrameRange(11, 30, blockSize = 6))
    }

    @Test
    fun peripheralCueFontSizeDoesNotDependOnTheDisplayedWord() {
        assertEquals(48f, stableContextCueFontSizeSp(48f), 0.0001f)
    }

    @Test
    fun cueSlotsStayOutsideTheFocusGapAtExtremeHorizontalPositions() {
        val left =
            resolveContextCueSlots(
                availableWidth = 400.dp,
                focusLeftReserve = 80.dp,
                focusRightReserve = 80.dp,
                horizontalBias = HORIZONTAL_BIAS_MIN,
                minimumCueWidth = 48.dp,
                cueInnerPadding = 8.dp,
            )
        val right =
            resolveContextCueSlots(
                availableWidth = 400.dp,
                focusLeftReserve = 80.dp,
                focusRightReserve = 80.dp,
                horizontalBias = HORIZONTAL_BIAS_MAX,
                minimumCueWidth = 48.dp,
                cueInnerPadding = 8.dp,
            )

        assertEquals(0.dp, left.previousWidth)
        assertFalse(left.hasPreviousRoom)
        assertTrue(left.hasUpcomingRoom)
        assertEquals(0.dp, right.upcomingWidth)
        assertTrue(right.hasPreviousRoom)
        assertFalse(right.hasUpcomingRoom)
        assertEquals(400.dp, left.previousWidth + left.focusGap + left.upcomingWidth)
        assertEquals(400.dp, right.previousWidth + right.focusGap + right.upcomingWidth)
    }

    @Test
    fun cueSlotsIncludeAStableInnerSafetyBuffer() {
        val slots =
            resolveContextCueSlots(
                availableWidth = 400.dp,
                focusLeftReserve = 80.dp,
                focusRightReserve = 80.dp,
                horizontalBias = CENTER_BIAS,
                minimumCueWidth = 48.dp,
                cueInnerPadding = 8.dp,
            )

        assertEquals(112.dp, slots.previousWidth)
        assertEquals(176.dp, slots.focusGap)
        assertEquals(112.dp, slots.upcomingWidth)
    }

    @Test
    fun asymmetricFocusEnvelopeKeepsEachCueCloseToItsOwnTextEdge() {
        val slots =
            resolveContextCueSlots(
                availableWidth = 400.dp,
                focusLeftReserve = 60.dp,
                focusRightReserve = 100.dp,
                horizontalBias = CENTER_BIAS,
                minimumCueWidth = 48.dp,
                cueInnerPadding = 8.dp,
            )

        assertEquals(132.dp, slots.previousWidth)
        assertEquals(176.dp, slots.focusGap)
        assertEquals(92.dp, slots.upcomingWidth)
    }

    @Test
    fun peripheralCueKeepsOnlyTheWordsNearestTheFocusGap() {
        val tokens = listOf(word("one"), word("two"), word("three"), word("four"))

        val previous =
            buildPeripheralContextText(
                tokens = tokens,
                startIndex = 0,
                endExclusive = tokens.size,
                maxWords = 2,
                takeLast = true,
                color = Color.White,
                nearestAlpha = 0.3f,
                farthestAlpha = 0.1f,
            )
        val upcoming =
            buildPeripheralContextText(
                tokens = tokens,
                startIndex = 0,
                endExclusive = tokens.size,
                maxWords = 1,
                takeLast = false,
                color = Color.White,
                nearestAlpha = 0.2f,
                farthestAlpha = 0.1f,
            )

        assertEquals("three four", previous.text)
        assertEquals("one", upcoming.text)
    }

    @Test
    fun fullClauseKeepsClauseStarterAndClosingPunctuation() {
        val tokens =
            listOf(
                word("Earlier"),
                punctuation(","),
                word("because", isClauseBoundary = true),
                word("context"),
                word("matters"),
                punctuation("."),
            )

        val window =
            requireNotNull(
                resolveRsvpContextWindow(
                    tokens = tokens,
                    frameStartIndex = 3,
                    frameEndExclusive = 4,
                    mode = RsvpContextAssistMode.FULL_CLAUSE,
                ),
            )

        assertEquals(2, window.startIndex)
        assertEquals(6, window.endExclusive)
        assertEquals(3, window.focusStartIndex)
    }

    @Test
    fun replayTargetsCurrentClauseThenThePreviousClauseAtItsStart() {
        val tokens =
            listOf(
                word("Read"),
                word("this"),
                punctuation(","),
                word("because", isClauseBoundary = true),
                word("it"),
                word("helps"),
                punctuation("."),
            )

        assertEquals(3, findReplayPhraseStartTokenIndex(tokens, currentTokenIndex = 5))
        assertEquals(0, findReplayPhraseStartTokenIndex(tokens, currentTokenIndex = 3))
    }

    @Test
    fun regressionPacingEasesThenReturnsTowardTheSelectedTempo() {
        val runtime = RsvpRuntimeState()

        registerRsvpRegression(runtime, enabled = true)
        assertEquals(1f + REGRESSION_PACE_STEP, runtime.comprehensionPaceScale, 0.0001f)

        repeat(REGRESSION_RECOVERY_START_FRAMES + 1) {
            recoverRsvpRegressionPace(runtime, enabled = true)
        }
        assertTrue(runtime.comprehensionPaceScale < 1f + REGRESSION_PACE_STEP)
        assertTrue(runtime.comprehensionPaceScale >= 1f)
    }

    private fun word(
        text: String,
        isClauseBoundary: Boolean = false,
    ): Token =
        Token(
            text = text,
            type = TokenType.WORD,
            isClauseBoundary = isClauseBoundary,
        )

    private fun punctuation(text: String): Token = Token(text, TokenType.PUNCTUATION)
}
