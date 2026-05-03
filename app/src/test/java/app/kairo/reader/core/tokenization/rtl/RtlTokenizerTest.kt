package app.kairo.reader.core.tokenization.rtl

import app.kairo.reader.core.model.Chapter
import app.kairo.reader.core.model.ChapterLink
import app.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtlTokenizerTest {
    private fun chapter(text: String) =
        Chapter(
            index = 0,
            title = null,
            htmlContent = "",
            plainText = text,
        )

    @Test
    fun tokenizesArabicWordsAndPunctuation() {
        val tokens = RtlTokenizer().tokenize(chapter("مرحبا، بالعالم؟"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }
        val punctuation = tokens.filter { it.type == TokenType.PUNCTUATION }.map { it.text }

        assertTrue(words.contains("مرحبا"))
        assertTrue(words.contains("بالعالم"))
        assertTrue(punctuation.contains("،"))
        assertTrue(punctuation.contains("؟"))
    }

    @Test
    fun preservesArabicDiacriticsWithinWord() {
        val tokens = RtlTokenizer().tokenize(chapter("سَلَام"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertEquals(listOf("سَلَام"), words)
    }

    @Test
    fun detectsFormFeedAsPageBreakAfterSentenceBoundary() {
        val tokens = RtlTokenizer().tokenize(chapter("الفصل؟\u000Cالتالي"))

        assertTrue(tokens.any { it.type == TokenType.PAGE_BREAK })
    }

    @Test
    fun ignoresInlineFormFeedInsideSentence() {
        val tokens = RtlTokenizer().tokenize(chapter("الفصل\u000Cالتالي؟"))

        assertFalse(tokens.any { it.type == TokenType.PAGE_BREAK })
    }

    @Test
    fun handlesHebrewMaqafAsPunctuation() {
        val tokens = RtlTokenizer().tokenize(chapter("שלום־עולם"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }
        val punctuation = tokens.filter { it.type == TokenType.PUNCTUATION }.map { it.text }

        assertEquals(listOf("שלום", "עולם"), words)
        assertTrue(punctuation.contains("־"))
    }

    @Test
    fun keepsLatinWordsInsideRtlText() {
        val tokens = RtlTokenizer().tokenize(chapter("الإصدار CPU الجديد"))
        val words = tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertTrue(words.contains("CPU"))
    }

    @Test
    fun appliesLinksByCharacterPositions() {
        val chapter =
            Chapter(
                index = 0,
                title = null,
                htmlContent = "",
                plainText = "שלום עולם",
                links =
                    listOf(
                        ChapterLink(
                            startChar = 0,
                            endChar = 4,
                            targetChapterIndex = 2,
                        ),
                    ),
            )
        val tokens = RtlTokenizer().tokenize(chapter)

        assertTrue(tokens.first().linkChapterIndex == 2)
        assertTrue(tokens.drop(1).none { it.linkChapterIndex == 2 })
    }
}
