package com.kairo.reader.data.token

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token

interface TokenRepository {
    suspend fun getTokens(
        bookId: BookId,
        chapterIndex: Int,
        chapter: Chapter? = null,
    ): List<Token>

    fun invalidateBook(bookId: BookId) {
        // Optional for stateless implementations.
    }

    fun clearCache() {
        // Optional for stateless implementations.
    }
}
