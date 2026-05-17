package com.kairo.reader.core.tokenization.cjk

import com.kairo.reader.core.linguistics.WordAnalyzer
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType

internal object CjkTokenFactory {
    fun word(
        text: String,
        isLatin: Boolean,
    ): Token =
        if (isLatin) {
            val analysis = WordAnalyzer.analyze(text)

            Token(
                text = text,
                type = TokenType.WORD,
                orpIndex = analysis.orpIndex,
                syllableCount = analysis.syllableCount,
                frequencyScore = analysis.frequencyScore,
                complexityMultiplier = analysis.complexityMultiplier,
                isClauseBoundary = analysis.isClauseBoundary,
            )
        } else {
            Token(
                text = text,
                type = TokenType.WORD,
                orpIndex = 0,
                syllableCount = 1,
                frequencyScore = 0.8,
                complexityMultiplier = 1.0,
            )
        }

    fun punctuation(text: String): Token =
        Token(
            text = text,
            type = TokenType.PUNCTUATION,
            pauseAfterMs = 0L,
        )

    fun paragraphBreak(): Token =
        Token(
            text = "\n",
            type = TokenType.PARAGRAPH_BREAK,
            pauseAfterMs = 0L,
        )

    fun pageBreak(text: String = "\u000C"): Token =
        Token(
            text = text,
            type = TokenType.PAGE_BREAK,
            pauseAfterMs = 0L,
        )
}
