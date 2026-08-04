package com.kairo.reader.data.search

internal fun String.toSqlLikePattern(): String =
    "%" +
        lowercase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_") +
        "%"

internal fun findSearchMatchOffsets(
    text: String,
    query: String,
    limit: Int,
): List<Int> {
    if (query.isEmpty() || limit <= 0) return emptyList()
    val offsets = mutableListOf<Int>()
    var searchFrom = 0
    while (offsets.size < limit) {
        val match = text.indexOf(query, startIndex = searchFrom, ignoreCase = true)
        if (match < 0) break
        offsets += match
        searchFrom = match + query.length
    }
    return offsets
}

internal fun buildSearchSnippet(
    text: String,
    matchOffset: Int,
    matchLength: Int,
    contextCharacters: Int,
): String {
    val start = (matchOffset - contextCharacters).coerceAtLeast(0)
    val end = (matchOffset + matchLength + contextCharacters).coerceAtMost(text.length)
    val body = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
    return buildString {
        if (start > 0) append(ELLIPSIS)
        append(body)
        if (end < text.length) append(ELLIPSIS)
    }
}

private const val ELLIPSIS = "…"
