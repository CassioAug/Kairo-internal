package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WebArticleUrlTest {
    @Test
    fun normalizeAddsHttpsAndStableSlash() {
        assertEquals(
            "https://example.com/story",
            WebArticleUrl.normalize("example.com/story"),
        )
    }

    @Test
    fun normalizeRemovesFragmentForStableImports() {
        assertEquals(
            "https://example.com/story?utm=test",
            WebArticleUrl.normalize("https://Example.com/story?utm=test#comments"),
        )
    }

    @Test
    fun extractFirstWebUrlFromSharedText() {
        assertEquals(
            "https://example.com/story",
            WebArticleUrl.extractFirstWebUrl("Read this: https://example.com/story."),
        )
    }

    @Test
    fun normalizeRejectsNonWebLinks() {
        try {
            WebArticleUrl.normalize("ftp://example.com/story")
            fail("Expected non-web links to be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("http"))
        }
    }
}
