package com.kairo.reader.core.rsvp.engine

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import kotlin.math.max

internal data class ExpandedToken(
    val token: Token,
    val originalIndex: Int,
    val expandedIndex: Int,
)


internal data class PhraseContour(
    val preBoundaryWeight: Double,
    val restartWeight: Double,
) {
    companion object {
        val NONE = PhraseContour(preBoundaryWeight = 0.0, restartWeight = 0.0)
    }
}


internal data class UnitBuildResult(val tokens: List<Token>, val originalWordIndex: Int, val nextCursor: Int,)


internal enum class BoundaryBefore {
    NONE,
    CLAUSE,
    SENTENCE,
    PARAGRAPH,
    PAGE,
    ;

    fun isMajorStart(): Boolean =
        this == SENTENCE || this == PARAGRAPH || this == PAGE
}


internal class ContextState {
    var parentheticalDepth: Int = 0
        private set
    var straightQuoteOpen: Boolean = false
        private set
    var inDialogue: Boolean = false
        private set

    fun snapshot(): ContextSnapshot =
        ContextSnapshot(
            parentheticalDepth = parentheticalDepth,
            inDialogue = inDialogue,
        )

    fun consume(token: Token) {
        if (token.type == TokenType.WORD) {
            if (token.isDialogue) inDialogue = true
            return
        }
        if (token.type != TokenType.PUNCTUATION) return

        val ch = token.text.firstOrNull() ?: return
        when (ch) {
            '(', '[', '{' -> parentheticalDepth++
            ')', ']', '}' -> parentheticalDepth = max(0, parentheticalDepth - 1)
            '"' -> straightQuoteOpen = !straightQuoteOpen
            '\u201C', '\u2018' -> Unit
            '\u201D', '\u2019' -> Unit
        }
        inDialogue = token.isDialogue
    }
}


internal data class ContextSnapshot(val parentheticalDepth: Int, val inDialogue: Boolean,)


/**
 * Sequential prose memory carried across frames during generation.
 *
 * Tracks how deep into the current sentence the reader is (for sentence wrap-up pauses) and
 * which content words have been shown recently (for given/new pacing). Frames are generated in
 * reading order, so this state is deterministic for a given token stream.
 */
internal class ProseState {
    var wordsInSentence: Int = 0
        private set

    private val seenWords =
        object : LinkedHashMap<String, Boolean>(
            GIVENNESS_INITIAL_CAPACITY,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Boolean>?
            ): Boolean = size > GIVENNESS_MAX_ENTRIES
        }

    fun onWordShown() {
        wordsInSentence++
    }

    fun onSentenceEnd() {
        wordsInSentence = 0
    }

    fun onParagraphBreak() {
        wordsInSentence = 0
    }

    fun onPageBreak() {
        wordsInSentence = 0
        seenWords.clear()
    }

    /** Whether [key] was shown recently; refreshes its recency when found. */
    fun isGiven(key: String): Boolean = seenWords[key] != null

    fun record(key: String) {
        seenWords[key] = true
    }
}


internal class RhythmState {
    private var ema: Double? = null
    private val smoothingAlpha: Double
    private val maxSpeedupFactor: Double
    private val maxSlowdownFactor: Double

    constructor(
        smoothingAlpha: Double,
        maxSpeedupFactor: Double,
        maxSlowdownFactor: Double,
    ) {
        this.smoothingAlpha = smoothingAlpha.coerceIn(0.0, 1.0)
        this.maxSpeedupFactor = maxSpeedupFactor.coerceAtLeast(1.0)
        this.maxSlowdownFactor = maxSlowdownFactor.coerceAtLeast(1.0)
    }

    fun apply(
        rawMs: Double,
        isBoundary: Boolean,
    ): Double {
        if (isBoundary) {
            ema = rawMs
            return rawMs
        }

        val prev = ema
        val next =
            if (prev == null) {
                rawMs
            } else {
                val mixed = prev + (smoothingAlpha * (rawMs - prev))
                val minAllowed = prev / maxSpeedupFactor
                val maxAllowed = prev * maxSlowdownFactor
                mixed.coerceIn(minAllowed, maxAllowed)
            }

        ema = next
        return next
    }

    fun reset() {
        ema = null
    }
}


internal class FlowState(
    private val alpha: Double,
    private val maxBoost: Double,
    private val maxSlowdown: Double,
    private val strength: Double,
) {
    private var ema: Double? = null

    fun apply(
        difficulty: Double,
        speedStrength: Double,
        isBoundary: Boolean,
    ): Double {
        if (isBoundary) {
            ema = difficulty
            return 1.0
        }

        val prev = ema ?: difficulty
        val delta = difficulty - prev

        // Gentle flow adjustment - reduce variation for smoother cadence
        val multiplier =
            (1.0 + (delta * strength * speedStrength))
                .coerceIn(1.0 - maxSlowdown, 1.0 + maxBoost)

        ema = prev + (alpha * (difficulty - prev))
        return multiplier
    }

    fun reset() {
        ema = null
    }
}
