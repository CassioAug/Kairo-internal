package com.kairo.reader.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.data.search.LibrarySearchRepositoryImpl
import com.kairo.reader.data.search.toSqlLikePattern
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
    fun replaceCheckpointsRejectsInvalidBatchesWithoutDeletingExistingData() =
        runBlocking {
            insertBook()
            insertBook(bookId = OTHER_BOOK_ID, title = "Other book")
            val sessionDao = database.readingSessionDao()
            val existing = checkpoint(id = "existing")
            assertTrue(sessionDao.replaceCheckpoints(SESSION_KEY, listOf(existing)))

            val invalidBatches =
                listOf(
                    listOf(checkpoint(id = "wrong-key", sessionKey = "reader:wrong")),
                    listOf(
                        checkpoint(id = "book-a"),
                        checkpoint(
                            id = "book-b",
                            bookId = OTHER_BOOK_ID,
                            sessionKey = SESSION_KEY,
                        ),
                    ),
                    listOf(
                        checkpoint(id = "mode-a"),
                        checkpoint(id = "mode-b", mode = "RSVP"),
                    ),
                    listOf(
                        checkpoint(
                            id = "wrong-book-a",
                            bookId = OTHER_BOOK_ID,
                            sessionKey = SESSION_KEY,
                        ),
                        checkpoint(
                            id = "wrong-book-b",
                            bookId = OTHER_BOOK_ID,
                            sessionKey = SESSION_KEY,
                        ),
                    ),
                    listOf(
                        checkpoint(id = "wrong-mode-a", mode = "RSVP"),
                        checkpoint(id = "wrong-mode-b", mode = "RSVP"),
                    ),
                )

            invalidBatches.forEach { invalidBatch ->
                assertFalse(sessionDao.replaceCheckpoints(SESSION_KEY, invalidBatch))
                assertEquals(listOf(existing), sessionDao.getAllCheckpoints())
            }
        }

    @Test
    fun finalizeCheckpointsRejectsInvalidBatchesWithoutMutatingData() =
        runBlocking {
            insertBook()
            insertBook(bookId = OTHER_BOOK_ID, title = "Other book")
            val sessionDao = database.readingSessionDao()
            val existingCheckpoint = checkpoint(id = "existing")
            assertTrue(sessionDao.replaceCheckpoints(SESSION_KEY, listOf(existingCheckpoint)))
            assertTrue(sessionDao.insert(session("existing-session")))

            val invalidBatches =
                listOf(
                    listOf(
                        session("book-a"),
                        session("book-b", bookId = OTHER_BOOK_ID),
                    ),
                    listOf(
                        session("mode-a"),
                        session("mode-b", mode = "RSVP"),
                    ),
                    listOf(
                        session("wrong-book-a", bookId = OTHER_BOOK_ID),
                        session("wrong-book-b", bookId = OTHER_BOOK_ID),
                    ),
                    listOf(
                        session("wrong-mode-a", mode = "RSVP"),
                        session("wrong-mode-b", mode = "RSVP"),
                    ),
                )

            invalidBatches.forEach { invalidBatch ->
                assertFalse(sessionDao.finalizeCheckpoints(SESSION_KEY, invalidBatch))
                assertEquals(1, rowCount("reading_sessions"))
                assertEquals(listOf(existingCheckpoint), sessionDao.getAllCheckpoints())
            }
        }

    @Test
    fun finalizeCheckpointsPreservesDataWhenTheBookIsMissing() =
        runBlocking {
            insertBook()
            val sessionDao = database.readingSessionDao()
            val existingSession = session("existing-session")
            val existingCheckpoint = checkpoint(id = "existing")
            assertTrue(sessionDao.insert(existingSession))
            assertTrue(sessionDao.replaceCheckpoints(SESSION_KEY, listOf(existingCheckpoint)))
            assertTrue(sessionDao.getCheckpoints(MISSING_BOOK_SESSION_KEY).isEmpty())

            assertFalse(
                sessionDao.finalizeCheckpoints(
                    MISSING_BOOK_SESSION_KEY,
                    listOf(session("missing-book-session", bookId = MISSING_BOOK_ID)),
                )
            )
            assertEquals(1, rowCount("reading_sessions"))
            assertEquals(listOf(existingCheckpoint), sessionDao.getAllCheckpoints())
        }

    @Test
    fun emptyFinalizationStillClearsValidCheckpoints() =
        runBlocking {
            insertBook()
            val sessionDao = database.readingSessionDao()
            assertTrue(sessionDao.replaceCheckpoints(SESSION_KEY, listOf(checkpoint())))

            assertTrue(sessionDao.finalizeCheckpoints(SESSION_KEY, emptyList()))

            assertTrue(sessionDao.getAllCheckpoints().isEmpty())
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
        bookId: String = BOOK_ID,
        title: String = "Book",
        coverImage: ByteArray? = null,
        plainText: String = "Chapter",
    ) =
        insertBookWithChapters(
            bookId = bookId,
            title = title,
            coverImage = coverImage,
            chapterTexts = listOf(plainText),
        )

    private suspend fun insertBookWithChapters(
        bookId: String,
        title: String,
        chapterTexts: List<String>,
        coverImage: ByteArray? = null,
    ) {
        database.bookDao().insertBook(
            book =
                BookEntity(
                    id = bookId,
                    title = title,
                    authors = listOf("Author"),
                    languageTag = "en",
                    coverImage = coverImage,
                ),
            chapters =
                chapterTexts.mapIndexed { chapterIndex, chapterText ->
                    ChapterEntity(
                        bookId = bookId,
                        index = chapterIndex,
                        title = "Chapter $chapterIndex",
                        htmlContent = "<p>Chapter $chapterIndex</p>",
                        plainText = chapterText,
                    )
                },
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

    private fun session(
        id: String,
        bookId: String = BOOK_ID,
        mode: String = "READER",
    ): ReadingSessionEntity =
        ReadingSessionEntity(
            id = id,
            bookId = bookId,
            mode = mode,
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

    private fun checkpoint(
        id: String = "checkpoint",
        bookId: String = BOOK_ID,
        sessionKey: String = "reader:$bookId",
        mode: String = "READER",
    ): ReadingSessionCheckpointEntity =
        ReadingSessionCheckpointEntity(
            id = id,
            sessionKey = sessionKey,
            logicalSessionId = "logical",
            bookId = bookId,
            mode = mode,
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
        const val OTHER_BOOK_ID = "other-book"
        const val SESSION_KEY = "reader:$BOOK_ID"
        const val MISSING_BOOK_ID = "missing-book"
        const val MISSING_BOOK_SESSION_KEY = "reader:$MISSING_BOOK_ID"
        const val SAME_TITLE_BOOK_A = "same-title-a"
        const val SAME_TITLE_BOOK_B = "same-title-b"
        const val MAX_SEARCH_SNIPPET_LENGTH = 120
    }
}

private object AndroidTestDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}
