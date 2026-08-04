package com.kairo.reader.ui.library

import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.ReadingMomentumDay
import com.kairo.reader.core.model.ReadingSessionItem
import java.util.Calendar
import java.util.TimeZone

internal data class MomentumDurationValue(
    val hours: Int,
    val minutes: Int,
    val isLessThanMinute: Boolean,
)

internal fun momentumDurationValue(durationMs: Long): MomentumDurationValue {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val totalMinutes = safeDurationMs / MILLIS_PER_MINUTE
    return MomentumDurationValue(
        hours = (totalMinutes / MINUTES_PER_HOUR).toInt(),
        minutes = (totalMinutes % MINUTES_PER_HOUR).toInt(),
        isLessThanMinute = safeDurationMs in 1 until MILLIS_PER_MINUTE,
    )
}

internal fun visibleMomentumSessions(sessions: List<ReadingSessionItem>): List<ReadingSessionItem> =
    sessions.take(RECENT_SESSION_LIMIT)

internal fun momentumDaysForDisplay(
    dailyActivity: List<ReadingMomentumDay>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): List<ReadingMomentumDay> {
    if (dailyActivity.size == ReadingMomentum.DAYS_PER_WEEK) return dailyActivity
    val start =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(ReadingMomentum.DAYS_PER_WEEK - 1))
        }
    return List(ReadingMomentum.DAYS_PER_WEEK) { index ->
        val day = start.clone() as Calendar
        day.add(Calendar.DAY_OF_YEAR, index)
        ReadingMomentumDay(startedAt = day.timeInMillis)
    }
}

internal const val RECENT_SESSION_LIMIT = 5
private const val MINUTES_PER_HOUR = 60L
private const val MILLIS_PER_MINUTE = 60_000L
