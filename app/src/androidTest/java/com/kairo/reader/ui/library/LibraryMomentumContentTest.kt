package com.kairo.reader.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.ReadingMomentumDay
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.core.model.ReadingSessionMode
import com.kairo.reader.ui.theme.KairoTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryMomentumContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun selectingDayUpdatesTotalAndRecentSessionsStayBounded() {
        val days = momentumDays()
        val sessions = (1L..8L).map(::session)
        composeRule.setContent {
            KairoTheme {
                LibraryMomentumContent(
                    momentum =
                        ReadingMomentum(
                            sessions = sessions,
                            activeDaysInLastSeven = 2,
                            dailyActivity = days,
                        ),
                    weeklyGoalMinutes = 120,
                    onWeeklyGoalChange = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_total_reading),
        ).performScrollTo()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_duration_hours, 2),
        ).assertIsDisplayed()

        val firstDayLabel =
            SimpleDateFormat(SHORT_DAY_PATTERN, Locale.getDefault()).format(Date(days.first().startedAt))
        val firstDayDuration =
            composeRule.activity.getString(R.string.momentum_duration_hours_minutes, 1, 30)
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(
                R.string.momentum_day_bar_description,
                firstDayLabel,
                firstDayDuration,
            ),
        ).performClick()
        composeRule.onNodeWithText(firstDayDuration).assertIsDisplayed()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.momentum_recent_sessions_limit, 5),
        ).performScrollTo().assertIsDisplayed()
    }

    private fun momentumDays(): List<ReadingMomentumDay> {
        val start =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -6)
            }
        return List(7) { index ->
            val day = start.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, index)
            when (index) {
                0 -> ReadingMomentumDay(day.timeInMillis, 5_400_000L, 9_000, 2)
                6 -> ReadingMomentumDay(day.timeInMillis, 7_200_000L, 12_000, 3)
                else -> ReadingMomentumDay(day.timeInMillis)
            }
        }
    }

    private fun session(id: Long): ReadingSessionItem =
        ReadingSessionItem(
            session =
                ReadingSession(
                    id = id.toString(),
                    bookId = BOOK.id,
                    mode = ReadingSessionMode.READER,
                    startedAt = id,
                    endedAt = id + SESSION_DURATION_MS,
                    activeDurationMs = SESSION_DURATION_MS,
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

    private companion object {
        const val SHORT_DAY_PATTERN = "EEE"
        const val SESSION_DURATION_MS = 300_000L
        val BOOK = Book(BookId("book"), "Book", emptyList(), chapters = emptyList())
    }
}
