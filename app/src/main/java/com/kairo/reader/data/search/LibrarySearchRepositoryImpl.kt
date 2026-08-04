package com.kairo.reader.data.search

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.core.text.TokenTextPositionResolver
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.local.SavedAnnotationDao
import com.kairo.reader.data.local.SearchDao
import com.kairo.reader.data.local.SearchableChapterEntity
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.withContext

class LibrarySearchRepositoryImpl(
    private val searchDao: SearchDao,
    private val annotationDao: SavedAnnotationDao,
    private val bookRepository: BookRepository,
    private val tokenRepository: TokenRepository,
    private val dispatcherProvider: DispatcherProvider,
) : LibrarySearchRepository {
    override suspend fun search(
        query: String,
        bookId: String?,
    ): List<LibrarySearchResult> =
        withContext(dispatcherProvider.io) {
            val normalized = query.trim()
            if (normalized.length < MIN_QUERY_LENGTH) return@withContext emptyList()
            val pattern = normalized.toSqlLikePattern()
            buildList {
                if (bookId == null) {
                    addAll(searchBookTitles(pattern))
                }
                addAll(searchPassages(normalized, pattern, bookId))
                if (bookId == null) {
                    addAll(searchSaved(pattern))
                }
            }.take(MAX_RESULTS)
        }

    private suspend fun searchBookTitles(pattern: String): List<LibrarySearchResult> =
        searchDao.searchBooks(pattern, BOOK_RESULT_LIMIT).map { book ->
            LibrarySearchResult(
                id = "book:${book.id}",
                kind = LibrarySearchResultKind.BOOK,
                bookId = BookId(book.id),
                bookTitle = book.title,
                chapterIndex = 0,
                chapterTitle = null,
                tokenIndex = 0,
                title = book.title,
                snippet = book.authors.joinToString(", "),
            )
        }

    private suspend fun searchPassages(
        query: String,
        pattern: String,
        bookId: String?,
    ): List<LibrarySearchResult> =
        searchDao
            .searchChapters(pattern, bookId, CHAPTER_RESULT_LIMIT)
            .flatMap { chapter -> chapter.toPassageResults(query) }

    private suspend fun SearchableChapterEntity.toPassageResults(
        query: String,
    ): List<LibrarySearchResult> {
        val chapter = bookRepository.getChapter(BookId(bookId), chapterIndex)
        val tokens = tokenRepository.getTokens(BookId(bookId), chapterIndex, chapter)
        return findSearchMatchOffsets(plainText, query, MATCHES_PER_CHAPTER).mapIndexed { index, offset ->
            val tokenIndex =
                TokenTextPositionResolver.resolveTokenIndex(
                    plainText = plainText,
                    tokens = tokens,
                    characterOffset = offset,
                )
            LibrarySearchResult(
                id = "passage:$bookId:$chapterIndex:$offset:$index",
                kind = LibrarySearchResultKind.PASSAGE,
                bookId = BookId(bookId),
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                tokenIndex = tokenIndex,
                title = chapterTitle?.takeIf(String::isNotBlank) ?: bookTitle,
                snippet = buildSearchSnippet(plainText, offset, query.length, SNIPPET_CONTEXT_CHARS),
            )
        }
    }

    private suspend fun searchSaved(pattern: String): List<LibrarySearchResult> =
        annotationDao.searchWithBook(pattern, SAVED_RESULT_LIMIT).map { item ->
            val annotation = item.annotation
            LibrarySearchResult(
                id = "saved:${annotation.id}",
                kind = LibrarySearchResultKind.SAVED,
                bookId = BookId(annotation.bookId),
                bookTitle = item.book.title,
                chapterIndex = annotation.chapterIndex,
                chapterTitle = null,
                tokenIndex = annotation.startTokenIndex,
                title = annotation.note.takeIf(String::isNotBlank) ?: item.book.title,
                snippet = annotation.selectedText,
            )
        }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_RESULTS = 100
        const val BOOK_RESULT_LIMIT = 20
        const val CHAPTER_RESULT_LIMIT = 40
        const val SAVED_RESULT_LIMIT = 20
        const val MATCHES_PER_CHAPTER = 3
        const val SNIPPET_CONTEXT_CHARS = 56
    }
}
