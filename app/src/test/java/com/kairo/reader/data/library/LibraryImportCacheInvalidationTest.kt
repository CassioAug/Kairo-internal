package com.kairo.reader.data.library

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.data.books.BookImportResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryImportCacheInvalidationTest {
    @Test
    fun successfulImportResultSynchronouslyInvalidatesBothReturnedBookCaches() {
        val bookId = BookId("imported")
        val result =
            BookImportResult(
                book = Book(bookId, "Book", emptyList(), chapters = emptyList()),
                alreadyImported = true,
            )
        val tokenInvalidated = mutableListOf<BookId>()
        val frameInvalidated = mutableListOf<BookId>()

        val returned =
            invalidateImportedBookCaches(result) { importedBookId ->
                tokenInvalidated += importedBookId
                frameInvalidated += importedBookId
            }

        assertSame(result, returned)
        assertEquals(listOf(bookId), tokenInvalidated)
        assertEquals(listOf(bookId), frameInvalidated)
    }

    @Test
    fun thrownImportSynchronouslyInvalidatesAllCachesAndRethrows() =
        runTest {
            val failure = IllegalStateException("import failed after mutation")
            var perBookInvalidations = 0
            var invalidateAllCalls = 0

            val thrown =
                runCatching {
                    importAndInvalidateCaches(
                        importBook = { throw failure },
                        invalidateBookCaches = { perBookInvalidations += 1 },
                        invalidateAllCaches = { invalidateAllCalls += 1 },
                    )
                }.exceptionOrNull()

            assertSame(failure, thrown)
            assertEquals(0, perBookInvalidations)
            assertEquals(1, invalidateAllCalls)
        }
}
