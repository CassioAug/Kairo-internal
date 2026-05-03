package app.kairo.reader.data.books

import android.content.Context
import android.net.Uri
import app.kairo.reader.core.model.Book
import app.kairo.reader.sample.SampleBooks

@Suppress("unused")
class SampleBookParser : BookParser {
    override suspend fun parse(
        context: Context,
        uri: Uri,
    ): Book = SampleBooks.defaultSample()

    override fun supports(extension: String): Boolean = true
}
