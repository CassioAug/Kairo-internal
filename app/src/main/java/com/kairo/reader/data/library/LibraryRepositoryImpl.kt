package com.kairo.reader.data.library

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.data.books.BookImportFormats
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.BookRepository
import com.kairo.reader.data.books.TextImportRequest
import com.kairo.reader.data.local.BookDao
import com.kairo.reader.data.local.BookmarkDao
import com.kairo.reader.data.local.KairoDatabase
import com.kairo.reader.data.local.ReadingPositionDao
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LibraryRepositoryImpl(
    private val bookRepository: BookRepository,
    private val database: KairoDatabase,
    private val bookDao: BookDao,
    private val positionDao: ReadingPositionDao,
    private val bookmarkDao: BookmarkDao,
    private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider,
) : LibraryRepository {
    override fun observeLibrary(): Flow<List<Book>> = bookRepository.observeBooks()

    override suspend fun import(uri: Uri): BookImportResult {
        // Don't silently swallow errors - let them propagate so UI can show error message
        return bookRepository.importBook(uri)
    }

    override suspend fun importUrl(rawUrl: String): BookImportResult =
        bookRepository.importUrl(rawUrl)

    override suspend fun importText(request: TextImportRequest): BookImportResult =
        bookRepository.importText(request)

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
            database.withTransaction {
                positionDao.deleteForBook(bookId)
                bookmarkDao.deleteForBook(bookId)
                bookDao.deleteChaptersForBook(bookId)
                bookDao.deleteBook(bookId)
            }
            deleteBookAssets(bookId)
        }
    }

    private fun deleteBookAssets(bookId: String) {
        BookImportFormats.assetRootNames.forEach { rootName ->
            runCatching {
                File(appContext.filesDir, "$rootName/$bookId").deleteRecursively()
            }
        }
    }
}
