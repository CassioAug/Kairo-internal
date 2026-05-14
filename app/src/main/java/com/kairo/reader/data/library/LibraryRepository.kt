package com.kairo.reader.data.library

import android.net.Uri
import com.kairo.reader.core.model.Book
import com.kairo.reader.data.books.BookImportResult
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibrary(): Flow<List<Book>>

    suspend fun import(uri: Uri): BookImportResult

    suspend fun setCompleted(
        bookId: String,
        isCompleted: Boolean,
    )

    suspend fun delete(bookId: String)
}
