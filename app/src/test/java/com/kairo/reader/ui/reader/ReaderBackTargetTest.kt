package com.kairo.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBackTargetTest {
    @Test
    fun resolvesTransientUiInPriorityOrder() {
        assertEquals(
            ReaderBackTarget.NOTE,
            resolveReaderBackTarget(
                showNoteDialog = true,
                showSearch = true,
                showReaderMenu = true,
                showChapterList = true,
                hasSelection = true,
                hasSearchMatch = true,
                hasFullScreenImage = true,
            ),
        )
        assertEquals(
            ReaderBackTarget.FULL_SCREEN_IMAGE,
            resolveReaderBackTarget(
                showNoteDialog = false,
                showSearch = false,
                showReaderMenu = true,
                showChapterList = true,
                hasSelection = true,
                hasSearchMatch = true,
                hasFullScreenImage = true,
            ),
        )
        assertEquals(
            ReaderBackTarget.TABLE_OF_CONTENTS,
            resolveReaderBackTarget(
                showNoteDialog = false,
                showSearch = false,
                showReaderMenu = false,
                showChapterList = true,
                hasSelection = true,
                hasSearchMatch = true,
                hasFullScreenImage = false,
            ),
        )
    }

    @Test
    fun returnsNoneWhenNoTransientUiIsOpen() {
        assertEquals(
            ReaderBackTarget.NONE,
            resolveReaderBackTarget(
                showNoteDialog = false,
                showSearch = false,
                showReaderMenu = false,
                showChapterList = false,
                hasSelection = false,
                hasSearchMatch = false,
                hasFullScreenImage = false,
            ),
        )
    }
}
