package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpPositioningGridTest {

    private fun uiPrefs(
        gridEnabled: Boolean,
        snap: Float,
    ) = RsvpUiPreferences(
        extremeSpeedUnlocked = false,
        readerTheme = ReaderTheme.LIGHT,
        focusModeEnabled = false,
        positioningGridEnabled = gridEnabled,
        positioningGridSnap = snap,
    )

    @Test
    fun disabledGridHasZeroSnapRadius() {
        assertEquals(0f, positioningSnapRadius(uiPrefs(gridEnabled = false, snap = 1f)), 0f)
    }

    @Test
    fun snapRadiusScalesWithStrengthUpToHalfSpacing() {
        assertEquals(
            POSITIONING_GRID_SPACING_BIAS / 2f,
            positioningSnapRadius(uiPrefs(gridEnabled = true, snap = 1f)),
            0f,
        )
        assertEquals(
            POSITIONING_GRID_SPACING_BIAS / 4f,
            positioningSnapRadius(uiPrefs(gridEnabled = true, snap = 0.5f)),
            0f,
        )
    }

    @Test
    fun biasNearLineSnapsToIt() {
        val snapped = snapBiasToGrid(bias = 0.108f, snapRadius = 0.025f)
        assertEquals(0.1f, snapped, 1e-6f)
    }

    @Test
    fun biasOutsideRadiusStaysFreeForm() {
        val bias = 0.14f
        assertEquals(bias, snapBiasToGrid(bias = bias, snapRadius = 0.025f), 0f)
        assertEquals(
            POSITIONING_GRID_LINE_NONE,
            snappedGridLineIndex(bias = bias, snapRadius = 0.025f),
        )
    }

    @Test
    fun zeroRadiusNeverSnaps() {
        assertEquals(0.1004f, snapBiasToGrid(bias = 0.1004f, snapRadius = 0f), 0f)
        assertEquals(
            POSITIONING_GRID_LINE_NONE,
            snappedGridLineIndex(bias = 0.1004f, snapRadius = 0f),
        )
    }

    @Test
    fun fullStrengthQuantizesEverywhere() {
        val radius = positioningSnapRadius(uiPrefs(gridEnabled = true, snap = 1f))
        assertEquals(-0.2f, snapBiasToGrid(bias = -0.24f, snapRadius = radius), 1e-6f)
        assertEquals(0f, snapBiasToGrid(bias = 0.04f, snapRadius = radius), 1e-6f)
    }

    @Test
    fun snappedLineIndexIdentifiesTheLine() {
        assertEquals(3, snappedGridLineIndex(bias = 0.31f, snapRadius = 0.025f))
        assertEquals(-7, snappedGridLineIndex(bias = -0.69f, snapRadius = 0.025f))
        assertEquals(0, snappedGridLineIndex(bias = 0.01f, snapRadius = 0.025f))
    }
}
