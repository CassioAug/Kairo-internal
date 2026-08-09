package com.kairo.reader.data.search

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.data.local.BookEntity
import com.kairo.reader.data.local.SavedAnnotationDao
import com.kairo.reader.data.local.SavedAnnotationEntity
import com.kairo.reader.data.local.SavedAnnotationWithBookEntity
import com.kairo.reader.data.local.SearchDao
import com.kairo.reader.data.local.SearchPassageMatchEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySearchRepositoryImplTest {
    @Test
    fun searchStartsCheapGroupsFirstAndFairlyMergesOffsetOnlyPassages() =
        runTest {
            var booksStarted = false
            var savedStarted = false
            val searchDao =
                FakeSearchDao(
                    bookSearch = {
                        booksStarted = true
                        listOf(book())
                    },
                    passageSearch = {
                        assertTrue(booksStarted)
                        assertTrue(savedStarted)
                        listOf(passageMatch())
                    },
                )
            val annotationDao =
                FakeSavedAnnotationDao {
                    savedStarted = true
                    listOf(savedWithBook())
                }
            val repository = repository(searchDao, annotationDao)

            val results = repository.search("needle", bookId = null)

            assertEquals(
                listOf(
                    LibrarySearchResultKind.BOOK,
                    LibrarySearchResultKind.PASSAGE,
                    LibrarySearchResultKind.SAVED,
                ),
                results.map { it.kind },
            )
            val passage = results.single { it.kind == LibrarySearchResultKind.PASSAGE }
            assertEquals(300_123, passage.matchStartCodePointOffset)
            assertEquals("needle".length, passage.matchLengthCodePoints)
            assertEquals(0, passage.tokenIndex)
        }

    @Test
    fun cancellationStopsTheInFlightPassageQuery() =
        runTest {
            var passageStarted = false
            val repository =
                repository(
                    searchDao =
                    FakeSearchDao(
                        passageSearch = {
                            passageStarted = true
                            awaitCancellation()
                        },
                    ),
                    annotationDao = FakeSavedAnnotationDao(),
                )
            val searchJob = launch { repository.search("needle", bookId = null) }
            runCurrent()
            assertTrue(passageStarted)

            searchJob.cancelAndJoin()

            assertTrue(searchJob.isCancelled)
        }

    @Test
    fun supplementaryQueryLengthIsPassedToSqlInCodePoints() =
        runTest {
            val searchDao = FakeSearchDao()
            val repository = repository(searchDao, FakeSavedAnnotationDao())

            repository.search("😀needle", bookId = "book")

            assertEquals(7, searchDao.requestedMatchLengthCodePoints)
        }

    private fun repository(
        searchDao: SearchDao,
        annotationDao: SavedAnnotationDao,
    ): LibrarySearchRepositoryImpl =
        LibrarySearchRepositoryImpl(
            searchDao = searchDao,
            annotationDao = annotationDao,
            dispatcherProvider = UnconfinedDispatcherProvider,
        )
}

private class FakeSearchDao(
    private val bookSearch: suspend () -> List<BookEntity> = { emptyList() },
    private val passageSearch: suspend () -> List<SearchPassageMatchEntity> = { emptyList() },
) : SearchDao {
    var requestedMatchLengthCodePoints: Int? = null
        private set

    override suspend fun searchPassageMatches(
        normalizedQuery: String,
        matchLengthCodePoints: Int,
        snippetContextCharacters: Int,
        matchesPerChapter: Int,
        chapterLimit: Int,
        bookId: String?,
        limit: Int,
    ): List<SearchPassageMatchEntity> {
        requestedMatchLengthCodePoints = matchLengthCodePoints
        return passageSearch()
    }

    override suspend fun searchBooks(
        pattern: String,
        limit: Int,
    ): List<BookEntity> = bookSearch()
}

private class FakeSavedAnnotationDao(
    private val search: suspend () -> List<SavedAnnotationWithBookEntity> = { emptyList() },
) : SavedAnnotationDao {
    override suspend fun upsertInternal(entity: SavedAnnotationEntity) = Unit

    override suspend fun bookExists(bookId: String): Boolean = true

    override suspend fun delete(annotationId: String) = Unit

    override suspend fun deleteForBook(bookId: String) = Unit

    override fun observeForBook(bookId: String): Flow<List<SavedAnnotationEntity>> = emptyFlow()

    override fun observeWithBook(): Flow<List<SavedAnnotationWithBookEntity>> = emptyFlow()

    override suspend fun searchWithBook(
        pattern: String,
        limit: Int,
    ): List<SavedAnnotationWithBookEntity> = search()
}

private object UnconfinedDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}

private fun book(): BookEntity =
    BookEntity(
        id = "book",
        title = "Needle Book",
        authors = listOf("Author"),
        languageTag = "en",
        coverImage = null,
    )

private fun passageMatch(): SearchPassageMatchEntity =
    SearchPassageMatchEntity(
        bookId = "book",
        bookTitle = "Needle Book",
        chapterIndex = 3,
        chapterTitle = "Late chapter",
        matchStartCodePointOffset = 300_123,
        matchLengthCodePoints = "needle".length,
        snippetStartCodePointOffset = 300_100,
        snippetText = "before needle after",
        chapterLengthCodePoints = 400_000,
    )

private fun savedWithBook(): SavedAnnotationWithBookEntity =
    SavedAnnotationWithBookEntity(
        annotation =
            SavedAnnotationEntity(
                id = "saved",
                bookId = "book",
                chapterIndex = 1,
                startTokenIndex = 4,
                endTokenIndex = 5,
                selectedText = "needle",
                note = "Saved needle",
                color = "YELLOW",
                kind = "HIGHLIGHT",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        book = book(),
        chapterCount = 4,
    )
