package com.kairo.reader.data.books

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import java.util.Locale
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class WebArticleExtractor(private val dispatcherProvider: DispatcherProvider) {
    suspend fun extract(
        normalizedUrl: String,
        bookId: BookId,
    ): Book =
        withContext(dispatcherProvider.io) {
            val document =
                Jsoup
                    .connect(normalizedUrl)
                    .userAgent(USER_AGENT)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE_BYTES)
                    .followRedirects(true)
                    .get()
            parseDocument(document, normalizedUrl, bookId)
        }

    internal fun parseHtml(
        html: String,
        normalizedUrl: String,
        bookId: BookId,
    ): Book = parseDocument(Jsoup.parse(html, normalizedUrl), normalizedUrl, bookId)

    private fun parseDocument(
        document: Document,
        normalizedUrl: String,
        bookId: BookId,
    ): Book {
        val host = WebArticleUrl.displayHost(normalizedUrl)
        val title = document.articleTitle(host)
        document.select(REMOVABLE_SELECTOR).remove()

        val plainText = document.readablePlainText().stripLeadingDuplicateTitle(title)
        require(plainText.length >= MIN_ARTICLE_CHARS) {
            "Could not extract readable article text from this link."
        }

        val author = document.articleAuthor() ?: host
        val languageTag = document.selectFirst("html")?.attr("lang")?.takeIf { it.isNotBlank() }
        val htmlContent = plainText.toSimpleHtml()
        return Book(
            id = bookId,
            title = title,
            authors = listOf(author),
            languageTag = languageTag,
            chapters =
                listOf(
                    Chapter(
                        index = 0,
                        title = title,
                        htmlContent = htmlContent,
                        plainText = plainText,
                        wordCount = countWords(plainText),
                    ),
                ),
        )
    }

    private fun Document.articleTitle(host: String): String {
        val title =
            metaContent(
                "meta[property=og:title]",
                "meta[name=twitter:title]",
            )
                ?: selectFirst("h1")?.text()
                ?: title()
                ?: host
        return title
            .normalizeText()
            .removeSiteSuffix(host)
            .ifBlank { host }
    }

    private fun Document.articleAuthor(): String? =
        metaContent(
            "meta[name=author]",
            "meta[property=article:author]",
            "meta[name=byl]",
        )
            ?.normalizeText()
            ?.takeIf { it.isNotBlank() }

    private fun Document.metaContent(vararg selectors: String): String? =
        selectors
            .asSequence()
            .mapNotNull { selector ->
                selectFirst(selector)
                    ?.attr("content")
                    ?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()

    private fun Document.readablePlainText(): String {
        val candidates =
            select(CANDIDATE_SELECTOR)
                .asSequence()
                .distinct()
                .toList()
                .ifEmpty { listOfNotNull(body()) }
        val best = candidates.maxByOrNull { it.readabilityScore() } ?: body()
        val extracted = best?.extractBlockText().orEmpty()
        if (extracted.length >= MIN_ARTICLE_CHARS) return extracted

        return body()
            ?.extractBlockText()
            ?.takeIf { it.length >= MIN_ARTICLE_CHARS }
            ?: body()?.text()?.normalizeText().orEmpty()
    }

    private fun Element.readabilityScore(): Int {
        val paragraphs = select("p")
        val paragraphLength = paragraphs.sumOf { it.text().normalizeText().length }
        val paragraphCount = paragraphs.size
        val linkLength = select("a").sumOf { it.text().normalizeText().length }
        val headingBonus = select("h1, h2").size * HEADING_SCORE
        return paragraphLength + paragraphCount * PARAGRAPH_SCORE + headingBonus - linkLength
    }

    private fun Element.extractBlockText(): String {
        val seen = mutableSetOf<String>()
        val blocks =
            select(BLOCK_SELECTOR)
                .asSequence()
                .map { block -> block.text().normalizeText() }
                .filter { text -> text.length >= MIN_BLOCK_CHARS || text.endsLikeHeading() }
                .filterNot { text -> text.looksLikeBoilerplate() }
                .filter { text ->
                    val key = text.lowercase(Locale.ROOT)
                    if (seen.contains(key)) {
                        false
                    } else {
                        seen += key
                        true
                    }
                }
                .toList()

        return blocks.joinToString("\n\n")
    }

    private fun String.toSimpleHtml(): String =
        split(PARAGRAPH_SPLIT)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n") { "<p>${it.escapeHtml()}</p>" }

    private fun String.stripLeadingDuplicateTitle(title: String): String {
        val parts = split(PARAGRAPH_SPLIT, limit = 2)
        val firstBlock = parts.firstOrNull()?.normalizeText().orEmpty()
        if (!firstBlock.equals(title.normalizeText(), ignoreCase = true)) return this
        return parts.getOrNull(1)?.trim().orEmpty()
    }

    private fun String.normalizeText(): String =
        replace(NBSP, ' ')
            .replace(TEXT_WHITESPACE, " ")
            .trim()

    private fun String.removeSiteSuffix(host: String): String {
        val site = host.removePrefix("www.")
        return TITLE_SEPARATORS.fold(this) { current, separator ->
            current
                .removeSuffix("$separator $site")
                .removeSuffix("$separator ${site.substringBefore('.')}")
                .trim()
        }
    }

    private fun String.endsLikeHeading(): Boolean =
        length >= MIN_HEADING_CHARS && !contains(".") && !contains("?") && !contains("!")

    private fun String.looksLikeBoilerplate(): Boolean {
        val normalized = lowercase(Locale.ROOT)
        return BOILERPLATE_MARKERS.any { marker -> normalized.contains(marker) }
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private companion object {
        private const val USER_AGENT =
            "KairoReader/1.0 (+https://kairo.reader; article text extraction)"
        private const val REQUEST_TIMEOUT_MS = 12_000
        private const val MAX_BODY_SIZE_BYTES = 4 * 1024 * 1024
        private const val MIN_ARTICLE_CHARS = 120
        private const val MIN_BLOCK_CHARS = 24
        private const val MIN_HEADING_CHARS = 4
        private const val PARAGRAPH_SCORE = 80
        private const val HEADING_SCORE = 120
        private const val NBSP = '\u00A0'
        private val PARAGRAPH_SPLIT = Regex("\\n{2,}")
        private val TEXT_WHITESPACE = Regex("\\s+")
        private val TITLE_SEPARATORS = listOf("|", "-", "–", "—")
        private const val REMOVABLE_SELECTOR =
            "script, style, noscript, svg, canvas, iframe, nav, aside, form, button, " +
                "header, footer, dialog, [role=banner], [role=navigation], [role=complementary]"
        private const val CANDIDATE_SELECTOR =
            "article, main, [role=main], [class*=article], [class*=post], " +
                "[class*=entry-content], [class*=story], [class*=content], body"
        private const val BLOCK_SELECTOR = "h1, h2, h3, p, li, blockquote, pre"
        private val BOILERPLATE_MARKERS =
            listOf(
                "accept cookies",
                "cookie settings",
                "privacy policy",
                "all rights reserved",
                "subscribe to",
                "sign up for",
                "advertisement",
            )
    }
}
