package app.kairo.reader.ui.rsvp

import app.kairo.reader.core.model.Token
import app.kairo.reader.core.model.TokenType
import app.kairo.reader.core.model.Chapter
import app.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpOrpTextTest {
    @Test
    fun multiWordChunkPivotNeverFallsOnWhitespace() {
        val content =
            buildOrpTextContent(
                listOf(
                    Token(text = "the", type = TokenType.WORD),
                    Token(text = "dog", type = TokenType.WORD),
                ),
            )

        val pivotChar = content.fullText[content.pivotPosition]
        assertTrue(pivotChar.isLetterOrDigit())
    }

    @Test
    fun multiWordChunkPivotSkipsPunctuationAndSpaces() {
        val content =
            buildOrpTextContent(
                listOf(
                    Token(text = "\"", type = TokenType.PUNCTUATION),
                    Token(text = "I", type = TokenType.WORD),
                    Token(text = "am", type = TokenType.WORD),
                ),
            )

        val pivotChar = content.fullText[content.pivotPosition]
        assertTrue(pivotChar.isLetterOrDigit())
    }

    @Test
    fun straightQuoteAfterCommaIsRenderedAsOpeningQuote() {
        val content =
            buildOrpTextContent(
                listOf(
                    Token(text = "said", type = TokenType.WORD),
                    Token(text = ",", type = TokenType.PUNCTUATION),
                    Token(text = "\"", type = TokenType.PUNCTUATION),
                    Token(text = "Hello", type = TokenType.WORD),
                ),
            )

        assertEquals("said, \"Hello", content.fullText)
    }

    @Test
    fun currencyPrefixPunctuationRendersWithoutSpaceBeforeNumber() {
        val content =
            buildOrpTextContent(
                listOf(
                    Token(text = "\$", type = TokenType.PUNCTUATION),
                    Token(text = "20", type = TokenType.WORD),
                ),
            )

        assertEquals("\$20", content.fullText)
    }

    @Test
    fun currencyPrefixPunctuationDoesNotAttachToPreviousWord() {
        val content =
            buildOrpTextContent(
                listOf(
                    Token(text = "extra", type = TokenType.WORD),
                    Token(text = "\$", type = TokenType.PUNCTUATION),
                    Token(text = "20", type = TokenType.WORD),
                ),
            )

        assertEquals("extra \$20", content.fullText)
    }

    @Test
    fun tokenizerNormalizedOpeningQuoteAfterPeriodKeepsFollowingWordAttached() {
        val tokens =
            Tokenizer().tokenize(
                Chapter(
                    index = 0,
                    title = null,
                    htmlContent = "",
                    plainText = "guy.\"Hello there\"",
                ),
            )

        val content = buildOrpTextContent(tokens)
        assertEquals("guy. \u201CHello there\u201D", content.fullText)
    }
}
