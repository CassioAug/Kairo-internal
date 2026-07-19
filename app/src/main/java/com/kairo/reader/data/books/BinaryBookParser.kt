package com.kairo.reader.data.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.withContext

internal data class BinaryBookParseRequest(
    val bookId: BookId,
    val bytes: ByteArray,
    val sourceDisplayName: String,
    val sourceExtension: String,
)

internal abstract class BinaryBookParser(
    private val dispatcherProvider: DispatcherProvider,
    supportedExtensions: Set<String>,
    private val maxFileSizeBytes: Long,
) : BookParser {
    private val normalizedExtensions =
        supportedExtensions
            .map { extension -> extension.lowercase(Locale.ROOT) }
            .toSet()

    final override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
    ): Book = parse(context, uri, bookId, sourceDisplayName = null)

    final override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
        sourceDisplayName: String?,
    ): Book =
        withContext(dispatcherProvider.io) {
            val displayName = resolveDisplayName(context, uri, sourceDisplayName)
            val extension = resolveSourceExtension(displayName, uri)
            val bytes = readSourceBytes(context, uri)
            parseSource(
                context = context,
                request =
                BinaryBookParseRequest(
                    bookId = bookId,
                    bytes = bytes,
                    sourceDisplayName = displayName,
                    sourceExtension = extension,
                ),
            )
        }

    final override fun supports(extension: String): Boolean =
        extension.trim().lowercase(Locale.ROOT) in normalizedExtensions

    protected abstract fun parseSource(
        context: Context,
        request: BinaryBookParseRequest,
    ): Book

    private fun resolveDisplayName(
        context: Context,
        uri: Uri,
        sourceDisplayName: String?,
    ): String =
        sourceDisplayName
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: queryDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: DEFAULT_SOURCE_NAME

    private fun queryDisplayName(
        context: Context,
        uri: Uri,
    ): String? =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                }
        }.getOrNull()?.takeIf(String::isNotBlank)

    private fun resolveSourceExtension(
        displayName: String,
        uri: Uri,
    ): String {
        val candidates = listOf(displayName, uri.lastPathSegment.orEmpty())
        return normalizedExtensions
            .sortedByDescending(String::length)
            .firstOrNull { extension ->
                candidates.any { candidate ->
                    candidate.lowercase(Locale.ROOT).endsWith(".$extension")
                }
            } ?: displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    }

    private fun readSourceBytes(
        context: Context,
        uri: Uri,
    ): ByteArray {
        val declaredSize = querySourceSize(context, uri)
        require(declaredSize < 0L || declaredSize <= maxFileSizeBytes) {
            "File is too large to import (maximum ${maxFileSizeBytes.toMebibytes()} MB)"
        }

        val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Unable to read imported file"
        }
        return BufferedInputStream(input).use { source ->
            val output = ByteArrayOutputStream(declaredSize.toInitialCapacity())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val count = source.read(buffer)
                if (count == -1) break
                if (count == 0) continue
                totalBytes += count
                require(totalBytes <= maxFileSizeBytes) {
                    "File is too large to import (maximum ${maxFileSizeBytes.toMebibytes()} MB)"
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun querySourceSize(
        context: Context,
        uri: Uri,
    ): Long =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                } ?: -1L
        }.getOrDefault(-1L)

    private fun Long.toInitialCapacity(): Int =
        takeIf { it in 1..MAX_INITIAL_CAPACITY_BYTES }
            ?.toInt()
            ?: DEFAULT_BUFFER_SIZE

    private fun Long.toMebibytes(): Long = this / BYTES_PER_MEBIBYTE

    private companion object {
        private const val DEFAULT_SOURCE_NAME = "Imported book"
        private const val MAX_INITIAL_CAPACITY_BYTES = 8L * 1024L * 1024L
        private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
    }
}
