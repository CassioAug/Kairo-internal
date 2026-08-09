package com.kairo.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchResultTest {
    @Test
    fun tokenRangeCoversWholePhraseAndNormalizesSavedEndpoints() {
        assertEquals(4..7, result(start = 4, end = 7).tokenRange)
        assertEquals(4..7, result(start = 7, end = 4).tokenRange)
    }

    private fun result(
        start: Int,
        end: Int,
    ): LibrarySearchResult =
        LibrarySearchResult(
            id = "result",
            kind = LibrarySearchResultKind.SAVED,
            bookId = BookId("book"),
            bookTitle = "Book",
            chapterIndex = 0,
            chapterTitle = null,
            tokenIndex = start,
            endTokenIndex = end,
            title = "Result",
            snippet = "passage",
        )
}
