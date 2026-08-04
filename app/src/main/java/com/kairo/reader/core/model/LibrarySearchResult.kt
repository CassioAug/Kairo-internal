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
    val title: String,
    val snippet: String,
)
