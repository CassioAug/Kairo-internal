package com.kairo.reader.core.tokenization.cjk

import com.kairo.reader.core.tokenization.ParagraphBreakPatterns

internal object CjkParagraphSplitter {
    fun split(text: String): List<String> =
        text
            .split(Regex("\\n\\s*\\n"))
            .mapNotNull { raw ->
                if (raw.contains(CjkTextNormalizer.FORM_FEED_MARKER)) {
                    CjkTextNormalizer.FORM_FEED_MARKER
                } else if (raw.contains(FORM_FEED)) {
                    FORM_FEED
                } else {
                    raw.trim().takeIf { it.isNotEmpty() }
                }
            }

    fun isPageBreakParagraph(paragraph: String): Boolean {
        if (paragraph == CjkTextNormalizer.FORM_FEED_MARKER) return true
        if (paragraph == FORM_FEED) return true
        if (paragraph.isBlank()) return false
        return ParagraphBreakPatterns.sceneBreak.matches(paragraph)
    }

    fun pageBreakText(paragraph: String): String =
        if (paragraph == CjkTextNormalizer.FORM_FEED_MARKER || paragraph == FORM_FEED) {
            FORM_FEED
        } else {
            paragraph.trim()
        }

    private const val FORM_FEED = "\u000C"
}
