package com.kairo.reader.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpSettingsValueConversionTest {
    @Test
    fun percentageSlidersStoreFractionalMultipliers() {
        assertEquals(0.5, percentToMultiplier(50f, minValue = 0.5, maxValue = 1.0), 0.0)
        assertEquals(0.92, percentToMultiplier(92f, minValue = 0.5, maxValue = 1.0), 0.000_001)
        assertEquals(0.75, percentToMultiplier(75f, minValue = 0.75, maxValue = 1.0), 0.0)
        assertEquals(1.0, percentToMultiplier(100f, minValue = 0.75, maxValue = 1.0), 0.0)
    }
}
