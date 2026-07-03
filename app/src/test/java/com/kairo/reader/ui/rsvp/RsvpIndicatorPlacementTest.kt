package com.kairo.reader.ui.rsvp

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpIndicatorPlacementTest {

    @Test
    fun keepsDefaultPositionWhenWordBandIsClear() {
        val padding =
            resolveIndicatorTopPadding(
                defaultTopPadding = TEMPO_INDICATOR_TOP_PADDING,
                flippedStackOffset = 0.dp,
                wordBandTop = 300.dp,
                wordBandBottom = 380.dp,
            )

        assertEquals(TEMPO_INDICATOR_TOP_PADDING, padding)
    }

    @Test
    fun flipsBelowWordWhenDefaultPositionWouldOverlap() {
        val padding =
            resolveIndicatorTopPadding(
                defaultTopPadding = TEMPO_INDICATOR_TOP_PADDING,
                flippedStackOffset = 0.dp,
                wordBandTop = 40.dp,
                wordBandBottom = 120.dp,
            )

        assertEquals(120.dp + INDICATOR_WORD_CLEARANCE, padding)
    }

    @Test
    fun preservesStackStaggerWhenBothPillsFlip() {
        val tempo =
            resolveIndicatorTopPadding(
                defaultTopPadding = TEMPO_INDICATOR_TOP_PADDING,
                flippedStackOffset = 0.dp,
                wordBandTop = 40.dp,
                wordBandBottom = 120.dp,
            )
        val fontSize =
            resolveIndicatorTopPadding(
                defaultTopPadding = FONT_SIZE_INDICATOR_TOP_PADDING,
                flippedStackOffset = FONT_SIZE_INDICATOR_TOP_PADDING - TEMPO_INDICATOR_TOP_PADDING,
                wordBandTop = 40.dp,
                wordBandBottom = 120.dp,
            )

        assertEquals(FONT_SIZE_INDICATOR_TOP_PADDING - TEMPO_INDICATOR_TOP_PADDING, fontSize - tempo)
    }
}
