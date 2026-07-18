package com.kairo.reader.data.preferences

import androidx.datastore.preferences.core.Preferences
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.RsvpProfileIds
import com.kairo.reader.core.rsvp.MILLISECONDS_PER_MINUTE
import com.kairo.reader.core.rsvp.RsvpSpeedControl

internal fun <T> Preferences.readOrDefault(
    key: Preferences.Key<T>,
    fallback: T,
): T = this[key] ?: fallback

internal fun legacyWpmToTempoMs(legacyWpm: Int?, defaultTempoMs: Long): Long =
    when {
        legacyWpm == null -> defaultTempoMs
        legacyWpm <= 0 -> defaultTempoMs
        else ->
            (MILLISECONDS_PER_MINUTE / legacyWpm.toDouble())
                .toLong()
                .coerceAtLeast(RsvpSpeedControl.EXTREME_MIN_TEMPO_MS_PER_WORD)
    }

internal fun RsvpConfig.withTiming(
    timingInfo: RsvpConfigPreferenceCodec.TimingInfo,
): RsvpConfig =
    copy(
        tempoMsPerWord = timingInfo.tempoMsPerWord,
        baseWpm = timingInfo.baseWpm,
    )

internal fun normalizeRsvpProfileId(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || trimmed == "CUSTOM") return RsvpProfileIds.CUSTOM_UNSAVED
    runCatching { RsvpProfile.valueOf(trimmed) }.getOrNull()?.let { parsed ->
        return RsvpProfileIds.builtIn(parsed)
    }
    return if (
        trimmed.startsWith("builtin:") ||
        trimmed.startsWith("user:") ||
        trimmed == RsvpProfileIds.CUSTOM_UNSAVED
    ) {
        trimmed
    } else {
        RsvpProfileIds.CUSTOM_UNSAVED
    }
}
