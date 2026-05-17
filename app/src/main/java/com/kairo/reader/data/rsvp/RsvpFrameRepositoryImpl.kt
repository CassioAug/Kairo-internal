package com.kairo.reader.data.rsvp

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.core.rsvp.engine.frameTimingKey
import com.kairo.reader.core.rsvp.timing.RsvpSessionTimingPolicy
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpFrameRepositoryImpl(
    private val tokenRepository: TokenRepository,
    private val engine: RsvpEngine,
    dispatcherProvider: DispatcherProvider,
) : RsvpFrameRepository {
    private enum class CacheMode {
        SEGMENT_BASE,
        EXACT_PLAYBACK,
    }

    private data class CacheKey(
        val bookId: String,
        val chapterIndex: Int,
        val timingConfig: RsvpConfig,
        val startIndex: Int,
        val mode: CacheMode,
    )

    private val cache =
        object : LinkedHashMap<CacheKey, RsvpFrameSet>(
            CACHE_INITIAL_CAPACITY,
            CACHE_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<CacheKey, RsvpFrameSet>?
            ): Boolean =
                size > MAX_CACHED_FRAME_SETS
        }

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<CacheKey, Deferred<RsvpFrameSet>>()
    private val engineDispatcher = dispatcherProvider.default.limitedParallelism(1)
    private val previewDispatcher = dispatcherProvider.default
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    override suspend fun getFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
    ): RsvpFrameSet {
        val safeStartIndex = startIndex.coerceAtLeast(0)
        val segmentStartIndex = safeStartIndex.segmentStartIndex()
        val baseConfig = config.withoutSessionRamps()
        val baseKey =
            CacheKey(
                bookId.value,
                chapterIndex,
                baseConfig.frameTimingKey(),
                segmentStartIndex,
                CacheMode.SEGMENT_BASE,
            )
        val baseFrameSet =
            ensureFramesAsync(
                key = baseKey,
                bookId = bookId,
                chapterIndex = chapterIndex,
                config = baseConfig,
                startIndex = segmentStartIndex,
            ).await()
        val playbackFrameSet = baseFrameSet.asPlaybackFrameSet(safeStartIndex, config)
        if (!playbackFrameSet.startsBefore(safeStartIndex)) return playbackFrameSet

        val exactKey =
            CacheKey(
                bookId.value,
                chapterIndex,
                config.frameTimingKey(),
                safeStartIndex,
                CacheMode.EXACT_PLAYBACK,
            )
        return ensureFramesAsync(exactKey, bookId, chapterIndex, config, safeStartIndex).await()
    }

    override fun prefetchFrames(
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
    ) {
        val safeStartIndex = startIndex.coerceAtLeast(0)
        val segmentStartIndex = safeStartIndex.segmentStartIndex()
        val baseConfig = config.withoutSessionRamps()
        val key =
            CacheKey(
                bookId.value,
                chapterIndex,
                baseConfig.frameTimingKey(),
                segmentStartIndex,
                CacheMode.SEGMENT_BASE,
            )
        scope.launch {
            val cached = mutex.withLock { cache.containsKey(key) }
            if (cached) return@launch
            runCatching {
                ensureFramesAsync(key, bookId, chapterIndex, baseConfig, segmentStartIndex)
            }
        }
    }

    override suspend fun getPreviewFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
        maxTokenCount: Int,
    ): RsvpFrameSet {
        if (tokens.isEmpty()) {
            return RsvpFrameSet(frames = emptyList(), baseTempoMs = config.tempoMsPerWord)
        }
        val safeStartIndex = startIndex.coerceIn(0, tokens.lastIndex)
        val endExclusive = (safeStartIndex + maxTokenCount.coerceAtLeast(1)).coerceAtMost(tokens.size)
        if (safeStartIndex >= endExclusive) {
            return RsvpFrameSet(frames = emptyList(), baseTempoMs = config.tempoMsPerWord)
        }
        val previewTokens = tokens.subList(safeStartIndex, endExclusive)
        val frames =
            withContext(previewDispatcher) {
                engine.generateFrames(previewTokens, startIndex = 0, config = config)
            }.map { frame ->
                frame.asPreviewFrame(originalIndexOffset = safeStartIndex, tokenCount = tokens.size)
            }
        return RsvpFrameSet(frames = frames, baseTempoMs = config.tempoMsPerWord)
    }

    private suspend fun ensureFramesAsync(
        key: CacheKey,
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
    ): Deferred<RsvpFrameSet> =
        mutex.withLock {
            cache[key]?.let { cached -> CompletableDeferred(cached) }
                ?: inFlight[key]?.takeIf { it.isActive }
                ?: scope.async {
                    buildFrameSet(key, bookId, chapterIndex, config, startIndex)
                }.also { inFlight[key] = it }
        }

    private suspend fun buildFrameSet(
        key: CacheKey,
        bookId: BookId,
        chapterIndex: Int,
        config: RsvpConfig,
        startIndex: Int,
    ): RsvpFrameSet {
        return try {
            val tokens = tokenRepository.getTokens(bookId, chapterIndex)
            val frames =
                withContext(engineDispatcher) {
                    engine.generateFrames(tokens, startIndex = startIndex, config = config)
                }
            val frameSet = RsvpFrameSet(frames = frames, baseTempoMs = config.tempoMsPerWord)
            mutex.withLock {
                cache[key] = frameSet
                inFlight.remove(key)
            }
            frameSet
        } catch (error: Throwable) {
            mutex.withLock { inFlight.remove(key) }
            throw error
        }
    }

    private fun RsvpFrameSet.asPlaybackFrameSet(
        startIndex: Int,
        config: RsvpConfig,
    ): RsvpFrameSet {
        if (frames.isEmpty()) {
            return RsvpFrameSet(frames = emptyList(), baseTempoMs = config.tempoMsPerWord)
        }

        val frameIndex =
            frameIndexMap.alignFrameIndex(
                tokenIndex = startIndex,
                frameCount = frames.size,
            )
        val playbackFrames = frames.subList(frameIndex, frames.size).toMutableList()
        RsvpSessionTimingPolicy.applyInitialSessionRamps(playbackFrames, config)
        return RsvpFrameSet(frames = playbackFrames, baseTempoMs = config.tempoMsPerWord)
    }

    private fun RsvpFrameSet.startsBefore(startIndex: Int): Boolean =
        startIndex > 0 && frames.firstOrNull()?.originalTokenIndex?.let { it < startIndex } == true

    private fun RsvpConfig.withoutSessionRamps(): RsvpConfig =
        copy(
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
        )

    private fun Int.segmentStartIndex(): Int =
        (this / FRAME_CACHE_SEGMENT_TOKENS) * FRAME_CACHE_SEGMENT_TOKENS

    private fun RsvpFrame.asPreviewFrame(
        originalIndexOffset: Int,
        tokenCount: Int,
    ): RsvpFrame =
        copy(
            originalTokenIndex = (originalTokenIndex + originalIndexOffset).coerceIn(0, tokenCount),
            nextOriginalTokenIndex = (nextOriginalTokenIndex + originalIndexOffset).coerceIn(0, tokenCount),
            resumeCursor = -1,
        )

    override fun clearCache() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                cache.clear()
                inFlight.values.forEach { deferred -> deferred.cancel() }
                inFlight.clear()
            }
        }
    }

    private companion object {
        private const val CACHE_INITIAL_CAPACITY = 12
        private const val CACHE_LOAD_FACTOR = 0.75f
        private const val FRAME_CACHE_SEGMENT_TOKENS = 512
        private const val MAX_CACHED_FRAME_SETS = 8
    }
}
