package com.kairo.reader.data.search

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.LibrarySearchResultKind
import com.kairo.reader.data.local.SavedAnnotationDao
import com.kairo.reader.data.local.SearchDao
import com.kairo.reader.data.local.SearchPassageMatchEntity
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class LibrarySearchRepositoryImpl(
    private val searchDao: SearchDao,
    private val annotationDao: SavedAnnotationDao,
    private val dispatcherProvider: DispatcherProvider,
) : LibrarySearchRepository {
    override suspend fun search(
        query: String,
        bookId: String?,
    ): List<LibrarySearchResult> =
        withContext(dispatcherProvider.io) {
            val normalized = normalizeLibrarySearchQuery(query)
            if (normalized.length < LibrarySearchConstraints.MIN_QUERY_LENGTH) return@withContext emptyList()
            currentCoroutineContext().ensureActive()
            if (bookId != null) {
                return@withContext searchPassages(normalized, bookId)
                    .take(LibrarySearchConstraints.MAX_RESULTS)
            }
            coroutineScope {
                // Start the cheap result groups before the complete-text passage query.
                val books = async { searchBookTitles(normalized.toSqlLikePattern()) }
                val saved = async { searchSaved(normalized.toSqlLikePattern()) }
                val passages = async { searchPassages(normalized, bookId = null) }
                fairMergeSearchResults(
                    groups = listOf(books.await(), passages.await(), saved.await()),
                    limit = LibrarySearchConstraints.MAX_RESULTS,
                )
            }
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
                endTokenIndex = 0,
                title = book.title,
                snippet = book.authors.joinToString(", "),
            )
        }

    private suspend fun searchPassages(
        query: String,
        bookId: String?,
    ): List<LibrarySearchResult> {
        val normalizedQuery = query.lowercase(Locale.ROOT)
        val matchLengthCodePoints =
            normalizedQuery.codePointCount(0, normalizedQuery.length)
        return searchDao
            .searchPassageMatches(
                normalizedQuery = normalizedQuery,
                matchLengthCodePoints = matchLengthCodePoints,
                snippetContextCharacters = SNIPPET_CONTEXT_CHARS,
                matchesPerChapter = MATCHES_PER_CHAPTER,
                chapterLimit = PASSAGE_CHAPTER_LIMIT,
                bookId = bookId,
                limit = PASSAGE_RESULT_LIMIT,
            ).map { match ->
                currentCoroutineContext().ensureActive()
                match.toSearchResult()
            }
    }

    private fun SearchPassageMatchEntity.toSearchResult(): LibrarySearchResult =
        LibrarySearchResult(
            id = "passage:$bookId:$chapterIndex:$matchStartCodePointOffset",
            kind = LibrarySearchResultKind.PASSAGE,
            bookId = BookId(bookId),
            bookTitle = bookTitle,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            tokenIndex = 0,
            endTokenIndex = 0,
            matchStartCodePointOffset = matchStartCodePointOffset,
            matchLengthCodePoints = matchLengthCodePoints,
            title = chapterTitle?.takeIf(String::isNotBlank) ?: bookTitle,
            snippet = boundedSnippet(),
        )

    private fun SearchPassageMatchEntity.boundedSnippet(): String {
        val body = snippetText.replace(WHITESPACE, " ").trim()
        return buildString {
            if (snippetStartCodePointOffset > 0) append(ELLIPSIS)
            append(body)
            val snippetLengthCodePoints = snippetText.codePointCount(0, snippetText.length)
            if (
                snippetStartCodePointOffset + snippetLengthCodePoints <
                chapterLengthCodePoints
            ) {
                append(ELLIPSIS)
            }
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
                endTokenIndex = annotation.endTokenIndex,
                title = annotation.note.takeIf(String::isNotBlank) ?: item.book.title,
                snippet = annotation.selectedText,
            )
        }

    private companion object {
        const val BOOK_RESULT_LIMIT = 20
        const val PASSAGE_RESULT_LIMIT = 60
        const val PASSAGE_CHAPTER_LIMIT = 32
        const val SAVED_RESULT_LIMIT = 20
        const val MATCHES_PER_CHAPTER = 3
        const val SNIPPET_CONTEXT_CHARS = 56
        const val ELLIPSIS = "…"
        val WHITESPACE = Regex("\\s+")
    }
}
