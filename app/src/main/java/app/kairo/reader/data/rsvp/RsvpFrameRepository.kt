package app.kairo.reader.data.rsvp

import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.RsvpConfig
import app.kairo.reader.core.model.RsvpFrame

data class RsvpFrameSet(val frames: List<RsvpFrame>, val baseTempoMs: Long,)

interface RsvpFrameRepository {
    suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
    ): RsvpFrameSet

    fun prefetchFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
    ) {
        // Optional optimization; default no-op for implementations without prefetching.
    }

    fun clearCache()
}
