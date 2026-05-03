package app.kairo.reader.data.rsvp

import app.kairo.reader.core.dispatchers.DispatcherProvider
import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.RsvpConfig
import app.kairo.reader.core.model.RsvpFrame
import app.kairo.reader.core.model.Token
import app.kairo.reader.core.model.TokenType
import app.kairo.reader.core.rsvp.RsvpEngine
import app.kairo.reader.data.token.TokenRepository
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
                tokenRepository = StaticTokenRepository,
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

    private object StaticTokenRepository : TokenRepository {
        override suspend fun getTokens(
            bookId: BookId,
            chapterIndex: Int,
            chapter: app.kairo.reader.core.model.Chapter?,
        ): List<Token> = listOf(Token(text = "Hello", type = TokenType.WORD))
    }

    private class CountingEngine : RsvpEngine {
        override fun generateFrames(
            tokens: List<Token>,
            startIndex: Int,
            config: RsvpConfig,
        ): List<RsvpFrame> {
            assertTrue(tokens.isNotEmpty())
            return listOf(RsvpFrame(tokens = tokens, durationMs = 100L, originalTokenIndex = 0))
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
