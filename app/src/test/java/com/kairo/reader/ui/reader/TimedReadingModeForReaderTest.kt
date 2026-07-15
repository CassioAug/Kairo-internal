package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.TimedReadingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TimedReadingModeForReaderTest {
    @Test
    fun selectedModeIsUsedOutsideTutorial() {
        assertEquals(
            TimedReadingMode.BIONIC,
            timedReadingModeForReader(
                selectedMode = TimedReadingMode.BIONIC,
                tutorialActive = false,
            ),
        )
    }

    @Test
    fun tutorialTemporarilyUsesRsvp() {
        assertEquals(
            TimedReadingMode.RSVP,
            timedReadingModeForReader(
                selectedMode = TimedReadingMode.BIONIC,
                tutorialActive = true,
            ),
        )
    }
}
