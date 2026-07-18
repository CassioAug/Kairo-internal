package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType

fun List<Token>.toParagraphs(): List<Paragraph> {
    if (isEmpty()) return emptyList()

    val paragraphs = mutableListOf<Paragraph>()
    val currentTokens = mutableListOf<Token>()
    var startIndex = 0

    forEachIndexed { index, token ->
        when (token.type) {
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> {
                if (currentTokens.isNotEmpty()) {
                    paragraphs.add(Paragraph(currentTokens.toList(), startIndex))
                    currentTokens.clear()
                }
                startIndex = index + 1
            }
            else -> {
                currentTokens.add(token)
            }
        }
    }

    // Don't forget the last paragraph (no trailing PARAGRAPH_BREAK)
    if (currentTokens.isNotEmpty()) {
        paragraphs.add(Paragraph(currentTokens.toList(), startIndex))
    }

    return paragraphs
}
