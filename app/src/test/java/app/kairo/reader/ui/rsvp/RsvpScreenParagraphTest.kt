package app.kairo.reader.ui.rsvp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import app.kairo.reader.core.model.Token
import app.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpScreenParagraphTest {
    @Test
    fun compactPreviewWindowKeepsHighlightedWordVisible() {
        val tokens =
            (0 until 40).map { index ->
                Token(
                    text = "word$index",
                    type = TokenType.WORD,
                )
            }
        val highlightIndex = 30
        val annotatedText =
            buildRsvpParagraphAnnotatedText(
                paragraph = RsvpParagraph(tokens = tokens, startIndex = 0),
                highlightIndex = highlightIndex,
                highlightStyle =
                    SpanStyle(
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        background = Color.Yellow,
                    ),
                maxWords = 8,
                highlightWindowFraction = 0.35f,
            )
        val highlightedWord = "word$highlightIndex"
        val highlightStart = annotatedText.text.indexOf(highlightedWord)

        assertTrue(highlightStart >= 0)
        assertTrue(highlightStart < annotatedText.text.length / 2)
        val highlightStyle =
            annotatedText.spanStyles.first { range ->
                range.start == highlightStart &&
                    range.end == highlightStart + highlightedWord.length
            }.item
        assertEquals(Color.Red, highlightStyle.color)
        assertEquals(FontWeight.Bold, highlightStyle.fontWeight)
        assertEquals(Color.Yellow, highlightStyle.background)
    }
}
