package app.kairo.reader.data.rsvp

import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.RsvpConfig
import app.kairo.reader.core.model.RsvpFrame
import app.kairo.reader.core.model.Token

data class RsvpFrameSet(val frames: List<RsvpFrame>, val baseTempoMs: Long,)

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
