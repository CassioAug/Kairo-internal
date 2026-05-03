package app.kairo.reader.core.tokenization.cjk

import app.kairo.reader.core.linguistics.ClauseDetector
import app.kairo.reader.core.linguistics.WordAnalyzer
import app.kairo.reader.core.model.Token
import app.kairo.reader.core.model.TokenType
import app.kairo.reader.core.model.calculateOrpIndex

internal object CjkTokenFactory {
    fun word(
        text: String,
        isLatin: Boolean,
    ): Token =
        if (isLatin) {
            val syllables = WordAnalyzer.countSyllables(text)
            val frequency = WordAnalyzer.getFrequencyScore(text)
            val complexity = WordAnalyzer.getComplexityMultiplier(text)
            val isClause = ClauseDetector.isClauseBoundary(text)

            Token(
                text = text,
                type = TokenType.WORD,
                orpIndex = calculateOrpIndex(text),
                syllableCount = syllables,
                frequencyScore = frequency,
                complexityMultiplier = complexity,
                isClauseBoundary = isClause,
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
