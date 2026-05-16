package com.kairo.reader.core.rsvp.timing

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.isMidSentencePunctuation
import com.kairo.reader.core.model.isSentenceEndingPunctuation
import com.kairo.reader.core.model.speedNarrowingFactor
import com.kairo.reader.core.rsvp.text.isAbbreviationDot
import com.kairo.reader.core.rsvp.text.isClauseLeadPunctuation
import com.kairo.reader.core.rsvp.text.isDecimalPoint
import com.kairo.reader.core.rsvp.text.isLikelySentenceContinuation
import com.kairo.reader.core.rsvp.text.isThousandSeparator
import kotlin.math.max
import kotlin.math.min

internal data class RsvpPunctuationPauseTiming(
    val baseMs: Double,
    val floorMs: Double,
    val scaleRetentionBoost: Double,
)

internal data class RsvpBoundaryContour(
    val landingHoldWeight: Double,
    val tailLiftWeight: Double,
)

internal enum class RsvpPunctuationTier {
    SENTENCE_END,
    CLAUSE_BREAK,
    SOFT_SEPARATOR,
    NONE,
}

internal object RsvpPunctuationTimingPolicy {
    fun resolvePauseTiming(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
        config: RsvpConfig,
    ): RsvpPunctuationPauseTiming {
        val ch = token.text.firstOrNull() ?: return ZERO_PAUSE_TIMING
        val prevText = prevWord?.text.orEmpty()
        val tier = resolveTier(token = token, prevWord = prevWord, nextToken = nextToken)

        if (tier == RsvpPunctuationTier.NONE) {
            return ZERO_PAUSE_TIMING
        }

        val base =
            pauseBaseMs(
                ch = ch,
                tier = tier,
                prevText = prevText,
                nextToken = nextToken,
                config = config,
            ) ?: return ZERO_PAUSE_TIMING

        val floor =
            pauseFloorMs(
                ch = ch,
                tier = tier,
                prevText = prevText,
                nextToken = nextToken,
                config = config,
            )

        val scaleRetentionBoost =
            scaleRetentionBoost(ch = ch, tier = tier, nextToken = nextToken)

        val breathingScale = punctuationBreathingScale(config)
        val speedScale = config.speedNarrowingFactor(config.tempoMsPerWord)

        return RsvpPunctuationPauseTiming(
            baseMs = base * breathingScale * speedScale,
            floorMs = floor * breathingScale * speedScale,
            scaleRetentionBoost = scaleRetentionBoost,
        )
    }

    fun boundaryLandingWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Double {
        return resolveBoundaryContour(
            token = token,
            prevWord = prevWord,
            nextToken = nextToken,
        ).landingHoldWeight
    }

    fun boundaryTailLiftWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Double {
        return resolveBoundaryContour(
            token = token,
            prevWord = prevWord,
            nextToken = nextToken,
        ).tailLiftWeight
    }

    internal fun resolveBoundaryContour(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): RsvpBoundaryContour {
        val ch = token.text.firstOrNull() ?: return ZERO_BOUNDARY_CONTOUR
        val prevText = prevWord?.text.orEmpty()
        val tier = resolveTier(token = token, prevWord = prevWord, nextToken = nextToken)
        if (tier == RsvpPunctuationTier.NONE) return ZERO_BOUNDARY_CONTOUR
        if (ch == '.' &&
            (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken))
        ) {
            return ZERO_BOUNDARY_CONTOUR
        }
        if (ch == ',' && isThousandSeparator(prevText, nextToken)) {
            return ZERO_BOUNDARY_CONTOUR
        }

        val contourStrength =
            boundaryTierContourWeight(
                token = token,
                prevWord = prevWord,
                nextToken = nextToken,
                tier = tier,
            )
        if (contourStrength <= 0.0) return ZERO_BOUNDARY_CONTOUR

        val landingHoldWeight =
            when {
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '\u2026' ->
                    ELLIPSIS_LANDING_HOLD_WEIGHT
                tier == RsvpPunctuationTier.SENTENCE_END ->
                    STRONG_LANDING_HOLD_WEIGHT
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ';' ->
                    SEMICOLON_LANDING_HOLD_WEIGHT
                tier == RsvpPunctuationTier.CLAUSE_BREAK ->
                    CLAUSE_LANDING_HOLD_WEIGHT
                else -> 0.0
            } * contourStrength

