package com.kairo.reader.data.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSessionWordsTest {
    @Test
    fun estimatesAcrossChapterBoundaries() {
        val words =
            estimateWordsRead(
                bookWordCounts = listOf(100, 200, 300),
                startChapterIndex = 0,
                startWordIndex = 80,
                endChapterIndex = 2,
                endWordIndex = 20,
            )

        assertEquals(240, words)
    }
}
