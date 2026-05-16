package com.kairo.reader.core.rsvp.analysis

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.rsvp.engine.CLAUSE_ANTICIPATORY_CONTOUR
import com.kairo.reader.core.rsvp.engine.CLAUSE_PRE_BOUNDARY_CONTOUR
import com.kairo.reader.core.rsvp.engine.CLAUSE_RESTART_CONTOUR
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.engine.PHRASE_CONTOUR_WORD_WINDOW
import com.kairo.reader.core.rsvp.engine.PhraseContour
import com.kairo.reader.core.rsvp.engine.SENTENCE_ANTICIPATORY_CONTOUR
import com.kairo.reader.core.rsvp.engine.SENTENCE_PRE_BOUNDARY_CONTOUR
import com.kairo.reader.core.rsvp.engine.SENTENCE_RESTART_CONTOUR
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTier
import com.kairo.reader.core.rsvp.timing.RsvpPunctuationTimingPolicy
import kotlin.math.max

internal data class RsvpTokenAnalysis(
    val focalWordIndices: Set<Int>,
    val landingWordIndices: Set<Int>,
    val emDashAsideIndices: Set<Int>,
    val phraseContours: Map<Int, PhraseContour>,
) {
    companion object {
        val EMPTY =
            RsvpTokenAnalysis(
                focalWordIndices = emptySet(),
                landingWordIndices = emptySet(),
                emDashAsideIndices = emptySet(),
                phraseContours = emptyMap(),
            )
    }
}

internal fun analyzeExpandedTokens(
    expanded: List<ExpandedToken>,
    config: RsvpConfig,
): RsvpTokenAnalysis {
    if (expanded.isEmpty()) return RsvpTokenAnalysis.EMPTY

    val focal = HashSet<Int>()
    val landings = HashSet<Int>()
    val asides = HashSet<Int>()
    val contours = HashMap<Int, PhraseContour>()
    val breathGroup = ArrayList<ExpandedToken>()
    var previousWord: Token? = null
    var emDashAsideCloseIndex = -1

    fun applyRestartContour(
        tier: RsvpPunctuationTier,
        afterIndex: Int,
    ) {
        var cursor = afterIndex + 1
        while (cursor < expanded.size) {
            val entry = expanded[cursor]
            when (entry.token.type) {
                TokenType.WORD -> {
                    val weight = restartContourWeight(tier = tier, distance = 1)
                    if (weight > 0.0) {
                        contours.mergeContour(entry.expandedIndex, restartWeight = weight)
                    }
                    return
                }
                TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> return
                TokenType.PUNCTUATION -> cursor++
            }
        }
    }

    fun applyBoundaryEffects(
        tier: RsvpPunctuationTier,
        boundaryIndex: Int,
    ) {
        if (config.useAnticipatoryLanding && breathGroup.size >= 2) {
            landings += breathGroup[breathGroup.lastIndex - 1].expandedIndex
        }
        breathGroup
            .asReversed()
            .take(PHRASE_CONTOUR_WORD_WINDOW)
            .forEachIndexed { distanceIndex, word ->
                val weight = preBoundaryContourWeight(tier = tier, distance = distanceIndex + 1)
                if (weight > 0.0) {
                    contours.mergeContour(word.expandedIndex, preBoundaryWeight = weight)
                }
            }
        if (config.useFocalStress) {
            addFocalWord(breathGroup, focal)
        }
        breathGroup.clear()
        applyRestartContour(tier = tier, afterIndex = boundaryIndex)
    }

    expanded.forEachIndexed { index, entry ->
        val token = entry.token
        when (token.type) {
            TokenType.WORD -> {
                if (config.useParentheticalAside && emDashAsideCloseIndex > index) {
                    asides += entry.expandedIndex
                }
                breathGroup += entry
                previousWord = token
            }
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> {
                if (config.useFocalStress) {
                    addFocalWord(breathGroup, focal)
                }
                breathGroup.clear()
                previousWord = null
                emDashAsideCloseIndex = -1
            }
            TokenType.PUNCTUATION -> {
                if (config.useParentheticalAside &&
                    emDashAsideCloseIndex < index &&
                    isEmDashChar(token.text.firstOrNull())
                ) {
                    val closeIndex = findEmDashAsideClose(expanded, index + 1)
                    if (closeIndex > index) {
                        emDashAsideCloseIndex = closeIndex
                    }
                }
                val tier =
                    RsvpPunctuationTimingPolicy.resolveTier(
                        token = token,
                        prevWord = previousWord,
                        nextToken = nextTokenAfter(expanded, index),
                    )
                if (tier == RsvpPunctuationTier.SENTENCE_END ||
                    tier == RsvpPunctuationTier.CLAUSE_BREAK
                ) {
                    applyBoundaryEffects(tier = tier, boundaryIndex = index)
                }
                if (index >= emDashAsideCloseIndex) {
                    emDashAsideCloseIndex = -1
                }
            }
        }
    }
    if (config.useFocalStress) {
        addFocalWord(breathGroup, focal)
    }

    return RsvpTokenAnalysis(
        focalWordIndices = if (config.useFocalStress) focal else emptySet(),
        landingWordIndices = if (config.useAnticipatoryLanding) landings else emptySet(),
        emDashAsideIndices = if (config.useParentheticalAside) asides else emptySet(),
        phraseContours = contours,
    )
}

