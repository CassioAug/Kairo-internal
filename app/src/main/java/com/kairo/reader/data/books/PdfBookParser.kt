package com.kairo.reader.data.books

import android.content.Context
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException

internal class PdfBookParser(dispatcherProvider: DispatcherProvider) :
    BinaryBookParser(
        dispatcherProvider = dispatcherProvider,
        supportedExtensions = setOf(PDF_EXTENSION),
        maxFileSizeBytes = MAX_FILE_SIZE_BYTES,
    ) {
    override fun parseSource(
        context: Context,
        request: BinaryBookParseRequest,
    ): Book {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PdfParserEngine.parse(request)
    }

    private companion object {
        private const val PDF_EXTENSION = "pdf"
        private const val MAX_FILE_SIZE_BYTES = 96L * 1024L * 1024L
    }
}

internal data class ExtractedPdfDocument(val title: String?, val author: String?, val pages: List<String>,)

internal object PdfParserEngine {
    fun parse(request: BinaryBookParseRequest): Book =
        try {
            PDDocument.load(request.bytes).use { document ->
                require(!document.isEncrypted) { "Password-protected or encrypted PDFs are not supported" }
                val pageCount = document.numberOfPages
                require(pageCount in 1..MAX_PAGES) { "PDF page count is outside the supported range" }

                val pages = extractPages(document)
                buildBook(
                    request = request,
                    extracted =
                    ExtractedPdfDocument(
                        title = document.documentInformation?.title,
                        author = document.documentInformation?.author,
                        pages = pages,
                    ),
                )
            }
        } catch (error: InvalidPasswordException) {
            throw IllegalArgumentException("Password-protected or encrypted PDFs are not supported", error)
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: IOException) {
            throw IllegalArgumentException("Unable to parse PDF document", error)
        }

    internal fun buildBook(
        request: BinaryBookParseRequest,
        extracted: ExtractedPdfDocument,
    ): Book {
        val readableWordCount = extracted.pages.sumOf(::countWords)
        require(readableWordCount >= MIN_READABLE_WORDS) {
            "No selectable text was found. Scanned PDFs require OCR and are not supported yet."
        }
        require(extracted.pages.sumOf { page -> page.length.toLong() } <= MAX_EXTRACTED_TEXT_CHARS) {
            "PDF contains too much extracted text"
        }
        val title =
            extracted.title?.normalizePdfMetadata()?.takeIf(String::isNotBlank)
                ?: request.sourceDisplayName.toPdfFilenameTitle()
        val authors =
            extracted.author
                ?.split(AUTHOR_SEPARATOR)
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.distinct()
                .orEmpty()
        return Book(
            id = request.bookId,
            title = title,
            authors = authors,
            chapters = buildChapters(extracted.pages),
        )
    }

    private fun extractPages(document: PDDocument): List<String> {
        val stripper =
            PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
                pageStart = ""
                pageEnd = ""
            }
        return (1..document.numberOfPages).map { pageNumber ->
            stripper.startPage = pageNumber
            stripper.endPage = pageNumber
            normalizePdfPageText(stripper.getText(document))
        }
    }

    internal fun normalizePdfPageText(rawText: String): String {
        val lines =
            rawText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "")
                .lines()
                .map { line -> line.replace(HORIZONTAL_WHITESPACE, " ").trim() }
        val paragraphs = mutableListOf<String>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            paragraph.toString().trim().takeIf(String::isNotBlank)?.let(paragraphs::add)
            paragraph.clear()
        }

        lines.forEach { line ->
            if (line.isBlank()) {
                flushParagraph()
            } else if (paragraph.isEmpty()) {
                paragraph.append(line)
            } else if (paragraph.last() == '-' && line.firstOrNull()?.isLowerCase() == true) {
                paragraph.deleteCharAt(paragraph.lastIndex)
                paragraph.append(line)
            } else {
                paragraph.append(' ')
                paragraph.append(line)
            }
        }
        flushParagraph()
        return paragraphs.joinToString("\n\n")
    }

    private fun buildChapters(pages: List<String>): List<Chapter> =
        pages.chunked(PAGES_PER_CHAPTER).mapIndexedNotNull { chapterIndex, pageGroup ->
            val firstPage = chapterIndex * PAGES_PER_CHAPTER + 1
            val lastPage = firstPage + pageGroup.lastIndex
            val readablePages = pageGroup.filter(String::isNotBlank)
            if (readablePages.isEmpty()) return@mapIndexedNotNull null
            val plainText = readablePages.joinToString(PAGE_BREAK_MARKER.toString())
            val html =
                readablePages.mapIndexed { index, page ->
                    val pageHtml =
                        page.split(PARAGRAPH_SEPARATOR)
                            .filter(String::isNotBlank)
                            .joinToString("\n") { paragraph -> "<p>${paragraph.escapeBookHtml()}</p>" }
                    if (index == readablePages.lastIndex) {
                        pageHtml
                    } else {
                        "$pageHtml\n<span epub:type=\"pagebreak\"></span>"
                    }
                }.joinToString("\n")
            Chapter(
                index = chapterIndex,
                title = if (firstPage == lastPage) "Page $firstPage" else "Pages $firstPage–$lastPage",
                htmlContent = html,
                plainText = plainText,
                wordCount = countWords(plainText),
            )
        }.mapIndexed { index, chapter -> chapter.copy(index = index) }

    private fun String.normalizePdfMetadata(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.toPdfFilenameTitle(): String =
        substringBeforeLast('.', this)
            .replace('_', ' ')
            .replace('-', ' ')
            .normalizePdfMetadata()
            .ifBlank { DEFAULT_TITLE }

    private const val DEFAULT_TITLE = "PDF import"
    private const val MAX_PAGES = 5000
    private const val PAGES_PER_CHAPTER = 10
    private const val MIN_READABLE_WORDS = 5
    private const val MAX_EXTRACTED_TEXT_CHARS = 16L * 1024L * 1024L
    private const val PAGE_BREAK_MARKER = '\u000C'
    private val HORIZONTAL_WHITESPACE = Regex("[\\t\\x0B\\f ]+")
    private val PARAGRAPH_SEPARATOR = Regex("\\n{2,}")
    private val AUTHOR_SEPARATOR = Regex("\\s*(?:;|,|\\band\\b)\\s*", RegexOption.IGNORE_CASE)
}
