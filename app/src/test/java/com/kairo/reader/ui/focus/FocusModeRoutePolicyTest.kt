package com.kairo.reader.ui.focus

import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.navigation.KairoRoutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeRoutePolicyTest {
    @Test
    fun shouldApplyFocusMode_returnsFalseWhenFocusModeDisabled() {
        val preferences = UserPreferences(focusModeEnabled = false)

        assertFalse(shouldApplyFocusMode(KairoRoutes.SETTINGS, preferences))
        assertFalse(shouldApplyFocusMode(KairoRoutes.READER, preferences))
        assertFalse(shouldApplyFocusMode(KairoRoutes.RSVP, preferences))
        assertFalse(shouldApplyFocusMode(KairoRoutes.BIONIC, preferences))
    }

    @Test
    fun shouldApplyFocusMode_alwaysAppliesToCoreSettingsRoutesWhenEnabled() {
        val preferences =
            UserPreferences(
                focusModeEnabled = true,
                focusApplyInReader = false,
                focusApplyInRsvp = false,
            )

        assertTrue(shouldApplyFocusMode(KairoRoutes.SETTINGS, preferences))
        assertTrue(shouldApplyFocusMode(KairoRoutes.SETTINGS_LANGUAGE, preferences))
        assertTrue(shouldApplyFocusMode(KairoRoutes.SETTINGS_INFO, preferences))
        assertTrue(shouldApplyFocusMode(KairoRoutes.SETTINGS_BIONIC, preferences))
    }

    @Test
    fun shouldApplyFocusMode_usesReaderAndRsvpRoutePreferences() {
        assertTrue(
            shouldApplyFocusMode(
                KairoRoutes.READER,
                UserPreferences(focusModeEnabled = true, focusApplyInReader = true),
            )
        )
        assertFalse(
            shouldApplyFocusMode(
                KairoRoutes.READER_WITH_POSITION,
                UserPreferences(focusModeEnabled = true, focusApplyInReader = false),
            )
        )
        assertTrue(
            shouldApplyFocusMode(
                KairoRoutes.RSVP,
                UserPreferences(focusModeEnabled = true, focusApplyInRsvp = true),
            )
        )
        assertFalse(
            shouldApplyFocusMode(
                KairoRoutes.RSVP,
                UserPreferences(focusModeEnabled = true, focusApplyInRsvp = false),
            )
        )
        assertTrue(
            shouldApplyFocusMode(
                KairoRoutes.BIONIC,
                UserPreferences(focusModeEnabled = true, focusApplyInRsvp = true),
            )
        )
    }

    @Test
    fun shouldApplyFocusMode_ignoresUnknownRoutes() {
        val preferences = UserPreferences(focusModeEnabled = true)

        assertFalse(shouldApplyFocusMode(KairoRoutes.LIBRARY, preferences))
        assertFalse(shouldApplyFocusMode(null, preferences))
        assertFalse(shouldApplyFocusMode("reader/book-123", preferences))
    }
}
