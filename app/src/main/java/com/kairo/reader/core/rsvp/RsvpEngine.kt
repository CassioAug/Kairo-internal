@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "UnreachableCode",
)

package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.splitTokenForRsvp
import kotlin.math.max

interface RsvpEngine {
    fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
    ): List<RsvpFrame>
}

/**
 * Ground-up redesign focused on comprehension at high WPM.
 *
 * Core idea:
 * - Build *reading units* (1–2 word phrases + attached punctuation) that match language flow.
 * - Compute unit durations via a difficulty model (length/syllables/rarity/complexity) + breath pauses.
 * - Apply context shaping (parentheticals/quotes) and rhythm shaping (EMA smoothing + jitter clamps).
 *
 * The result is a calm, legible cadence where long words and punctuation never "flash" away.
 */
@Suppress("LargeClass", "TooManyFunctions")
class ComprehensionRsvpEngine : RsvpEngine {
    override fun generateFrames(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
    ): List<RsvpFrame> =
        generateFramesWithConfig(
            tokens = tokens,
            startIndex = startIndex,
            config = config.normalizedForPlayback(),
        )

    private fun generateFramesWithConfig(
        tokens: List<Token>,
        startIndex: Int,
        config: RsvpConfig,
    ): List<RsvpFrame> {
        if (tokens.isEmpty()) return emptyList()

        val analysisStartIndex = resolveAnalysisStartIndex(tokens, startIndex)
        val analysisTokens = tokens.subList(analysisStartIndex, tokens.size)
        val expanded =
            analysisTokens.flatMapIndexed { index, token ->
                splitTokenForRsvp(
                    token = token,
                    maxChunkLength = config.maxChunkLength,
                    subwordChunkPauseMs = config.subwordChunkPauseMs,
                ).map { splitToken ->
                    ExpandedToken(splitToken, analysisStartIndex + index, -1)
                }
            }.mapIndexed { expandedIndex, expandedToken ->
                expandedToken.copy(expandedIndex = expandedIndex)
            }

        val tokenAnalysis = analyzeExpandedTokens(expanded, config)

        val startCursor = expanded.indexOfFirst { it.originalIndex >= startIndex }
        val fallbackCursor =
            expanded.indexOfLast { it.originalIndex <= startIndex }
                .coerceAtLeast(0)
        var cursor = if (startCursor == -1) fallbackCursor else startCursor
        cursor = cursor.coerceIn(0, expanded.lastIndex)
        val firstWordCursor = findFirstWordCursor(expanded, cursor)
        if (firstWordCursor >= expanded.size) return emptyList()
        cursor = firstWordCursor
        while (cursor > 0) {
            val prevExpandedToken = expanded[cursor - 1]
            val prevToken = prevExpandedToken.token
            val nextToken = expanded.getOrNull(cursor)?.token
            val isCurrencyPrefix = isCurrencyPrefixPunctuation(prevToken, nextToken)
            if (prevExpandedToken.originalIndex < startIndex && !isCurrencyPrefix) break
            val ch = prevToken.text.firstOrNull() ?: break
            val isLeadingOpening =
                prevToken.type == TokenType.PUNCTUATION &&
                    (ch == '"' ||
                        ch in OPENING_PUNCTUATION ||
                        isCurrencyPrefix)
            if (!isLeadingOpening) break
            cursor--
        }

        val frames = mutableListOf<RsvpFrame>()

        val state = ContextState()
        val rhythm =
            RhythmState(
                smoothingAlpha = config.smoothingAlpha,
                maxSpeedupFactor = config.maxSpeedupFactor,
                maxSlowdownFactor = config.maxSlowdownFactor,
            )
        val flow =
            FlowState(
                alpha = FLOW_EMA_ALPHA,
                maxBoost = FLOW_MAX_BOOST,
                maxSlowdown = FLOW_MAX_SLOWDOWN,
                strength = FLOW_STRENGTH,
            )

        while (cursor < expanded.size) {
            val cursorToken = expanded[cursor].token
            if (cursorToken.type == TokenType.PARAGRAPH_BREAK ||
                cursorToken.type == TokenType.PAGE_BREAK
            ) {
                val nextWordCursor = findFirstWordCursor(expanded, cursor + 1)
                if (nextWordCursor >= expanded.size) break

                val msPerWord = config.tempoMsPerWord.toDouble()
                val paragraphPauseScale =
                    pauseScale(
                        msPerWord = msPerWord,
                        config = config,
                        extraRetention = PARAGRAPH_BREAK_RETENTION_BOOST,
                    )
                val pagePauseScale =
                    pauseScale(
                        msPerWord = msPerWord,
                        config = config,
                        extraRetention = PAGE_BREAK_RETENTION_BOOST,
                    )
                val paragraphBase = paragraphBreakBasePauseMs(config)
                val paragraphFloor = paragraphBase * config.minPauseScale
                val pageFloor = pageBreakBasePauseMs(config) * config.minPauseScale
                val extraPause =
                    (cursorToken.pauseAfterMs.coerceAtLeast(0L).toDouble()) *
                        when (cursorToken.type) {
                            TokenType.PAGE_BREAK -> pagePauseScale
                            TokenType.PARAGRAPH_BREAK -> paragraphPauseScale
                            else -> paragraphPauseScale
                        }
                val durationMs =
                    when (cursorToken.type) {
                        TokenType.PAGE_BREAK -> max(
                            pageBreakBasePauseMs(config) * pagePauseScale,
                            pageFloor
                        ).toLong()
                        TokenType.PARAGRAPH_BREAK -> max(
                            paragraphBase * paragraphPauseScale,
                            paragraphFloor
                        ).toLong()
                        else -> 0L
                    }.let { base ->
                        (base + extraPause).toLong()
                    }.coerceAtLeast(MIN_FRAME_MS)

                frames +=
                    RsvpFrame(
                        tokens = listOf(breakMarkerToken(cursorToken.type)),
                        durationMs = durationMs,
                        originalTokenIndex = expanded[cursor].originalIndex,
                        resumeCursor = expanded[cursor].expandedIndex,
                        nextOriginalTokenIndex = expanded[nextWordCursor].originalIndex,
                    )
                rhythm.reset()
                flow.reset()
                cursor++
                continue
            }

            val contextBefore = state.snapshot()
            val wordCursor = findFirstWordCursor(expanded, cursor)
            if (wordCursor >= expanded.size) break
            val boundaryBefore = boundaryBefore(expanded, wordCursor)

            val frameStartCursor = cursor
            val (frameTokens, frameOriginalIndex, nextCursor) =
                buildUnit(
                    expandedTokens = expanded,
                    startCursor = cursor,
                    config = config,
                    state = state,
                )
            val prevTokenGlobal = expanded.getOrNull(cursor - 1)?.token
            val prevWordGlobal = findPrevWord(expanded, beforeIndex = cursor)
            val nextTokenGlobal = expanded.getOrNull(nextCursor)?.token
            val nextWordGlobal = expanded.getOrNull(
                findFirstWordCursor(expanded, nextCursor)
            )?.token
            cursor = nextCursor
            val nextFrameWordCursor = findFirstWordCursor(expanded, cursor)
            val frameNextOriginalIndex =
                if (nextFrameWordCursor < expanded.size) {
                    expanded[nextFrameWordCursor].originalIndex
                } else {
                    tokens.size
                }

            val contourWord = expanded[wordCursor].token
            val focalSuppression =
                if (config.useFocalStress &&
                    wordCursor !in tokenAnalysis.focalWordIndices &&
                    !shouldKeepFullFocalDuration(contourWord)
                ) {
                    config.focalSupportCompression.coerceIn(MIN_FOCAL_SUPPORT_COMPRESSION, 1.0)
                } else {
                    1.0
                }
            val anticipatoryLanding =
                if (config.useAnticipatoryLanding && wordCursor in tokenAnalysis.landingWordIndices) {
                    config.anticipatoryLandingBoost.coerceIn(1.0, MAX_ANTICIPATORY_LANDING_BOOST)
                } else {
                    1.0
                }
            val emDashAside =
                config.useParentheticalAside && wordCursor in tokenAnalysis.emDashAsideIndices
            val phraseContour = tokenAnalysis.phraseContours[wordCursor] ?: PhraseContour.NONE

            val durationMs =
                computeUnitDurationMs(
                    frameTokens = frameTokens,
                    config = config,
                    contextBefore = contextBefore,
                    rhythm = rhythm,
                    flow = flow,
                    prevToken = prevTokenGlobal,
                    prevWord = prevWordGlobal,
                    nextToken = nextTokenGlobal,
                    nextWord = nextWordGlobal,
                    boundaryBefore = boundaryBefore,
                    focalSuppression = focalSuppression,
                    anticipatoryLanding = anticipatoryLanding,
                    emDashAside = emDashAside,
                    phraseContour = phraseContour,
                )

            frames +=
                RsvpFrame(
                    tokens = frameTokens,
                    durationMs = durationMs,
                    originalTokenIndex = frameOriginalIndex,
                    resumeCursor = expanded[frameStartCursor].expandedIndex,
                    nextOriginalTokenIndex = frameNextOriginalIndex,
                )

            while (cursor < expanded.size &&
                expanded[cursor].token.type != TokenType.WORD &&
                expanded[cursor].token.type != TokenType.PARAGRAPH_BREAK &&
                expanded[cursor].token.type != TokenType.PAGE_BREAK &&
                !(
                    expanded[cursor].token.type == TokenType.PUNCTUATION &&
                        isOpeningPunctuation(
                            token = expanded[cursor].token,
                            state = state,
                            nextToken = expanded.getOrNull(cursor + 1)?.token,
                        )
                    )
            ) {
                state.consume(expanded[cursor].token)
                cursor++
            }
        }

        applySessionRamps(frames, config)
        applyBlinkSeparation(frames, config)
        return frames
    }

}

