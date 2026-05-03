package app.kairo.reader.data.library

import android.content.Context
import android.net.Uri
import app.kairo.reader.core.dispatchers.DispatcherProvider
import app.kairo.reader.core.model.Book
import app.kairo.reader.data.books.BookRepository
import app.kairo.reader.data.local.BookDao
import app.kairo.reader.data.local.BookmarkDao
import app.kairo.reader.data.local.ReadingPositionDao
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LibraryRepositoryImpl(
    private val bookRepository: BookRepository,
    private val bookDao: BookDao,
    private val positionDao: ReadingPositionDao,
    private val bookmarkDao: BookmarkDao,
    private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider,
) : LibraryRepository {
    override fun observeLibrary(): Flow<List<Book>> = bookRepository.observeBooks()

    override suspend fun import(uri: Uri): Book {
        // Don't silently swallow errors - let them propagate so UI can show error message
        return bookRepository.importBook(uri)
    }

    override suspend fun setCompleted(
        bookId: String,
        isCompleted: Boolean,
    ) {
        withContext(dispatcherProvider.io) {
            bookDao.setCompleted(bookId, isCompleted)
        }
    }

    override suspend fun delete(bookId: String) {
        withContext(dispatcherProvider.io) {
            bookDao.deleteChaptersForBook(bookId)
            bookDao.deleteBook(bookId)
            positionDao.deleteForBook(bookId)
            bookmarkDao.deleteForBook(bookId)
            deleteBookAssets(bookId)
        }
    }

    private fun deleteBookAssets(bookId: String) {
        runCatching {
            File(appContext.filesDir, "kairo_epub_assets/$bookId").deleteRecursively()
        }
    }
}
