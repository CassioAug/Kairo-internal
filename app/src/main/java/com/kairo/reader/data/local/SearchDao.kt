package com.kairo.reader.data.local

import androidx.room.Dao
import androidx.room.Query

data class SearchableChapterEntity(
    val bookId: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val chapterTitle: String?,
    val plainText: String,
)

@Dao
interface SearchDao {
    @Query(
        """
        SELECT
            chapters.bookId AS bookId,
            books.title AS bookTitle,
            chapters.`index` AS chapterIndex,
            chapters.title AS chapterTitle,
            chapters.plainText AS plainText
        FROM chapters
        JOIN books ON chapters.bookId = books.id
        WHERE (:bookId IS NULL OR chapters.bookId = :bookId)
          AND lower(chapters.plainText) LIKE :pattern ESCAPE '\'
        ORDER BY lower(books.title), chapters.`index`
        LIMIT :limit
        """,
    )
    suspend fun searchChapters(
        pattern: String,
        bookId: String?,
        limit: Int,
    ): List<SearchableChapterEntity>

    @Query(
        """
        SELECT id, title, authors, languageTag, NULL AS coverImage, isCompleted, importFingerprint
        FROM books
        WHERE lower(title) LIKE :pattern ESCAPE '\'
           OR lower(authors) LIKE :pattern ESCAPE '\'
        ORDER BY lower(title)
        LIMIT :limit
        """,
    )
    suspend fun searchBooks(
        pattern: String,
        limit: Int,
    ): List<BookEntity>
}
