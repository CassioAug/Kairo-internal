package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Test

internal class EpubCoverTest : EpubParserTestBase() {
    @Test
    fun selectFallbackCoverPathPrefersCoverNamedImage() {
        val candidates =
            linkedSetOf(
                "images/illustration.jpg",
                "images/front-matter.png",
                "images/cover-art.webp",
            )

        val selected = invokeSelectFallbackCoverPath(candidates)

        assertEquals("images/cover-art.webp", selected)
    }
}
