package com.kairo.reader.data.preferences

import android.content.res.Configuration
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.TimedReadingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesRepositoryDefaultsTest {
    @Test
    fun defaultReaderThemeFollowsSystemNightMode() {
        assertEquals(
            ReaderTheme.DARK,
            readerThemeForNightMode(Configuration.UI_MODE_NIGHT_YES),
        )
        assertEquals(
            ReaderTheme.LIGHT,
            readerThemeForNightMode(Configuration.UI_MODE_NIGHT_NO),
        )
        assertEquals(
            ReaderTheme.LIGHT,
            readerThemeForNightMode(Configuration.UI_MODE_NIGHT_UNDEFINED),
        )
    }

    @Test
    fun naturalFlowMultipliersRepairInvalidPersistedValues() {
        val defaults = RsvpConfig()
        val normalized =
            RsvpConfig(
                focalSupportCompression = 75.0,
                dialoguePunctuationScale = 50.0,
                parentheticalAsideMultiplier = Double.NaN,
            ).normalizedNaturalFlowMultipliers(defaults)

        assertEquals(1.0, normalized.focalSupportCompression, 0.0)
        assertEquals(1.0, normalized.dialoguePunctuationScale, 0.0)
        assertEquals(
            defaults.parentheticalAsideMultiplier,
            normalized.parentheticalAsideMultiplier,
            0.0,
        )
    }

    @Test
    fun timedReadingModeDefaultsToRsvpForMissingOrInvalidValues() {
        assertEquals(TimedReadingMode.RSVP, timedReadingModeFromStored(null))
        assertEquals(TimedReadingMode.RSVP, timedReadingModeFromStored("not-a-mode"))
    }

    @Test
    fun timedReadingModeRestoresBionicSelection() {
        assertEquals(TimedReadingMode.BIONIC, timedReadingModeFromStored("BIONIC"))
    }
}
