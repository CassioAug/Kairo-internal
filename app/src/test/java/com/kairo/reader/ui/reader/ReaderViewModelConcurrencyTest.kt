package com.kairo.reader.ui.reader

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelConcurrencyTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadAndPreload_doNotCrash() = runTest(testDispatcher) {
        val chapters =
            listOf(
                Chapter(index = 0, title = "One", htmlContent = "", plainText = "Hello world"),
                Chapter(index = 1, title = "Two", htmlContent = "", plainText = "Next chapter"),
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = chapters,
                coverImage = null,
            )
        val repository = FakeBookRepository(book, chapters)
        val tokenRepository = FakeTokenRepository()
        val dispatcherProvider =
            object : DispatcherProvider {
                override val default = testDispatcher
                override val io = testDispatcher
            }

        val viewModel = ReaderViewModel(repository, tokenRepository, dispatcherProvider)

        viewModel.loadBook(book, initialChapterIndex = 0, initialFocusIndex = 0)
        advanceUntilIdle()
        viewModel.loadChapter(0)
        viewModel.loadChapter(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.chapterData)
    }

    @Test
    fun loadBook_restoresInitialFocusIndex() = runTest(testDispatcher) {
        val chapters =
            listOf(
                Chapter(index = 0, title = "One", htmlContent = "", plainText = "Hello bright world"),
            )
        val book =
            Book(
                id = BookId("book-1"),
                title = "Test Book",
                authors = listOf("Author"),
                chapters = chapters,
                coverImage = null,
            )
        val repository = FakeBookRepository(book, chapters)
        val tokenRepository = FakeTokenRepository()
        val dispatcherProvider =
            object : DispatcherProvider {
                override val default = testDispatcher
                override val io = testDispatcher
            }
        val viewModel = ReaderViewModel(repository, tokenRepository, dispatcherProvider)

        viewModel.loadBook(book, initialChapterIndex = 0, initialFocusIndex = 1)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.focusIndex)
    }

    @Test
    fun loadBook_convertsInitialSearchCodePointOffsetBeforeResolvingFocus() =
        runTest(testDispatcher) {
            val plainText = "😀😀😀 abc needle"
            val chapter =
                Chapter(index = 0, title = "One", htmlContent = "", plainText = plainText)
            val book =
                Book(
                    id = BookId("book-1"),
                    title = "Test Book",
                    authors = listOf("Author"),
                    chapters = listOf(chapter),
                    coverImage = null,
                )
            val tokens =
                listOf(
                    Token(text = "😀", type = TokenType.WORD),
                    Token(text = "😀", type = TokenType.WORD),
                    Token(text = "😀", type = TokenType.WORD),
                    Token(text = "abc", type = TokenType.WORD),
                    Token(text = "needle", type = TokenType.WORD),
                )
            val dispatcherProvider =
                object : DispatcherProvider {
                    override val default = testDispatcher
                    override val io = testDispatcher
                }
            val viewModel =
                ReaderViewModel(
                    FakeBookRepository(book, listOf(chapter)),
                    FakeTokenRepository(tokens),
                    dispatcherProvider,
                )

            viewModel.loadBook(
                book = book,
                initialChapterIndex = 0,
                initialSearchCodePointOffset = 8,
            )
            advanceUntilIdle()

            assertEquals(4, viewModel.uiState.value.focusIndex)
        }
}

private class FakeBookRepository(private val book: Book, private val chapters: List<Chapter>,) : BookRepository {
    override suspend fun importBook(uri: android.net.Uri): BookImportResult =
        BookImportResult(book = book, alreadyImported = false)

    override suspend fun importUrl(rawUrl: String): BookImportResult =
        BookImportResult(book = book, alreadyImported = false)

    override suspend fun importText(request: TextImportRequest): BookImportResult =
        BookImportResult(book = book, alreadyImported = false)

    override suspend fun getBook(bookId: BookId): Book = book

    override suspend fun getChapter(
        bookId: BookId,
        chapterIndex: Int,
    ): Chapter = chapters[chapterIndex]

    override suspend fun updateChapterWordCount(
        bookId: BookId,
        chapterIndex: Int,
        wordCount: Int,
    ) = Unit

    override suspend fun getBookLanguageTag(bookId: BookId): String? = null

    override fun observeBooks(): Flow<List<Book>> = flowOf(listOf(book))
}

private class FakeTokenRepository(
    private val tokens: List<Token> =
    listOf(
        Token(text = "Hello", type = TokenType.WORD),
        Token(text = "world", type = TokenType.WORD),
    ),
) : TokenRepository {
    override suspend fun getTokens(
        bookId: BookId,
        chapterIndex: Int,
        chapter: Chapter?,
    ): List<Token> = tokens
}
