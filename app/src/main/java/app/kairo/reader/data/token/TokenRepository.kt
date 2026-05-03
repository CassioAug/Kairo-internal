package app.kairo.reader.data.token

import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.Chapter
import app.kairo.reader.core.model.Token

interface TokenRepository {
    suspend fun getTokens(
        bookId: BookId,
        chapterIndex: Int,
        chapter: Chapter? = null,
    ): List<Token>
}
