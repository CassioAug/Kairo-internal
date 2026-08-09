package com.kairo.reader.data.local

import androidx.room.Dao
import androidx.room.Query

data class SearchPassageMatchEntity(
    val bookId: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val chapterTitle: String?,
    val matchStartCodePointOffset: Int,
    val matchLengthCodePoints: Int,
    val snippetStartCodePointOffset: Int,
    val snippetText: String,
    val chapterLengthCodePoints: Int,
)

@Dao
interface SearchDao {
    @Query(
        """
        WITH RECURSIVE candidate_chapters(
            bookId,
            bookTitle,
            chapterIndex,
            chapterTitle,
            plainText,
            chapterLengthCodePoints
        ) AS (
            SELECT
                chapters.bookId,
                books.title,
                chapters.`index`,
                chapters.title,
                chapters.plainText,
                length(chapters.plainText)
            FROM chapters
            JOIN books ON chapters.bookId = books.id
            WHERE (:bookId IS NULL OR chapters.bookId = :bookId)
              AND instr(lower(chapters.plainText), :normalizedQuery) > 0
            ORDER BY lower(books.title), chapters.`index`
            LIMIT :chapterLimit
        ),
        chapter_matches(
            bookId,
            bookTitle,
            chapterIndex,
            chapterTitle,
            plainText,
            chapterLengthCodePoints,
            searchStartCodePoint,
            relativeOffsetCodePoint,
            matchNumber
        ) AS (
            SELECT
                bookId,
                bookTitle,
                chapterIndex,
                chapterTitle,
                plainText,
                chapterLengthCodePoints,
                1,
                instr(lower(plainText), :normalizedQuery),
                1
            FROM candidate_chapters

            UNION ALL

            SELECT
                bookId,
                bookTitle,
                chapterIndex,
                chapterTitle,
                plainText,
                chapterLengthCodePoints,
                searchStartCodePoint + relativeOffsetCodePoint + :matchLengthCodePoints - 1,
                instr(
                    substr(
                        lower(plainText),
                        searchStartCodePoint + relativeOffsetCodePoint + :matchLengthCodePoints - 1
                    ),
                    :normalizedQuery
                ),
                matchNumber + 1
            FROM chapter_matches
            WHERE relativeOffsetCodePoint > 0
              AND matchNumber < :matchesPerChapter
              AND searchStartCodePoint + relativeOffsetCodePoint + :matchLengthCodePoints - 1 <=
                    chapterLengthCodePoints
              AND instr(
                    substr(
                        lower(plainText),
                        searchStartCodePoint + relativeOffsetCodePoint + :matchLengthCodePoints - 1
                    ),
                    :normalizedQuery
                  ) > 0
        )
        SELECT
            bookId,
            bookTitle,
            chapterIndex,
            chapterTitle,
            searchStartCodePoint + relativeOffsetCodePoint - 2 AS matchStartCodePointOffset,
            :matchLengthCodePoints AS matchLengthCodePoints,
            max(
                0,
                searchStartCodePoint + relativeOffsetCodePoint - 2 - :snippetContextCharacters
            ) AS snippetStartCodePointOffset,
            substr(
                plainText,
                max(
                    1,
                    searchStartCodePoint + relativeOffsetCodePoint - 1 - :snippetContextCharacters
                ),
                :matchLengthCodePoints + (2 * :snippetContextCharacters)
            ) AS snippetText,
            chapterLengthCodePoints
        FROM chapter_matches
        WHERE relativeOffsetCodePoint > 0
        ORDER BY lower(bookTitle), chapterIndex, matchStartCodePointOffset
        LIMIT :limit
        """,
    )
    suspend fun searchPassageMatches(
        normalizedQuery: String,
        matchLengthCodePoints: Int,
        snippetContextCharacters: Int,
        matchesPerChapter: Int,
        chapterLimit: Int,
        bookId: String?,
        limit: Int,
    ): List<SearchPassageMatchEntity>

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
