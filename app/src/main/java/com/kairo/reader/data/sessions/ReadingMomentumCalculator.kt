package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.ReadingMomentumDay
import com.kairo.reader.core.model.ReadingSessionItem
import java.util.Calendar
import java.util.TimeZone

fun buildReadingMomentum(
    sessions: List<ReadingSessionItem>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): ReadingMomentum {
    val periodStart = startOfLocalDay(now, timeZone).apply {
        add(Calendar.DAY_OF_YEAR, -(ReadingMomentum.DAYS_PER_WEEK - 1))
    }
    val recent = sessions.filter { it.session.startedAt >= periodStart.timeInMillis }
    val daily = buildDailyActivity(periodStart)
    recent.forEach { item ->
        val day = startOfLocalDay(item.session.startedAt, timeZone)
        val index = daysBetween(periodStart, day)
        if (index in daily.indices) {
            val current = daily[index]
            daily[index] =
                current.copy(
                    activeDurationMs = current.activeDurationMs + item.session.activeDurationMs,
                    wordsRead = current.wordsRead + item.session.wordsRead,
                    sessionCount = current.sessionCount + 1,
                )
        }
    }
    val duration = recent.sumOf { it.session.activeDurationMs }
    val words = recent.sumOf { it.session.wordsRead }
    val averageWpm =
        if (duration > 0L && words > 0) {
            ((words * MILLIS_PER_MINUTE) / duration).toInt().coerceAtLeast(1)
        } else {
            null
        }
    val preferredMode =
        recent
            .groupBy { it.session.mode }
            .maxByOrNull { (_, modeSessions) -> modeSessions.sumOf { it.session.activeDurationMs } }
            ?.key
    return ReadingMomentum(
        sessions = sessions.sortedByDescending { it.session.startedAt },
        weekDurationMs = duration,
        weekWordsRead = words,
        activeDaysInLastSeven = daily.count { it.activeDurationMs > 0L },
        averageEffectiveWpm = averageWpm,
        preferredMode = preferredMode,
        dailyActivity = daily,
    )
}

private fun buildDailyActivity(periodStart: Calendar): MutableList<ReadingMomentumDay> =
    MutableList(ReadingMomentum.DAYS_PER_WEEK) { index ->
        val day = periodStart.clone() as Calendar
        day.add(Calendar.DAY_OF_YEAR, index)
        ReadingMomentumDay(startedAt = day.timeInMillis)
    }

private fun startOfLocalDay(
    timestamp: Long,
    timeZone: TimeZone,
): Calendar =
    Calendar.getInstance(timeZone).apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

private fun daysBetween(
    start: Calendar,
    end: Calendar,
): Int {
    val cursor = start.clone() as Calendar
    var days = 0
    while (cursor.before(end) && days <= ReadingMomentum.DAYS_PER_WEEK) {
        cursor.add(Calendar.DAY_OF_YEAR, 1)
        days += 1
    }
    return days
}

private const val MILLIS_PER_MINUTE = 60_000L
