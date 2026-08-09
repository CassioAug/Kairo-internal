package com.kairo.reader.core.model

enum class LibrarySearchResultKind { BOOK, PASSAGE, SAVED }

data class LibrarySearchResult(
    val id: String,
    val kind: LibrarySearchResultKind,
    val bookId: BookId,
    val bookTitle: String,
    val chapterIndex: Int,
    val chapterTitle: String?,
    val tokenIndex: Int,
    val endTokenIndex: Int = tokenIndex,
    val matchStartCodePointOffset: Int? = null,
    val matchLengthCodePoints: Int = 0,
    val title: String,
    val snippet: String,
) {
    val tokenRange: IntRange
        get() = minOf(tokenIndex, endTokenIndex)..maxOf(tokenIndex, endTokenIndex)
}
