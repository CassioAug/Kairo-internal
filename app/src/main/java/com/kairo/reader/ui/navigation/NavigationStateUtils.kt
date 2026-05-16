package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.wordIndexForToken
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import kotlinx.coroutines.withContext

internal fun resolveWordIndex(
    wordCountByToken: IntArray?,
    tokenIndex: Int,
): Int {
    if (wordCountByToken == null || wordCountByToken.isEmpty()) return -1
    return wordIndexForToken(wordCountByToken, tokenIndex)
}

@Composable
internal fun rememberReaderEstimatedWpm(
    baseConfig: RsvpConfig,
    fallbackEstimatedWpm: Int,
    dispatcherProvider: DispatcherProvider,
    languageTag: String? = null,
): Int {
    val estimatedWpm by produceState(
        initialValue = fallbackEstimatedWpm,
        baseConfig,
        fallbackEstimatedWpm,
        languageTag,
    ) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEstimatedReadingPace.estimateWpm(
                    config = baseConfig,
                    fallbackEstimatedWpm = fallbackEstimatedWpm,
                    languageTag = languageTag,
                )
            }
    }
    return estimatedWpm
}
