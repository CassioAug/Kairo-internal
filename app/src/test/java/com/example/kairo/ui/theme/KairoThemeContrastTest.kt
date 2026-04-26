@file:Suppress("MagicNumber")

package com.example.kairo.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.kairo.core.model.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class KairoThemeContrastTest {
    @Test
    fun readingTextColorsPassEnhancedContrastAgainstBackground() {
        ReaderTheme.values().forEach { theme ->
            val palette = theme.readerThemePalette()

            assertContrast(
                theme = theme,
                label = "reader text",
                foreground = palette.onBackground,
                background = palette.background,
                minimum = WCAG_ENHANCED_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun rsvpFocusLetterPassesContrastAgainstBackground() {
        ReaderTheme.values().forEach { theme ->
            val palette = theme.readerThemePalette()

            assertContrast(
                theme = theme,
                label = "RSVP focus letter",
                foreground = palette.primary,
                background = palette.background,
                minimum = WCAG_NORMAL_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun themeAccentRolesPassContrastAgainstBackground() {
        ReaderTheme.values().forEach { theme ->
            val palette = theme.readerThemePalette()

            listOf(
                "secondary" to palette.secondary,
                "link" to palette.tertiary,
            ).forEach { (label, color) ->
                assertContrast(
                    theme = theme,
                    label = label,
                    foreground = color,
                    background = palette.background,
                    minimum = WCAG_NORMAL_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun surfacesAndFilledControlsPassContrast() {
        ReaderTheme.values().forEach { theme ->
            val palette = theme.readerThemePalette()

            assertContrast(
                theme = theme,
                label = "surface variant text",
                foreground = palette.onSurfaceVariant,
                background = palette.surfaceVariant,
                minimum = WCAG_NORMAL_TEXT_CONTRAST,
            )
            assertContrast(
                theme = theme,
                label = "filled control text",
                foreground = palette.onPrimary,
                background = palette.primary,
                minimum = WCAG_NORMAL_TEXT_CONTRAST,
            )
            assertContrast(
                theme = theme,
                label = "primary container text",
                foreground = palette.onPrimaryContainer,
                background = palette.primaryContainer,
                minimum = WCAG_NORMAL_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun corePaletteRolesAreOpaque() {
        ReaderTheme.values().forEach { theme ->
            val palette = theme.readerThemePalette()

            listOf(
                palette.background,
                palette.onBackground,
                palette.surfaceVariant,
                palette.onSurfaceVariant,
                palette.primary,
                palette.onPrimary,
                palette.primaryContainer,
                palette.onPrimaryContainer,
                palette.secondary,
                palette.tertiary,
            ).forEach { color ->
                assertEquals("$theme color should be opaque", OPAQUE_ALPHA, color.alpha)
            }
        }
    }

    private fun assertContrast(
        theme: ReaderTheme,
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val contrast = contrastRatio(foreground, background)
        assertTrue(
            "$theme $label contrast $contrast should be >= $minimum",
            contrast >= minimum,
        )
    }

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Double {
        val firstLuminance = first.relativeLuminance()
        val secondLuminance = second.relativeLuminance()
        return (max(firstLuminance, secondLuminance) + LUMINANCE_OFFSET) /
            (min(firstLuminance, secondLuminance) + LUMINANCE_OFFSET)
    }

    private fun Color.relativeLuminance(): Double =
        RED_LUMINANCE * red.linearized() +
            GREEN_LUMINANCE * green.linearized() +
            BLUE_LUMINANCE * blue.linearized()

    private fun Float.linearized(): Double =
        if (this <= SRGB_LINEAR_THRESHOLD) {
            this / SRGB_LINEAR_DIVISOR
        } else {
            ((this + SRGB_EXPONENT_OFFSET) / SRGB_EXPONENT_DIVISOR).pow(SRGB_EXPONENT)
        }.toDouble()

    private companion object {
        const val WCAG_NORMAL_TEXT_CONTRAST = 4.5
        const val WCAG_ENHANCED_TEXT_CONTRAST = 7.0
        const val OPAQUE_ALPHA = 1f
        const val LUMINANCE_OFFSET = 0.05
        const val RED_LUMINANCE = 0.2126
        const val GREEN_LUMINANCE = 0.7152
        const val BLUE_LUMINANCE = 0.0722
        const val SRGB_LINEAR_THRESHOLD = 0.04045f
        const val SRGB_LINEAR_DIVISOR = 12.92
        const val SRGB_EXPONENT_OFFSET = 0.055
        const val SRGB_EXPONENT_DIVISOR = 1.055
        const val SRGB_EXPONENT = 2.4
    }
}
