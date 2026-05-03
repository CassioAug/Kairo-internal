package com.example.kairo.ui.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpParagraphPreviewPlacementTest {
    @Test
    fun choosesAboveOutsidePositioningWhenBelowNoLongerFits() {
        val placement =
            resolveParagraphPreviewPlacement(
                currentSide = PreviewSide.BELOW,
                isPositioningMode = false,
                anchorTop = 420f,
                previewHeightPx = 200f,
                preferredOffsetPx = 180f,
                edgePaddingPx = 20f,
                protectedTop = 430f,
                protectedBottom = 520f,
                maxTop = 580f,
                switchHysteresisPx = 18f,
                switchOverlapThresholdPx = 8f,
            )

        assertEquals(PreviewSide.ABOVE, placement.side)
        assertTrue(placement.topPx < 420f)
    }

    @Test
    fun keepsPreviewClearOfBottomControlsWhenPaused() {
        val placement =
            resolveParagraphPreviewPlacement(
                currentSide = PreviewSide.BELOW,
                isPositioningMode = false,
                anchorTop = 260f,
                previewHeightPx = 200f,
                preferredOffsetPx = 180f,
                edgePaddingPx = 20f,
                protectedTop = 290f,
                protectedBottom = 380f,
                maxTop = 340f,
                switchHysteresisPx = 18f,
                switchOverlapThresholdPx = 8f,
            )

        assertEquals(PreviewSide.ABOVE, placement.side)
        assertTrue(placement.topPx + 200f <= 540f)
    }

    @Test
    fun compactLandscapeBandStartsBehindProtectedOrpArea() {
        val band =
            resolveCompactLandscapePreviewBand(
                orpCenterY = 120f,
                orpBandHalfHeightPx = 44f,
                viewportHeightPx = 320f,
                orpOverlapPx = 8f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
            )

        assertEquals(68f, band.topPx, 0f)
        assertEquals(240f, band.heightPx, 0f)
    }

    @Test
    fun compactLandscapeBandPreservesMinimumTextHeightWhenGapIsTiny() {
        val band =
            resolveCompactLandscapePreviewBand(
                orpCenterY = 120f,
                orpBandHalfHeightPx = 44f,
                viewportHeightPx = 188f,
                orpOverlapPx = 8f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
            )

        assertEquals(68f, band.topPx, 0f)
        assertEquals(108f, band.heightPx, 0f)
    }

    @Test
    fun compactLandscapeTextRegionSitsBetweenOrpAndControls() {
        val region =
            resolveCompactLandscapePreviewTextRegion(
                orpCenterY = 120f,
                orpBandHalfHeightPx = 44f,
                controlsTopPx = 260f,
                orpClearancePx = 10f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
                preferredHeightPx = 72f,
            )

        assertEquals(PreviewSide.BELOW, region.side)
        assertEquals(174f, region.topPx, 0f)
        assertEquals(72f, region.heightPx, 0f)
    }

    @Test
    fun compactLandscapeTextRegionMovesAboveOrpWhenBelowWouldHitControls() {
        val region =
            resolveCompactLandscapePreviewTextRegion(
                orpCenterY = 120f,
                orpBandHalfHeightPx = 44f,
                controlsTopPx = 180f,
                orpClearancePx = 10f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
                preferredHeightPx = 72f,
            )

        assertEquals(PreviewSide.ABOVE, region.side)
        assertEquals(12f, region.topPx, 0f)
        assertEquals(54f, region.heightPx, 0f)
    }

    @Test
    fun compactLandscapeTextRegionMovesAboveOrpWhenBelowIsCramped() {
        val region =
            resolveCompactLandscapePreviewTextRegion(
                orpCenterY = 120f,
                orpBandHalfHeightPx = 44f,
                controlsTopPx = 220f,
                orpClearancePx = 10f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
                preferredHeightPx = 72f,
            )

        assertEquals(PreviewSide.ABOVE, region.side)
        assertEquals(12f, region.topPx, 0f)
        assertEquals(54f, region.heightPx, 0f)
    }

    @Test
    fun compactLandscapeTextRegionUsesPreferredHeightAboveOrpWhenSpaceAllows() {
        val region =
            resolveCompactLandscapePreviewTextRegion(
                orpCenterY = 220f,
                orpBandHalfHeightPx = 44f,
                controlsTopPx = 240f,
                orpClearancePx = 10f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
                preferredHeightPx = 72f,
            )

        assertEquals(PreviewSide.ABOVE, region.side)
        assertEquals(94f, region.topPx, 0f)
        assertEquals(72f, region.heightPx, 0f)
    }

    @Test
    fun compactLandscapeTextRegionStaysAboveAsOrpMovesLower() {
        val upperRegion =
            resolveCompactLandscapePreviewTextRegion(
                orpCenterY = 190f,
                orpBandHalfHeightPx = 44f,
                controlsTopPx = 240f,
                orpClearancePx = 10f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
                preferredHeightPx = 72f,
            )
        val lowerRegion =
            resolveCompactLandscapePreviewTextRegion(
                orpCenterY = 270f,
                orpBandHalfHeightPx = 44f,
                controlsTopPx = 240f,
                orpClearancePx = 10f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
                preferredHeightPx = 72f,
            )

        assertEquals(PreviewSide.ABOVE, upperRegion.side)
        assertEquals(PreviewSide.ABOVE, lowerRegion.side)
    }

    @Test
    fun compactLandscapeTextRegionCanSitAbovePreviewBandWithoutBeingClipped() {
        val band =
            resolveCompactLandscapePreviewBand(
                orpCenterY = 220f,
                orpBandHalfHeightPx = 44f,
                viewportHeightPx = 320f,
                orpOverlapPx = 8f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
            )
        val region =
            resolveCompactLandscapePreviewTextRegion(
                orpCenterY = 220f,
                orpBandHalfHeightPx = 44f,
                controlsTopPx = 240f,
                orpClearancePx = 10f,
                edgePaddingPx = 12f,
                minHeightPx = 24f,
                preferredHeightPx = 72f,
            )

        assertEquals(PreviewSide.ABOVE, region.side)
        assertTrue(region.topPx < band.topPx)
    }

    @Test
    fun compactLandscapeControlsTopReservesPanelOnlyWhenPreviewUsesFullViewport() {
        assertEquals(
            188f,
            resolveCompactLandscapeControlsTop(
                viewportHeightPx = 320f,
                controlsReservedHeightPx = 132f,
                bottomChromeInsetPx = 0f,
                controlsVisible = true,
            ),
            0f,
        )
        assertEquals(
            320f,
            resolveCompactLandscapeControlsTop(
                viewportHeightPx = 320f,
                controlsReservedHeightPx = 132f,
                bottomChromeInsetPx = 132f,
                controlsVisible = true,
            ),
            0f,
        )
        assertEquals(
            320f,
            resolveCompactLandscapeControlsTop(
                viewportHeightPx = 320f,
                controlsReservedHeightPx = 132f,
                bottomChromeInsetPx = 0f,
                controlsVisible = false,
            ),
            0f,
        )
    }
}
