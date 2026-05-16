package com.kairo.reader.data.rsvp

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token

data class RsvpFrameSet(
    val frames: List<RsvpFrame>,
    val baseTempoMs: Long,
    val frameIndexMap: RsvpFrameIndexMap = RsvpFrameIndexMap.from(frames),
)

interface RsvpFrameRepository {
    suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int = 0,
    ): RsvpFrameSet

    fun prefetchFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int = 0,
    ) {
        // Optional optimization; default no-op for implementations without prefetching.
    }

    suspend fun getPreviewFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
        maxTokenCount: Int = DEFAULT_PREVIEW_TOKEN_COUNT,
    ): RsvpFrameSet =
        RsvpFrameSet(frames = emptyList(), baseTempoMs = config.tempoMsPerWord)

    fun clearCache()

    companion object {
        const val DEFAULT_PREVIEW_TOKEN_COUNT = 320
    }
}
