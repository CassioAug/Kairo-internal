package com.kairo.reader.data.books

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal object BookImportFormatDetector {
    fun detect(
        context: Context,
        uri: Uri,
    ): String? {
        val localFile = uri.localFileOrNull()
        if (localFile?.isFile == true) {
            return detect(localFile)
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use(::detect)
        }.getOrNull()
    }

    internal fun detect(file: File): String? {
        val signature = file.inputStream().buffered().use(::readSignature)
        return when {
            signature.hasPdfHeader() -> PDF_EXTENSION
            signature.hasZipHeader() -> detectDocxPackage(file)
            else -> null
        }
    }

    internal fun detect(input: InputStream): String? {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(SIGNATURE_SCAN_BYTES)
        val signature = readSignature(buffered)
        buffered.reset()
        return when {
            signature.hasPdfHeader() -> PDF_EXTENSION
            signature.hasZipHeader() -> detectDocxPackage(buffered)
            else -> null
        }
    }

    private fun detectDocxPackage(file: File): String? =
        runCatching {
            ZipFile(file).use { archive ->
                val entryNames =
                    archive.entries().asSequence()
                        .take(MAX_PACKAGE_ENTRIES)
                        .map { entry -> entry.name.normalizedPackagePath() }
                        .toSet()
                DOCX_EXTENSION.takeIf { entryNames.isDocxPackage() }
            }
        }.getOrNull()

    private fun detectDocxPackage(input: InputStream): String? =
        runCatching {
            ZipInputStream(input).use { archive ->
                val entryNames = mutableSetOf<String>()
                var entryCount = 0
                while (entryCount < MAX_PACKAGE_ENTRIES) {
                    val entry = archive.nextEntry ?: break
                    entryCount += 1
                    entryNames += entry.name.normalizedPackagePath()
                    if (entryNames.isDocxPackage()) return@use DOCX_EXTENSION
                    archive.closeEntry()
                }
                null
            }
        }.getOrNull()

    private fun readSignature(input: InputStream): ByteArray {
        val signature = ByteArray(SIGNATURE_SCAN_BYTES)
        var total = 0
        while (total < signature.size) {
            val count = input.read(signature, total, signature.size - total)
            if (count == -1) break
            if (count > 0) total += count
        }
        return signature.copyOf(total)
    }

    private fun Set<String>.isDocxPackage(): Boolean =
        CONTENT_TYPES_PATH in this && DOCUMENT_PATH in this

    private fun String.normalizedPackagePath(): String =
        replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)

    private fun ByteArray.hasPdfHeader(): Boolean {
        if (size < PDF_HEADER.size) return false
        return indices
            .take(size - PDF_HEADER.size + 1)
            .any { startIndex ->
                PDF_HEADER.indices.all { offset -> this[startIndex + offset] == PDF_HEADER[offset] }
            }
    }

    private fun ByteArray.hasZipHeader(): Boolean =
        size >= ZIP_HEADER.size && ZIP_HEADER.indices.all { index -> this[index] == ZIP_HEADER[index] }

    private fun Uri.localFileOrNull(): File? =
        path?.takeIf { scheme == ContentResolver.SCHEME_FILE }?.let(::File)

    private const val PDF_EXTENSION = "pdf"
    private const val DOCX_EXTENSION = "docx"
    private const val CONTENT_TYPES_PATH = "[content_types].xml"
    private const val DOCUMENT_PATH = "word/document.xml"
    private const val SIGNATURE_SCAN_BYTES = 1024
    private const val MAX_PACKAGE_ENTRIES = 4096
    private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)

    // ZIP local-header bytes are a fixed protocol signature.
    @Suppress("MagicNumber")
    private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
}
