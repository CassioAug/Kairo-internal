package com.kairo.reader.data.books

import android.content.Context
import android.net.Uri
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.sample.SampleBooks

@Suppress("unused")
class SampleBookParser : BookParser {
    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
    ): Book = SampleBooks.defaultSample().copy(id = bookId)

    override fun supports(extension: String): Boolean = true
}
