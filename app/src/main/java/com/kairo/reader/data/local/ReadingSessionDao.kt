package com.kairo.reader.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ReadingSessionWithBookEntity(
    @Embedded val session: ReadingSessionEntity,
    @Embedded(prefix = "book_") val book: BookEntity,
)

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReadingSessionEntity)

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query(
        """
        SELECT
            reading_sessions.*,
            books.id AS book_id,
            books.title AS book_title,
            books.authors AS book_authors,
            books.languageTag AS book_languageTag,
            CASE
                WHEN books.coverImage IS NOT NULL AND length(books.coverImage) <= 1900000 THEN books.coverImage
                ELSE NULL
            END AS book_coverImage,
            books.isCompleted AS book_isCompleted,
            books.importFingerprint AS book_importFingerprint
        FROM reading_sessions
        JOIN books ON reading_sessions.bookId = books.id
        ORDER BY reading_sessions.startedAt DESC
        """,
    )
    fun observeWithBook(): Flow<List<ReadingSessionWithBookEntity>>
}
