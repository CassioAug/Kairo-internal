package com.kairo.reader.core.tokenization.cjk

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.tokenization.HtmlChapterLinkApplier
import com.kairo.reader.core.tokenization.LinkPositionMapper

internal object CjkLinkApplier {
    fun apply(
        tokens: MutableList<Token>,
        chapter: Chapter,
        tokenizeInlineText: (String) -> List<String>,
    ): List<Token> {
        if (chapter.links.isNotEmpty()) {
            LinkPositionMapper.apply(tokens, chapter.links, chapter.plainText)
        }
        HtmlChapterLinkApplier.apply(
            tokens = tokens,
            html = chapter.htmlContent,
            normalizeInlineText = CjkTextNormalizer::normalizeInlineText,
            tokenizeInlineText = tokenizeInlineText,
            minimumRomanPageNumberLength = 1,
        )
        return tokens
    }
}
