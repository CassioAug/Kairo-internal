package app.kairo.reader.data.books

import android.content.Context
import android.net.Uri
import app.kairo.reader.core.model.Book

interface BookParser {
    suspend fun parse(
        context: Context,
        uri: Uri,
    ): Book

    fun supports(extension: String): Boolean
}
