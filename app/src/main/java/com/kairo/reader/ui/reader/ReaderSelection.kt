package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.joinTokensForDisplay

internal fun resolveReaderSelectionRange(
    anchor: Int?,
    end: Int?,
): IntRange? =
    anchor?.let { start ->
        val resolvedEnd = end ?: start
        minOf(start, resolvedEnd)..maxOf(start, resolvedEnd)
    }

internal fun buildReaderSelectionText(
    tokens: List<Token>,
    selectionRange: IntRange?,
): String {
    if (tokens.isEmpty() || selectionRange == null) return ""
    val start = selectionRange.first.coerceIn(tokens.indices)
    val end = selectionRange.last.coerceIn(start, tokens.lastIndex)
    return joinTokensForDisplay(tokens.subList(start, end + 1))
}
