package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.buildWordCountByToken

/** Pure chapter-to-reader-model transformation kept outside the screen state owner. */
class ReaderChapterProcessor {
    fun process(
        chapter: Chapter,
        tokens: List<Token>,
        wordsPerPage: Int,
    ): ChapterData? {
        if (tokens.isEmpty() && chapter.imagePaths.isEmpty()) return null

        val paragraphs = tokens.toParagraphs()
        val wordCountByToken = buildWordCountByToken(tokens)
        return ChapterData(
            tokens = tokens,
            plainText = chapter.plainText,
            paragraphs = paragraphs,
            blocks =
            buildReaderBlocks(
                htmlContent = chapter.htmlContent,
                paragraphs = paragraphs,
                imagePaths = chapter.imagePaths,
            ),
            firstWordIndex = tokens.indexOfFirst { it.type == TokenType.WORD },
            imagePaths = chapter.imagePaths,
            pages = buildChapterPages(tokens, wordsPerPage),
            wordCountByToken = wordCountByToken,
            totalWords = wordCountByToken.lastOrNull() ?: 0,
        )
    }

    fun wordsPerPage(
        fontSizeSp: Float,
        viewportHeightDp: Int,
    ): Int = estimateWordsPerPage(fontSizeSp, viewportHeightDp)

    fun repage(
        chapterData: ChapterData,
        wordsPerPage: Int,
    ): ChapterData = rePageChapterData(chapterData, wordsPerPage)
}