        val tailLiftWeight =
            when {
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '.' ->
                    when {
                        isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken) -> 0.0
                        isLikelySentenceContinuation(nextToken) -> 1.18
                        else -> 1.34
                    }
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '\u2026' -> 1.40
                tier == RsvpPunctuationTier.SENTENCE_END -> 1.26
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ';' -> 0.64
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ':' -> 0.68
                tier == RsvpPunctuationTier.CLAUSE_BREAK &&
                    (ch == '\u2014' || ch == '\u2013' || ch == '-') -> 0.74
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ',' ->
                    if (isClauseLeadPunctuation(ch, nextToken)) {
                        0.36
                    } else {
                        0.24
                    }
                else -> 0.0
            } * contourStrength

        return balanceBoundaryContour(
            tier = tier,
            landingHoldWeight = landingHoldWeight,
            tailLiftWeight = tailLiftWeight,
        )
    }

    private fun boundaryTierContourWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
        tier: RsvpPunctuationTier,
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = prevWord?.text.orEmpty()
        return when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                when (ch) {
                    '.' -> {
                        when {
                            isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken) -> 0.0
                            isLikelySentenceContinuation(nextToken) -> 0.55
                            else -> 0.92
                        }
                    }
                    '\u2026' -> 1.0
                    else -> 0.88
                }
            RsvpPunctuationTier.CLAUSE_BREAK ->
                when (ch) {
                    ';' -> 0.72
                    ':' -> 0.76
                    '\u2014', '\u2013', '-' -> 0.82
                    ',' ->
                        if (isClauseLeadPunctuation(ch, nextToken)) {
                            0.42
                        } else {
                            0.24
                        }
                    else -> 0.0
                }
            RsvpPunctuationTier.SOFT_SEPARATOR -> 0.0
            RsvpPunctuationTier.NONE -> 0.0
        }
    }

    private fun balanceBoundaryContour(
        tier: RsvpPunctuationTier,
        landingHoldWeight: Double,
        tailLiftWeight: Double,
    ): RsvpBoundaryContour {
        if (landingHoldWeight <= 0.0 || tailLiftWeight <= 0.0) {
            return RsvpBoundaryContour(
                landingHoldWeight = landingHoldWeight,
                tailLiftWeight = tailLiftWeight,
            )
        }

        val overlapPressure =
            when (tier) {
                RsvpPunctuationTier.SENTENCE_END -> 0.48
                RsvpPunctuationTier.CLAUSE_BREAK -> 0.32
                RsvpPunctuationTier.SOFT_SEPARATOR, RsvpPunctuationTier.NONE -> 0.0
            }

        if (overlapPressure <= 0.0) {
            return RsvpBoundaryContour(
                landingHoldWeight = landingHoldWeight,
                tailLiftWeight = tailLiftWeight,
            )
        }

        val dampening = min(0.16, landingHoldWeight.coerceIn(0.0, 0.35) * overlapPressure)
        return RsvpBoundaryContour(
            landingHoldWeight = landingHoldWeight,
            tailLiftWeight = tailLiftWeight * (1.0 - dampening),
        )
    }

    private fun ellipsisPauseBaseMs(
        nextToken: Token?,
        config: RsvpConfig,
    ): Double {
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD }?.text
        val nextStartsSentenceLike =
            nextWord?.firstOrNull { it.isLetter() }?.isUpperCase() == true ||
                nextWord?.lowercase() in SENTENCE_STARTERS
        val breakAfterEllipsis =
            nextToken?.type == TokenType.PARAGRAPH_BREAK || nextToken?.type == TokenType.PAGE_BREAK
        return if (nextStartsSentenceLike || breakAfterEllipsis || nextWord == null) {
            max(
                config.commaPauseMs * ELLIPSIS_SENTENCE_COMMA_FACTOR,
                config.periodPauseMs * ELLIPSIS_PERIOD_FACTOR,
            )
        } else {
            config.commaPauseMs * ELLIPSIS_INLINE_COMMA_FACTOR
        }
    }

    private fun punctuationTier(ch: Char): RsvpPunctuationTier =
        when {
            isSentenceEndingPunctuation(ch) -> RsvpPunctuationTier.SENTENCE_END
            ch == '\u2026' -> RsvpPunctuationTier.SENTENCE_END
            ch in listOf(',', ';', ':', '\u2014', '\u2013', '-') -> RsvpPunctuationTier.CLAUSE_BREAK
            ch in listOf('(', ')', '[', ']', '{', '}', '"', '\u201C', '\u201D', '\u2018', '\u2019') ->
                RsvpPunctuationTier.SOFT_SEPARATOR
            isMidSentencePunctuation(ch) -> RsvpPunctuationTier.SOFT_SEPARATOR
            else -> RsvpPunctuationTier.NONE
        }

    private fun punctuationBreathingScale(config: RsvpConfig): Double =
        config.punctuationPauseFactor.coerceIn(0.5, 1.75)

    private fun pauseBaseMs(
        ch: Char,
        tier: RsvpPunctuationTier,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double? =
        when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                sentencePauseBaseMs(ch = ch, prevText = prevText, nextToken = nextToken, config = config)
            RsvpPunctuationTier.CLAUSE_BREAK ->
                clausePauseBaseMs(ch = ch, prevText = prevText, nextToken = nextToken, config = config)
            RsvpPunctuationTier.SOFT_SEPARATOR ->
                softSeparatorPauseBaseMs(ch = ch, config = config)
            RsvpPunctuationTier.NONE -> null
        }

    private fun pauseFloorMs(
        ch: Char,
        tier: RsvpPunctuationTier,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double =
        when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                sentencePauseFloorMs(ch = ch, prevText = prevText, nextToken = nextToken, config = config)
            RsvpPunctuationTier.CLAUSE_BREAK ->
                clausePauseFloorMs(ch = ch, prevText = prevText, nextToken = nextToken, config = config)
            RsvpPunctuationTier.SOFT_SEPARATOR ->
                softSeparatorPauseFloorMs(ch = ch, config = config)
            RsvpPunctuationTier.NONE -> 0.0
        }

    private fun sentencePauseBaseMs(
        ch: Char,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double? =
        when (ch) {
            '.' ->
                when {
                    isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken) -> null
                    isLikelySentenceContinuation(nextToken) ->
                        min(config.periodPauseMs.toDouble(), config.commaPauseMs * 0.8)
                    else -> config.periodPauseMs.toDouble()
                }
            '\u2026' -> ellipsisPauseBaseMs(nextToken = nextToken, config = config)
            else -> config.sentenceEndPauseMs.toDouble()
        }

    private fun sentencePauseFloorMs(
        ch: Char,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double =
        when (ch) {
            '.' ->
                when {
                    isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken) -> 0.0
                    isLikelySentenceContinuation(nextToken) ->
                        min(
                            config.periodPauseMs * config.minPauseScale,
                            (config.commaPauseMs * 0.8) * config.minPauseScale,
                        )
                    else -> config.periodPauseMs * config.minPauseScale
                }
            '\u2026' -> ellipsisPauseBaseMs(nextToken = nextToken, config = config) * config.minPauseScale
            else -> config.sentenceEndPauseMs * config.minPauseScale
        }

    private fun clausePauseBaseMs(
        ch: Char,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double? =
        when (ch) {
            ',' ->
                if (isThousandSeparator(prevText, nextToken)) {
                    null
                } else {
                    config.commaPauseMs.toDouble()
                }
            ';' -> config.semicolonPauseMs.toDouble()
            ':' -> config.colonPauseMs.toDouble()
            '\u2014', '\u2013', '-' -> config.dashPauseMs.toDouble()
            else -> null
        }

    private fun clausePauseFloorMs(
        ch: Char,
        prevText: String,
        nextToken: Token?,
        config: RsvpConfig,
    ): Double =
        when (ch) {
            ',' ->
                if (isThousandSeparator(prevText, nextToken)) {
                    0.0
                } else {
                    config.commaPauseMs * config.minPauseScale
                }
            ';' -> config.semicolonPauseMs * config.minPauseScale
            ':' -> config.colonPauseMs * config.minPauseScale
            '\u2014', '\u2013', '-' -> config.dashPauseMs * config.minPauseScale
            else -> 0.0
        }

    private fun softSeparatorPauseBaseMs(
        ch: Char,
        config: RsvpConfig,
    ): Double? =
        when {
            isParenthesisPunctuation(ch) -> config.parenthesesPauseMs.toDouble()
            isQuotePunctuation(ch) -> config.quotePauseMs.toDouble()
            isMidSentencePunctuation(ch) -> config.commaPauseMs * 0.85
            else -> null
        }

    private fun softSeparatorPauseFloorMs(
        ch: Char,
        config: RsvpConfig,
    ): Double =
        when {
            isParenthesisPunctuation(ch) -> config.parenthesesPauseMs * config.minPauseScale
            isQuotePunctuation(ch) -> config.quotePauseMs * config.minPauseScale
            isMidSentencePunctuation(ch) -> (config.commaPauseMs * 0.85) * config.minPauseScale
            else -> 0.0
        }

    private fun scaleRetentionBoost(
        ch: Char,
        tier: RsvpPunctuationTier,
        nextToken: Token?,
    ): Double =
        when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                if (ch == '\u2026') ELLIPSIS_RETENTION_BOOST else STRONG_PUNCTUATION_RETENTION_BOOST
            RsvpPunctuationTier.CLAUSE_BREAK ->
                when (ch) {
                    ';' -> SEMICOLON_RETENTION_BOOST
                    ':', '\u2014', '\u2013', '-' -> CLAUSE_PUNCTUATION_RETENTION_BOOST
                    ',' ->
                        if (isClauseLeadPunctuation(ch, nextToken)) {
                            CLAUSE_PUNCTUATION_RETENTION_BOOST
                        } else {
                            COMMA_RETENTION_BOOST
                        }
                    else -> 0.0
                }
            RsvpPunctuationTier.SOFT_SEPARATOR ->
                when {
                    isQuotePunctuation(ch) -> QUOTE_RETENTION_BOOST
                    isParenthesisPunctuation(ch) -> PARENTHESIS_RETENTION_BOOST
                    isMidSentencePunctuation(ch) -> COMMA_RETENTION_BOOST
                    else -> 0.0
                }
            RsvpPunctuationTier.NONE -> 0.0
        }

    private fun isParenthesisPunctuation(ch: Char): Boolean =
        ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}'

    private fun isQuotePunctuation(ch: Char): Boolean =
        ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019'

    internal fun resolveTier(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): RsvpPunctuationTier {
        val ch = token.text.firstOrNull() ?: return RsvpPunctuationTier.NONE
        val prevText = prevWord?.text.orEmpty()
        return when (punctuationTier(ch)) {
            RsvpPunctuationTier.SENTENCE_END ->
                when {
                    ch == '.' &&
                        (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken)) ->
                        RsvpPunctuationTier.NONE
                    else -> RsvpPunctuationTier.SENTENCE_END
                }
            RsvpPunctuationTier.CLAUSE_BREAK -> {
                if (ch == ',' && isThousandSeparator(prevText, nextToken)) {
                    RsvpPunctuationTier.NONE
                } else {
                    RsvpPunctuationTier.CLAUSE_BREAK
                }
            }
            RsvpPunctuationTier.SOFT_SEPARATOR -> RsvpPunctuationTier.SOFT_SEPARATOR
            RsvpPunctuationTier.NONE -> RsvpPunctuationTier.NONE
        }
    }

    private const val ELLIPSIS_INLINE_COMMA_FACTOR = 1.25
    private const val ELLIPSIS_SENTENCE_COMMA_FACTOR = 1.35
    private const val ELLIPSIS_PERIOD_FACTOR = 0.84
    private const val COMMA_RETENTION_BOOST = 0.06
    private const val QUOTE_RETENTION_BOOST = 0.07
    private const val PARENTHESIS_RETENTION_BOOST = 0.08
    private const val SEMICOLON_RETENTION_BOOST = 0.15
    private const val ELLIPSIS_RETENTION_BOOST = 0.24
    private const val CLAUSE_LANDING_HOLD_WEIGHT = 0.18
    private const val SEMICOLON_LANDING_HOLD_WEIGHT = 0.20
    private const val STRONG_LANDING_HOLD_WEIGHT = 0.22
    private const val ELLIPSIS_LANDING_HOLD_WEIGHT = 0.24

    private val ZERO_BOUNDARY_CONTOUR = RsvpBoundaryContour(0.0, 0.0)

    private val ZERO_PAUSE_TIMING = RsvpPunctuationPauseTiming(0.0, 0.0, 0.0)
}

