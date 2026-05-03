package app.kairo.reader.data.books.mobi

import app.kairo.reader.core.model.Book
import app.kairo.reader.core.model.BookId

internal class MobiFallbackParser(
    private val contentProcessor: MobiContentProcessor = MobiContentProcessor(),
) {
    fun parse(
        bookId: BookId,
        data: ByteArray,
        fileName: String,
    ): Book {
        val extracted = contentProcessor.extractFallbackText(data)
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
}
