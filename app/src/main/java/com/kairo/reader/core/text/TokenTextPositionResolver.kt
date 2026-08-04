package com.kairo.reader.core.text

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.nearestWordIndex

object TokenTextPositionResolver {
    fun resolveTokenIndex(
        plainText: String,
        tokens: List<Token>,
        characterOffset: Int,
    ): Int {
        if (tokens.isEmpty()) return 0
        val targetOffset = characterOffset.coerceIn(0, plainText.length)
        if (targetOffset == 0) return tokens.nearestWordIndex(0)

        var searchFrom = 0
        var lastWordIndex = tokens.nearestWordIndex(0)
        tokens.forEachIndexed { index, token ->
            if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
                return@forEachIndexed
            }
            val match = findToken(plainText, token.text, searchFrom) ?: return@forEachIndexed
            if (match.start >= targetOffset || targetOffset < match.endExclusive) {
                return tokens.nearestWordIndex(index)
            }
            if (token.type == TokenType.WORD) lastWordIndex = index
            searchFrom = match.endExclusive
        }
        return tokens.nearestWordIndex(lastWordIndex)
    }

    private fun findToken(
        plainText: String,
        tokenText: String,
        searchFrom: Int,
    ): TextRange? {
        if (tokenText.isEmpty()) return null
        var bestStart = Int.MAX_VALUE
        var bestLength = 0
        tokenVariants(tokenText).forEach { variant ->
            val found = plainText.indexOf(variant, startIndex = searchFrom.coerceAtLeast(0))
            if (found >= 0 && found < bestStart) {
                bestStart = found
                bestLength = variant.length
            }
        }
        return if (bestStart == Int.MAX_VALUE) null else TextRange(bestStart, bestStart + bestLength)
    }

    private fun tokenVariants(tokenText: String): Set<String> =
        buildSet {
            add(tokenText)
            when (tokenText) {
                "\u201C", "\u201D" -> add("\"")
                "\u2018", "\u2019" -> add("'")
                "\u2026" -> add("...")
                "\u2014" -> add("--")
                "\u2013" -> add("-")
            }
        }

    private data class TextRange(val start: Int, val endExclusive: Int,)
}