private fun addFocalWord(
    group: List<ExpandedToken>,
    focal: MutableSet<Int>,
) {
    if (group.isEmpty()) return
    if (group.size == 1) {
        focal += group.first().expandedIndex
        return
    }

    var bestIndex = -1
    var bestScore = Double.NEGATIVE_INFINITY
    group.forEachIndexed { positionInGroup, entry ->
        val score = focalScore(entry.token, positionInGroup, group.size)
        if (score > bestScore || (score == bestScore && bestIndex != -1)) {
            bestScore = score
            bestIndex = entry.expandedIndex
        }
    }
    if (bestIndex >= 0) {
        focal += bestIndex
    } else {
        group.forEach { focal += it.expandedIndex }
    }
}

internal fun focalScore(
    token: Token,
    positionInGroup: Int,
    groupSize: Int,
): Double {
    if (token.isSubwordChunk) return Double.NEGATIVE_INFINITY
    val normalized = normalizeWord(token.text)
    if (normalized.isEmpty()) return Double.NEGATIVE_INFINITY
    val letters = normalized.count { it.isLetterOrDigit() }
    if (letters == 0) return Double.NEGATIVE_INFINITY

    val functionPenalty = if (isFunctionWord(normalized)) 0.25 else 1.0
    val anchorBonus = if (isSemanticAnchor(normalized)) 1.5 else 1.0
    // Tiny end-weighting so the final content word of a breath wins ties.
    val endWeight = 1.0 + (positionInGroup.toDouble() / (groupSize * 20.0))
    return letters.toDouble() * functionPenalty * anchorBonus * endWeight
}

internal fun MutableMap<Int, PhraseContour>.mergeContour(
    expandedIndex: Int,
    preBoundaryWeight: Double = 0.0,
    restartWeight: Double = 0.0,
) {
    val current = this[expandedIndex] ?: PhraseContour.NONE
    this[expandedIndex] =
        PhraseContour(
            preBoundaryWeight = max(current.preBoundaryWeight, preBoundaryWeight),
            restartWeight = max(current.restartWeight, restartWeight),
        )
}

internal fun preBoundaryContourWeight(
    tier: RsvpPunctuationTier,
    distance: Int,
): Double =
    when (tier) {
        RsvpPunctuationTier.SENTENCE_END ->
            when (distance) {
                1 -> SENTENCE_PRE_BOUNDARY_CONTOUR
                2 -> SENTENCE_ANTICIPATORY_CONTOUR
                else -> 0.0
            }
        RsvpPunctuationTier.CLAUSE_BREAK ->
            when (distance) {
                1 -> CLAUSE_PRE_BOUNDARY_CONTOUR
                2 -> CLAUSE_ANTICIPATORY_CONTOUR
                else -> 0.0
            }
        RsvpPunctuationTier.SOFT_SEPARATOR, RsvpPunctuationTier.NONE -> 0.0
    }

internal fun restartContourWeight(
    tier: RsvpPunctuationTier,
    distance: Int,
): Double =
    when (tier) {
        RsvpPunctuationTier.SENTENCE_END ->
            when (distance) {
                1 -> SENTENCE_RESTART_CONTOUR
                else -> 0.0
            }
        RsvpPunctuationTier.CLAUSE_BREAK ->
            when (distance) {
                1 -> CLAUSE_RESTART_CONTOUR
                else -> 0.0
            }
        RsvpPunctuationTier.SOFT_SEPARATOR, RsvpPunctuationTier.NONE -> 0.0
    }

internal fun phraseContourMultiplier(
    contour: PhraseContour,
    speedStrength: Double,
): Double {
    if (contour == PhraseContour.NONE || speedStrength <= 0.0) return 1.0
    val weight = contour.preBoundaryWeight + contour.restartWeight
    return 1.0 + (weight * speedStrength)
}

internal fun nextTokenAfter(
    expanded: List<ExpandedToken>,
    index: Int,
): Token? = expanded.getOrNull(index + 1)?.token

internal fun findEmDashAsideClose(expanded: List<ExpandedToken>, startIndex: Int): Int {
    for (j in startIndex until expanded.size) {
        val t = expanded[j].token
        when (t.type) {
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> return -1
            TokenType.PUNCTUATION -> {
                val ch = t.text.firstOrNull() ?: continue
                if (isEmDashChar(ch)) return j
                if (isSentenceEndingPunctuation(ch)) return -1
            }
            TokenType.WORD -> Unit
        }
    }
    return -1
}

internal fun isEmDashChar(ch: Char?): Boolean =
    ch == '\u2014' || ch == '\u2013'
