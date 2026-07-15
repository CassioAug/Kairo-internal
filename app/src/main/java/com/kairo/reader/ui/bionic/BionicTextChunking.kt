@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MatchingDeclarationName",
    "ReturnCount",
    "TooManyFunctions",
)

package com.kairo.reader.ui.bionic

import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.engine.BoundaryBefore
import com.kairo.reader.core.rsvp.engine.SKIPPABLE_BOUNDARY_PUNCTUATION
import com.kairo.reader.core.rsvp.text.boundaryBeforeForPunctuation
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal data class BionicTextChunk(
    val startFrameIndex: Int,
    val endFrameIndexExclusive: Int,
    val startTokenIndex: Int,
    val endTokenIndexExclusive: Int,
)

private data class BionicChunkSplit(
    val endFrameIndexExclusive: Int,
    val endTokenIndexExclusive: Int,
    val nextStartFrameIndex: Int,
    val nextStartTokenIndex: Int,
    val boundary: BoundaryBefore,
)

private data class BionicMeasuredSplit(
    val split: BionicChunkSplit,
    val wordCount: Int,
    val characterCount: Int,
)

private data class BionicTokenMetrics(
    val wordPrefix: IntArray,
    val characterPrefix: IntArray,
)

private data class BionicChunkSelectionContext(
    val metrics: BionicTokenMetrics,
    val startTokenIndex: Int,
    val targetWordCount: Int,
    val maximumWordCount: Int,
    val maximumCharacterCount: Int,
)

internal fun buildBionicTextChunks(
    frames: List<RsvpFrame>,
    tokens: List<Token>,
    targetWordCount: Int,
    maximumWordCount: Int = Int.MAX_VALUE,
    maximumCharacterCount: Int = Int.MAX_VALUE,
): List<BionicTextChunk> {
    if (frames.isEmpty() || tokens.isEmpty()) return emptyList()

    val target = targetWordCount.coerceAtLeast(1)
    val visualMaximum = maximumWordCount.coerceAtLeast(target)
    val visualCharacterMaximum = maximumCharacterCount.coerceAtLeast(1)
    val splits = buildBionicChunkSplits(frames, tokens)
    if (splits.isEmpty()) return emptyList()
    val tokenMetrics = buildBionicTokenMetrics(tokens)

    val chunks = mutableListOf<BionicTextChunk>()
    var chunkStartFrame = 0
    var chunkStartToken = frames.first().safeDisplayStart(tokens.size)
    var splitCursor = 0

    while (chunkStartFrame < frames.size) {
        while (
            splitCursor < splits.size &&
            splits[splitCursor].endFrameIndexExclusive <= chunkStartFrame
        ) {
            splitCursor++
        }
        if (splitCursor >= splits.size) break

        val chosen =
            chooseBionicChunkSplit(
                splits = splits,
                startSplitIndex = splitCursor,
                context =
                    BionicChunkSelectionContext(
                        metrics = tokenMetrics,
                        startTokenIndex = chunkStartToken,
                        targetWordCount = target,
                        maximumWordCount = visualMaximum,
                        maximumCharacterCount = visualCharacterMaximum,
                    ),
            )
        val safeStartToken = chunkStartToken.coerceIn(0, tokens.size)
        val safeEndToken =
            chosen.endTokenIndexExclusive
                .coerceIn(safeStartToken, tokens.size)

        if (safeEndToken > safeStartToken) {
            chunks +=
                BionicTextChunk(
                    startFrameIndex = chunkStartFrame,
                    endFrameIndexExclusive = chosen.endFrameIndexExclusive,
                    startTokenIndex = safeStartToken,
                    endTokenIndexExclusive = safeEndToken,
                )
        } else if (chosen.endFrameIndexExclusive >= frames.size) {
            break
        }

        if (chosen.endFrameIndexExclusive >= frames.size) break
        chunkStartFrame = chosen.nextStartFrameIndex
        chunkStartToken = chosen.nextStartTokenIndex.coerceIn(0, tokens.size)
    }

    return chunks
}

