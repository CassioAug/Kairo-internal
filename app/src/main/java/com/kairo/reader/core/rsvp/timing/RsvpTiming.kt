package com.kairo.reader.core.rsvp

import com.kairo.reader.core.linguistics.ClauseDetector
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.model.wordFloorMsForReadability
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTier
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

internal fun adaptiveHoldMs(
    words: List<Token>,
    difficulty: Double,
    config: RsvpConfig,
    speedStrength: Double,
    hardBoundary: Boolean,
    nextWord: Token?,
    clauseConfigStrength: Double,
): Double {
    if (!config.useAdaptiveTiming || words.isEmpty() || nextWord == null) return 0.0

    val difficultyScale =
        ((difficulty - ADAPTIVE_DIFFICULTY_FLOOR).coerceAtLeast(0.0) /
            (1.0 - ADAPTIVE_DIFFICULTY_FLOOR))
            .coerceIn(0.0, 1.0)
    var hold = difficultyScale * config.adaptiveDifficultyMaxHoldMs * speedStrength

    if (words.any { it.complexityMultiplier >= config.complexWordThreshold }) {
        hold += config.complexWordHoldMs * speedStrength
    }

    val lastWord = words.lastOrNull()
    if (!hardBoundary && config.useClausePausing && lastWord?.isClauseBoundary == true) {
        hold += CLAUSE_BOUNDARY_HOLD_MS * speedStrength * clauseConfigStrength
    }

    // Add hold for phrase enders to give reader time to process the phrase
    if (lastWord != null && ClauseDetector.isPhraseEnder(lastWord.text)) {
        hold += PHRASE_BREAK_HOLD_MS * speedStrength * 0.6
    }

    // Reduce hold if next word has high coherence with current (they belong together)
    val coherence = ClauseDetector.getCoherenceScore(
        lastWord?.text.orEmpty(),
        nextWord.text,
    )
    if (coherence >= 0.5) {
        // Words belong together, reduce the hold to keep them mentally grouped
        hold *= (1.0 - coherence * 0.4)
    }

    return hold.coerceAtMost(ADAPTIVE_HOLD_MAX_MS * speedStrength)
}


internal fun wordDurationMs(
    word: Token,
    msPerWord: Double,
    config: RsvpConfig,
): Double {
    val text = word.text
    val fullLetters = text.count { it.isLetterOrDigit() }.coerceAtLeast(1)
    val (letters, syllables) =
        if (word.isSubwordChunk &&
            word.highlightStart != null &&
            word.highlightEndExclusive != null &&
            word.highlightEndExclusive > word.highlightStart &&
            word.highlightEndExclusive <= text.length
        ) {
            val chunkText = text.substring(word.highlightStart, word.highlightEndExclusive)
            val chunkLetters = chunkText.count { it.isLetterOrDigit() }.coerceAtLeast(1)
            val ratio = (chunkLetters.toDouble() / fullLetters.toDouble()).coerceIn(0.2, 1.0)
            val scaledSyllables =
                max(1.0, word.syllableCount.toDouble() * ratio).roundToLong().toInt()
            chunkLetters to scaledSyllables
        } else {
            fullLetters to word.syllableCount
        }

    val lengthCurve =
        run {
            val x = ((letters - 4).coerceAtLeast(0) / 10.0)
            1.0 + config.lengthStrength * (x.pow(config.lengthExponent))
        }

    val complexityComponent =
        1.0 + (max(0.0, word.complexityMultiplier - 1.0) * config.complexityStrength)

    val rarityExtra = (1.0 - word.frequencyScore).coerceIn(0.0, 1.0) * config.rarityExtraMaxMs
    val syllableExtra = max(0, syllables - 1) * config.syllableExtraMs

    var duration = (msPerWord * lengthCurve * complexityComponent) + rarityExtra + syllableExtra

    if (letters >= config.longWordChars) {
        duration = max(duration, config.longWordMinMs.toDouble())
    }

    if (text.endsWith("-")) {
        duration += msPerWord * 0.25
    }

    return duration
}


