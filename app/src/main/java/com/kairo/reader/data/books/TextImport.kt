package com.kairo.reader.data.books

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords

data class TextImportRequest(val content: String, val title: String? = null,)

data class SharedTextImport(val content: String, val title: String? = null,)

internal data class ParsedTextImport(val title: String, val plainText: String, val htmlContent: String,) {
    fun toBook(bookId: BookId): Book =
        Book(
            id = bookId,
            title = title,
            authors = emptyList(),
            chapters =
            listOf(
                Chapter(
                    index = 0,
                    title = null,
                    htmlContent = htmlContent,
                    plainText = plainText,
                    wordCount = countWords(plainText),
                )
            ),
        )
}

/** Converts pasted plain text or lightweight Markdown into reader-safe chapter content. */
internal object TextImportParser {
    fun parse(request: TextImportRequest): ParsedTextImport {
        val normalizedSource =
            request.content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim()
        require(normalizedSource.isNotBlank()) { "Add some text before importing" }

        val lines = stripFrontMatter(normalizedSource.lines())
        val explicitHeading =
            lines.firstNotNullOfOrNull { line ->
                MARKDOWN_HEADING.matchEntire(line.trim())
                    ?.groupValues
                    ?.get(2)
                    ?.let(::cleanInlineMarkdown)
                    ?.takeIf(String::isNotBlank)
            }
        val blocks = parseBlocks(lines, explicitHeading)
        val plainText =
            blocks
                .joinToString(separator = "\n\n") { block -> block.plainText }
                .replace(EXCESS_BLANK_LINES, "\n\n")
                .trim()
        require(plainText.isNotBlank()) { "No readable text found" }

        val title =
            request.title
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: explicitHeading
                ?: derivePlainTextTitle(plainText)
                ?: DEFAULT_TITLE

        return ParsedTextImport(
            title = title.take(MAX_TITLE_LENGTH).trim(),
            plainText = plainText,
            htmlContent = blocks.joinToString(separator = "\n") { block -> block.html },
        )
    }

    private fun parseBlocks(
        lines: List<String>,
        titleHeading: String?,
    ): List<TextBlock> {
        val blocks = mutableListOf<TextBlock>()
        val paragraph = mutableListOf<String>()
        val listItems = mutableListOf<String>()
        var listOrdered = false
        var inCodeFence = false
        val codeLines = mutableListOf<String>()
        var skippedTitleHeading = false

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            val text = cleanInlineMarkdown(paragraph.joinToString(" "))
            if (text.isNotBlank()) {
                blocks += TextBlock(text, "<p>${escapeHtml(text)}</p>")
            }
            paragraph.clear()
        }

        fun flushList() {
            if (listItems.isEmpty()) return
            val tag = if (listOrdered) "ol" else "ul"
            val plain = listItems.joinToString("\n")
            val html =
                listItems.joinToString(
                    prefix = "<$tag>",
                    postfix = "</$tag>",
                    separator = "",
                ) { item -> "<li>${escapeHtml(item)}</li>" }
            blocks += TextBlock(plain, html)
            listItems.clear()
        }

        fun flushCode() {
            if (codeLines.isEmpty()) return
            val text = codeLines.joinToString("\n").trim()
            if (text.isNotBlank()) {
                blocks += TextBlock(text, "<pre>${escapeHtml(text)}</pre>")
            }
            codeLines.clear()
        }

        lines.forEach { sourceLine ->
            val trimmed = sourceLine.trim()
            if (FENCE.matches(trimmed)) {
                flushParagraph()
                flushList()
                if (inCodeFence) flushCode()
                inCodeFence = !inCodeFence
                return@forEach
            }
            if (inCodeFence) {
                codeLines += sourceLine
                return@forEach
            }
            if (trimmed.isBlank()) {
                flushParagraph()
                flushList()
                return@forEach
            }

            MARKDOWN_HEADING.matchEntire(trimmed)?.let { match ->
                flushParagraph()
                flushList()
                val level = match.groupValues[1].length.coerceIn(1, 6)
                val text = cleanInlineMarkdown(match.groupValues[2])
                if (!skippedTitleHeading && titleHeading != null && text == titleHeading) {
                    skippedTitleHeading = true
                } else if (text.isNotBlank()) {
                    blocks += TextBlock(text, "<h$level>${escapeHtml(text)}</h$level>")
                }
                return@forEach
            }

            LIST_ITEM.matchEntire(trimmed)?.let { match ->
                flushParagraph()
                val ordered = match.groupValues[1].firstOrNull()?.isDigit() == true
                if (listItems.isNotEmpty() && ordered != listOrdered) flushList()
                listOrdered = ordered
                cleanInlineMarkdown(match.groupValues[2])
                    .takeIf(String::isNotBlank)
                    ?.let(listItems::add)
                return@forEach
            }

            if (trimmed.startsWith('>')) {
                flushParagraph()
                flushList()
                val text = cleanInlineMarkdown(trimmed.trimStart('>', ' '))
                if (text.isNotBlank()) {
                    blocks += TextBlock(text, "<blockquote>${escapeHtml(text)}</blockquote>")
                }
                return@forEach
            }

            if (HORIZONTAL_RULE.matches(trimmed)) {
                flushParagraph()
                flushList()
                return@forEach
            }
            paragraph += trimmed
        }

