package com.kairo.reader.data.books

import com.kairo.reader.core.model.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfParserEngineTest {
    @Test
    fun normalizePdfPageTextJoinsWrappedLinesAndDehyphenatesWords() {
        val normalized =
            PdfParserEngine.normalizePdfPageText(
                "A para-\ngraph wraps across lines.\n\nA second paragraph remains separate.",
            )

        assertEquals("A paragraph wraps across lines.\n\nA second paragraph remains separate.", normalized)
    }

    @Test
    fun splitExtractedPagesPreservesPageBoundariesFromSinglePass() {
        val pages =
            PdfParserEngine.splitExtractedPages(
                extractedText =
                "First page has readable text.\u0000KAIRO_PDF_PAGE\u0000" +
                    "Second page is also readable.\u0000KAIRO_PDF_PAGE\u0000",
                pageCount = 2,
            )

        assertEquals(listOf("First page has readable text.", "Second page is also readable."), pages)
    }

    @Test
    fun parseExtractsTextAndMetadataFromPdf() {
        val book =
            PdfParserEngine.buildBook(
                request = request(),
                extracted =
                ExtractedPdfDocument(
                    title = "PDF Research",
                    author = "Ada Lovelace",
                    pages = listOf("This PDF contains enough readable words for a complete import."),
                ),
            )

        assertEquals("PDF Research", book.title)
        assertEquals(listOf("Ada Lovelace"), book.authors)
        assertEquals(1, book.chapters.size)
        assertTrue(book.chapters.single().plainText.contains("enough readable words"))
    }

    @Test
    fun parseRejectsPdfWithoutSelectableText() {
        val error =
            runCatching {
                PdfParserEngine.buildBook(
                    request = request(),
                    extracted = ExtractedPdfDocument(title = null, author = null, pages = listOf("")),
                )
            }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("Scanned PDFs require OCR"))
    }

    private fun request() =
        PdfBookParseRequest(
            bookId = BookId("pdf-test"),
            sourceDisplayName = "fallback.pdf",
        )
}
