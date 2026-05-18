package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun buildVisualChapterPages_insertsInlineImageAsNavigablePage() {
        val tokens = words("one", "two", "three", "four")
        val textPages =
            listOf(
                ChapterPage(index = 0, startTokenIndex = 0, endTokenIndex = 1, wordCount = 2),
                ChapterPage(index = 1, startTokenIndex = 2, endTokenIndex = 3, wordCount = 2),
            )
        val imageBlock = ReaderImageBlock(imagePath = "images/full-page.jpg", index = 0)
        val blocks =
            listOf(
                ReaderParagraphBlock(Paragraph(tokens = tokens.subList(0, 2), startIndex = 0)),
                imageBlock,
                ReaderParagraphBlock(Paragraph(tokens = tokens.subList(2, 4), startIndex = 2)),
            )

        val pages = buildVisualChapterPages(textPages, blocks, tokens)

        assertEquals(3, pages.size)
        assertEquals(ChapterPageKind.TEXT, pages[0].kind)
        assertEquals(ChapterPageKind.IMAGE, pages[1].kind)
        assertEquals(ChapterPageKind.TEXT, pages[2].kind)
        assertEquals("images/full-page.jpg", pages[1].imagePath)
        assertEquals(1, pages[1].focusTokenIndex)
        assertEquals(listOf(imageBlock), sliceBlocksForPage(blocks, pages[1]))
        assertTrue(sliceBlocksForPage(blocks, pages[0]).none { it is ReaderImageBlock })
    }

    @Test
    fun buildVisualChapterPages_createsPageForImageOnlyChapter() {
        val imageBlock = ReaderImageBlock(imagePath = "images/plate.jpg", index = 0)

        val pages = buildVisualChapterPages(
            textPages = emptyList(),
            blocks = listOf(imageBlock),
            tokens = emptyList(),
        )

        assertEquals(1, pages.size)
        assertEquals(ChapterPageKind.IMAGE, pages[0].kind)
        assertEquals(0, pages[0].focusTokenIndex)
        assertEquals(listOf(imageBlock), sliceBlocksForPage(listOf(imageBlock), pages[0]))
    }

    private fun words(vararg texts: String): List<Token> =
        texts.map { text -> Token(text = text, type = TokenType.WORD) }

    private fun punctuation(text: String): Token = Token(text = text, type = TokenType.PUNCTUATION)

    private fun paragraphBreak(): Token = Token(text = "\n\n", type = TokenType.PARAGRAPH_BREAK)

    private fun pageBreak(): Token = Token(text = "\u000C", type = TokenType.PAGE_BREAK)
}
