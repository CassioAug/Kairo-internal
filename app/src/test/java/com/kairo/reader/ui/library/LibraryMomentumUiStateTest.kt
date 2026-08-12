package com.kairo.reader.ui.library

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.core.model.ReadingSessionMode
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMomentumUiStateTest {
    @Test
    fun durationValueUsesHoursMinutesAndSubMinuteState() {
        assertEquals(MomentumDurationValue(0, 0, false), momentumDurationValue(0L))
        assertEquals(MomentumDurationValue(0, 0, true), momentumDurationValue(30_000L))
        assertEquals(MomentumDurationValue(0, 42, false), momentumDurationValue(2_520_000L))
        assertEquals(MomentumDurationValue(2, 5, false), momentumDurationValue(7_500_000L))
    }

    @Test
    fun recentSessionListNeverGrowsPastFiveItems() {
        val sessions = (1L..8L).map(::session)

        val visible = visibleMomentumSessions(sessions)

        assertEquals(5, visible.size)
        assertEquals(sessions.take(5), visible)
    }

    @Test
    fun missingDailyActivityGetsTheLocaleCalendarWeek() {
        val timeZone = TimeZone.getTimeZone("Europe/London")
        val now =
            Calendar.getInstance(timeZone).apply {
                clear()
                set(2026, Calendar.AUGUST, 4, 15, 30)
            }.timeInMillis

        val days = momentumDaysForDisplay(emptyList(), now, timeZone, Locale.UK)

        assertEquals(7, days.size)
        assertEquals(Calendar.AUGUST, calendar(days.first().startedAt, timeZone).get(Calendar.MONTH))
        assertEquals(3, calendar(days.first().startedAt, timeZone).get(Calendar.DAY_OF_MONTH))
        assertEquals(9, calendar(days.last().startedAt, timeZone).get(Calendar.DAY_OF_MONTH))
        assertTrue(days.all { it.activeDurationMs == 0L })
        assertFalse(days.any { it.sessionCount > 0 })
    }

    @Test
    fun todayIndexUsesTheActualDayInsideTheCalendarWeek() {
        val timeZone = TimeZone.getTimeZone("UTC")
        val now =
            Calendar.getInstance(timeZone).apply {
                clear()
                set(2026, Calendar.AUGUST, 5, 15, 30)
            }.timeInMillis
        val days = momentumDaysForDisplay(emptyList(), now, timeZone, Locale.UK)

        assertEquals(
            2,
            momentumTodayDayIndex(
                days = days,
                todayStartedAt = 0L,
                now = now,
                timeZone = timeZone,
                locale = Locale.UK,
            ),
        )
    }

    @Test
    fun customGoalValidationRejectsEmptyNonNumericAndOutOfRangeInput() {
        assertEquals(null, validatedWeeklyGoalMinutes(""))
        assertEquals(null, validatedWeeklyGoalMinutes("minutes"))
        assertEquals(null, validatedWeeklyGoalMinutes("29"))
        assertEquals(null, validatedWeeklyGoalMinutes("1401"))
        assertEquals(30, validatedWeeklyGoalMinutes("30"))
        assertEquals(1_400, validatedWeeklyGoalMinutes("1400"))
    }

    private fun session(id: Long): ReadingSessionItem =
        ReadingSessionItem(
            session =
            ReadingSession(
                id = id.toString(),
                bookId = BOOK.id,
                mode = ReadingSessionMode.READER,
                startedAt = id,
                endedAt = id + 300_000L,
                activeDurationMs = 300_000L,
                startChapterIndex = 0,
                startTokenIndex = 0,
                endChapterIndex = 0,
                endTokenIndex = 500,
                wordsRead = 500,
                effectiveWpm = 100,
                isWordCountEstimated = false,
            ),
            book = BOOK,
        )

    private fun calendar(
        timestamp: Long,
        timeZone: TimeZone,
    ): Calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }

    private companion object {
        val BOOK = Book(BookId("book"), "Book", emptyList(), chapters = emptyList())
    }
}
