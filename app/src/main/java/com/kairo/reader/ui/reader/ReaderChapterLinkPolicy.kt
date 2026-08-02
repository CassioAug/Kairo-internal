package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.countWords
import java.util.Locale

internal fun resolveNonInteractiveChapterLinkTargets(book: Book): Set<Int> {
    val labelledTargets =
        book.tableOfContents
            .asSequence()
            .filter { entry -> isTableOfContentsLabel(entry.label) }
            .mapNotNull { entry -> entry.target?.chapterIndex }
    val navigationChapters =
        book.chapters
            .asSequence()
            .filter(::isLikelyNavigationChapter)
            .map(Chapter::index)
    return (labelledTargets + navigationChapters).toSet()
}

internal fun resolveInteractiveChapterLinkTarget(
    token: Token,
    nonInteractiveTargets: Set<Int>,
): Int? = token.linkChapterIndex?.takeUnless(nonInteractiveTargets::contains)

private fun isLikelyNavigationChapter(chapter: Chapter): Boolean {
    if (isTableOfContentsLabel(chapter.title.orEmpty())) return true
    if (chapter.htmlContent.indexOf("<nav", ignoreCase = true) >= 0) return true

    val internalLinkCount = countInternalChapterLinks(chapter.htmlContent)
    if (internalLinkCount < MIN_DENSE_NAVIGATION_LINKS) return false
    val wordCount = chapter.wordCount.takeIf { it > 0 } ?: countWords(chapter.plainText)
    if (wordCount <= 0 || wordCount > MAX_DENSE_NAVIGATION_WORDS) return false
    return internalLinkCount.toDouble() / wordCount.toDouble() >= MIN_NAVIGATION_LINK_DENSITY
}

private fun isTableOfContentsLabel(label: String): Boolean {
    val identifier =
        label
            .lowercase(Locale.ROOT)
            .filter(Char::isLetterOrDigit)
            .trimEnd(Char::isDigit)
            .removeSuffix("page")
    return identifier in TABLE_OF_CONTENTS_IDENTIFIERS
}

private fun countInternalChapterLinks(html: String): Int =
    INTERNAL_CHAPTER_LINK_REGEX
        .findAll(html)
        .take(MAX_COUNTED_NAVIGATION_LINKS)
        .count()

private const val MIN_DENSE_NAVIGATION_LINKS = 3
private const val MAX_DENSE_NAVIGATION_WORDS = 420
private const val MIN_NAVIGATION_LINK_DENSITY = 0.15
private const val MAX_COUNTED_NAVIGATION_LINKS = 1_000
private val INTERNAL_CHAPTER_LINK_REGEX = Regex("kairo://chapter/\\d+", RegexOption.IGNORE_CASE)
private val TABLE_OF_CONTENTS_IDENTIFIERS =
    setOf(
        "contents",
        "tableofcontents",
        "toc",
    )
