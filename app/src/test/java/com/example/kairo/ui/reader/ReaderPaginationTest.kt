package com.example.kairo.ui.reader

import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPaginationTest {
    @Test
    fun buildChapterPages_usesEarlierSentenceBoundaryInsteadOfCuttingMidSentence() {
        val tokens =
            words("one", "two", "three", "four", "five", "six") +
                punctuation(".") +
                words(
                    "seven",
                    "eight",
                    "nine",
                    "ten",
                    "eleven",
                    "twelve",
                    "thirteen",
                    "fourteen",
                    "fifteen",
                    "sixteen",
                    "seventeen",
                    "eighteen",
                )

        val pages = buildChapterPages(tokens, wordsPerPage = 10)

        assertEquals(2, pages.size)
        assertEquals(6, pages[0].wordCount)
        assertEquals(".", tokens[pages[0].endTokenIndex].text)
        assertEquals("seven", tokens[pages[1].startTokenIndex].text)
    }

    @Test
    fun buildChapterPages_usesEarlierParagraphBoundaryInsteadOfCrossingIntoNextParagraph() {
        val tokens =
            words("one", "two", "three", "four", "five", "six") +
                paragraphBreak() +
                words(
                    "seven",
                    "eight",
                    "nine",
                    "ten",
                    "eleven",
                    "twelve",
                    "thirteen",
                    "fourteen",
                    "fifteen",
                    "sixteen",
                    "seventeen",
                    "eighteen",
                )

        val pages = buildChapterPages(tokens, wordsPerPage = 10)

        assertEquals(2, pages.size)
        assertEquals(6, pages[0].wordCount)
        assertEquals("six", tokens[pages[0].endTokenIndex].text)
        assertEquals("seven", tokens[pages[1].startTokenIndex].text)
    }

    @Test
    fun buildChapterPages_ignoresInlinePhysicalPageBreakInsideSentence() {
        val tokens =
            words("one", "two", "three", "four") +
                pageBreak() +
                words("five", "six") +
                punctuation(".") +
                words("seven", "eight", "nine", "ten")

        val pages = buildChapterPages(tokens, wordsPerPage = 4)

        assertEquals(2, pages.size)
        assertEquals(6, pages[0].wordCount)
        assertEquals(".", tokens[pages[0].endTokenIndex].text)
        assertEquals("seven", tokens[pages[1].startTokenIndex].text)
    }

    private fun words(vararg texts: String): List<Token> =
        texts.map { text -> Token(text = text, type = TokenType.WORD) }

    private fun punctuation(text: String): Token = Token(text = text, type = TokenType.PUNCTUATION)

    private fun paragraphBreak(): Token = Token(text = "\n\n", type = TokenType.PARAGRAPH_BREAK)

    private fun pageBreak(): Token = Token(text = "\u000C", type = TokenType.PAGE_BREAK)
}
