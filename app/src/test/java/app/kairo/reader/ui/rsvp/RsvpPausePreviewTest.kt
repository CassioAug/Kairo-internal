package app.kairo.reader.ui.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPausePreviewTest {
    @Test
    fun pausePreviewOnlyShowsForPausedControls() {
        assertEquals(
            true,
            shouldShowPausePreview(
                isPlaying = false,
                showControls = true,
                showQuickSettings = false,
                isExiting = false,
            ),
        )
        assertEquals(
            false,
            shouldShowPausePreview(
                isPlaying = true,
                showControls = true,
                showQuickSettings = false,
                isExiting = false,
            ),
        )
        assertEquals(
            false,
            shouldShowPausePreview(
                isPlaying = false,
                showControls = false,
                showQuickSettings = false,
                isExiting = false,
            ),
        )
        assertEquals(
            false,
            shouldShowPausePreview(
                isPlaying = false,
                showControls = true,
                showQuickSettings = true,
                isExiting = false,
            ),
        )
        assertEquals(
            false,
            shouldShowPausePreview(
                isPlaying = false,
                showControls = true,
                showQuickSettings = false,
                isExiting = true,
            ),
        )
    }

    @Test
    fun reservesControlsInsetWhenLargeOrpWouldHitPausePanel() {
        val reserveInset =
            shouldReserveControlsChromeInset(
                viewportHeightPx = 800f,
                controlsHeightPx = 424f,
                orpCenterY = 500f,
                orpBandHalfHeightPx = 60f,
                clearancePx = 18f,
            )

        assertEquals(true, reserveInset)
    }

    @Test
    fun doesNotReserveControlsInsetWhenOrpIsComfortablyAbovePausePanel() {
        val reserveInset =
            shouldReserveControlsChromeInset(
                viewportHeightPx = 800f,
                controlsHeightPx = 424f,
                orpCenterY = 280f,
                orpBandHalfHeightPx = 48f,
                clearancePx = 18f,
            )

        assertEquals(false, reserveInset)
    }

    @Test
    fun reservesControlsInsetForCompactLandscapeWhenDefaultOrpWouldOverlapControls() {
        val reserveInset =
            shouldReserveControlsChromeInset(
                viewportHeightPx = 320f,
                controlsHeightPx = 244f,
                orpCenterY = 136f,
                orpBandHalfHeightPx = 42f,
                clearancePx = 18f,
            )

        assertEquals(true, reserveInset)
    }

    @Test
    fun resolvesPartialControlsInsetInsteadOfJumpingByFullPanelHeight() {
        val insetPx =
            resolveControlsChromeInsetPx(
                viewportHeightPx = 800f,
                controlsHeightPx = 424f,
                orpCenterY = 360f,
                orpBandHalfHeightPx = 48f,
                verticalBias = 0.25f,
                clearancePx = 18f,
            )

        assertEquals(80f, insetPx, 0.001f)
        assertTrue(insetPx < 424f)
    }
}
