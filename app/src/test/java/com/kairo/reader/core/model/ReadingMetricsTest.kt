package com.kairo.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingMetricsTest {
    @Test
    fun countWordsHandlesCommonApostrophesAndUnicodeLetters() {
        val text = "It's Alice's cafe. L'etranger was here. 東京 2026"

        assertEquals(8, countWords(text))
    }

    @Test
    fun countWordsDoesNotCountStandaloneApostrophes() {
        val text = "' Hello ' world '"

        assertEquals(2, countWords(text))
    }
}
