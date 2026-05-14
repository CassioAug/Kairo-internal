package com.kairo.reader.data.preferences

import android.content.res.Configuration
import com.kairo.reader.core.model.ReaderTheme
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
}
