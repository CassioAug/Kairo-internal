package com.kairo.reader.data.books.mobi

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.countWords

internal class MobiFallbackParser(private val contentProcessor: MobiContentProcessor = MobiContentProcessor(),) {
    fun parse(
        bookId: BookId,
        data: ByteArray,
        fileName: String,
    ): Book {
        val extracted = contentProcessor.extractFallbackText(data)
        require(countWords(extracted) >= MIN_FALLBACK_WORDS) {
            "No readable MOBI or PalmDOC content could be recovered"
        }
        val text =
            when {
                extracted.isBlank() -> "No readable content found."
                else -> extracted
            }
        val chapters = contentProcessor.splitFallbackText(text)
        return Book(
            id = bookId,
            title = fileName.substringBeforeLast('.', "MOBI Import"),
            authors = emptyList(),
            languageTag = null,
            coverImage = null,
            chapters = chapters,
        )
    }

    private companion object {
        private const val MIN_FALLBACK_WORDS = 5
    }
}
