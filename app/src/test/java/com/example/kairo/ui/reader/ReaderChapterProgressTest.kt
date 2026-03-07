package com.example.kairo.ui.reader

import com.example.kairo.core.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChapterProgressTest {
    @Test
    fun usesVisibleChapterNumbersWhenTitlesAreNumbered() {
        val chapters =
            listOf(
                chapter(index = 0, title = "Cover"),
                chapter(index = 1, title = "Contents"),
                chapter(index = 2, title = "1"),
                chapter(index = 3, title = "2"),
                chapter(index = 4, title = "3"),
            )

        val progress = resolveReaderChapterProgress(chapters, chapterIndex = 2)

        assertEquals(1, progress.currentNumber)
        assertEquals(3, progress.totalNumber)
    }

    @Test
    fun fallsBackToSequenceWhenTitlesAreNotNumbered() {
        val chapters =
            listOf(
                chapter(index = 0, title = "Arrival"),
                chapter(index = 1, title = "Departure"),
            )

        val progress = resolveReaderChapterProgress(chapters, chapterIndex = 1)

        assertEquals(2, progress.currentNumber)
        assertEquals(2, progress.totalNumber)
    }

    private fun chapter(
        index: Int,
        title: String?,
    ) = Chapter(
        index = index,
        title = title,
        htmlContent = "",
        plainText = "",
    )
}
