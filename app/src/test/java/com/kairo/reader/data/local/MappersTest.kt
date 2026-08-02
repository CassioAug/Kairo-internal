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

    @Test
    fun bookMapperBuildsChapterFallbackWhenMigratedBookHasNoStoredTableOfContents() {
        val entity =
            BookEntity(
                id = "legacy-book",
                title = "Legacy book",
                authors = emptyList(),
                languageTag = null,
                coverImage = null,
            )
        val chapters =
            listOf(
                ChapterEntity(
                    bookId = entity.id,
                    index = 1,
                    title = "The Crossing",
                    htmlContent = "",
                    plainText = "",
                ),
                ChapterEntity(
                    bookId = entity.id,
                    index = 0,
                    title = "The Arrival",
                    htmlContent = "",
                    plainText = "",
                ),
            )

        val restored = entity.toDomain(chapters = chapters, tableOfContentsEntries = emptyList())

        assertEquals(listOf("The Arrival", "The Crossing"), restored.tableOfContents.map { it.label })
        assertEquals(listOf(0, 1), restored.tableOfContents.map { it.target?.chapterIndex })
    }
}
