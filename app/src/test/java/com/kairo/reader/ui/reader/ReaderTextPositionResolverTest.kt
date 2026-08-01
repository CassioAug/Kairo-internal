package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTextPositionResolverTest {
    @Test
    fun resolvesAuthoredFragmentOffsetToItsFirstWordToken() {
        val plainText = "The Arrival\n\nFirst chapter.\n\nThe Crossing\n\nSecond chapter."
        val tokens =
            listOf(
                word("The"),
                word("Arrival"),
                paragraphBreak(),
                word("First"),
                word("chapter"),
                punctuation("."),
                paragraphBreak(),
                word("The"),
                word("Crossing"),
                paragraphBreak(),
                word("Second"),
                word("chapter"),
                punctuation("."),
            )

        val resolved =
            ReaderTextPositionResolver.resolveTokenIndex(
                plainText = plainText,
                tokens = tokens,
                characterOffset = plainText.indexOf("The Crossing"),
            )

        assertEquals(7, resolved)
    }

    private fun word(text: String) = Token(text = text, type = TokenType.WORD)

    private fun punctuation(text: String) = Token(text = text, type = TokenType.PUNCTUATION)

    private fun paragraphBreak() = Token(text = "\n", type = TokenType.PARAGRAPH_BREAK)
}
