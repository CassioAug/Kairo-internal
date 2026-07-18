package com.kairo.reader.data.rsvp

import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.RsvpEngine
import com.kairo.reader.data.token.TokenRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpFrameRepositoryImplTest {
    @Test
    fun clearCacheWaitsForLockedMutexAndClearsEntries() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = CountingEngine(),
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val config = RsvpConfig()

        val firstRequest = backgroundScope.launch { repository.getFrames(bookId, 0, config) }
        advanceUntilIdle()
        firstRequest.join()
        assertEquals(1, repository.cacheSize())

        val mutex = repository.mutex()
        mutex.lock()
        repository.clearCache()
        assertEquals(1, repository.cacheSize())

        mutex.unlock()
        advanceUntilIdle()

        assertEquals(0, repository.cacheSize())
    }

    @Test
    fun getFramesReusesSegmentCacheForNearbyStartIndexes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val config = RsvpConfig()
        var firstFrameSet: RsvpFrameSet? = null
        var secondFrameSet: RsvpFrameSet? = null

        val firstRequest = backgroundScope.launch {
            firstFrameSet = repository.getFrames(bookId, 0, config, startIndex = 4)
        }
        advanceUntilIdle()
        firstRequest.join()

        val secondRequest = backgroundScope.launch {
            secondFrameSet = repository.getFrames(bookId, 0, config, startIndex = 7)
        }
        advanceUntilIdle()
        secondRequest.join()

        assertEquals(listOf(0), engine.startIndexes)
        assertEquals(4, requireNotNull(firstFrameSet).frames.first().originalTokenIndex)
        assertEquals(7, requireNotNull(secondFrameSet).frames.first().originalTokenIndex)
    }

    @Test
    fun getFramesReusesCacheForVisualOnlyConfigChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val config = RsvpConfig()

        val firstRequest = backgroundScope.launch {
            repository.getFrames(bookId, 0, config, startIndex = 4)
        }
        advanceUntilIdle()
        firstRequest.join()

        val secondRequest = backgroundScope.launch {
            repository.getFrames(
                bookId,
                0,
                config.copy(
                    orpHighlightEnabled = !config.orpHighlightEnabled,
                    orpGuideEnabled = !config.orpGuideEnabled,
                    orpGuideBrightness = config.orpGuideBrightness + 0.25,
                    orpGuideThickness = config.orpGuideThickness + 0.25,
                ),
                startIndex = 4,
            )
        }
        advanceUntilIdle()
        secondRequest.join()

        assertEquals(listOf(0), engine.startIndexes)
    }

    @Test
    fun getFramesReusesBaseCacheForSessionRampOnlyConfigChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        val config =
            RsvpConfig(
                startDelayMs = 10L,
                endDelayMs = 0L,
                rampUpFrames = 0,
                rampDownFrames = 0,
            )
        var firstFrameSet: RsvpFrameSet? = null
        var secondFrameSet: RsvpFrameSet? = null

        val firstRequest = backgroundScope.launch {
            firstFrameSet = repository.getFrames(bookId, 0, config, startIndex = 4)
        }
        advanceUntilIdle()
        firstRequest.join()

        val secondRequest = backgroundScope.launch {
            secondFrameSet =
                repository.getFrames(
                    bookId,
                    0,
                    config.copy(startDelayMs = 50L),
                    startIndex = 4,
                )
        }
        advanceUntilIdle()
        secondRequest.join()

        assertEquals(listOf(0), engine.startIndexes)
        assertEquals(110L, requireNotNull(firstFrameSet).frames.first().durationMs)
        assertEquals(150L, requireNotNull(secondFrameSet).frames.first().durationMs)
    }

    @Test
    fun getFramesFallsBackToExactGenerationWhenSegmentStartsBeforeRequestedToken() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = PhraseLikeEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val bookId = BookId("book")
        var frameSet: RsvpFrameSet? = null

        val request = backgroundScope.launch {
            frameSet = repository.getFrames(bookId, 0, RsvpConfig(), startIndex = 1)
        }
        advanceUntilIdle()
        request.join()

        assertEquals(listOf(0, 1), engine.startIndexes)
        assertEquals(1, requireNotNull(frameSet).frames.first().originalTokenIndex)
    }

    @Test
    fun getPreviewFramesUsesContextWindowAndShiftsOriginalIndexes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CountingEngine()
        val repository =
            RsvpFrameRepositoryImpl(
                tokenRepository = CountingTokenRepository(),
                engine = engine,
                dispatcherProvider =
                object : DispatcherProvider {
                    override val default: CoroutineDispatcher = dispatcher
                    override val io: CoroutineDispatcher = dispatcher
                },
            )
        val tokens = (0 until 10).map { index -> Token(text = "w$index", type = TokenType.WORD) }
        var preview: RsvpFrameSet? = null

        val request = backgroundScope.launch {
            preview = repository.getPreviewFrames(
                tokens = tokens,
                startIndex = 4,
                config = RsvpConfig(),
                maxTokenCount = 3,
            )
        }
        advanceUntilIdle()
        request.join()

        val frames = requireNotNull(preview).frames
        assertEquals(listOf(4), engine.startIndexes)
        assertEquals(listOf(7), engine.tokenCounts)
        assertEquals(3, frames.size)
        assertEquals(4, frames.first().originalTokenIndex)
        assertEquals(7, frames.last().nextOriginalTokenIndex)
        assertTrue(frames.all { it.resumeCursor == -1 })
    }

    private class CountingTokenRepository(private val tokenCount: Int = 20,) : TokenRepository {
        override suspend fun getTokens(
            bookId: BookId,
            chapterIndex: Int,
            chapter: com.kairo.reader.core.model.Chapter?,
        ): List<Token> =
            (0 until tokenCount).map { index ->
                Token(text = "w$index", type = TokenType.WORD)
            }
    }

    private class CountingEngine : RsvpEngine {
        val startIndexes = mutableListOf<Int>()
        val tokenCounts = mutableListOf<Int>()

        override fun generateFrames(
            tokens: List<Token>,
            startIndex: Int,
            config: RsvpConfig,
        ): List<RsvpFrame> {
            assertTrue(tokens.isNotEmpty())
            startIndexes += startIndex
            tokenCounts += tokens.size
            return (startIndex until tokens.size).map { index ->
                RsvpFrame(
                    tokens = listOf(tokens[index]),
                    durationMs = 100L,
                    originalTokenIndex = index,
                    nextOriginalTokenIndex = index + 1,
                )
            }
        }
    }

    private class PhraseLikeEngine : RsvpEngine {
        val startIndexes = mutableListOf<Int>()

        override fun generateFrames(
            tokens: List<Token>,
            startIndex: Int,
            config: RsvpConfig,
        ): List<RsvpFrame> {
            assertTrue(tokens.isNotEmpty())
            startIndexes += startIndex
            return if (startIndex == 0) {
                listOf(
                    RsvpFrame(
                        tokens = listOf(tokens[0], tokens[1]),
                        durationMs = 100L,
                        originalTokenIndex = 0,
                        nextOriginalTokenIndex = 2,
                    ),
                    RsvpFrame(
                        tokens = listOf(tokens[2]),
                        durationMs = 100L,
                        originalTokenIndex = 2,
                        nextOriginalTokenIndex = 3,
                    ),
                )
            } else {
                listOf(
                    RsvpFrame(
                        tokens = listOf(tokens[startIndex]),
                        durationMs = 100L,
                        originalTokenIndex = startIndex,
                        nextOriginalTokenIndex = startIndex + 1,
                    )
                )
            }
        }
    }

    private fun RsvpFrameRepositoryImpl.cacheSize(): Int {
        val field = javaClass.getDeclaredField("cache")
        field.isAccessible = true
        val cache = field.get(this) as Map<*, *>
        return cache.size
    }

    private fun RsvpFrameRepositoryImpl.mutex(): Mutex {
        val field = javaClass.getDeclaredField("mutex")
        field.isAccessible = true
        return field.get(this) as Mutex
    }
}
