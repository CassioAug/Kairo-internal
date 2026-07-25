package com.kairo.reader.data.books.mobi

import java.nio.charset.StandardCharsets

internal object MobiFormatValidator {
    fun validate(data: ByteArray) {
        require(data.size >= MobiLimits.MIN_FILE_SIZE_BYTES) { "File is too small to be a valid MOBI/PRC book" }
        val recordCount = readUnsignedShort(data, PALM_DATABASE_RECORD_COUNT_OFFSET)
        require(recordCount >= MobiLimits.MIN_RECORD_COUNT) { "Palm database contains too few records" }

        val recordTableEnd = PALM_DATABASE_RECORD_TABLE_OFFSET + recordCount.toLong() * RECORD_INFO_BYTES
        require(recordTableEnd <= data.size) { "Palm database record table is truncated" }
        val offsets = MobiBinary.parseRecordOffsets(data, recordCount)
        require(offsets.size == recordCount) { "Palm database record table is incomplete" }
        require(offsets.first().toLong() >= recordTableEnd) { "Palm database first record overlaps its header" }
        require(offsets.zipWithNext().all { (first, second) -> first < second }) {
            "Palm database record offsets are not strictly ordered"
        }
        require(offsets.last() in 0 until data.size) { "Palm database record points outside the file" }

        val typeCreator = ascii(data, PALM_DATABASE_TYPE_OFFSET, TYPE_CREATOR_BYTES)
        val firstRecordStart = offsets.first()
        val firstRecordEnd = offsets.getOrElse(1) { data.size }
        require(firstRecordEnd - firstRecordStart >= PALMDOC_HEADER_BYTES) {
            "PalmDOC header is truncated"
        }
        val hasMobiHeader =
            ascii(data, firstRecordStart + MobiLimits.MOBI_HEADER_OFFSET, MOBI_MAGIC_BYTES) == MOBI_MAGIC
        require(typeCreator in SUPPORTED_TYPE_CREATORS || hasMobiHeader) {
            "Palm database is not a supported MOBI or PalmDOC book"
        }

        val compression = readUnsignedShort(data, firstRecordStart)
        require(compression in SUPPORTED_COMPRESSIONS) { "Unsupported MOBI compression type: $compression" }
        val encryptionType = readUnsignedShort(data, firstRecordStart + PALMDOC_ENCRYPTION_OFFSET)
        require(encryptionType == NO_ENCRYPTION) {
            "DRM-protected MOBI, PRC, and AZW files are not supported"
        }
    }

    private fun readUnsignedShort(
        data: ByteArray,
        offset: Int,
    ): Int {
        require(offset >= 0 && offset + SHORT_BYTES <= data.size) { "Book header is truncated" }
        return ((data[offset].toInt() and BYTE_MASK) shl BYTE_BITS) or
            (data[offset + 1].toInt() and BYTE_MASK)
    }

    private fun ascii(
        data: ByteArray,
        offset: Int,
        length: Int,
    ): String {
        if (offset < 0 || offset + length > data.size) return ""
        return String(data, offset, length, StandardCharsets.US_ASCII)
    }

    private const val PALM_DATABASE_TYPE_OFFSET = 60
    private const val PALM_DATABASE_RECORD_COUNT_OFFSET = 76
    private const val PALM_DATABASE_RECORD_TABLE_OFFSET = 78
    private const val RECORD_INFO_BYTES = 8
    private const val TYPE_CREATOR_BYTES = 8
    private const val PALMDOC_HEADER_BYTES = 16
    private const val PALMDOC_ENCRYPTION_OFFSET = 12
    private const val MOBI_MAGIC_BYTES = 4
    private const val MOBI_MAGIC = "MOBI"
    private const val SHORT_BYTES = 2
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xFF
    private const val NO_ENCRYPTION = 0
    private const val UNCOMPRESSED_TEXT = 1
    private const val PALMDOC_COMPRESSION = 2
    private const val HUFF_CDIC_COMPRESSION = 17480
    private val SUPPORTED_COMPRESSIONS = setOf(UNCOMPRESSED_TEXT, PALMDOC_COMPRESSION, HUFF_CDIC_COMPRESSION)
    private val SUPPORTED_TYPE_CREATORS = setOf("BOOKMOBI", "TEXtREAd")
}
