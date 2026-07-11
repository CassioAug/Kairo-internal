@file:Suppress("MagicNumber")

package com.kairo.reader.core.model

private val openingPunctuationCharsForDisplay = PAIRED_OPENING_PUNCTUATION_CHARS + '"'
private val closingPunctuationCharsForDisplay =
    setOf(
        '.',
        ',',
        ';',
        ':',
        '!',
        '?',
        '"',
        '\u2014',
        '\u2013', // Em-dash — and en-dash –
        '\u2026', // Ellipsis …
        '\u3001', // Ideographic comma 、
        '\u3002', // Ideographic full stop 。
        '\uFF01', // Fullwidth exclamation mark ！
        '\uFF0C', // Fullwidth comma ，
        '\uFF0E', // Fullwidth full stop ．
        '\uFF1A', // Fullwidth colon ：
        '\uFF1B', // Fullwidth semicolon ；
        '\uFF1F', // Fullwidth question mark ？
        '\uFF61', // Halfwidth ideographic full stop ｡
        '\uFF64', // Halfwidth ideographic comma ､
    ) + PAIRED_CLOSING_PUNCTUATION_CHARS

private val dashJoinersForDisplay = setOf('\u2014', '\u2013')

private fun Token.singleCharOrNull(): Char? = if (text.length == 1) text[0] else null

private fun Token.isPunctuationIn(chars: Set<Char>): Boolean =
    type == TokenType.PUNCTUATION && singleCharOrNull()?.let(chars::contains) == true

/**
 * Returns whether a space should be inserted before [token] when rendering tokens as human-readable text.
 *
 * This is used by the scrollable Reader view so punctuation spacing stays stable regardless of the original
 * whitespace in the source.
 */
fun shouldInsertSpaceBeforeToken(
    token: Token,
    prevToken: Token?,
    tokenIndexInParagraph: Int,
): Boolean {
    if (tokenIndexInParagraph == 0) return false

    val isClosing = token.isPunctuationIn(closingPunctuationCharsForDisplay)
    val prevWasOpening = prevToken?.isPunctuationIn(openingPunctuationCharsForDisplay) == true
    val prevWasDashJoiner = prevToken?.isPunctuationIn(dashJoinersForDisplay) == true
    val dashNotParagraphStart = prevWasDashJoiner && tokenIndexInParagraph >= 2

    return !(isClosing || prevWasOpening || dashNotParagraphStart)
}

/**
 * Joins [tokens] into a readable string using [shouldInsertSpaceBeforeToken].
 *
 * Paragraph break tokens are treated as paragraph boundaries.
 */
fun joinTokensForDisplay(tokens: List<Token>): String {
    val builder = StringBuilder()
    var paragraphIndex = 0
    var prevNonBreakToken: Token? = null

    for (token in tokens) {
        if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
            paragraphIndex = 0
            prevNonBreakToken = null
            continue
        }

        if (shouldInsertSpaceBeforeToken(token, prevNonBreakToken, paragraphIndex)) {
            builder.append(' ')
        }
        builder.append(token.text)
        prevNonBreakToken = token
        paragraphIndex++
    }

    return builder.toString()
}
