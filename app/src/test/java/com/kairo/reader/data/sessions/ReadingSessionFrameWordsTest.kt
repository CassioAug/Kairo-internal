package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSessionFrameWordsTest {
    @Test
    fun punctuationOnlyFramesCountNoWords() {
        val frame =
            RsvpFrame(
                tokens = listOf(Token(".", TokenType.PUNCTUATION)),
                durationMs = 100L,
                displayOriginalStartIndex = 0,
                displayOriginalEndExclusive = 1,
            )

        assertEquals(
            0,
            countCompletedWordsInFrame(frame, listOf(Token("word", TokenType.WORD))),
        )
    }

    @Test
    fun splitWordCountsOnlyWhenFinalChunkIsConsumed() {
        val source = listOf(Token("extraordinary", TokenType.WORD))
        val first =
            RsvpFrame(
                tokens = listOf(Token("extra", TokenType.WORD, isSubwordChunk = true)),
                durationMs = 100L,
                displayOriginalStartIndex = 0,
                displayOriginalEndExclusive = 1,
                displayOriginalEndCharacterOffset = 5,
            )
        val final =
            first.copy(
                tokens = listOf(Token("ordinary", TokenType.WORD, isSubwordChunk = true)),
                displayOriginalStartCharacterOffset = 5,
                displayOriginalEndCharacterOffset = source.single().text.length,
            )

        assertEquals(0, countCompletedWordsInFrame(first, source))
        assertEquals(1, countCompletedWordsInFrame(final, source))
        assertEquals(null, completedFrameProgress(first, source).lastFullyConsumedOriginalTokenIndex)
        assertEquals(0, completedFrameProgress(final, source).lastFullyConsumedOriginalTokenIndex)
    }

    @Test
    fun multiWordFrameCountsAllCompletedSourceWords() {
        val source =
            listOf(
                Token("one", TokenType.WORD),
                Token(" ", TokenType.PUNCTUATION),
                Token("two", TokenType.WORD),
            )
        val frame =
            RsvpFrame(
                tokens = listOf(source[0], source[2]),
                durationMs = 100L,
                displayOriginalStartIndex = 0,
                displayOriginalEndExclusive = 3,
            )

        assertEquals(2, countCompletedWordsInFrame(frame, source))
        val completed = completedFrameProgress(frame, source)
        assertEquals(2, completed.words)
        assertEquals(2, completed.lastFullyConsumedOriginalTokenIndex)
    }
}
