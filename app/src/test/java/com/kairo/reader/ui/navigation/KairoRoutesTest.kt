package com.kairo.reader.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class KairoRoutesTest {
    @Test
    fun reader_buildsBaseReaderRoute() {
        assertEquals("reader/book-123", KairoRoutes.reader("book-123"))
    }

    @Test
    fun reader_buildsPositionedReaderRoute() {
        assertEquals(
            "reader/book-123/4/56",
            KairoRoutes.reader(
                bookId = "book-123",
                chapterIndex = 4,
                tokenIndex = 56,
            ),
        )
    }

    @Test
    fun rsvp_usesPositiveTempoWhenProvided() {
        assertEquals(
            "rsvp/book-123/2/34?tempoMs=118",
            KairoRoutes.rsvp(
                bookId = "book-123",
                chapterIndex = 2,
                tokenIndex = 34,
                tempoMsPerWord = 118L,
            ),
        )
    }

    @Test
    fun rsvp_usesSentinelTempoWhenMissingOrInvalid() {
        assertEquals(
            "rsvp/book-123/2/34?tempoMs=-1",
            KairoRoutes.rsvp(
                bookId = "book-123",
                chapterIndex = 2,
                tokenIndex = 34,
            ),
        )
        assertEquals(
            "rsvp/book-123/2/34?tempoMs=-1",
            KairoRoutes.rsvp(
                bookId = "book-123",
                chapterIndex = 2,
                tokenIndex = 34,
                tempoMsPerWord = 0L,
            ),
        )
    }

    @Test
    fun libraryBookmarks_buildsBookmarksTabRoute() {
        assertEquals("library?tab=bookmarks", KairoRoutes.libraryBookmarks())
    }
}
