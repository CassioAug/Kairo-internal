package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.data.books.epub.EpubChapterOrdering
import java.util.Locale

internal data class ParsedChapter(val pathLower: String, val baseDir: String, val chapter: Chapter,)

internal data class NavigationFilterResult(val chapters: List<ParsedChapter>, val filteredCount: Int, val suppressed: Boolean,)

internal class EpubNavigationClassifier(private val contentRewriter: EpubContentRewriter,) {
    private companion object {
        const val MAX_NAV_FILTER_REMOVAL_RATIO = 0.60
        const val MIN_SUBSTANTIAL_CHAPTER_WORDS = 80
        const val NAVIGATION_TITLE_SCAN_CHARS = 400
        const val NAVIGATION_WORD_SCAN_LIMIT = 601
        val TOC_TITLE_PATTERNS =
            listOf(
                Regex("\\btable\\s+of\\s+contents\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcontents\\b", RegexOption.IGNORE_CASE),
            )
    }

    private fun isLikelyNavigationHtmlPath(pathLower: String): Boolean {
        return EpubChapterOrdering.isLikelyNavigationHtmlPath(pathLower)
    }

    fun filter(parsed: List<ParsedChapter>): NavigationFilterResult {
        if (parsed.size <= 1) {
            return NavigationFilterResult(chapters = parsed, filteredCount = 0, suppressed = false)
        }
        val classified = parsed.map { it to isLikelyNavigationChapter(it) }
        val flagged =
            classified.mapNotNull { (chapter, isNavigation) ->
                if (isNavigation) chapter else null
            }
        if (flagged.isEmpty()) {
            return NavigationFilterResult(chapters = parsed, filteredCount = 0, suppressed = false)
        }
        val nonNavigation =
            classified.mapNotNull { (chapter, isNavigation) ->
                if (isNavigation) null else chapter
            }
        val removalRatio = flagged.size.toDouble() / parsed.size.toDouble()
        val hasRetainedSubstantial = nonNavigation.any(::isSubstantialChapter)
        val hasRemovedSubstantial = flagged.any(::isSubstantialChapter)
        val suppress =
            nonNavigation.isEmpty() ||
                removalRatio > MAX_NAV_FILTER_REMOVAL_RATIO ||
                (!hasRetainedSubstantial && hasRemovedSubstantial)
        if (suppress) {
            return NavigationFilterResult(chapters = parsed, filteredCount = 0, suppressed = true)
        }
        return NavigationFilterResult(
            chapters = nonNavigation,
            filteredCount = flagged.size,
            suppressed = false,
        )
    }

    private fun isLikelyNavigationChapter(parsed: ParsedChapter): Boolean {
        val html = parsed.chapter.htmlContent
        val plainText = parsed.chapter.plainText
        val title = parsed.chapter.title.orEmpty()
        val anchorCount = contentRewriter.countAnchorTags(html)
        val lowerPath = parsed.pathLower.lowercase(Locale.ROOT)
        val lowerTitle = title.lowercase(Locale.ROOT)
        val lowerPlainPreview =
            plainText
                .take(NAVIGATION_TITLE_SCAN_CHARS)
                .lowercase(Locale.ROOT)

        if (html.indexOf("<nav", ignoreCase = true) >= 0) return true

        val looksLikeTocTitle =
            TOC_TITLE_PATTERNS.any { pattern ->
                pattern.containsMatchIn(lowerTitle) || pattern.containsMatchIn(lowerPlainPreview)
            }
        val fileLooksLikeNav = isLikelyNavigationHtmlPath(lowerPath)
        val estimatedWords = estimateWordCount(parsed.chapter, NAVIGATION_WORD_SCAN_LIMIT)
        val anchorDensity =
            if (estimatedWords > 0) {
                anchorCount.toDouble() / estimatedWords.toDouble()
            } else {
                anchorCount.toDouble()
            }

        if ((fileLooksLikeNav || looksLikeTocTitle) && anchorCount >= 6 && estimatedWords <= 600) {
            return true
        }
        if (anchorCount >= 20 && estimatedWords <= 220) {
            return true
        }
        if (anchorCount >= 12 && anchorDensity >= 0.18 && estimatedWords <= 420) {
            return true
        }
        return false
    }

    private fun isSubstantialChapter(parsed: ParsedChapter): Boolean {
        return estimateWordCount(
            parsed.chapter,
            MIN_SUBSTANTIAL_CHAPTER_WORDS,
        ) >= MIN_SUBSTANTIAL_CHAPTER_WORDS
    }

    private fun estimateWordCount(
        chapter: Chapter,
        limit: Int,
    ): Int {
        if (chapter.wordCount > 0) return chapter.wordCount.coerceAtMost(limit)
        return countWordsUpTo(chapter.plainText, limit)
    }

    private fun countWordsUpTo(
        text: String,
        limit: Int,
    ): Int {
        if (limit <= 0 || text.isEmpty()) return 0
        var count = 0
        var inWord = false
        var index = 0
        while (index < text.length && count < limit) {
            val codePoint = Character.codePointAt(text, index)
            val charCount = Character.charCount(codePoint)
            when {
                Character.isLetterOrDigit(codePoint) -> {
                    if (!inWord) count += 1
                    inWord = true
                }
                isWordApostrophe(codePoint) && inWord -> {
                    val nextIndex = index + charCount
                    inWord =
                        nextIndex < text.length &&
                        Character.isLetterOrDigit(Character.codePointAt(text, nextIndex))
                }
                else -> inWord = false
            }
            index += charCount
        }
        return count
    }

    private fun isWordApostrophe(codePoint: Int): Boolean =
        codePoint == '\''.code || codePoint == '\u2019'.code
}
