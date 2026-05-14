package com.kairo.reader.data.books

import android.content.Context
import android.net.Uri
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import java.util.UUID

interface BookParser {
    suspend fun parse(
        context: Context,
        uri: Uri,
    ): Book = parse(context, uri, BookId(UUID.randomUUID().toString()))

    suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
    ): Book

    fun supports(extension: String): Boolean
}
