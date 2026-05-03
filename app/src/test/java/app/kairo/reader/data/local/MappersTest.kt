package app.kairo.reader.data.local

import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.Chapter
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
}
