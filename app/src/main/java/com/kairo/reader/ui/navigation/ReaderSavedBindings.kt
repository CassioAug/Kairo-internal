package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.LibrarySearchResult
import com.kairo.reader.core.model.SaveAnnotationRequest
import com.kairo.reader.core.model.SavedAnnotation
import java.util.UUID
import kotlinx.coroutines.launch

internal data class ReaderSavedBindings(
    val annotations: List<SavedAnnotation>,
    val searchResults: List<LibrarySearchResult>,
    val isSearching: Boolean,
    val onSearch: (String) -> Unit,
    val onSaveAnnotation: (SaveAnnotationRequest) -> Unit,
)

@Composable
internal fun rememberReaderSavedBindings(
    container: KairoApplication,
    bookId: BookId,
    chapterIndex: Int,
    onShowUserMessage: (String) -> Unit,
): ReaderSavedBindings {
    val annotations by
        container.savedAnnotationRepository.observeForBook(bookId).collectAsState(
            initial = emptyList(),
        )
    val coroutineScope = rememberCoroutineScope()
    val resources = LocalResources.current
    var searchResults by remember { mutableStateOf<List<LibrarySearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchRequestId by remember { mutableIntStateOf(0) }
    return ReaderSavedBindings(
        annotations = annotations,
        searchResults = searchResults,
        isSearching = isSearching,
        onSearch = { query ->
            val requestId = ++searchRequestId
            isSearching = true
            coroutineScope.launch {
                val results =
                    runCatching { container.searchRepository.search(query, bookId.value) }
                        .getOrDefault(emptyList())
                if (requestId == searchRequestId) {
                    searchResults = results
                    isSearching = false
                }
            }
        },
        onSaveAnnotation = { request ->
            coroutineScope.launch {
                val now = System.currentTimeMillis()
                container.savedAnnotationRepository.save(
                    request.toSavedAnnotation(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        now = now,
                    ),
                )
                onShowUserMessage(resources.getString(R.string.toast_passage_saved))
            }
        },
    )
}

private fun SaveAnnotationRequest.toSavedAnnotation(
    id: String,
    bookId: BookId,
    chapterIndex: Int,
    now: Long,
): SavedAnnotation =
    SavedAnnotation(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        startTokenIndex = startTokenIndex,
        endTokenIndex = endTokenIndex,
        selectedText = selectedText,
        note = note,
        color = color,
        kind = kind,
        createdAt = now,
        updatedAt = now,
    )
