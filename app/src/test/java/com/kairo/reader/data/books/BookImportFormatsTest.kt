package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookImportFormatsTest {
    @Test
    fun displayNameRecognizesCompoundFb2ZipBeforeZip() {
        assertEquals(listOf("fb2.zip", "zip"), BookImportFormats.extensionsForDisplayName("Novel.FB2.ZIP"))
    }

    @Test
    fun mimeTypesResolveAllSupportedFormatFamilies() {
        val expected =
            mapOf(
                "application/epub+zip" to "epub",
                "application/x-mobipocket-ebook" to "mobi",
                "application/x-palm-database" to "prc",
                "application/vnd.amazon.ebook" to "azw",
                "text/plain" to "txt",
                "text/markdown" to "md",
                "text/html" to "html",
                "application/x-fictionbook+xml" to "fb2",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
                "application/pdf" to "pdf",
            )

        expected.forEach { (mime, extension) ->
            assertEquals(extension, BookImportFormats.extensionForMimeType(mime))
        }
    }

    @Test
    fun pickerIncludesFallbackForProvidersWithGenericMimeTypes() {
        assertTrue(BookImportFormats.pickerMimeTypes.contains("application/octet-stream"))
        assertTrue(BookImportFormats.pickerMimeTypes.contains("*/*"))
    }

    @Test
    fun supportedFormatsExposeEveryParserExtensionToTheUi() {
        val visibleExtensions = BookImportFormats.supportedFormats.flatMap(BookImportFormat::extensions).toSet()

        assertEquals(
            setOf("epub", "mobi", "prc", "azw", "fb2", "fb2.zip", "pdf", "docx", "txt", "md", "markdown", "html", "htm"),
            visibleExtensions,
        )
    }

    @Test
    fun supportedFormatsAreGroupedForDiscovery() {
        assertEquals(
            listOf("EPUB", "MOBI", "PRC", "AZW", "FB2 / FB2.ZIP"),
            BookImportFormats.formatsIn(BookImportFormatCategory.EBOOK).map(BookImportFormat::displayName),
        )
        assertEquals(
            listOf("PDF", "DOCX"),
            BookImportFormats.formatsIn(BookImportFormatCategory.DOCUMENT).map(BookImportFormat::displayName),
        )
        assertEquals(
            listOf("TXT", "Markdown", "HTML"),
            BookImportFormats.formatsIn(BookImportFormatCategory.TEXT).map(BookImportFormat::displayName),
        )
    }
}
