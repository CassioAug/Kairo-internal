package com.kairo.reader.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.data.search.toSqlLikePattern
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceIntegrityTest {
    private lateinit var database: KairoDatabase

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    KairoDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun literalLikeCharactersDoNotActAsWildcards() =
        runBlocking {
            insertBook()
            val annotations =
                listOf(
                    annotation("percent", "100% complete"),
                    annotation("percent-noise", "100x complete"),
                    annotation("underscore", "under_score"),
                    annotation("underscore-noise", "underXscore"),
                    annotation("slash", "path\\name"),
                    annotation("slash-noise", "pathXname"),
                )
            annotations.forEach { assertTrue(database.savedAnnotationDao().upsert(it)) }

            assertEquals(
                listOf("percent"),
                searchSaved("%").map { it.annotation.id },
            )
            assertEquals(
                listOf("underscore"),
                searchSaved("_").map { it.annotation.id },
            )
            assertEquals(
                listOf("slash"),
                searchSaved("\\").map { it.annotation.id },
            )
        }

    @Test
    fun reimportPreservesDependentRowsAndCompletionStateWithoutLoadingCoverBlobs() =
        runBlocking {
            insertBook(coverImage = ByteArray(32) { 7 })
            database.bookDao().setCompleted(BOOK_ID, true)
            assertTrue(database.savedAnnotationDao().upsert(annotation("saved", "passage")))
            assertTrue(database.readingSessionDao().insert(session("session")))

            insertBook(title = "Updated title", coverImage = ByteArray(32) { 9 })

            assertEquals("Updated title", database.bookDao().getBook(BOOK_ID)?.title)
            assertTrue(database.bookDao().getBook(BOOK_ID)?.isCompleted == true)
            assertEquals(1, rowCount("saved_annotations"))
            assertEquals(1, rowCount("reading_sessions"))
            assertNull(searchSaved("passage").single().book.coverImage)
        }

    @Test
    fun lateWritesRequireAParentAndDeletingBookCascadesNewChildren() =
        runBlocking {
            assertFalse(database.savedAnnotationDao().upsert(annotation("orphan", "passage")))
            assertFalse(database.readingSessionDao().insert(session("orphan-session")))

            insertBook()
            assertTrue(database.savedAnnotationDao().upsert(annotation("saved", "passage")))
            assertTrue(database.readingSessionDao().insert(session("session")))
            assertTrue(
                database.readingSessionDao().replaceCheckpoints(
                    sessionKey = "reader:$BOOK_ID",
                    entities = listOf(checkpoint()),
                )
            )

            database.bookDao().deleteBook(BOOK_ID)

            assertEquals(0, rowCount("saved_annotations"))
            assertEquals(0, rowCount("reading_sessions"))
            assertEquals(0, rowCount("reading_session_checkpoints"))
        }

    @Test
    fun passageSearchFindsLateOffsetsAndReturnsOnlyABoundedSnippet() =
        runBlocking {
            val prefix = "x".repeat(275_000)
            val plainText = "$prefix needle followed by a short ending"
            insertBook(plainText = plainText)

            val match =
                database.searchDao()
                    .searchPassageMatches(
                        normalizedQuery = "needle",
                        matchLengthCodePoints = "needle".length,
                        snippetContextCharacters = 24,
                        matchesPerChapter = 3,
                        chapterLimit = 10,
                        bookId = BOOK_ID,
                        limit = 10,
                    ).single()

            assertEquals(prefix.length + 1, match.matchStartCodePointOffset)
            assertTrue(match.snippetText.contains("needle"))
            assertTrue(match.snippetText.length <= "needle".length + 48)
            assertEquals(plainText.length, match.chapterLengthCodePoints)
        }

    @Test
    fun passageSearchReportsOffsetsAndLengthsInUnicodeCodePoints() =
        runBlocking {
            val plainText = "😀😀😀 abc needle and 😀query"
            insertBook(plainText = plainText)

            val needle =
                database.searchDao()
                    .searchPassageMatches(
                        normalizedQuery = "needle",
                        matchLengthCodePoints = 6,
                        snippetContextCharacters = 12,
                        matchesPerChapter = 3,
                        chapterLimit = 10,
                        bookId = BOOK_ID,
                        limit = 10,
                    ).single()
            val supplementaryQuery =
                database.searchDao()
                    .searchPassageMatches(
                        normalizedQuery = "😀query",
                        matchLengthCodePoints = 6,
                        snippetContextCharacters = 12,
                        matchesPerChapter = 3,
                        chapterLimit = 10,
                        bookId = BOOK_ID,
                        limit = 10,
                    ).single()

            assertEquals(8, needle.matchStartCodePointOffset)
            assertEquals(11, plainText.indexOf("needle"))
            assertEquals(6, needle.matchLengthCodePoints)
            assertEquals(19, supplementaryQuery.matchStartCodePointOffset)
            assertEquals(6, supplementaryQuery.matchLengthCodePoints)
            assertEquals(plainText.codePointCount(0, plainText.length), needle.chapterLengthCodePoints)
        }

    private suspend fun insertBook(
        title: String = "Book",
        coverImage: ByteArray? = null,
        plainText: String = "Chapter",
    ) {
        database.bookDao().insertBook(
            book =
                BookEntity(
                    id = BOOK_ID,
                    title = title,
                    authors = listOf("Author"),
                    languageTag = "en",
                    coverImage = coverImage,
                ),
            chapters =
                listOf(
                    ChapterEntity(
                        bookId = BOOK_ID,
                        index = 0,
                        title = "Chapter",
                        htmlContent = "<p>Chapter</p>",
                        plainText = plainText,
                    )
                ),
            tableOfContentsEntries = emptyList(),
        )
    }

    private suspend fun searchSaved(query: String): List<SavedAnnotationWithBookEntity> =
        database.savedAnnotationDao().searchWithBook(query.toSqlLikePattern(), limit = 20)

    private fun rowCount(table: String): Int =
        database.query("SELECT COUNT(*) FROM $table", emptyArray()).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun annotation(
        id: String,
        selectedText: String,
    ): SavedAnnotationEntity =
        SavedAnnotationEntity(
            id = id,
            bookId = BOOK_ID,
            chapterIndex = 0,
            startTokenIndex = 0,
            endTokenIndex = 1,
            selectedText = selectedText,
            note = "",
            color = "YELLOW",
            kind = "HIGHLIGHT",
            createdAt = 1L,
            updatedAt = 1L,
        )

    private fun session(id: String): ReadingSessionEntity =
        ReadingSessionEntity(
            id = id,
            bookId = BOOK_ID,
            mode = "READER",
            startedAt = 1L,
            endedAt = 300_001L,
            activeDurationMs = 300_000L,
            startChapterIndex = 0,
            startTokenIndex = 0,
            endChapterIndex = 0,
            endTokenIndex = 20,
            wordsRead = 20,
            effectiveWpm = 4,
            isWordCountEstimated = true,
        )

    private fun checkpoint(): ReadingSessionCheckpointEntity =
        ReadingSessionCheckpointEntity(
            id = "checkpoint",
            sessionKey = "reader:$BOOK_ID",
            logicalSessionId = "logical",
            bookId = BOOK_ID,
            mode = "READER",
            logicalStartedAt = 1L,
            dayStartedAt = 0L,
            startedAt = 1L,
            endedAt = 2L,
            activeDurationMs = 1L,
            startChapterIndex = 0,
            startTokenIndex = 0,
            endChapterIndex = 0,
            endTokenIndex = 1,
            wordsRead = 1,
            isWordCountEstimated = true,
            lastReaderWordIndex = 1,
        )

    private companion object {
        const val BOOK_ID = "book"
    }
}