internal fun punctuationPauseMs(
    token: Token,
    prevWord: Token?,
    nextToken: Token?,
    msPerWord: Double,
    config: RsvpConfig,
    insideAside: Boolean = false,
    insideDialogue: Boolean = false,
): Double {
    val ch = token.text.firstOrNull() ?: return 0.0
    val prevText = prevWord?.text.orEmpty()

    if (ch == '.' && isDecimalPoint(prevText, nextToken)) {
        return 0.0
    }

    val timing = RsvpPunctuationTimingPolicy.resolvePauseTiming(token, prevWord, nextToken, config)
    var base = timing.baseMs
    var floor = timing.floorMs
    val tier =
        RsvpPunctuationTimingPolicy.resolveTier(
            token = token,
            prevWord = prevWord,
            nextToken = nextToken,
        )
    if (prevWord != null && nextToken?.type == TokenType.WORD) {
        base *= phraseContourPauseRedistributionFactor(tier)
    }

    val speedStrength = speedStrength(msPerWord)
    if (isClauseLeadPunctuation(ch, nextToken)) {
        base += CLAUSE_LEAD_BOOST_MS * speedStrength
    }

    if (isSentenceEndingPunctuation(ch) || ch == '.') {
        if (nextToken?.type == TokenType.PARAGRAPH_BREAK ||
            nextToken?.type == TokenType.PAGE_BREAK
        ) {
            base += SENTENCE_END_BREAK_BOOST_MS * speedStrength
        }
    }

    if (isEmbeddedQuote(ch, prevWord, nextToken)) {
        base *= EMBEDDED_QUOTE_FACTOR
        floor *= EMBEDDED_QUOTE_FACTOR
    }

    val punctuationScale =
        pauseScale(
            msPerWord = msPerWord,
            config = config,
            extraRetention = timing.scaleRetentionBoost,
        )
    val dialogueScale =
        if (config.useDialogueDetection && insideDialogue && !isQuoteChar(ch)) {
            config.dialoguePunctuationScale.coerceIn(0.5, 1.0)
        } else {
            1.0
        }
    val asideScale =
        if (insideAside && !isQuoteChar(ch)) {
            config.parentheticalAsideMultiplier.coerceIn(0.5, 1.0)
        } else {
            1.0
        }
    val scaled = base * punctuationScale * dialogueScale * asideScale
    return max(scaled, floor)
}


internal fun phraseContourPauseRedistributionFactor(tier: RsvpPunctuationTier): Double =
    when (tier) {
        RsvpPunctuationTier.SENTENCE_END -> SENTENCE_CONTOUR_PAUSE_RETAINED
        RsvpPunctuationTier.CLAUSE_BREAK -> CLAUSE_CONTOUR_PAUSE_RETAINED
        RsvpPunctuationTier.SOFT_SEPARATOR, RsvpPunctuationTier.NONE -> 1.0
    }


internal fun punctuationLandingHoldMs(
    frameTokens: List<Token>,
    nextToken: Token?,
    msPerWord: Double,
    speedStrength: Double,
): Double {
    val nextWordExists =
        nextToken?.type == TokenType.WORD ||
            frameTokens.any { it.type == TokenType.PARAGRAPH_BREAK || it.type == TokenType.PAGE_BREAK }
    if (!nextWordExists) return 0.0

    val weight =
        frameTokens
            .mapIndexedNotNull { index, token ->
                if (token.type != TokenType.PUNCTUATION) return@mapIndexedNotNull null
                val prevWord =
                    frameTokens
                        .subList(0, index)
                        .lastOrNull { it.type == TokenType.WORD }
                boundaryLandingWeight(
                    token = token,
                    prevWord = prevWord,
                    nextToken = nextToken,
                )
            }.maxOrNull()
          ?: return 0.0
    if (weight <= 0.0) return 0.0

    val base = (msPerWord * weight).coerceIn(MIN_LANDING_HOLD_MS, MAX_LANDING_HOLD_MS)
    val speedAdjusted = base * (1.0 + (speedStrength * LANDING_HOLD_SPEED_BOOST))
    return speedAdjusted.coerceAtMost(MAX_LANDING_HOLD_MS)
}


internal fun boundaryLandingWeight(
    token: Token,
    prevWord: Token?,
    nextToken: Token?,
): Double {
    return RsvpPunctuationTimingPolicy.boundaryLandingWeight(
        token = token,
        prevWord = prevWord,
        nextToken = nextToken,
    )
}


