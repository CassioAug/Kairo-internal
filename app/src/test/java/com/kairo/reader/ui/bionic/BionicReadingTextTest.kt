package com.kairo.reader.ui.bionic

import androidx.compose.ui.graphics.Color
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BionicReadingTextTest {
    @Test
    fun buildBionicTextChunks_endsChunksOnRsvpFrameBoundaries() {
        val tokens = (0 until 4).map { index -> word("word$index") }
        val frames = tokens.indices.map { index -> frame(index, index + 1) }

        val chunks = buildBionicTextChunks(frames, tokens, targetWordCount = 2)

        assertEquals(
            listOf(
                BionicTextChunk(0, 2, 0, 2),
                BionicTextChunk(2, 4, 2, 4),
            ),
            chunks,
        )
    }

    @Test
    fun buildBionicTextChunks_doesNotSplitSubwordFramesAcrossChunks() {
        val tokens = listOf(word("reading"), word("flows"))
        val frames =
            listOf(
                frame(
                    tokenIndex = 0,
                    nextTokenIndex = 0,
                    characterStart = 0,
                    characterEnd = 4,
                ),
                frame(
                    tokenIndex = 0,
                    nextTokenIndex = 1,
                    characterStart = 4,
                    characterEnd = 7,
                ),
                frame(tokenIndex = 1, nextTokenIndex = 2),
            )

        val chunks = buildBionicTextChunks(frames, tokens, targetWordCount = 1)

        assertEquals(BionicTextChunk(0, 2, 0, 1), chunks.first())
        assertEquals(BionicTextChunk(2, 3, 1, 2), chunks.last())
    }

    @Test
    fun buildBionicTextChunks_movesParagraphPauseToIncomingChunk() {
        val tokens =
            buildList {
                repeat(8) { index -> add(word("word$index")) }
                add(Token("\n", TokenType.PARAGRAPH_BREAK))
                add(word("next"))
            }
        val frames =
            buildList {
                repeat(8) { index -> add(frame(index, index + 1)) }
                add(
                    RsvpFrame(
                        tokens = listOf(Token("", TokenType.PARAGRAPH_BREAK)),
                        durationMs = 300L,
                        originalTokenIndex = 8,
                        nextOriginalTokenIndex = 9,
                        displayOriginalStartIndex = 8,
                        displayOriginalEndExclusive = 9,
                    )
                )
                add(frame(9, 10))
            }

        val chunks = buildBionicTextChunks(frames, tokens, targetWordCount = 16)

        assertEquals(BionicTextChunk(0, 8, 0, 8), chunks.first())
        assertEquals(BionicTextChunk(8, 10, 9, 10), chunks.last())
    }

    @Test
    fun buildBionicTextChunks_prefersNearbySentenceBoundary() {
        val tokens =
            buildList {
                repeat(8) { index -> add(word("first$index")) }
                add(punctuation("."))
                repeat(8) { index -> add(word("second$index")) }
                add(punctuation("."))
            }
        val frames = readingFramesForWords(tokens)

        val chunks =
            buildBionicTextChunks(
                frames = frames,
                tokens = tokens,
                targetWordCount = 10,
                maximumWordCount = 12,
            )

        assertEquals(BionicTextChunk(0, 8, 0, 9), chunks.first())
    }

    @Test
    fun buildBionicTextChunks_canFinishSentenceJustPastTarget() {
        val tokens =
            buildList {
                repeat(12) { index -> add(word("first$index")) }
                add(punctuation("."))
                repeat(8) { index -> add(word("second$index")) }
            }
        val frames = readingFramesForWords(tokens)

        val chunks =
            buildBionicTextChunks(
                frames = frames,
                tokens = tokens,
                targetWordCount = 10,
                maximumWordCount = 12,
            )

        assertEquals(BionicTextChunk(0, 12, 0, 13), chunks.first())
    }

    @Test
    fun buildBionicTextChunks_doesNotCrossVisualWordCapacityForSentence() {
        val tokens =
            buildList {
                repeat(12) { index -> add(word("first$index")) }
                add(punctuation("."))
                repeat(8) { index -> add(word("second$index")) }
            }
        val frames = readingFramesForWords(tokens)

        val chunks =
            buildBionicTextChunks(
                frames = frames,
                tokens = tokens,
                targetWordCount = 10,
                maximumWordCount = 10,
            )

        assertEquals(BionicTextChunk(0, 10, 0, 10), chunks.first())
    }

    @Test
    fun buildBionicTextChunks_usesCharacterCapacityForLongWords() {
        val tokens = (0 until 12).map { index -> word("extraordinary$index") }
        val frames = tokens.indices.map { index -> frame(index, index + 1) }

        val chunks =
            buildBionicTextChunks(
                frames = frames,
                tokens = tokens,
                targetWordCount = 10,
                maximumWordCount = 12,
                maximumCharacterCount = 50,
            )

        assertTrue(chunks.first().endTokenIndexExclusive < 10)
    }

    @Test
    fun buildBionicTextChunks_usesClauseWhenNoSentenceIsNearby() {
        val tokens =
            buildList {
                repeat(8) { index -> add(word("first$index")) }
                add(punctuation(";"))
                repeat(8) { index -> add(word("second$index")) }
            }
        val frames = readingFramesForWords(tokens)

        val chunks = buildBionicTextChunks(frames, tokens, targetWordCount = 10)

        assertEquals(BionicTextChunk(0, 8, 0, 9), chunks.first())
    }

    @Test
    fun buildBionicTextChunks_doesNotCreateChunkForTrailingBreak() {
        val tokens =
            listOf(
                word("one"),
                word("two"),
                Token("\n", TokenType.PARAGRAPH_BREAK),
            )
        val frames =
            listOf(
                frame(0, 1),
                frame(1, 2),
                RsvpFrame(
                    tokens = listOf(Token("", TokenType.PARAGRAPH_BREAK)),
                    durationMs = 300L,
                    originalTokenIndex = 2,
                    nextOriginalTokenIndex = 3,
                    displayOriginalStartIndex = 2,
                    displayOriginalEndExclusive = 3,
                ),
            )

        val chunks = buildBionicTextChunks(frames, tokens, targetWordCount = 16)

        assertEquals(listOf(BionicTextChunk(0, 3, 0, 3)), chunks)
    }

    @Test
    fun bionicBoundaryBefore_reusesRsvpAbbreviationAndDecimalRules() {
        assertEquals(
            BoundaryBefore.NONE,
            bionicBoundaryBefore(
                tokens = listOf(word("Mr"), punctuation("."), word("Kemp")),
                endTokenIndexExclusive = 2,
            ),
        )
        assertEquals(
            BoundaryBefore.NONE,
            bionicBoundaryBefore(
                tokens = listOf(word("12"), punctuation("."), word("5")),
                endTokenIndexExclusive = 2,
            ),
        )
    }

    @Test
    fun bionicBoundaryBefore_keepsClosingQuoteWithSentence() {
        assertEquals(
            BoundaryBefore.SENTENCE,
            bionicBoundaryBefore(
                tokens =
                    listOf(
                        word("Done"),
                        punctuation("."),
                        punctuation("\u201D"),
                        word("Next"),
                    ),
                endTokenIndexExclusive = 3,
            ),
        )
    }

    @Test
    fun buildBionicAnnotatedText_joinsAdjacentActiveHighlights() {
        val tokens = listOf(word("one"), word("two"))
        val activeBackground = Color.Blue
        val activeFrame =
            RsvpFrame(
                tokens = tokens,
                durationMs = 100L,
                originalTokenIndex = 0,
                nextOriginalTokenIndex = 2,
                displayOriginalStartIndex = 0,
                displayOriginalEndExclusive = 2,
            )

        val annotatedText =
            buildBionicAnnotatedText(
                tokens = tokens,
                chunk = BionicTextChunk(0, 1, 0, 2),
                activeFrame = activeFrame,
                fixationStrength = 0.45f,
                fixationColor = Color.Black,
                activeBackground = activeBackground,
            )
        val highlights =
            annotatedText.spanStyles.filter { range ->
                range.item.background == activeBackground
            }

        assertEquals("one two", annotatedText.text)
        assertEquals(1, highlights.size)
        assertEquals(0, highlights.single().start)
        assertEquals(annotatedText.length, highlights.single().end)
        assertEquals(Color.Unspecified, highlights.single().item.color)
    }

    @Test
    fun bionicTextColors_addRestrainedTonalContrastOnDarkThemes() {
        val lightText = Color.Black
        val darkText = Color.White
        val darkBody = Color.LightGray

        assertEquals(
            lightText.copy(alpha = 0.90f),
            bionicBodyColor(
                onBackgroundColor = lightText,
                onSurfaceVariantColor = Color.DarkGray,
                backgroundColor = Color.White,
                textBrightness = 0.90f,
            ),
        )
        assertEquals(
            lightText.copy(alpha = 0.90f),
            bionicFixationColor(
                onBackgroundColor = lightText,
                backgroundColor = Color.White,
                textBrightness = 0.90f,
            ),
        )
        assertEquals(
            darkBody.copy(alpha = 0.90f),
            bionicBodyColor(
                onBackgroundColor = darkText,
                onSurfaceVariantColor = darkBody,
                backgroundColor = Color.Black,
                textBrightness = 0.90f,
            ),
        )
        assertEquals(
            darkText.copy(alpha = 0.98f),
            bionicFixationColor(
                onBackgroundColor = darkText,
                backgroundColor = Color.Black,
                textBrightness = 0.90f,
            ),
        )
    }

    @Test
    fun bionicHighlightAlpha_capsBrightOverlayOnDarkThemes() {
        assertEquals(
            BIONIC_MAX_HIGHLIGHT_STRENGTH,
            bionicHighlightAlpha(1f, backgroundColor = Color.White),
            0f,
        )
        assertEquals(
            BIONIC_DARK_MAX_HIGHLIGHT_STRENGTH,
            bionicHighlightAlpha(1f, backgroundColor = Color.Black),
            0f,
        )
    }

    @Test
    fun bionicFixationEndOffset_preservesUnicodeCodePointBoundaries() {
        val text = "A\uD83D\uDE00B"

        assertEquals(3, bionicFixationEndOffset(text, fixationStrength = 0.45f))
    }

    @Test
    fun bionicPaneLineCount_keepsFiveLinesExceptInCompactLandscape() {
        assertEquals(BIONIC_PANE_LINES, bionicPaneLineCount(412, 915))
        assertEquals(BIONIC_COMPACT_PANE_LINES, bionicPaneLineCount(800, 360))
        assertEquals(BIONIC_PANE_LINES, bionicPaneLineCount(1200, 800))
    }

    @Test
    fun estimateBionicWordCapacity_reducesDensityForLargerText() {
        val regular = estimateBionicWordCapacity(720, 24f, BIONIC_PANE_LINES)
        val large = estimateBionicWordCapacity(720, 40f, BIONIC_PANE_LINES)
        val scaled =
            estimateBionicWordCapacity(
                screenWidthDp = 720,
                fontSizeSp = 24f,
                paneLineCount = BIONIC_PANE_LINES,
                fontScale = 1.6f,
            )

        assertTrue(large < regular)
        assertTrue(scaled < regular)
        assertTrue(
            bionicPaneLineCount(
                screenWidthDp = 412,
                screenHeightDp = 640,
                fontSizeSp = 40f,
                fontScale = 2f,
            ) < BIONIC_PANE_LINES,
        )
    }

    @Test
    fun bionicDisplayMetrics_allowSingleLinePaneAtExtremeAccessibilityScale() {
        assertEquals(
            BIONIC_MIN_PANE_LINES,
            bionicPaneLineCount(
                screenWidthDp = 800,
                screenHeightDp = 220,
                fontSizeSp = 40f,
                fontScale = 2f,
            ),
        )
        assertEquals(
            1,
            estimateBionicWordCapacity(
                screenWidthDp = 240,
                fontSizeSp = 40f,
                paneLineCount = BIONIC_MIN_PANE_LINES,
                fontScale = 2f,
            ),
        )
        assertEquals(
            4,
            estimateBionicCharacterCapacity(
                screenWidthDp = 240,
                fontSizeSp = 40f,
                paneLineCount = BIONIC_MIN_PANE_LINES,
                fontScale = 2f,
            ),
        )
    }

    @Test
    fun resolveBionicTargetWordCount_reservesSentenceHeadroomWhenSpaceAllows() {
        assertEquals(14, resolveBionicTargetWordCount(wordCapacity = 17))
        assertEquals(30, resolveBionicTargetWordCount(wordCapacity = 40))
        assertEquals(4, resolveBionicTargetWordCount(wordCapacity = 4))
    }

    private fun word(text: String): Token = Token(text, TokenType.WORD)

    private fun punctuation(text: String): Token = Token(text, TokenType.PUNCTUATION)

    private fun readingFramesForWords(tokens: List<Token>): List<RsvpFrame> {
        val wordIndices = tokens.indices.filter { tokens[it].type == TokenType.WORD }
        return wordIndices.mapIndexed { frameIndex, tokenIndex ->
            val nextWordIndex = wordIndices.getOrNull(frameIndex + 1) ?: tokens.size
            RsvpFrame(
                tokens = tokens.subList(tokenIndex, nextWordIndex),
                durationMs = 100L,
                originalTokenIndex = tokenIndex,
                nextOriginalTokenIndex = nextWordIndex,
                displayOriginalStartIndex = tokenIndex,
                displayOriginalEndExclusive = nextWordIndex,
            )
        }
    }

    private fun frame(
        tokenIndex: Int,
        nextTokenIndex: Int,
        characterStart: Int = 0,
        characterEnd: Int? = null,
    ): RsvpFrame =
        RsvpFrame(
            tokens = listOf(word("frame")),
            durationMs = 100L,
            originalTokenIndex = tokenIndex,
            nextOriginalTokenIndex = nextTokenIndex,
            displayOriginalStartIndex = tokenIndex,
            displayOriginalEndExclusive = tokenIndex + 1,
            displayOriginalStartCharacterOffset = characterStart,
            displayOriginalEndCharacterOffset = characterEnd,
        )
}
