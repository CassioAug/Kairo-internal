package com.kairo.reader.data.books

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFingerprintTest {
    @Test
    fun `text fingerprint ignores line ending differences`() {
        assertEquals(
            ImportFingerprint.textFingerprint("First line\nSecond line"),
            ImportFingerprint.textFingerprint("First line\r\nSecond line"),
        )
    }

    @Test
    fun sourceFingerprintIsStableForSameFileBytes() {
        val first =
            ImportFingerprint.sourceFingerprint(
                extension = "EPUB",
                input = ByteArrayInputStream("same book".toByteArray()),
            )
        val second =
            ImportFingerprint.sourceFingerprint(
                extension = "epub",
                input = ByteArrayInputStream("same book".toByteArray()),
            )

        assertEquals(first, second)
        assertTrue(first.startsWith("source:epub:"))
    }

    @Test
    fun stableBookIdIsDerivedFromFingerprint() {
        val first = ImportFingerprint.bookIdForFingerprint("source:epub:abc")
        val second = ImportFingerprint.bookIdForFingerprint("source:epub:abc")
        val other = ImportFingerprint.bookIdForFingerprint("source:epub:def")

        assertEquals(first, second)
        assertNotEquals(first, other)
        assertTrue(first.value.startsWith("imported-"))
    }

    @Test
    fun contentFingerprintMatchesSameParsedBookContent() {
        val first =
            book(
                title = " Example Book ",
                authors = listOf("Ada"),
                chapterText = "First line.\r\nSecond line.",
            )
        val second =
            book(
                title = "example   book",
                authors = listOf("Ada"),
                chapterText = "First line.\nSecond line.",
            )
        val changed =
            book(
                title = "Example Book",
                authors = listOf("Ada"),
                chapterText = "Different text.",
            )

        assertEquals(
            ImportFingerprint.contentFingerprint(first),
            ImportFingerprint.contentFingerprint(second),
        )
        assertNotEquals(
            ImportFingerprint.contentFingerprint(first),
            ImportFingerprint.contentFingerprint(changed),
        )
    }

    private fun book(
        title: String,
        authors: List<String>,
        chapterText: String,
    ): Book =
        Book(
            id = BookId("book"),
            title = title,
            authors = authors,
            chapters =
                listOf(
                    Chapter(
                        index = 0,
                        title = "Chapter",
                        htmlContent = "<p>$chapterText</p>",
                        plainText = chapterText,
                    ),
                ),
        )
}