internal fun resolveAnalysisStartIndex(
    tokens: List<Token>,
    startIndex: Int,
): Int {
    if (tokens.isEmpty() || startIndex <= 0) return 0

    val safeStartIndex = startIndex.coerceIn(0, tokens.lastIndex)
    val lowerBound = (safeStartIndex - START_CONTEXT_TOKEN_LIMIT).coerceAtLeast(0)
    var cursor = safeStartIndex - 1
    while (cursor >= lowerBound) {
        val token = tokens[cursor]
        when (token.type) {
            TokenType.PAGE_BREAK,
            TokenType.PARAGRAPH_BREAK -> return cursor + 1
            TokenType.PUNCTUATION -> {
                val boundary =
                    boundaryBeforeForPunctuation(
                        token = token,
                        prevWord = findPrevWord(tokens, beforeIndex = cursor),
                        nextToken = tokens.getOrNull(cursor + 1),
                    )
                if (boundary != BoundaryBefore.NONE) return cursor + 1
            }
            TokenType.WORD -> Unit
        }
        cursor--
    }
    return lowerBound
}

private fun findPrevWord(
    tokens: List<Token>,
    beforeIndex: Int,
): Token? {
    var cursor = beforeIndex - 1
    while (cursor >= 0) {
        val token = tokens[cursor]
        if (token.type == TokenType.WORD) return token
        cursor--
    }
    return null
}

private const val START_CONTEXT_TOKEN_LIMIT = 240
