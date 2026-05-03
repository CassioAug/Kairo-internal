package app.kairo.reader.data.books

import android.net.Uri
import app.kairo.reader.core.model.Book
import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.Chapter
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    suspend fun importBook(uri: Uri): Book

    suspend fun getBook(bookId: BookId): Book

    suspend fun getChapter(
        bookId: BookId,
        chapterIndex: Int,
    ): Chapter

    suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        wordCount: Int,
    )

    suspend fun getBookLanguageTag(bookId: BookId): String?

    fun observeBooks(): Flow<List<Book>>
}