internal const val CLAUSE_PUNCTUATION_RETENTION_BOOST = 0.10
internal const val STRONG_PUNCTUATION_RETENTION_BOOST = 0.18

internal val TITLE_ABBREVIATIONS =
    setOf(
        "mr",
        "mrs",
        "ms",
        "dr",
        "prof",
        "sr",
        "jr",
        "st",
        "rev",
        "fr",
    )

internal val KNOWN_ABBREVIATIONS =
    setOf(
        "mr",
        "mrs",
        "ms",
        "dr",
        "prof",
        "sr",
        "jr",
        "st",
        "vs",
        "etc",
        "e.g",
        "i.e",
        "eg",
        "ie",
        "no",
        "vol",
        "fig",
        "al",
        "inc",
        "ltd",
        "dept",
        "est",
        "approx",
        "misc",
        "jan",
        "feb",
        "mar",
        "apr",
        "jun",
        "jul",
        "aug",
        "sep",
        "sept",
        "oct",
        "nov",
        "dec",
        "u.s",
        "u.k",
        "u.n",
    )

internal val SENTENCE_STARTERS =
    setOf(
        "i",
        "he",
        "she",
        "they",
        "we",
        "it",
        "the",
        "a",
        "an",
        "this",
        "that",
        "these",
        "those",
    )
