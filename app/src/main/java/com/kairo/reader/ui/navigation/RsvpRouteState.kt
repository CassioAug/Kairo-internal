package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.Token
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal data class RsvpRouteData(
    val tokens: List<Token>,
    val chapterCount: Int,
    val savedResumePosition: ReadingPosition?,
    val languageTag: String?,
)

@Composable
internal fun rememberRsvpRouteData(
    container: KairoApplication,
    bookId: String,
    chapterIndex: Int,
    safeStartIndex: Int,
): RsvpRouteData {
    val bookIdValue = remember(bookId) { BookId(bookId) }
    val launchSnapshotTokens =
        remember(bookId, chapterIndex) {
            RsvpLaunchSnapshotStore.tokensFor(bookId, chapterIndex)
        }
    DisposableEffect(bookId, chapterIndex) {
        onDispose {
            RsvpLaunchSnapshotStore.clear(bookId, chapterIndex)
        }
    }
    val initialRouteData =
        RsvpRouteData(
            tokens = launchSnapshotTokens,
            chapterCount = chapterIndex + 1,
            savedResumePosition = null,
            languageTag = null,
        )

    val routeData by produceState(
        initialValue = initialRouteData,
        bookId,
        chapterIndex,
        safeStartIndex,
    ) {
        value = initialRouteData
        coroutineScope {
            val loadedTokensDeferred =
                async {
                    runCatching {
                        container.tokenRepository.getTokens(bookIdValue, chapterIndex)
                    }.getOrElse { emptyList() }
                }
            val chapterCountDeferred =
                async {
                    runCatching {
                        container.bookRepository.getBook(bookIdValue).chapters.size
                    }.getOrDefault(chapterIndex + 1)
                }
            val savedResumePositionDeferred =
                async {
                    runCatching {
                        container.readingPositionRepository.getPosition(bookIdValue)
                    }.getOrNull()
                }
            val languageTagDeferred =
                async {
                    runCatching {
                        container.bookRepository.getBookLanguageTag(bookIdValue)
                    }.getOrNull()
                }

            val loadedTokens = loadedTokensDeferred.await()
            if (loadedTokens.isNotEmpty() || value.tokens.isEmpty()) {
                value = value.copy(tokens = loadedTokens)
            }
            if (loadedTokens.isNotEmpty()) {
                RsvpLaunchSnapshotStore.clear(bookId, chapterIndex)
            }

            value =
                value.copy(
                    chapterCount = chapterCountDeferred.await(),
                    savedResumePosition = savedResumePositionDeferred.await(),
                    languageTag = languageTagDeferred.await(),
                )
        }
    }

    return routeData
}
