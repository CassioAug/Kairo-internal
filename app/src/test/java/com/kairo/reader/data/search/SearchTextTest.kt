package com.kairo.reader.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {
    @Test
    fun findsRepeatedMatchesCaseInsensitivelyWithinLimit() {
        val offsets = findSearchMatchOffsets("Flow then flow then FLOW", "flow", limit = 2)

        assertEquals(listOf(0, 10), offsets)
    }

    @Test
    fun snippetAddsEllipsesAndNormalizesWhitespace() {
        val text = "Before words\n\nThe searched phrase appears after words"

        val snippet =
            buildSearchSnippet(
                text = text,
                matchOffset = text.indexOf("searched"),
                matchLength = "searched".length,
                contextCharacters = 5,
            )

        assertEquals("…The searched phra…", snippet)
    }

    @Test
    fun sqlLikePatternEscapesWildcardCharacters() {
        assertEquals("%100\\%\\_done%", "100%_done".toSqlLikePattern())
    }
}
