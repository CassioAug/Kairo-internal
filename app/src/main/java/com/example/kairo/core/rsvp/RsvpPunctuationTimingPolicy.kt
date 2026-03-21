package com.example.kairo.core.rsvp

import com.example.kairo.core.linguistics.ClauseDetector
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.isMidSentencePunctuation
import kotlin.math.max
import kotlin.math.min

internal data class RsvpPunctuationPauseTiming(
    val baseMs: Double,
    val floorMs: Double,
    val scaleRetentionBoost: Double,
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
            when {
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '.' ->
                    when {
                        isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken) -> {
                            return ZERO_PAUSE_TIMING
                        }

                        isLikelySentenceContinuation(nextToken) ->
                            min(config.periodPauseMs.toDouble(), config.commaPauseMs * 0.8)

                        else -> config.periodPauseMs.toDouble()
                    }
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '\u2026' ->
                    ellipsisPauseBaseMs(nextToken = nextToken, config = config)
                tier == RsvpPunctuationTier.SENTENCE_END ->
                    config.sentenceEndPauseMs.toDouble()
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ',' ->
                    if (isThousandSeparator(prevText, nextToken)) {
                        return ZERO_PAUSE_TIMING
                    } else {
                        config.commaPauseMs.toDouble()
                    }
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ';' ->
                    config.semicolonPauseMs.toDouble()
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ':' ->
                    config.colonPauseMs.toDouble()
                tier == RsvpPunctuationTier.CLAUSE_BREAK &&
                    (ch == '\u2014' || ch == '\u2013' || ch == '-') ->
                    config.dashPauseMs.toDouble()
                tier == RsvpPunctuationTier.SOFT_SEPARATOR &&
                    (ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}') ->
                    config.parenthesesPauseMs.toDouble()
                tier == RsvpPunctuationTier.SOFT_SEPARATOR &&
                    (ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019') ->
                    config.quotePauseMs.toDouble()
                tier == RsvpPunctuationTier.SOFT_SEPARATOR && isMidSentencePunctuation(ch) ->
                    config.commaPauseMs * 0.85
                else -> return ZERO_PAUSE_TIMING
            }

        val floor =
            when {
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '.' -> {
                    if (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken)) {
                        0.0
                    } else if (isLikelySentenceContinuation(nextToken)) {
                        min(
                            config.periodPauseMs * config.minPauseScale,
                            (config.commaPauseMs * 0.8) * config.minPauseScale,
                        )
                    } else {
                        config.periodPauseMs * config.minPauseScale
                    }
                }
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '\u2026' ->
                    ellipsisPauseBaseMs(nextToken = nextToken, config = config) * config.minPauseScale
                tier == RsvpPunctuationTier.SENTENCE_END ->
                    config.sentenceEndPauseMs * config.minPauseScale
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ',' ->
                    if (isThousandSeparator(prevText, nextToken)) {
                        0.0
                    } else {
                        config.commaPauseMs * config.minPauseScale
                    }
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ';' ->
                    config.semicolonPauseMs * config.minPauseScale
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ':' ->
                    config.colonPauseMs * config.minPauseScale
                tier == RsvpPunctuationTier.CLAUSE_BREAK &&
                    (ch == '\u2014' || ch == '\u2013' || ch == '-') ->
                    config.dashPauseMs * config.minPauseScale
                tier == RsvpPunctuationTier.SOFT_SEPARATOR &&
                    (ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}') ->
                    config.parenthesesPauseMs * config.minPauseScale
                tier == RsvpPunctuationTier.SOFT_SEPARATOR &&
                    (ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019') ->
                    config.quotePauseMs * config.minPauseScale
                tier == RsvpPunctuationTier.SOFT_SEPARATOR && isMidSentencePunctuation(ch) ->
                    (config.commaPauseMs * 0.85) * config.minPauseScale
                else -> 0.0
            }

        val scaleRetentionBoost =
            when {
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '\u2026' ->
                    ELLIPSIS_RETENTION_BOOST
                tier == RsvpPunctuationTier.SENTENCE_END ->
                    STRONG_PUNCTUATION_RETENTION_BOOST
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ';' ->
                    SEMICOLON_RETENTION_BOOST
                tier == RsvpPunctuationTier.CLAUSE_BREAK &&
                    (ch == ':' || ch == '\u2014' || ch == '\u2013' || ch == '-') ->
                    CLAUSE_PUNCTUATION_RETENTION_BOOST
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ',' ->
                    if (isClauseLeadPunctuation(ch, nextToken)) {
                        CLAUSE_PUNCTUATION_RETENTION_BOOST
                    } else {
                        COMMA_RETENTION_BOOST
                    }
                tier == RsvpPunctuationTier.SOFT_SEPARATOR &&
                    (ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019') ->
                    QUOTE_RETENTION_BOOST
                tier == RsvpPunctuationTier.SOFT_SEPARATOR &&
                    (ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}') ->
                    PARENTHESIS_RETENTION_BOOST
                tier == RsvpPunctuationTier.SOFT_SEPARATOR && isMidSentencePunctuation(ch) ->
                    COMMA_RETENTION_BOOST
                else -> 0.0
            }

        val breathingScale = punctuationBreathingScale(config)

        return RsvpPunctuationPauseTiming(
            baseMs = base * breathingScale,
            floorMs = floor * breathingScale,
            scaleRetentionBoost = scaleRetentionBoost,
        )
    }

    fun boundaryLandingWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = prevWord?.text.orEmpty()
        val tier = resolveTier(token = token, prevWord = prevWord, nextToken = nextToken)
        if (tier == RsvpPunctuationTier.NONE) return 0.0
        if (ch == '.' &&
            (isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken))
        ) {
            return 0.0
        }
        if (ch == ',' && isThousandSeparator(prevText, nextToken)) {
            return 0.0
        }
        val base =
            when {
                tier == RsvpPunctuationTier.SENTENCE_END && ch == '\u2026' ->
                    ELLIPSIS_LANDING_HOLD_WEIGHT
                tier == RsvpPunctuationTier.SENTENCE_END ->
                    STRONG_LANDING_HOLD_WEIGHT
                tier == RsvpPunctuationTier.CLAUSE_BREAK && ch == ';' ->
                    SEMICOLON_LANDING_HOLD_WEIGHT
                tier == RsvpPunctuationTier.CLAUSE_BREAK ->
                    CLAUSE_LANDING_HOLD_WEIGHT
                else -> return 0.0
            }
        val contourStrength = boundaryTierContourWeight(token = token, nextToken = nextToken, tier = tier)
        if (contourStrength <= 0.0) return 0.0
        return base * contourStrength
    }

    fun boundaryTailLiftWeight(
        token: Token,
        prevWord: Token?,
        nextToken: Token?,
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = prevWord?.text.orEmpty()
        val tier = resolveTier(token = token, prevWord = prevWord, nextToken = nextToken)
        if (tier == RsvpPunctuationTier.NONE) return 0.0
        return when {
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
        }
    }

    private fun boundaryTierContourWeight(
        token: Token,
        nextToken: Token?,
        tier: RsvpPunctuationTier,
    ): Double {
        val ch = token.text.firstOrNull() ?: return 0.0
        val prevText = ""
        return when (tier) {
            RsvpPunctuationTier.SENTENCE_END ->
                when {
                    ch == '.' -> {
                        when {
                            isDecimalPoint(prevText, nextToken) || isAbbreviationDot(prevText, nextToken) -> 0.0
                            isLikelySentenceContinuation(nextToken) -> 0.55
                            else -> 0.92
                        }
                    }
                    ch == '\u2026' -> 1.0
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

    private fun ellipsisPauseBaseMs(
        nextToken: Token?,
        config: RsvpConfig,
    ): Double {
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD }?.text
        val nextStartsSentenceLike =
            nextWord?.filter { it.isLetter() }?.firstOrNull()?.isUpperCase() == true ||
                nextWord?.lowercase() in SENTENCE_STARTERS
        val breakAfterEllipsis =
            nextToken?.type == TokenType.PARAGRAPH_BREAK || nextToken?.type == TokenType.PAGE_BREAK
        return if (nextStartsSentenceLike || breakAfterEllipsis || nextWord == null) {
            max(config.commaPauseMs * 1.15, config.periodPauseMs * 0.72)
        } else {
            config.commaPauseMs * 1.15
        }
    }

    private fun isClauseLeadPunctuation(
        ch: Char,
        nextToken: Token?,
    ): Boolean {
        if (ch != ',' &&
            ch != ';' &&
            ch != ':' &&
            ch != '\u2014' &&
            ch != '\u2013' &&
            ch != '-'
        ) {
            return false
        }
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD } ?: return false
        val nextLower = nextWord.text.lowercase()
        return ClauseDetector.isClauseBoundary(nextLower) ||
            ClauseDetector.isCoordinatingConjunction(nextLower)
    }

    private fun isLikelySentenceContinuation(nextToken: Token?): Boolean {
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD } ?: return false
        val firstChar = nextWord.text.firstOrNull() ?: return false
        return firstChar.isLowerCase()
    }

    private fun punctuationTier(ch: Char): RsvpPunctuationTier =
        when (ch) {
            '.', '\u2026', '!', '?' -> RsvpPunctuationTier.SENTENCE_END
            ',', ';', ':', '\u2014', '\u2013', '-' -> RsvpPunctuationTier.CLAUSE_BREAK
            '(', ')', '[', ']', '{', '}', '"', '\u201C', '\u201D', '\u2018', '\u2019' ->
                RsvpPunctuationTier.SOFT_SEPARATOR
            else -> if (isMidSentencePunctuation(ch)) RsvpPunctuationTier.SOFT_SEPARATOR else RsvpPunctuationTier.NONE
        }

    private fun punctuationBreathingScale(config: RsvpConfig): Double =
        config.punctuationPauseFactor.coerceIn(0.5, 1.75)

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

    private fun isDecimalPoint(
        prevText: String,
        nextToken: Token?,
    ): Boolean {
        if (!prevText.any { it.isDigit() }) return false
        val nextText = nextToken?.text ?: return false
        return nextText.any { it.isDigit() }
    }

    private fun isThousandSeparator(
        prevText: String,
        nextToken: Token?,
    ): Boolean {
        if (prevText.isEmpty() || nextToken?.type != TokenType.WORD) return false
        if (!prevText.all { it.isDigit() }) return false
        val nextText = nextToken.text
        return nextText.length == 3 && nextText.all { it.isDigit() }
    }

    private fun isAbbreviationDot(
        prevWordText: String,
        nextToken: Token?,
    ): Boolean {
        val rawPrev = prevWordText.trim()
        if (rawPrev.isEmpty()) return false

        val normalized = rawPrev.trimEnd('.', ',', ';', ':').lowercase()
        val nextWord = nextToken?.takeIf { it.type == TokenType.WORD }?.text
        if (nextWord == null) return false

        val nextLetters = nextWord.filter { it.isLetter() }
        val nextFirst = nextLetters.firstOrNull()
        val nextStartsLower = nextFirst?.isLowerCase() == true
        val nextStartsUpper = nextFirst?.isUpperCase() == true
        val isSentenceStarter = nextWord.lowercase() in SENTENCE_STARTERS
        val nextIsInitial = nextLetters.length == 1 && nextLetters.all { it.isUpperCase() }

        if (normalized in TITLE_ABBREVIATIONS) return true

        if (normalized in KNOWN_ABBREVIATIONS) {
            return nextStartsLower || (nextStartsUpper && !isSentenceStarter) || nextIsInitial
        }

        val prevLetters = rawPrev.filter { it.isLetter() }
        if (prevLetters.isEmpty()) return false
        if (prevLetters.length == 1) {
            return nextStartsLower || (nextStartsUpper && !isSentenceStarter) || nextIsInitial
        }
        if (prevLetters.length <= 3 && prevLetters.all { it.isUpperCase() }) {
            return nextStartsLower || (nextStartsUpper && !isSentenceStarter) || nextIsInitial
        }

        return false
    }

    private const val COMMA_RETENTION_BOOST = 0.03
    private const val QUOTE_RETENTION_BOOST = 0.04
    private const val PARENTHESIS_RETENTION_BOOST = 0.05
    private const val CLAUSE_PUNCTUATION_RETENTION_BOOST = 0.10
    private const val SEMICOLON_RETENTION_BOOST = 0.12
    private const val STRONG_PUNCTUATION_RETENTION_BOOST = 0.18
    private const val ELLIPSIS_RETENTION_BOOST = 0.20
    private const val CLAUSE_LANDING_HOLD_WEIGHT = 0.18
    private const val SEMICOLON_LANDING_HOLD_WEIGHT = 0.20
    private const val STRONG_LANDING_HOLD_WEIGHT = 0.22
    private const val ELLIPSIS_LANDING_HOLD_WEIGHT = 0.24

    private val TITLE_ABBREVIATIONS =
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

    private val KNOWN_ABBREVIATIONS =
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

    private val SENTENCE_STARTERS =
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

    private val ZERO_PAUSE_TIMING = RsvpPunctuationPauseTiming(0.0, 0.0, 0.0)
}
