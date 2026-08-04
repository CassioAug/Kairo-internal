package com.kairo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderScreenGesturesTest {
    @Test
    fun pageSwipeIsIgnoredWhilePassageSelectionIsActive() {
        assertNull(
            resolveReaderPageSwipeAction(
                totalX = -500f,
                swipeThreshold = 100f,
                pageGesturesEnabled = false,
            ),
        )
        assertNull(
            resolveReaderPageSwipeAction(
                totalX = 500f,
                swipeThreshold = 100f,
                pageGesturesEnabled = false,
            ),
        )
    }

    @Test
    fun pageSwipeStillNavigatesWhenNoPassageIsSelected() {
        assertEquals(
            ReaderPageSwipeAction.NEXT,
            resolveReaderPageSwipeAction(
                totalX = -500f,
                swipeThreshold = 100f,
                pageGesturesEnabled = true,
            ),
        )
        assertEquals(
            ReaderPageSwipeAction.PREVIOUS,
            resolveReaderPageSwipeAction(
                totalX = 500f,
                swipeThreshold = 100f,
                pageGesturesEnabled = true,
            ),
        )
    }
}
