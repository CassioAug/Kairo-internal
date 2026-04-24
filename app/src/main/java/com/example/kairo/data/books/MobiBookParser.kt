package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.kairo.core.dispatchers.DispatcherProvider
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.data.books.mobi.MobiFallbackParser
import com.example.kairo.data.books.mobi.MobiLimits
import com.example.kairo.data.books.mobi.MobiParserEngine
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.withContext

class MobiBookParser(
    private val dispatcherProvider: DispatcherProvider,
) : BookParser {
    private val parserEngine = MobiParserEngine()
    private val fallbackParser = MobiFallbackParser()

    override suspend fun parse(
        context: Context,
        uri: Uri,
    ): Book =
        withContext(dispatcherProvider.io) {
            val fileName = uri.lastPathSegment ?: "book.mobi"
            val bookId = BookId(UUID.randomUUID().toString())

            val fileSize = resolveFileSize(context, uri)
            require(fileSize < 0 || !isFileTooLarge(fileSize)) {
                "MOBI file too large (max ${MobiLimits.MAX_FILE_SIZE_BYTES / 1024 / 1024}MB)"
            }

            val data =
                requireNotNull(context.contentResolver.openInputStream(uri)) {
                    "Unable to read MOBI file"
                }.use { input ->
                    readInputBytesWithLimit(BufferedInputStream(input), MobiLimits.MAX_FILE_SIZE_BYTES)
                }

            runCatching {
                parserEngine.parse(
                    context = context,
                    bookId = bookId,
                    data = data,
                    fallbackFileName = fileName,
                )
            }.getOrElse {
                fallbackParser.parse(
                    bookId = bookId,
                    data = data,
                    fileName = fileName,
                )
            }
        }

    override fun supports(extension: String): Boolean =
        extension == "mobi" || extension == "prc" || extension == "azw"

    private fun resolveFileSize(
        context: Context,
        uri: Uri,
    ): Long {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && index >= 0) {
                    return cursor.getLong(index)
                }
            }
        }
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                return descriptor.statSize
            }
        }
        return -1L
    }

    private fun isFileTooLarge(fileSize: Long): Boolean =
        fileSize > MobiLimits.MAX_FILE_SIZE_BYTES

    private fun readInputBytesWithLimit(
        input: InputStream,
        maxBytes: Long,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            if (read == 0) continue
            total += read
            require(total <= maxBytes) {
                "MOBI file too large (max ${maxBytes / 1024 / 1024}MB)"
            }
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }
}