private fun buildBionicTokenMetrics(tokens: List<Token>): BionicTokenMetrics {
    val wordPrefix = IntArray(tokens.size + 1)
    val characterPrefix = IntArray(tokens.size + 1)
    tokens.forEachIndexed { index, token ->
        wordPrefix[index + 1] = wordPrefix[index] + if (token.type == TokenType.WORD) 1 else 0
        val spacingWeight = if (token.type == TokenType.WORD) 1 else 0
        val breakWeight =
            if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
                2
            } else {
                0
            }
        characterPrefix[index + 1] =
            characterPrefix[index] +
                token.text.codePointCount(0, token.text.length) +
                spacingWeight +
                breakWeight
    }
    return BionicTokenMetrics(wordPrefix = wordPrefix, characterPrefix = characterPrefix)
}

private fun buildBionicChunkSplits(
    frames: List<RsvpFrame>,
    tokens: List<Token>,
): List<BionicChunkSplit> {
    val splits = mutableListOf<BionicChunkSplit>()

    frames.forEachIndexed { frameIndex, frame ->
        val nextFrameIndex = frameIndex + 1
        val nextFrame = frames.getOrNull(nextFrameIndex)
        if (nextFrame == null) {
            val endToken =
                max(frame.safeConsumedEnd(tokens.size), frame.safeDisplayEnd(tokens.size))
                    .coerceIn(0, tokens.size)
            splits +=
                BionicChunkSplit(
                    endFrameIndexExclusive = frames.size,
                    endTokenIndexExclusive = endToken,
                    nextStartFrameIndex = frames.size,
                    nextStartTokenIndex = tokens.size,
                    boundary = bionicBoundaryBefore(tokens, endToken),
                )
            return@forEachIndexed
        }

        if (nextFrame.isBreakFrame()) {
            val nextReadableFrame =
                frames
                    .subList(nextFrameIndex + 1, frames.size)
                    .firstOrNull { candidate -> candidate.hasReadableText(tokens) }
                    ?: return@forEachIndexed
            val endToken = nextFrame.safeDisplayStart(tokens.size)
            splits +=
                BionicChunkSplit(
                    endFrameIndexExclusive = nextFrameIndex,
                    endTokenIndexExclusive = endToken,
                    nextStartFrameIndex = nextFrameIndex,
                    nextStartTokenIndex = nextReadableFrame.safeDisplayStart(tokens.size),
                    boundary =
                        if (nextFrame.tokens.any { it.type == TokenType.PAGE_BREAK }) {
                            BoundaryBefore.PAGE
                        } else {
                            BoundaryBefore.PARAGRAPH
                        },
                )
            return@forEachIndexed
        }

        val nextDisplayStart = nextFrame.safeDisplayStart(tokens.size)
        if (nextDisplayStart < frame.safeDisplayEnd(tokens.size)) return@forEachIndexed

        splits +=
            BionicChunkSplit(
                endFrameIndexExclusive = nextFrameIndex,
                endTokenIndexExclusive = nextDisplayStart,
                nextStartFrameIndex = nextFrameIndex,
                nextStartTokenIndex = nextDisplayStart,
                boundary = bionicBoundaryBefore(tokens, nextDisplayStart),
            )
    }

    return splits
}

