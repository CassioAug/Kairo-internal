package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.core.model.ReadingSessionMode
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingMomentumCalculatorTest {
    @Test
    fun summarizesOnlyTheCurrentSevenDayWindow() {
        val timeZone = TimeZone.getTimeZone("UTC")
        val now = timestamp(timeZone, 2026, Calendar.JANUARY, 7)
        val sessions =
            listOf(
                session(timestamp(timeZone, 2026, Calendar.JANUARY, 7), 180_000L, 300, ReadingSessionMode.BIONIC),
                session(timestamp(timeZone, 2026, Calendar.JANUARY, 3), 120_000L, 200, ReadingSessionMode.RSVP),
                session(timestamp(timeZone, 2026, Calendar.JANUARY, 1), 60_000L, 100, ReadingSessionMode.READER),
                session(timestamp(timeZone, 2025, Calendar.DECEMBER, 31), 600_000L, 1_000, ReadingSessionMode.READER),
            )

        val momentum = buildReadingMomentum(sessions, now, timeZone)

        assertEquals(360_000L, momentum.weekDurationMs)
        assertEquals(600, momentum.weekWordsRead)
        assertEquals(3, momentum.activeDaysInLastSeven)
        assertEquals(100, momentum.averageEffectiveWpm)
        assertEquals(ReadingSessionMode.BIONIC, momentum.preferredMode)
        assertEquals(
            listOf(60_000L, 0L, 120_000L, 0L, 0L, 0L, 180_000L),
            momentum.dailyActivity.map { it.activeDurationMs },
        )
        assertEquals(listOf(100, 0, 200, 0, 0, 0, 300), momentum.dailyActivity.map { it.wordsRead })
        assertEquals(listOf(1, 0, 1, 0, 0, 0, 1), momentum.dailyActivity.map { it.sessionCount })
    }

    @Test
    fun assignsDaysByLocalCalendarAcrossDaylightSavingChange() {
        val timeZone = TimeZone.getTimeZone("Europe/London")
        val now = timestamp(timeZone, 2026, Calendar.MARCH, 30)
        val duration = 300_000L

        val momentum =
            buildReadingMomentum(
                sessions = listOf(session(now, duration, 500, ReadingSessionMode.READER)),
                now = now,
                timeZone = timeZone,
            )

        assertEquals(duration, momentum.dailyActivity.last().activeDurationMs)
        assertEquals(
            startOfDay(timeZone, 2026, Calendar.MARCH, 30),
            momentum.dailyActivity.last().startedAt,
        )
    }

    private fun session(
        startedAt: Long,
        durationMs: Long,
        words: Int,
        mode: ReadingSessionMode,
    ): ReadingSessionItem =
        ReadingSessionItem(
            session =
            ReadingSession(
                id = "$startedAt:$mode",
                bookId = BOOK.id,
                mode = mode,
                startedAt = startedAt,
                endedAt = startedAt + durationMs,
                activeDurationMs = durationMs,
                startChapterIndex = 0,
                startTokenIndex = 0,
                endChapterIndex = 0,
                endTokenIndex = words,
                wordsRead = words,
                effectiveWpm = 100,
                isWordCountEstimated = false,
            ),
            book = BOOK,
        )

    private fun timestamp(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
    ): Long =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis

    private fun startOfDay(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
    ): Long =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis

    private companion object {
        val BOOK = Book(BookId("book"), "Book", emptyList(), chapters = emptyList())
    }
}
