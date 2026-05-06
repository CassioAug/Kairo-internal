package com.kairo.reader.data.library

import android.net.Uri
import com.kairo.reader.core.model.Book
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibrary(): Flow<List<Book>>

    suspend fun import(uri: Uri): Book

    suspend fun setCompleted(
        bookId: String,
        isCompleted: Boolean,
    )

    suspend fun delete(bookId: String)
}
