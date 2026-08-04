package com.kairo.reader.data.search

import com.kairo.reader.core.model.LibrarySearchResult

interface LibrarySearchRepository {
    suspend fun search(
        query: String,
        bookId: String? = null,
    ): List<LibrarySearchResult>
}
