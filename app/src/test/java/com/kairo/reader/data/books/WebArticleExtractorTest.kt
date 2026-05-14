package com.kairo.reader.data.books

import com.kairo.reader.core.model.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebArticleExtractorTest {
    private val extractor = WebArticleExtractor(TestDispatchers)

    @Test
    fun parseHtmlExtractsReadableArticleText() {
        val book =
            extractor.parseHtml(
                html =
                    """
                    <html lang="en">
                      <head>
                        <title>Browser title</title>
                        <meta property="og:title" content="A Clear Piece | example.com">
                        <meta name="author" content="Ada Writer">
                      </head>
                      <body>
                        <nav>Home Pricing Subscribe</nav>
                        <article>
                          <h1>A Clear Piece</h1>
                          <p>This is the first useful paragraph of the article, with enough text to read.</p>
                          <p>This is the second useful paragraph, kept separate so RSVP gets a clean break.</p>
                        </article>
                        <footer>All rights reserved</footer>
                      </body>
                    </html>
                    """.trimIndent(),
                normalizedUrl = "https://example.com/story",
                bookId = BookId("article"),
            )

        val chapter = book.chapters.single()
        assertEquals("A Clear Piece", book.title)
        assertEquals(listOf("Ada Writer"), book.authors)
        assertEquals("en", book.languageTag)
        assertTrue(chapter.plainText.contains("first useful paragraph"))
        assertTrue(chapter.plainText.contains("\n\nThis is the second useful paragraph"))
        assertFalse(chapter.plainText.contains("Home Pricing"))
        assertFalse(chapter.plainText.startsWith("A Clear Piece"))
        assertTrue(chapter.wordCount > 0)
    }

    @Test
    fun parseHtmlFallsBackToHostAuthor() {
        val book =
            extractor.parseHtml(
                html =
                    """
                    <html>
                      <body>
                        <main>
                          <p>Readable article text appears here with enough words for import.</p>
                          <p>Another paragraph gives the extractor a useful body to keep.</p>
                        </main>
                      </body>
                    </html>
                    """.trimIndent(),
                normalizedUrl = "https://news.example.com/post",
                bookId = BookId("article"),
            )

        assertEquals(listOf("news.example.com"), book.authors)
    }
}
