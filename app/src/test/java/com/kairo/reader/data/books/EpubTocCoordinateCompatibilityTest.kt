package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTocCoordinateCompatibilityTest {
    @Test
    fun acceptsSameUniqueChapterIndexesTextAndValidTocTargets() {
        val existing = listOf(chapter(0, "First"), chapter(2, "Second"))
        val probed = listOf(chapter(2, "Second"), chapter(0, "First"))
        val tableOfContents =
            listOf(
                TableOfContentsEntry("Group", depth = 0, target = null),
                TableOfContentsEntry(
                    "Second",
                    depth = 1,
                    target = TableOfContentsTarget(chapterIndex = 2, characterOffset = 6),
                ),
            )

        assertTrue(hasCompatibleEpubTocCoordinates(existing, probed, tableOfContents))
    }

    @Test
    fun rejectsPrependedChapterOrChangedText() {
        val existing = listOf(chapter(0, "First"), chapter(1, "Second"))

        assertFalse(
            hasCompatibleEpubTocCoordinates(
                existing,
                listOf(chapter(0, "Contents"), chapter(1, "First"), chapter(2, "Second")),
                emptyList(),
            ),
        )
        assertFalse(
            hasCompatibleEpubTocCoordinates(
                existing,
                listOf(chapter(0, "Changed"), chapter(1, "Second")),
                emptyList(),
            ),
        )
    }

    @Test
    fun rejectsDuplicateIndexesAndInvalidTocCoordinates() {
        val existing = listOf(chapter(0, "First"), chapter(1, "Second"))

        assertFalse(
            hasCompatibleEpubTocCoordinates(
                existing,
                listOf(chapter(0, "First"), chapter(0, "Second")),
                emptyList(),
            ),
        )
        assertFalse(
            hasCompatibleEpubTocCoordinates(
                existing,
                existing,
                listOf(
                    TableOfContentsEntry(
                        "Missing",
                        depth = 0,
                        target = TableOfContentsTarget(chapterIndex = 9),
                    ),
                ),
            ),
        )
        assertFalse(
            hasCompatibleEpubTocCoordinates(
                existing,
                existing,
                listOf(
                    TableOfContentsEntry(
                        "Past end",
                        depth = 0,
                        target = TableOfContentsTarget(chapterIndex = 0, characterOffset = 6),
                    ),
                ),
            ),
        )
    }

    private fun chapter(
        index: Int,
        text: String,
    ): Chapter =
        Chapter(
            index = index,
            title = "Chapter $index",
            htmlContent = "<p>$text</p>",
            plainText = text,
        )
}
