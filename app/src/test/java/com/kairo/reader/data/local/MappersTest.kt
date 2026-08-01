package com.kairo.reader.data.local

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.TableOfContentsEntry
import com.kairo.reader.core.model.TableOfContentsTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    @Test
    fun chapterToEntityPreservesDeferredWordCount() {
        val chapter =
            Chapter(
                index = 0,
                title = "Large chapter",
                htmlContent = "<p>one two three</p>",
                plainText = "one two three",
                wordCount = 0,
            )

        val entity = chapter.toEntity(BookId("book"))

        assertEquals(0, entity.wordCount)
    }

    @Test
    fun tableOfContentsEntryRoundTripsAuthoredDestination() {
        val entry =
            TableOfContentsEntry(
                label = "The Crossing",
                depth = 2,
                target = TableOfContentsTarget(chapterIndex = 4, characterOffset = 315),
            )

        val restored = entry.toEntity(BookId("book"), entryIndex = 7).toDomain()

        assertEquals(entry, restored)
    }
}
