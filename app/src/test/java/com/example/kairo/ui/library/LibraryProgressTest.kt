package com.example.kairo.ui.library

import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryProgressTest {
    @Test
    fun buildLibraryProgress_usesEstimatedWpmForEachBook() =
        runTest {
            val books =
                listOf(
                    book(id = "fast", wordCount = 600),
                    book(id = "slow", wordCount = 600),
                )

            val progress =
                buildLibraryProgress(
                    books = books,
                    positions = emptyList(),
                    estimatedWpmByBookId =
                        mapOf(
                            "fast" to 600,
                            "slow" to 300,
                        ),
                )

            assertEquals(1, progress.getValue("fast").remainingMinutes)
            assertEquals(2, progress.getValue("slow").remainingMinutes)
        }

    private fun book(
        id: String,
        wordCount: Int,
    ): Book =
        Book(
            id = BookId(id),
            title = id,
            authors = emptyList(),
            chapters =
                listOf(
                    Chapter(
                        index = 0,
                        title = null,
                        htmlContent = "",
                        plainText = "",
                        wordCount = wordCount,
                    ),
                ),
        )
}
