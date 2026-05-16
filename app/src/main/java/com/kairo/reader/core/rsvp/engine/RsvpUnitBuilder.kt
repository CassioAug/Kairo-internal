package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.analysis.isPhraseChunkCandidate
import com.kairo.reader.core.rsvp.text.isHardBoundaryPunctuation
import com.kairo.reader.core.rsvp.text.isOpeningPunctuation
import com.kairo.reader.core.rsvp.text.isQuoteChar

internal fun buildUnit(
    expandedTokens: List<ExpandedToken>,
    startCursor: Int,
    config: RsvpConfig,
    state: ContextState,
): UnitBuildResult {
    val unitTokens = mutableListOf<Token>()
    var cursor = startCursor.coerceIn(0, expandedTokens.lastIndex)

    // Allow opening punctuation right before the first word to be part of the unit.
    while (cursor < expandedTokens.size) {
        val token = expandedTokens[cursor].token
        val nextToken = expandedTokens.getOrNull(cursor + 1)?.token
        val isLeadingQuote =
            token.type == TokenType.PUNCTUATION &&
                token.text.firstOrNull()?.let { isQuoteChar(it) } == true &&
                nextToken?.type == TokenType.WORD
        if (token.type == TokenType.PUNCTUATION &&
            (isOpeningPunctuation(token, state, nextToken) || isLeadingQuote)
        ) {
            unitTokens += token
            state.consume(token)
            cursor++
        } else {
            break
        }
    }

    // First word is required.
    while (cursor < expandedTokens.size &&
        expandedTokens[cursor].token.type != TokenType.WORD
    ) {
        cursor++
    }
    val firstWord =
        expandedTokens.getOrNull(cursor)
            ?: return UnitBuildResult(unitTokens, startCursor, cursor)
    val firstWordOriginalIndex = firstWord.originalIndex
    unitTokens += firstWord.token
    state.consume(firstWord.token)
    cursor++

    // Optionally add more words for phrase chunking (only across "soft" boundaries).
    val maxWordsInUnit = config.maxWordsPerUnit.coerceAtLeast(1)
    val maxCharsInUnit = config.maxCharsPerUnit.coerceAtLeast(1)
    if (config.enablePhraseChunking && maxWordsInUnit > 1) {
        var wordsInUnit = 1
        var wordCharsInUnit = firstWord.token.text.length
        while (wordsInUnit < maxWordsInUnit) {
            val candidate = expandedTokens.getOrNull(cursor) ?: break
            if (candidate.token.type != TokenType.WORD) break

            val combinedWordChars = wordCharsInUnit + candidate.token.text.length
            val withinLimits = combinedWordChars <= maxCharsInUnit
            val prevWord = unitTokens.lastOrNull { it.type == TokenType.WORD } ?: break
            val canBridge =
                withinLimits &&
                    isPhraseChunkCandidate(prev = prevWord, next = candidate.token)

            if (!canBridge) break

            unitTokens += candidate.token
            state.consume(candidate.token)
            cursor++
            wordsInUnit++
            wordCharsInUnit = combinedWordChars
        }
    }

    // Attach closing punctuation + breaks to this unit.
    // If we hit a hard boundary (sentence end), keep consuming trailing closers like quotes/brackets
    // so we don't strand them on the next unit.
    var hitHardBoundary = false
    while (cursor < expandedTokens.size) {
        val token = expandedTokens[cursor].token
        if (token.type == TokenType.PUNCTUATION) {
            val ch = token.text.firstOrNull()
            val nextToken = expandedTokens.getOrNull(cursor + 1)?.token
            val isQuote = ch != null && isQuoteChar(ch)
            val isOpening = isOpeningPunctuation(token, state, nextToken)
            val isExplicitOpeningQuote = ch == '\u201C' || ch == '\u2018'

            // Opening quotes that precede a word should stay with that word,
            // even if a sentence-ending punctuation token came right before.
            val quoteFollowedByWord =
                isQuote &&
                    nextToken?.type == TokenType.WORD &&
                    (isExplicitOpeningQuote || (ch == '"' && isOpening))
            if (quoteFollowedByWord) {
                break
            }

            if (hitHardBoundary && isOpening) break
            if (!isOpening) {
                val prevWord = unitTokens.lastOrNull { it.type == TokenType.WORD }
                unitTokens += token
                state.consume(token)
                cursor++

                val nextTokenAfter = expandedTokens.getOrNull(cursor)?.token
                if (!hitHardBoundary &&
                    isHardBoundaryPunctuation(token, prevWord = prevWord, nextToken = nextTokenAfter)
                ) {
                    hitHardBoundary = true
                }
                continue
            }
            break
        }
        if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
            break
        }
        break
    }

    return UnitBuildResult(
        tokens = unitTokens,
        originalWordIndex = firstWordOriginalIndex,
        nextCursor = cursor,
    )
}
