package com.kairo.reader.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SavedAnnotationWithBookEntity(
    @Embedded val annotation: SavedAnnotationEntity,
    @Embedded(prefix = "book_") val book: BookEntity,
    val chapterCount: Int,
)

@Dao
interface SavedAnnotationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedAnnotationEntity)

    @Query("DELETE FROM saved_annotations WHERE id = :annotationId")
    suspend fun delete(annotationId: String)

    @Query("DELETE FROM saved_annotations WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("SELECT * FROM saved_annotations WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeForBook(bookId: String): Flow<List<SavedAnnotationEntity>>

    @Query(SAVED_ANNOTATIONS_WITH_BOOK_QUERY)
    fun observeWithBook(): Flow<List<SavedAnnotationWithBookEntity>>

    @Query(
        SAVED_ANNOTATIONS_WITH_BOOK_QUERY +
            " AND (lower(saved_annotations.selectedText) LIKE :pattern " +
            "OR lower(saved_annotations.note) LIKE :pattern) " +
            "ORDER BY saved_annotations.updatedAt DESC LIMIT :limit",
    )
    suspend fun searchWithBook(
        pattern: String,
        limit: Int,
    ): List<SavedAnnotationWithBookEntity>
}

private const val SAVED_ANNOTATIONS_WITH_BOOK_QUERY =
    """
    SELECT
        saved_annotations.id,
        saved_annotations.bookId,
        saved_annotations.chapterIndex,
        saved_annotations.startTokenIndex,
        saved_annotations.endTokenIndex,
        saved_annotations.selectedText,
        saved_annotations.note,
        saved_annotations.color,
        saved_annotations.kind,
        saved_annotations.createdAt,
        saved_annotations.updatedAt,
        books.id AS book_id,
        books.title AS book_title,
        books.authors AS book_authors,
        books.languageTag AS book_languageTag,
        CASE
            WHEN books.coverImage IS NOT NULL AND length(books.coverImage) <= 1900000 THEN books.coverImage
            ELSE NULL
        END AS book_coverImage,
        books.isCompleted AS book_isCompleted,
        books.importFingerprint AS book_importFingerprint,
        (SELECT COUNT(*) FROM chapters WHERE chapters.bookId = books.id) AS chapterCount
    FROM saved_annotations
    JOIN books ON saved_annotations.bookId = books.id
    WHERE 1 = 1
    """
