package com.kairo.reader.ui.reader

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderInlineHighlightsTest {
    @Test
    fun adjacentTokenRangesMergeIntoOneContinuousHighlight() {
        val highlights = mutableListOf<ReaderInlineHighlightRange>()

        highlights.addOrExtendInlineHighlight("saved:note", start = 0, endExclusive = 4, color = Color.Yellow)
        highlights.addOrExtendInlineHighlight("saved:note", start = 4, endExclusive = 8, color = Color.Yellow)

        assertEquals(
            listOf(ReaderInlineHighlightRange("saved:note", 0, 8, Color.Yellow)),
            highlights,
        )
    }

    @Test
    fun separateAnnotationsRemainSeparateHighlights() {
        val highlights = mutableListOf<ReaderInlineHighlightRange>()

        highlights.addOrExtendInlineHighlight("saved:first", start = 0, endExclusive = 4, color = Color.Yellow)
        highlights.addOrExtendInlineHighlight("saved:second", start = 4, endExclusive = 8, color = Color.Yellow)

        assertEquals(2, highlights.size)
    }
}
