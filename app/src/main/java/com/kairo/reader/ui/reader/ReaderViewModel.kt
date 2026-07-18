package com.kairo.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.countWords
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.token.TokenRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGINATION_WORDS_MIN_DELTA = 14

/**
 * ViewModel for the Reader screen.
 * Handles chapter loading, tokenization, and paragraph computation off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    private val bookRepository: BookRepository,
    private val tokenRepository: TokenRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val chapterProcessor: ReaderChapterProcessor = ReaderChapterProcessor(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // LRU cache for processed chapters - avoids re-tokenizing when switching back
    private val chapterCache = ReaderChapterCache()
    private val tokenizationDispatcher = dispatcherProvider.default.limitedParallelism(1)

    // Thread-safe book reference for cross-coroutine access
    private val currentBook = AtomicReference<Book?>(null)
    private val chapterLoadSequence = AtomicInteger(0)

    // Pending focus index to apply after chapter loads (thread-safe for cross-coroutine access)
    private val pendingFocusIndex = AtomicReference<Int?>(null)
    private val pendingPageIndex = AtomicReference<Int?>(null)
    private val wordsPerPageTarget = AtomicReference(DEFAULT_WORDS_PER_PAGE)

    /**
     * Load a book and optionally jump to a specific chapter and focus position.
     */
    fun loadBook(
        book: Book,
        initialChapterIndex: Int = 0,
        initialFocusIndex: Int = 0,
    ) {
        currentBook.set(book)
        chapterLoadSequence.incrementAndGet()
        chapterCache.clear()
        pendingFocusIndex.set(if (initialFocusIndex > 0) initialFocusIndex else null)
        pendingPageIndex.set(null)
        _uiState.update { it.copy(bookWordCounts = emptyList(), bookTotalWords = 0) }
        loadBookWordCounts(book)
        loadChapter(initialChapterIndex)
    }

    private fun loadBookWordCounts(book: Book) {
        val bookId = book.id
        val initialCounts = book.chapters.map { it.wordCount }
        _uiState.update {
            it.copy(
                bookWordCounts = initialCounts,
                bookTotalWords = initialCounts.sum(),
            )
        }
        if (initialCounts.all { it > 0 } || book.chapters.isEmpty()) return

        viewModelScope.launch {
            val counts =
                runCatching {
                    withContext(dispatcherProvider.io) {
                        book.chapters.map { chapter ->
                            if (chapter.wordCount > 0) {
                                chapter.wordCount
                            } else {
                                val resolved =
                                    runCatching {
                                        bookRepository.getChapter(bookId, chapter.index)
                                    }.getOrNull()
                                val count =
                                    if (resolved == null) {
                                        0
                                    } else {
                                        countWords(resolved.plainText)
                                    }
                                if (count > 0) {
                                    bookRepository.updateChapterWordCount(
                                        bookId,
                                        chapter.index,
                                        count,
                                    )
                                }
                                count
                            }
                        }
                    }
                }.getOrNull() ?: emptyList()

            if (currentBook.get()?.id != bookId) return@launch
            val total = counts.sum()
            _uiState.update { it.copy(bookWordCounts = counts, bookTotalWords = total) }
        }
    }

    /**
     * Load a chapter by index. Shows loading state immediately,
     * then processes tokens in background.
     */
    fun loadChapter(
        chapterIndex: Int,
        initialFocusIndex: Int? = null,
        initialPageIndex: Int? = null,
    ) {
        val book = currentBook.get() ?: return

        if (chapterIndex !in book.chapters.indices) return
        val requestId = chapterLoadSequence.incrementAndGet()
        val requestedBookId = book.id

        if (initialFocusIndex != null) {
            pendingFocusIndex.set(initialFocusIndex)
        }
        when {
            initialPageIndex != null -> pendingPageIndex.set(initialPageIndex)
            initialFocusIndex == Int.MAX_VALUE -> pendingPageIndex.set(Int.MAX_VALUE)
        }

        // Check cache first - instant load if available
        val cached = chapterCache[chapterIndex]
        if (cached != null) {
            // Use pending focus if set, otherwise use first word
            val pageIdx = pendingPageIndex.getAndSet(null)
            val focusIdx =
                pendingFocusIndex.getAndSet(null)?.let { cached.tokens.nearestWordIndex(it) }
                    ?: cached.firstWordIndex.coerceAtLeast(0)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    chapterIndex = chapterIndex,
                    chapterData = cached,
                    chapterLoadError = null,
                    focusIndex = focusIdx,
                    pageIndexOverride = pageIdx,
                )
            }
            // Preload adjacent chapters in background
            preloadAdjacentChapters(chapterIndex)
        } else {
            // Not cached - show loading state immediately (UI stays responsive)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    chapterIndex = chapterIndex,
                    chapterData = null, // Clear old data while loading
                    chapterLoadError = null,
                )
            }

            viewModelScope.launch {
                val processed =
                    runCatching {
                        val chapter =
                            withContext(dispatcherProvider.io) {
                                bookRepository.getChapter(book.id, chapterIndex)
                            }
                        val tokens = tokenRepository.getTokens(book.id, chapterIndex, chapter)
                        processChapter(chapter, tokens)
                    }
                val result = processed.getOrNull()
                val errorMessage =
                    processed.exceptionOrNull()?.message
                        ?.takeIf { it.isNotBlank() }
                        ?: if (processed.isFailure) DEFAULT_CHAPTER_LOAD_ERROR else null

                if (
                    chapterLoadSequence.get() != requestId ||
                    currentBook.get()?.id != requestedBookId
                ) {
                    return@launch
                }

                // Cache the result
                result?.let {
                    chapterCache[chapterIndex] = it
                }

                // Use pending focus if set, otherwise use first word
                val focusIdx =
                    if (result != null) {
                        pendingFocusIndex.getAndSet(null)?.let { result.tokens.nearestWordIndex(it) }
                            ?: result.firstWordIndex.coerceAtLeast(0)
                    } else {
                        pendingFocusIndex.set(null)
                        pendingPageIndex.set(null)
                        0
                    }
                val pageIdx = if (result != null) pendingPageIndex.getAndSet(null) else null

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chapterData = result,
                        chapterLoadError = errorMessage,
                        focusIndex = focusIdx,
                        pageIndexOverride = pageIdx,
                    )
                }

                // Preload adjacent chapters after current one loads
                preloadAdjacentChapters(chapterIndex)
            }
        }
    }

    /**
     * Process a chapter on a background thread.
     * Returns null if chapter is empty.
     */
    private suspend fun processChapter(
        chapter: Chapter,
        tokens: List<Token>,
    ): ChapterData? =
        withContext(tokenizationDispatcher) {
            chapterProcessor.process(chapter, tokens, wordsPerPageTarget.get())
        }

    /**
     * Preload adjacent chapters in background so chapter switching feels instant.
     */
    private fun preloadAdjacentChapters(currentIndex: Int) {
        val book = currentBook.get() ?: return

        viewModelScope.launch(tokenizationDispatcher) {
            listOf(currentIndex + 1)
                .filter { it in book.chapters.indices }
                .filter { index ->
                    !chapterCache.contains(index)
                }
                .forEach { index ->
                    val chapter =
                        runCatching {
                            withContext(dispatcherProvider.io) {
                                bookRepository.getChapter(book.id, index)
                            }
                        }.getOrNull() ?: return@forEach

                    val tokens = runCatching {
                        tokenRepository.getTokens(book.id, index, chapter)
                    }.getOrNull()
                    if (tokens != null) {
                        processChapter(chapter, tokens)?.let { data ->
                            chapterCache[index] = data
                        }
                    }
                }
        }
    }

    fun setFocusIndex(index: Int) {
        _uiState.update { it.copy(focusIndex = index, pageIndexOverride = null) }
    }

    fun applyFocusIndex(index: Int) {
        val uiState = _uiState.value
        if (uiState.isLoading || uiState.chapterData == null) {
            pendingFocusIndex.set(index)
            pendingPageIndex.set(null)
        }
        _uiState.update { it.copy(focusIndex = index, pageIndexOverride = null) }
    }

    fun setPageIndex(
        pageIndex: Int,
        focusIndex: Int,
    ) {
        val uiState = _uiState.value
        if (uiState.isLoading || uiState.chapterData == null) {
            pendingFocusIndex.set(focusIndex)
            pendingPageIndex.set(pageIndex)
        }
        _uiState.update {
            it.copy(
                focusIndex = focusIndex,
                pageIndexOverride = pageIndex,
            )
        }
    }

    fun updatePaginationMetrics(
        fontSizeSp: Float,
        viewportHeightDp: Int,
    ) {
        val resolvedWordsPerPage = chapterProcessor.wordsPerPage(fontSizeSp, viewportHeightDp)
        val previousWordsPerPage = wordsPerPageTarget.get()
        if (abs(resolvedWordsPerPage - previousWordsPerPage) < PAGINATION_WORDS_MIN_DELTA) return
        wordsPerPageTarget.set(resolvedWordsPerPage)

        chapterCache.transformAll { chapterProcessor.repage(it, resolvedWordsPerPage) }

        _uiState.update { state ->
            val chapterData = state.chapterData ?: return@update state
            state.copy(chapterData = chapterProcessor.repage(chapterData, resolvedWordsPerPage))
        }
    }

    @Suppress("unused")
    fun nextChapter() {
        val book = currentBook.get() ?: return
        val nextIndex = (_uiState.value.chapterIndex + 1).coerceAtMost(book.chapters.lastIndex)
        if (nextIndex != _uiState.value.chapterIndex) {
            loadChapter(nextIndex)
        }
    }

    @Suppress("unused")
    fun previousChapter() {
        val prevIndex = (_uiState.value.chapterIndex - 1).coerceAtLeast(0)
        if (prevIndex != _uiState.value.chapterIndex) {
            loadChapter(prevIndex)
        }
    }

    /**
     * Clear cache when ViewModel is cleared to free memory.
     */
    override fun onCleared() {
        super.onCleared()
        chapterCache.clear()
    }

    companion object {
        private const val DEFAULT_CHAPTER_LOAD_ERROR = "Chapter could not be loaded."

        fun factory(
            bookRepository: BookRepository,
            tokenRepository: TokenRepository,
            dispatcherProvider: DispatcherProvider,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
                        return ReaderViewModel(
                            bookRepository,
                            tokenRepository,
                            dispatcherProvider
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

// =============================================================================
// State classes
// =============================================================================
