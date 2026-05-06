package com.kairo.reader.core.tokenization.rtl

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType

internal object RtlTokenFactory {
    fun word(text: String): Token =
        Token(
            text = text,
            type = TokenType.WORD,
            orpIndex = 0,
            syllableCount = 1,
            frequencyScore = 0.7,
            complexityMultiplier = 1.0,
        )

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