        flushParagraph()
        flushList()
        flushCode()
        return blocks
    }

    private fun stripFrontMatter(lines: List<String>): List<String> {
        if (lines.firstOrNull()?.trim() != "---") return lines
        val closingIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
        return if (closingIndex >= 0) lines.drop(closingIndex + 2) else lines
    }

    private fun derivePlainTextTitle(plainText: String): String? {
        val firstLine = plainText.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank() || firstLine.length > MAX_DERIVED_TITLE_LENGTH) return null
        if (firstLine.split(WHITESPACE).size > MAX_DERIVED_TITLE_WORDS) return null
        return firstLine.trimEnd('.', ':', ';', '!', '?').takeIf(String::isNotBlank)
    }

    private fun cleanInlineMarkdown(value: String): String =
        value
            .replace(MARKDOWN_IMAGE) { match -> match.groupValues[1] }
            .replace(MARKDOWN_LINK) { match -> match.groupValues[1] }
            .replace(AUTOLINK) { match -> match.groupValues[1] }
            .replace(INLINE_CODE) { match -> match.groupValues[1] }
            .replace(HTML_TAG) { match ->
                if (match.groupValues[1].lowercase() in SUPPORTED_HTML_TAGS) {
                    ""
                } else {
                    match.value
                }
            }
            .replace(STRIKETHROUGH) { match -> match.groupValues[1] }
            .replace(EMPHASIS_MARKERS, "")
            .replace(ESCAPED_MARKDOWN) { match -> match.groupValues[1] }
            .replace(WHITESPACE, " ")
            .trim()

    private fun escapeHtml(value: String): String =
        buildString(value.length) {
            value.forEach { character ->
                append(
                    when (character) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&#39;"
                        else -> character
                    }
                )
            }
        }

    private data class TextBlock(val plainText: String, val html: String)

    private const val DEFAULT_TITLE = "Imported text"
    private const val MAX_TITLE_LENGTH = 120
    private const val MAX_DERIVED_TITLE_LENGTH = 72
    private const val MAX_DERIVED_TITLE_WORDS = 10
    private val MARKDOWN_HEADING = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
    private val LIST_ITEM = Regex("^(?:([-+*]|\\d+[.)]))\\s+(.+)$")
    private val FENCE = Regex("^(```|~~~).*$")
    private val HORIZONTAL_RULE = Regex("^([-*_])(?:\\s*\\1){2,}\\s*$")
    private val MARKDOWN_IMAGE = Regex("!\\[([^]]*)]\\([^)]*\\)")
    private val MARKDOWN_LINK = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val AUTOLINK = Regex("<(https?://[^>]+)>")
    private val INLINE_CODE = Regex("`([^`]+)`")
    private val STRIKETHROUGH = Regex("~~(.+?)~~")
    private val EMPHASIS_MARKERS = Regex("(?<!\\w)[*_]{1,3}|[*_]{1,3}(?!\\w)")
    private val ESCAPED_MARKDOWN = Regex("\\\\([\\\\`*_{}\\[\\]()#+.!>-])")
    private val HTML_TAG = Regex("</?([A-Za-z][A-Za-z0-9]*)[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private val EXCESS_BLANK_LINES = Regex("\\n{3,}")
    private val SUPPORTED_HTML_TAGS =
        setOf(
            "a",
            "article",
            "aside",
            "b",
            "blockquote",
            "br",
            "code",
            "del",
            "div",
            "em",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "i",
            "li",
            "main",
            "ol",
            "p",
            "pre",
            "section",
            "span",
            "strong",
            "ul",
        )
}
