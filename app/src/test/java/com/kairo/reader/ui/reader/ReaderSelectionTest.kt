package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionTest {
    @Test
    fun normalizesReverseSelectionAndBuildsReadableText() {
        val tokens =
            listOf(
                Token("One", TokenType.WORD),
                Token(",", TokenType.PUNCTUATION),
                Token("two", TokenType.WORD),
                Token("three", TokenType.WORD),
            )
        val range = resolveReaderSelectionRange(anchor = 3, end = 1)

        assertEquals(1..3, range)
        assertEquals(", two three", buildReaderSelectionText(tokens, range))
    }

    @Test
    fun selectionLimitsAllowBoundaryAndRejectOversizedPassages() {
        val atLimit =
            readerSelectionLimitState(
                selectedText = "a".repeat(com.kairo.reader.core.model.SavedAnnotationLimits.MAX_SELECTED_TEXT_CHARACTERS),
                selectionRange = 0 until com.kairo.reader.core.model.SavedAnnotationLimits.MAX_SELECTED_TOKENS,
            )
        val tooManyTokens =
            readerSelectionLimitState(
                selectedText = "passage",
                selectionRange = 0..com.kairo.reader.core.model.SavedAnnotationLimits.MAX_SELECTED_TOKENS,
            )

        assertTrue(atLimit.canSave)
        assertFalse(tooManyTokens.canSave)
    }
}
