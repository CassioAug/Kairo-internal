package com.kairo.reader.ui.focus

import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.navigation.KairoRoutes

internal fun shouldApplyFocusMode(
    route: String?,
    preferences: UserPreferences,
): Boolean =
    preferences.focusModeEnabled &&
        when (route) {
            KairoRoutes.SETTINGS,
            KairoRoutes.SETTINGS_LANGUAGE,
            KairoRoutes.SETTINGS_INFO,
            -> true
            KairoRoutes.READER,
            KairoRoutes.READER_WITH_POSITION,
            -> preferences.focusApplyInReader
            KairoRoutes.RSVP -> preferences.focusApplyInRsvp
            else -> false
        }
