package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.ReadingSessionMode
import com.kairo.reader.data.sessions.ReadingSessionFactory
import com.kairo.reader.data.sessions.ReadingSessionDraft
import com.kairo.reader.data.sessions.ReadingSessionLocation
import com.kairo.reader.data.sessions.ReadingSessionTracker
import com.kairo.reader.data.sessions.estimateWordsRead
import com.kairo.reader.ui.reader.ReaderUiState

@Composable
internal fun RecordReaderSessionEffect(
    container: KairoApplication,
    bookId: BookId,
    hasInitialized: Boolean,
    readerState: ReaderUiState,
    lifecycleOwner: LifecycleOwner,
) {
    var sessionCapture by remember(bookId) { mutableStateOf<ReaderSessionCapture?>(null) }
    LaunchedEffect(hasInitialized, readerState.chapterData) {
        val chapterData = readerState.chapterData
        if (hasInitialized && chapterData != null && sessionCapture == null) {
            val now = System.currentTimeMillis()
            sessionCapture =
                ReaderSessionCapture(
                    startPosition =
                    ReadingPosition(
                        bookId = bookId,
                        chapterIndex = readerState.chapterIndex,
                        tokenIndex = readerState.focusIndex,
                        wordIndex = resolveWordIndex(chapterData.wordCountByToken, readerState.focusIndex),
                    ),
                    tracker =
                    ReadingSessionTracker(
                        startedAt = now,
                        initiallyActive =
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                    ),
                )
        }
    }
    val latestReaderState by rememberUpdatedState(readerState)
    val latestSessionCapture by rememberUpdatedState(sessionCapture)
    DisposableEffect(lifecycleOwner, bookId) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START ->
                        latestSessionCapture?.tracker?.setActive(true, System.currentTimeMillis())
                    Lifecycle.Event.ON_STOP ->
                        latestSessionCapture?.tracker?.setActive(false, System.currentTimeMillis())
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recordReaderSession(
                container = container,
                bookId = bookId,
                capture = latestSessionCapture,
                endState = latestReaderState,
            )
        }
    }
}

private data class ReaderSessionCapture(
    val startPosition: ReadingPosition,
    val tracker: ReadingSessionTracker,
)

private fun recordReaderSession(
    container: KairoApplication,
    bookId: BookId,
    capture: ReaderSessionCapture?,
    endState: ReaderUiState,
) {
    val finishedCapture = capture ?: return
    val chapterData = endState.chapterData ?: return
    val endedAt = System.currentTimeMillis()
    finishedCapture.tracker.setActive(false, endedAt)
    val endWordIndex = resolveWordIndex(chapterData.wordCountByToken, endState.focusIndex)
    val session =
        ReadingSessionFactory.create(
            ReadingSessionDraft(
                bookId = bookId,
                mode = ReadingSessionMode.READER,
                startedAt = finishedCapture.tracker.startedAt,
                endedAt = endedAt,
                activeDurationMs = finishedCapture.tracker.activeDurationMs(endedAt),
                start =
                ReadingSessionLocation(
                    finishedCapture.startPosition.chapterIndex,
                    finishedCapture.startPosition.tokenIndex,
                ),
                end = ReadingSessionLocation(endState.chapterIndex, endState.focusIndex),
                wordsRead =
                estimateWordsRead(
                    bookWordCounts = endState.bookWordCounts,
                    startChapterIndex = finishedCapture.startPosition.chapterIndex,
                    startWordIndex = finishedCapture.startPosition.wordIndex,
                    endChapterIndex = endState.chapterIndex,
                    endWordIndex = endWordIndex,
                ),
                isWordCountEstimated = true,
            ),
        )
    if (session != null) container.recordReadingSession(session)
}
