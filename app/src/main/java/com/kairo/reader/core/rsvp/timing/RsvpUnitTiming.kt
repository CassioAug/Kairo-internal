package com.kairo.reader.core.rsvp

import com.kairo.reader.core.linguistics.ClauseDetector
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.timing.CLAUSE_PUNCTUATION_RETENTION_BOOST
import com.kairo.reader.core.rsvp.timing.STRONG_PUNCTUATION_RETENTION_BOOST
import kotlin.math.max

internal fun computeUnitDurationMs(
    frameTokens: List<Token>,
    config: RsvpConfig,
    contextBefore: ContextSnapshot,
    rhythm: RhythmState,
    flow: FlowState,
    prevToken: Token?,
    prevWord: Token?,
    nextToken: Token?,
    nextWord: Token?,
    boundaryBefore: BoundaryBefore,
    focalSuppression: Double = 1.0,
    anticipatoryLanding: Double = 1.0,
    emDashAside: Boolean = false,
    phraseContour: PhraseContour = PhraseContour.NONE,
): Long {
    val msPerWord = config.tempoMsPerWord.toDouble()
    val pauseScale = pauseScale(msPerWord, config)
    val clausePauseScale =
        pauseScale(
            msPerWord = msPerWord,
            config = config,
            extraRetention = CLAUSE_PUNCTUATION_RETENTION_BOOST,
        )
    val sentencePauseScale =
        pauseScale(
            msPerWord = msPerWord,
            config = config,
            extraRetention = STRONG_PUNCTUATION_RETENTION_BOOST,
        )
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

    val words = frameTokens.filter { it.type == TokenType.WORD }
    val paragraphBreaks = frameTokens.count { it.type == TokenType.PARAGRAPH_BREAK }
    val pageBreaks = frameTokens.count { it.type == TokenType.PAGE_BREAK }
    val firstWordIndex = frameTokens.indexOfFirst { it.type == TokenType.WORD }
    val speedStrength = speedStrength(msPerWord)
    val prosodyStrength =
        if (config.useProsodyPacing) {
            config.prosodyStrength.coerceIn(0.0, 1.6)
        } else {
            0.0
        }
    val firstWord = words.firstOrNull()
    val boundaryForBoost =
        if (boundaryBefore == BoundaryBefore.NONE &&
            prevToken?.type == TokenType.PUNCTUATION &&
            boundaryBeforeForPunctuation(
                token = prevToken,
                prevWord = prevWord,
                nextToken = firstWord ?: nextToken,
            ) != BoundaryBefore.NONE
        ) {
            boundaryBeforeForPunctuation(
                token = prevToken,
                prevWord = prevWord,
                nextToken = firstWord ?: nextToken,
            )
        } else {
            boundaryBefore
        }
    val startBoost =
        startBoostMultiplier(msPerWord = msPerWord, boundaryBefore = boundaryForBoost)
    val clauseConfigStrength = (
        (config.clausePauseFactor - 1.0) /
            (DEFAULT_CLAUSE_PAUSE_FACTOR - 1.0)
        ).coerceIn(0.0, 2.0)
    val dialogueEntryBoost = 1.0 + (DIALOGUE_ENTRY_BOOST * speedStrength)
    val speakerTagMultiplier =
        speakerTagMultiplier(
            wordsInFrame = words,
            prevWord = prevWord,
            nextWord = nextWord,
            config = config,
            speedStrength = speedStrength,
        )

    var duration = 0.0
    var parentheticalDepth = contextBefore.parentheticalDepth
    var inDialogue = contextBefore.inDialogue
    var enteredDialogue = false
    var exitedDialogue = false
    var sawParentheticalWord = false

    frameTokens.forEachIndexed { index, token ->
        when (token.type) {
            TokenType.PUNCTUATION -> {
                val ch = token.text.firstOrNull()
                val wasInDialogue = inDialogue
                when (ch) {
                    '(', '[', '{' -> parentheticalDepth++
                    ')', ']', '}' -> parentheticalDepth = max(0, parentheticalDepth - 1)
                    '"', '\u201C', '\u201D', '\u2018', '\u2019' -> Unit
                }
                inDialogue = token.isDialogue
                if (config.useDialogueDetection && ch != null && isQuoteChar(ch)) {
                    if (!wasInDialogue && inDialogue) {
                        enteredDialogue = true
                    } else if (wasInDialogue && !inDialogue) {
                        exitedDialogue = true
                    }
                }
            }
            TokenType.WORD -> {
                val dialogueMultiplier = if (config.useDialogueDetection &&
                    inDialogue
                ) {
                    contextShapingMultiplier(
                        targetMultiplier = config.dialogueMultiplier,
                        speedStrength = speedStrength,
                    )
                } else {
                    1.0
                }
                val inAside =
                    config.useParentheticalAside && (parentheticalDepth > 0 || emDashAside)
                val parentheticalFactor =
                    when {
                        inAside -> config.parentheticalAsideMultiplier.coerceIn(0.5, 1.0)
                        parentheticalDepth > 0 -> config.parentheticalMultiplier
                        else -> 1.0
                    }
                val contextWordMultiplier = parentheticalFactor * dialogueMultiplier
                if (parentheticalDepth > 0 && !inAside) {
                    sawParentheticalWord = true
                }
                val boosted = if (index == firstWordIndex) startBoost else 1.0
                val nextWordText =
                    frameTokens
                        .subList(index + 1, frameTokens.size)
                        .firstOrNull { it.type == TokenType.WORD }
                        ?.text
                        ?: nextWord?.text
                val prevWordText =
                    frameTokens
                        .subList(0, index)
                        .lastOrNull { it.type == TokenType.WORD }
                        ?.text
                        ?: prevWord?.text

                val clauseMultiplier =
                    if (!config.useClausePausing) {
                        1.0
                    } else {
                        val raw = ClauseDetector.getClausePauseFactor(token.text, nextWordText)
                        1.0 + ((raw - 1.0) * speedStrength * clauseConfigStrength)
                    }

                val terminalMultiplier =
                    terminalWordMultiplier(
                        wordIndex = index,
                        word = token,
                        frameTokens = frameTokens,
                        nextToken = nextToken,
                        speedStrength = speedStrength,
                    )

                val emphasisMultiplier =
                    emphasisMultiplier(
                        token = token,
                        isFirstWord = index == firstWordIndex,
                        boundaryBefore = boundaryBefore,
                        speedStrength = speedStrength,
                    )
                val prosodyMultiplier =
                    prosodyMultiplier(
                        token = token,
                        prevWordText = prevWordText,
                        nextWordText = nextWordText,
                        isFirstWord = index == firstWordIndex,
                        boundaryBefore = boundaryBefore,
                        speedStrength = speedStrength,
                        prosodyStrength = prosodyStrength,
                    )

                val dialogueEntryMultiplier =
                    if (config.useDialogueDetection &&
                        !contextBefore.inDialogue &&
                        index == firstWordIndex &&
                        token.isDialogue
                    ) {
                        dialogueEntryBoost
                    } else {
                        1.0
                    }
                val phraseContourMultiplier =
                    phraseContourMultiplier(
                        contour = phraseContour,
                        speedStrength = speedStrength,
                    )

                val wordMs =
                    wordDurationMs(token, msPerWord, config) *
                        contextWordMultiplier *
                        boosted *
                        clauseMultiplier *
                        terminalMultiplier *
                        emphasisMultiplier *
                        prosodyMultiplier *
                        dialogueEntryMultiplier *
                        speakerTagMultiplier *
                        focalSuppression *
                        anticipatoryLanding *
                        phraseContourMultiplier
                duration += max(wordMs, wordFloorMs(token, config).toDouble())
                if (token.pauseAfterMs > 0L) {
                    duration += token.pauseAfterMs * pauseScale
                }
            }
            else -> Unit
        }
    }

    val transitionHold =
        transitionHoldMs(
            frameTokens = frameTokens,
            firstWord = firstWord,
            nextWord = nextWord,
            speedStrength = speedStrength,
            prosodyStrength = prosodyStrength,
        )
    if (transitionHold > 0.0) {
        duration += transitionHold
    }

    duration *= multiWordPenalty(words.size)

    // Apply flow and rhythm smoothing to word duration BEFORE adding punctuation pauses.
    // This ensures punctuation pauses are not reduced by the smoothing algorithms.
    val hardBoundary = isHardBoundary(frameTokens, nextToken)
    val difficulty = frameDifficulty(words)
    duration *=
        flow.apply(
            difficulty = difficulty,
            speedStrength = speedStrength,
            isBoundary = hardBoundary,
        )

    val smoothedWordDuration = rhythm.apply(duration, isBoundary = hardBoundary)

    // Now add punctuation pauses on top of the smoothed word duration.
    // These pauses are intentionally NOT smoothed so they remain prominent.
    var totalDuration = smoothedWordDuration
    if (words.isNotEmpty()) {
        when (boundaryForBoost) {
            BoundaryBefore.SENTENCE -> {
                totalDuration +=
                    max(config.sentenceEndPauseMs, config.periodPauseMs) *
                        sentencePauseScale *
                        SENTENCE_START_HOLD_FRACTION
            }
            BoundaryBefore.CLAUSE -> {
                totalDuration += clauseStartHoldMs(config = config, pauseScale = clausePauseScale)
            }
            BoundaryBefore.PARAGRAPH, BoundaryBefore.PAGE, BoundaryBefore.NONE -> Unit
        }
        totalDuration += boundaryStartMicroHoldMs(
            msPerWord = msPerWord,
            speedStrength = speedStrength,
            boundaryBefore = boundaryForBoost,
        )
    }
    var punctuationParentheticalDepth = contextBefore.parentheticalDepth
    var punctuationInDialogue = contextBefore.inDialogue
    frameTokens.forEachIndexed { index, token ->
        if (token.type == TokenType.WORD) {
            if (token.isDialogue) punctuationInDialogue = true
            return@forEachIndexed
        }
        if (token.type != TokenType.PUNCTUATION) return@forEachIndexed

        val punctuationChar = token.text.firstOrNull()
        val punctuationInsideAside =
            config.useParentheticalAside &&
                (
                    punctuationParentheticalDepth > 0 ||
                        emDashAside ||
                        punctuationChar == ')' ||
                        punctuationChar == ']' ||
                        punctuationChar == '}'
                    )
        val punctuationInsideDialogue =
            config.useDialogueDetection && (punctuationInDialogue || token.isDialogue)

        val prevTokenInFrame = frameTokens.getOrNull(index - 1)
        val nextTokenInFrame = frameTokens.getOrNull(index + 1)
        if (shouldSkipPunctuationPause(
                token = token,
                index = index,
                firstWordIndex = firstWordIndex,
                prevToken = prevTokenInFrame,
                nextToken = nextTokenInFrame,
            )
        ) {
            punctuationParentheticalDepth =
                updateParentheticalDepthAfterPunctuation(punctuationParentheticalDepth, token)
            punctuationInDialogue = token.isDialogue
            return@forEachIndexed
        }

        val prevWordInFrame = frameTokens.subList(0, index).lastOrNull {
            it.type ==
                TokenType.WORD
        }
        val nextWordInFrame = frameTokens.subList(index + 1, frameTokens.size).firstOrNull {
            it.type ==
                TokenType.WORD
        }

        totalDuration +=
            punctuationPauseMs(
                token = token,
                prevWord = prevWordInFrame ?: prevToken,
                nextToken = nextWordInFrame ?: nextToken,
                msPerWord = msPerWord,
                config = config,
                insideAside = punctuationInsideAside,
                insideDialogue = punctuationInsideDialogue,
            )

        punctuationParentheticalDepth =
            updateParentheticalDepthAfterPunctuation(punctuationParentheticalDepth, token)
        punctuationInDialogue = token.isDialogue
    }
    if (config.usePunctuationLandingHold) {
        totalDuration +=
            punctuationLandingHoldMs(
                frameTokens = frameTokens,
                nextToken = nextToken,
                msPerWord = msPerWord,
                speedStrength = speedStrength,
            )
    }
    if (paragraphBreaks > 0) {
        totalDuration += config.paragraphPauseMs * paragraphPauseScale * paragraphBreaks
    }
    if (pageBreaks > 0) {
        val base = pageBreakBasePauseMs(config)
        val floor = base * config.minPauseScale
        val scaled = base * pagePauseScale
        totalDuration += max(scaled, floor) * pageBreaks
    }
    if (paragraphBreaks == 0 && pageBreaks == 0) {
        totalDuration +=
            adaptiveHoldMs(
                words = words,
                difficulty = difficulty,
                config = config,
                speedStrength = speedStrength,
                hardBoundary = hardBoundary,
                nextWord = nextWord,
                clauseConfigStrength = clauseConfigStrength,
            )
        if (!hardBoundary &&
            config.useClausePausing &&
            nextWord?.isClauseBoundary == true
        ) {
            totalDuration +=
                config.commaPauseMs * pauseScale * CLAUSE_LEAD_HOLD_FRACTION * clauseConfigStrength
        }
    }
    if (config.useDialogueDetection && (enteredDialogue || exitedDialogue)) {
        val quoteHold = config.quotePauseMs * pauseScale * QUOTE_TRANSITION_HOLD_FRACTION
        if (enteredDialogue) totalDuration += quoteHold
        if (exitedDialogue) totalDuration += quoteHold
    }
    if (sawParentheticalWord) {
        totalDuration =
            max(
                totalDuration,
                smoothedWordDuration * config.parentheticalMultiplier.coerceAtLeast(1.0),
            )
        totalDuration += parentheticalHoldMs(msPerWord = msPerWord, config = config)
    }

    return totalDuration
        .toLong()
        .coerceAtLeast(MIN_FRAME_MS)
}
