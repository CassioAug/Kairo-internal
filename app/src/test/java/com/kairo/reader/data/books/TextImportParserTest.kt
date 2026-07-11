package com.kairo.reader.data.books

import com.kairo.reader.core.model.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextImportParserTest {
    @Test
    fun `plain text keeps paragraphs and escapes generated html`() {
        val parsed =
            TextImportParser.parse(
                TextImportRequest(
                    title = "My note",
                    content = "First paragraph with <unsafe> text.\n\nSecond paragraph here.",
                )
            )

        assertEquals("My note", parsed.title)
        assertEquals(
            "First paragraph with <unsafe> text.\n\nSecond paragraph here.",
            parsed.plainText,
        )
        assertTrue(parsed.htmlContent.contains("&lt;unsafe&gt;"))
        assertFalse(parsed.htmlContent.contains("<unsafe>"))
    }

    @Test
    fun `markdown heading becomes title and formatting becomes readable text`() {
        val parsed =
            TextImportParser.parse(
                TextImportRequest(
                    content =
                    """
                        # A useful article

                        This is **important** and has [a source](https://example.com).

                        - First point
                        - Second `code` point
                    """.trimIndent(),
                )
            )

        assertEquals("A useful article", parsed.title)
        assertEquals(
            "This is important and has a source.\n\nFirst point\nSecond code point",
            parsed.plainText,
        )
        assertTrue(parsed.htmlContent.contains("<ul>"))
        assertFalse(parsed.plainText.contains("https://"))
    }

    @Test
    fun `fenced content is retained without fence markers`() {
        val parsed =
            TextImportParser.parse(
                TextImportRequest(
                    title = "Snippet",
                    content = "Before the example.\n\n```kotlin\nval answer = 42\n```\n\nAfter it.",
                )
            )

        assertTrue(parsed.plainText.contains("val answer = 42"))
        assertFalse(parsed.plainText.contains("```"))
        assertTrue(parsed.htmlContent.contains("<pre>val answer = 42</pre>"))
    }

    @Test
    fun `parsed text creates a single reader chapter`() {
        val parsed =
            TextImportParser.parse(
                TextImportRequest(
                    title = "Shared note",
                    content = "This shared note contains enough words to read comfortably.",
                )
            )

        val book = parsed.toBook(BookId("text-book"))

        assertEquals("text-book", book.id.value)
        assertEquals(1, book.chapters.size)
        assertTrue(book.chapters.single().wordCount > 0)
    }
}
