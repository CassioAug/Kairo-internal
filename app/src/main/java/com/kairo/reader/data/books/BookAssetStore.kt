package com.kairo.reader.data.books

import android.content.Context
import com.kairo.reader.core.model.BookId
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal class BookAssetStore(context: Context, private val rootName: String, private val bookId: BookId,) {
    private val imageDirectory = File(context.filesDir, "$rootName/${bookId.value}/images")

    fun writeImage(
        sourceName: String,
        bytes: ByteArray,
    ): String? {
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null
        val extension = sourceName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in SUPPORTED_IMAGE_EXTENSIONS) return null
        if (!imageDirectory.exists() && !imageDirectory.mkdirs()) return null

        val digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(bytes).toHexPrefix()
        val fileName = "$digest.$extension"
        val destination = File(imageDirectory, fileName)
        if (!destination.exists()) {
            destination.outputStream().use { output -> output.write(bytes) }
        }
        return "$rootName/${bookId.value}/images/$fileName"
    }

    private fun ByteArray.toHexPrefix(): String =
        take(HASH_PREFIX_BYTES).joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
        private const val HASH_ALGORITHM = "SHA-256"
        private const val HASH_PREFIX_BYTES = 16
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")
    }
}
