package com.kairo.reader.data.books.epub

import java.nio.charset.Charset

internal object EpubTextDecoder {
    fun decodeTextEntry(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        detectBom(bytes)?.let { detected ->
            val slice = bytes.copyOfRange(detected.signature.size, bytes.size)
            return runCatching { String(slice, detected.charset) }
                .getOrElse { String(bytes, Charsets.UTF_8) }
        }

        val sampleLength = minOf(bytes.size, CHARSET_PROBE_BYTES)
        val sample = bytes.copyOf(sampleLength)
        val asciiProbe = String(sample, Charsets.ISO_8859_1)
        val declared = extractDeclaredCharset(asciiProbe)
        if (!declared.isNullOrBlank()) {
            val declaredCharset = runCatching { Charset.forName(declared) }.getOrNull()
            if (declaredCharset != null) {
                return runCatching { String(bytes, declaredCharset) }.getOrElse { String(bytes, Charsets.UTF_8) }
            }
        }

        detectUtf16Heuristic(sample)?.let { charset ->
            return runCatching { String(bytes, charset) }.getOrElse { String(bytes, Charsets.UTF_8) }
        }

        return runCatching { String(bytes, Charsets.UTF_8) }.getOrElse {
            runCatching { String(bytes, Charsets.ISO_8859_1) }.getOrElse { String(bytes) }
        }
    }

    private fun detectBom(bytes: ByteArray): BomEncoding? =
        BOM_ENCODINGS.firstOrNull { encoding -> bytes.startsWith(encoding.signature) }

    private fun ByteArray.startsWith(signature: ByteArray): Boolean =
        size >= signature.size &&
            signature.indices.all { index -> this[index] == signature[index] }

    private fun detectUtf16Heuristic(sample: ByteArray): Charset? {
        if (sample.size < MIN_UTF16_PROBE_BYTES) return null
        var evenNulls = 0
        var oddNulls = 0
        var evenCount = 0
        var oddCount = 0

        for (i in sample.indices) {
            if (i % 2 == 0) {
                evenCount++
                if (sample[i] == 0.toByte()) evenNulls++
            } else {
                oddCount++
                if (sample[i] == 0.toByte()) oddNulls++
            }
        }

        val evenRatio = if (evenCount == 0) 0.0 else evenNulls.toDouble() / evenCount.toDouble()
        val oddRatio = if (oddCount == 0) 0.0 else oddNulls.toDouble() / oddCount.toDouble()

        return when {
            evenRatio > UTF16_NULL_DOMINANT_RATIO && oddRatio < UTF16_NULL_SPARSE_RATIO ->
                Charsets.UTF_16BE
            oddRatio > UTF16_NULL_DOMINANT_RATIO && evenRatio < UTF16_NULL_SPARSE_RATIO ->
                Charsets.UTF_16LE
            else -> null
        }
    }

    private fun extractDeclaredCharset(sample: String): String? {
        val declarationRegex = Regex("(?i)encoding\\s*=\\s*['\"]([^'\"]+)['\"]")
        val declarationMatch = declarationRegex.find(sample)
        if (declarationMatch != null) {
            return declarationMatch.groupValues.getOrNull(1)?.trim()
        }

        val metaCharsetRegex = Regex("(?i)charset\\s*=\\s*([A-Za-z0-9_\\-]+)")
        val metaMatch = metaCharsetRegex.find(sample)
        return metaMatch?.groupValues?.getOrNull(1)?.trim()
    }

    private data class BomEncoding(val charset: Charset, val signature: ByteArray,)

    private const val CHARSET_PROBE_BYTES = 4096
    private const val MIN_UTF16_PROBE_BYTES = 4
    private const val UTF16_NULL_DOMINANT_RATIO = 0.3
    private const val UTF16_NULL_SPARSE_RATIO = 0.1

    // BOM byte sequences are fixed protocol signatures. UTF-32 precedes UTF-16 intentionally.
    @Suppress("MagicNumber")
    private val BOM_ENCODINGS =
        listOf(
            BomEncoding(Charset.forName("UTF-32BE"), byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())),
            BomEncoding(Charset.forName("UTF-32LE"), byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)),
            BomEncoding(Charsets.UTF_8, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
            BomEncoding(Charsets.UTF_16BE, byteArrayOf(0xFE.toByte(), 0xFF.toByte())),
            BomEncoding(Charsets.UTF_16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
        )
}
