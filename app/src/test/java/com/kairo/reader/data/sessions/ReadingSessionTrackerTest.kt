package com.kairo.reader.data.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSessionTrackerTest {
    @Test
    fun excludesPausedTimeFromActiveDuration() {
        val tracker = ReadingSessionTracker(startedAt = 1_000L, initiallyActive = true)

        tracker.setActive(false, now = 4_000L)
        tracker.setActive(true, now = 10_000L)

        assertEquals(5_000L, tracker.activeDurationMs(now = 12_000L))
    }
}
