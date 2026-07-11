package com.kairo.reader.data.books

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

internal object ImportFingerprint {
    fun sourceFingerprint(
        extension: String,
        input: InputStream,
    ): String {
        val normalizedExtension = extension.lowercase(Locale.ROOT)
        return "source:$normalizedExtension:${input.sha256Hex()}"
    }

    fun sourceFingerprint(
        extension: String,
        input: InputStream,
        copyTo: OutputStream,
    ): String {
        val normalizedExtension = extension.lowercase(Locale.ROOT)
        return "source:$normalizedExtension:${input.sha256Hex(copyTo)}"
    }

    fun webUrlFingerprint(normalizedUrl: String): String =
        "web:${normalizeContent(normalizedUrl)}".toByteArray(Charsets.UTF_8).sha256Hex().let {
            "web:url:$it"
        }

    fun textFingerprint(normalizedText: String): String =
        normalizeContent(normalizedText).toByteArray(Charsets.UTF_8).sha256Hex().let {
            "text:sha256:$it"
        }

    fun contentFingerprint(book: Book): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateValue(normalizeMetadata(book.title))
        digest.updateValue(book.authors.joinToString("|") { normalizeMetadata(it) })
        digest.updateValue(book.chapters.size.toString())
        book.chapters
            .sortedBy { it.index }
            .forEachIndexed { index, chapter ->
                digest.updateValue(index.toString())
                digest.updateValue(normalizeMetadata(chapter.title.orEmpty()))
                digest.updateValue(normalizeContent(chapter.plainText))
            }
        return "content:sha256:${digest.hexDigest()}"
    }

    fun bookIdForFingerprint(fingerprint: String): BookId =
        BookId("imported-${fingerprint.toByteArray(Charsets.UTF_8).sha256Hex()}")

    private fun normalizeMetadata(value: String): String =
        value
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)

    private fun normalizeContent(value: String): String =
        value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

    private fun InputStream.sha256Hex(copyTo: OutputStream? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            if (read > 0) {
                digest.update(buffer, 0, read)
                copyTo?.write(buffer, 0, read)
            }
        }
        return digest.hexDigest()
    }

    private fun ByteArray.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(this)
        return digest.hexDigest()
    }

    private fun MessageDigest.updateValue(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        update(bytes.size.toString().toByteArray(Charsets.UTF_8))
        update(0.toByte())
        update(bytes)
        update(0.toByte())
    }

    private fun MessageDigest.hexDigest(): String =
        digest().joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private val WHITESPACE = Regex("\\s+")
    private const val BUFFER_SIZE = 64 * 1024
}