private fun chooseBionicChunkSplit(
    splits: List<BionicChunkSplit>,
    startSplitIndex: Int,
    context: BionicChunkSelectionContext,
): BionicChunkSplit {
    val targetWordCount = context.targetWordCount
    val maximumWordCount = context.maximumWordCount
    val maximumCharacterCount = context.maximumCharacterCount
    val wordPrefix = context.metrics.wordPrefix
    val characterPrefix = context.metrics.characterPrefix
    val semanticMinimum = ceil(targetWordCount * 0.65f).toInt().coerceAtLeast(1)
    val paragraphMinimum = ceil(targetWordCount * 0.5f).toInt().coerceAtLeast(1)
    val semanticHardMaximum = ceil(targetWordCount * 1.35f).toInt().coerceAtLeast(targetWordCount)
    val hardMaximum = min(semanticHardMaximum, maximumWordCount.coerceAtLeast(targetWordCount))
    val candidates = mutableListOf<BionicMeasuredSplit>()
    val safeStartToken = context.startTokenIndex.coerceIn(0, wordPrefix.lastIndex)

    for (splitIndex in startSplitIndex until splits.size) {
        val split = splits[splitIndex]
        val safeEndToken = split.endTokenIndexExclusive.coerceIn(safeStartToken, wordPrefix.lastIndex)
        val wordCount = wordPrefix[safeEndToken] - wordPrefix[safeStartToken]
        val characterCount = characterPrefix[safeEndToken] - characterPrefix[safeStartToken]
        candidates +=
            BionicMeasuredSplit(
                split = split,
                wordCount = wordCount,
                characterCount = characterCount,
            )
        if (split.endFrameIndexExclusive >= splits.last().endFrameIndexExclusive) break
        if (wordCount >= hardMaximum || characterCount >= maximumCharacterCount) break
    }

    val fittingCandidates =
        candidates
            .filter { candidate ->
                candidate.wordCount in 1..maximumWordCount &&
                    candidate.characterCount <= maximumCharacterCount
            }
            .ifEmpty { candidates.filter { it.wordCount > 0 }.take(1) }

    fun closest(candidatesForBoundary: List<BionicMeasuredSplit>): BionicMeasuredSplit? =
        candidatesForBoundary.minWithOrNull(
            compareBy<BionicMeasuredSplit> { abs(it.wordCount - targetWordCount) }
                .thenBy { it.wordCount > targetWordCount }
                .thenBy { it.split.endFrameIndexExclusive },
        )

    val structural =
        closest(
            fittingCandidates.filter { candidate ->
                candidate.wordCount >= paragraphMinimum &&
                    (candidate.split.boundary == BoundaryBefore.PAGE ||
                        candidate.split.boundary == BoundaryBefore.PARAGRAPH)
            },
        )
    val sentence =
        closest(
            fittingCandidates.filter { candidate ->
                candidate.wordCount >= semanticMinimum &&
                    candidate.split.boundary == BoundaryBefore.SENTENCE
            },
        )
    val clause =
        closest(
            fittingCandidates.filter { candidate ->
                candidate.wordCount >= semanticMinimum &&
                    candidate.split.boundary == BoundaryBefore.CLAUSE
            },
        )
    val raw = closest(fittingCandidates) ?: candidates.last()
    return (structural ?: sentence ?: clause ?: raw).split
}

internal fun bionicBoundaryBefore(
    tokens: List<Token>,
    endTokenIndexExclusive: Int,
): BoundaryBefore {
    if (tokens.isEmpty()) return BoundaryBefore.NONE
    val safeEnd = endTokenIndexExclusive.coerceIn(0, tokens.size)
    val nextWord = tokens.subList(safeEnd, tokens.size).firstOrNull { it.type == TokenType.WORD }
    var cursor = safeEnd - 1

    while (cursor >= 0) {
        val token = tokens[cursor]
        when (token.type) {
            TokenType.PAGE_BREAK -> return BoundaryBefore.PAGE
            TokenType.PARAGRAPH_BREAK -> return BoundaryBefore.PARAGRAPH
            TokenType.PUNCTUATION -> {
                val punctuation = token.text.firstOrNull()
                if (punctuation != null && punctuation in SKIPPABLE_BOUNDARY_PUNCTUATION) {
                    cursor--
                    continue
                }
                return boundaryBeforeForPunctuation(
                    token = token,
                    prevWord = tokens.findPreviousWord(beforeIndex = cursor),
                    nextToken = nextWord,
                )
            }
            TokenType.WORD -> {
                return if (nextWord?.isClauseBoundary == true) {
                    BoundaryBefore.CLAUSE
                } else {
                    BoundaryBefore.NONE
                }
            }
        }
    }
    return BoundaryBefore.NONE
}

private fun List<Token>.findPreviousWord(beforeIndex: Int): Token? {
    var cursor = (beforeIndex - 1).coerceAtMost(lastIndex)
    while (cursor >= 0) {
        val token = this[cursor]
        if (token.type == TokenType.WORD) return token
        cursor--
    }
    return null
}

private fun RsvpFrame.safeDisplayStart(tokenCount: Int): Int =
    displayOriginalStartIndex.coerceIn(0, (tokenCount - 1).coerceAtLeast(0))

private fun RsvpFrame.safeConsumedEnd(tokenCount: Int): Int =
    max(displayOriginalEndExclusive, nextOriginalTokenIndex).coerceIn(0, tokenCount)

private fun RsvpFrame.safeDisplayEnd(tokenCount: Int): Int =
    displayOriginalEndExclusive.coerceIn(0, tokenCount)

private fun RsvpFrame.isBreakFrame(): Boolean =
    tokens.any { token ->
        token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK
    }

private fun RsvpFrame.hasReadableText(tokens: List<Token>): Boolean {
    val start = safeDisplayStart(tokens.size)
    val end = safeDisplayEnd(tokens.size).coerceAtLeast(start)
    return tokens.subList(start, end).any { it.type == TokenType.WORD }
}