internal fun pauseScale(
    msPerWord: Double,
    config: RsvpConfig,
    extraRetention: Double = 0.0,
): Double {
    val minPauseScale = config.minPauseScale.coerceIn(0.0, MAX_MIN_PAUSE_SCALE)
    val pauseScaleExponent = config.pauseScaleExponent.coerceAtLeast(0.0)
    val ratio = (msPerWord / BASE_MS_PER_WORD_AT_300).coerceIn(0.12, 2.5)
    val compressed = ratio.pow(pauseScaleExponent)
    val preservedFloor =
        (minPauseScale + extraRetention)
            .coerceIn(minPauseScale, MAX_MIN_PAUSE_SCALE)
    val scaled = preservedFloor + ((1.0 - preservedFloor) * compressed)
    return scaled.coerceIn(minPauseScale, 1.35)
}


internal fun wordFloorMs(
    word: Token,
    config: RsvpConfig,
): Long = config.wordFloorMsForReadability(word)


internal fun pageBreakBasePauseMs(config: RsvpConfig): Double =
    max(
        config.paragraphPauseMs.toDouble() * config.pageBreakPauseMultiplier,
        max(config.sentenceEndPauseMs.toDouble(), config.periodPauseMs.toDouble()) *
            (config.pageBreakPauseMultiplier * PAGE_BREAK_SENTENCE_MULTIPLIER_RATIO),
    )


internal fun paragraphBreakBasePauseMs(config: RsvpConfig): Double =
    max(
        config.paragraphPauseMs.toDouble() * config.paragraphPauseMultiplier,
        config.sentenceEndPauseMs.toDouble() * PARAGRAPH_SENTENCE_MULTIPLIER,
    )


internal fun boundaryStartMicroHoldMs(
    msPerWord: Double,
    speedStrength: Double,
    boundaryBefore: BoundaryBefore,
): Double {
    if (msPerWord > 110.0) return 0.0
    return when (boundaryBefore) {
        BoundaryBefore.SENTENCE -> SENTENCE_START_MIN_HOLD_MS * speedStrength
        BoundaryBefore.CLAUSE -> CLAUSE_START_MIN_HOLD_MS * speedStrength
        BoundaryBefore.PARAGRAPH, BoundaryBefore.PAGE, BoundaryBefore.NONE -> 0.0
    }
}


internal fun clauseStartHoldMs(
    config: RsvpConfig,
    pauseScale: Double,
): Double {
    val base =
        max(
            config.commaPauseMs.toDouble(),
            max(
                config.semicolonPauseMs.toDouble() * 0.72,
                max(
                    config.colonPauseMs.toDouble() * 0.78,
                    config.dashPauseMs.toDouble() * 0.78,
                ),
            ),
        )
    return base * pauseScale * CLAUSE_START_HOLD_FRACTION
}


internal fun parentheticalHoldMs(
    msPerWord: Double,
    config: RsvpConfig,
): Double {
    val multiplierDelta = (config.parentheticalMultiplier - 1.0).coerceAtLeast(0.0)
    if (multiplierDelta <= 0.0) return 0.0
    return msPerWord * multiplierDelta * PARENTHETICAL_HOLD_FRACTION
}


internal fun startBoostMultiplier(
    msPerWord: Double,
    boundaryBefore: BoundaryBefore,
): Double {
    val strength = speedStrength(msPerWord)
    val maxExtra =
        when (boundaryBefore) {
            BoundaryBefore.CLAUSE -> 0.05
            BoundaryBefore.SENTENCE -> 0.10
            BoundaryBefore.PARAGRAPH -> 0.16
            BoundaryBefore.PAGE -> 0.22
            BoundaryBefore.NONE -> 0.0
        }

    return 1.0 + (maxExtra * strength)
}


internal fun speedStrength(msPerWord: Double): Double {
    val speedFactor = (BASE_MS_PER_WORD_AT_300 / msPerWord).coerceIn(1.0, 3.5)
    return ((speedFactor - 1.0) / 2.5).coerceIn(0.0, 1.0)
}
