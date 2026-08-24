package com.kairo.reader.core.rsvp

import com.kairo.reader.core.language.LanguageFamily
import com.kairo.reader.core.language.LanguageFamilyClassifier
import com.kairo.reader.core.model.RsvpConfig
import kotlin.math.max
import kotlin.math.roundToLong

object RsvpConfigResolver {
    fun resolve(
        baseConfig: RsvpConfig,
        languageTag: String?,
    ): RsvpConfig {
        return when (LanguageFamilyClassifier.classify(languageTag).family) {
            LanguageFamily.CJK -> baseConfig.withCjkAdjustments()
            LanguageFamily.RTL -> baseConfig.withRtlAdjustments()
            LanguageFamily.ENGLISH,
            LanguageFamily.DEFAULT_NON_ENGLISH,
            LanguageFamily.UNKNOWN -> baseConfig
        }
    }

    fun toBaseTempoMs(
        tempoMsPerWord: Long,
        languageTag: String?,
    ): Long {
        val multiplier =
            when (LanguageFamilyClassifier.classify(languageTag).family) {
                LanguageFamily.CJK -> CJK_TEMPO_MULTIPLIER
                LanguageFamily.RTL -> RTL_TEMPO_MULTIPLIER
                LanguageFamily.ENGLISH,
                LanguageFamily.DEFAULT_NON_ENGLISH,
                LanguageFamily.UNKNOWN -> 1.0
            }
        if (multiplier == 1.0) return tempoMsPerWord
        return (tempoMsPerWord / multiplier).roundToLong().coerceAtLeast(1L)
    }
}

private fun RsvpConfig.withCjkAdjustments(): RsvpConfig =
    copy(
        tempoMsPerWord = (tempoMsPerWord * CJK_TEMPO_MULTIPLIER).roundToLong(),
        minWordMs = max(minWordMs, CJK_MIN_WORD_MS),
        longWordMinMs = max(longWordMinMs, CJK_LONG_WORD_MIN_MS),
    )

private const val CJK_TEMPO_MULTIPLIER = 1.35
private const val CJK_MIN_WORD_MS = 65L
private const val CJK_LONG_WORD_MIN_MS = 140L

private fun RsvpConfig.withRtlAdjustments(): RsvpConfig =
    copy(
        tempoMsPerWord = (tempoMsPerWord * RTL_TEMPO_MULTIPLIER).roundToLong(),
        minWordMs = max(minWordMs, RTL_MIN_WORD_MS),
        longWordMinMs = max(longWordMinMs, RTL_LONG_WORD_MIN_MS),
    )

private const val RTL_TEMPO_MULTIPLIER = 1.2
private const val RTL_MIN_WORD_MS = 55L
private const val RTL_LONG_WORD_MIN_MS = 130L
