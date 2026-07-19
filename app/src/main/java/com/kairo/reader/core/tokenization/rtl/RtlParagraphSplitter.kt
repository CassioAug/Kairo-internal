package com.kairo.reader.core.tokenization.rtl

import com.kairo.reader.core.tokenization.ParagraphBreakPatterns

internal object RtlParagraphSplitter {
    fun split(text: String): List<String> =
        text
            .split(Regex("\\n\\s*\\n"))
            .mapNotNull { raw ->
                when {
                    raw.contains(RtlTextNormalizer.FORM_FEED_MARKER) ->
                        RtlTextNormalizer.FORM_FEED_MARKER
                    raw.contains(FORM_FEED) -> FORM_FEED
                    else -> raw.trim().takeIf { it.isNotEmpty() }
                }
            }

    fun isPageBreakParagraph(paragraph: String): Boolean {
        if (paragraph == RtlTextNormalizer.FORM_FEED_MARKER) return true
        if (paragraph == FORM_FEED) return true
        if (paragraph.isBlank()) return false
        return ParagraphBreakPatterns.sceneBreak.matches(paragraph)
    }

    fun pageBreakText(paragraph: String): String =
        if (paragraph == RtlTextNormalizer.FORM_FEED_MARKER || paragraph == FORM_FEED) {
            FORM_FEED
        } else {
            paragraph.trim()
        }

    private const val FORM_FEED = "\u000C"
}
