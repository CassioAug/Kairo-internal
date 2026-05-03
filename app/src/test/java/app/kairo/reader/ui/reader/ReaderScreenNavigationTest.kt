package app.kairo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScreenNavigationTest {
    @Test
    fun nextPage_advancesWithinCurrentChapterBeforeChangingChapter() {
        var focusedIndex: Int? = null
        var chapterChange: Pair<Int, Int?>? = null
        val pages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 9, wordCount = 10),
                ChapterPage(index = 1, startTokenIndex = 10, endTokenIndex = 19, wordCount = 10),
            )

        val navigation =
            buildReaderNavigationState(
                pages = pages,
                isPagedChapter = true,
                resolvedPageIndex = 0,
                chapterIndex = 2,
                lastChapterIndex = 4,
                onFocusChange = { focusedIndex = it },
                onChapterChange = { chapter, focus -> chapterChange = chapter to focus },
            )

        navigation.onNextPage()

        assertEquals(10, focusedIndex)
        assertEquals(null, chapterChange)
    }

    @Test
    fun nextPage_changesChapterAtEndOfPagedChapter() {
        var chapterChange: Pair<Int, Int?>? = null
        val pages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 9, wordCount = 10),
                ChapterPage(index = 1, startTokenIndex = 10, endTokenIndex = 19, wordCount = 10),
            )

        val navigation =
            buildReaderNavigationState(
                pages = pages,
                isPagedChapter = true,
                resolvedPageIndex = 1,
                chapterIndex = 2,
                lastChapterIndex = 4,
                onFocusChange = {},
                onChapterChange = { chapter, focus -> chapterChange = chapter to focus },
            )

        navigation.onNextPage()

        assertEquals(3 to 0, chapterChange)
    }
}
