package com.kairo.reader.data.books.mobi

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

internal class MobiHeaderParser {
    fun parsePalmDocHeader(record0: ByteArray): PalmDocHeader {
        val buffer = ByteBuffer.wrap(record0).order(ByteOrder.BIG_ENDIAN)
        val compression = buffer.short.toInt() and UNSIGNED_SHORT_MASK
        buffer.short
        buffer.int
        val recordCount = buffer.short.toInt() and UNSIGNED_SHORT_MASK
        return PalmDocHeader(compression = compression, textRecordCount = recordCount)
    }

    fun parseHeaders(
        record0: ByteArray,
        fallbackTitle: String,
        fileName: String,
    ): MobiHeaders {
        val primary =
            parseHeaderAtOffset(record0, fallbackTitle, fileName, MobiLimits.MOBI_HEADER_OFFSET)
                ?: buildFallbackHeader(fallbackTitle, fileName)
        val kf8 = parseSecondaryHeader(record0, fallbackTitle, fileName)
        return MobiHeaders(primary = primary, kf8 = kf8)
    }

    fun findKf8CoverRecordIndex(
        data: ByteArray,
        recordOffsets: List<Int>,
        textRecordCount: Int,
        charset: Charset,
        firstResourceIndexHint: Int = -1,
    ): Int? {
        val opf = findOpfPackageXml(data, recordOffsets, charset) ?: return null
        val coverHref = parseCoverHrefFromOpf(opf) ?: return null
        val resourceNames = findResourceNameList(data, recordOffsets) ?: return null
        val firstResource =
            firstResourceIndexHint.takeIf { it in recordOffsets.indices }
                ?: (PALM_DATABASE_HEADER_RECORD_COUNT + textRecordCount)
                    .takeIf { it in recordOffsets.indices }
                ?: MobiBinary.findFirstImageRecordIndex(data, recordOffsets)
                ?: return null

        val target = normalizeResourceName(coverHref)
        val targetBase = target.substringAfterLast('/', target)
        val resourceIndex =
            resourceNames.indexOfFirst { raw ->
                val normalized = normalizeResourceName(raw)
                normalized == target || normalized.substringAfterLast('/', normalized) == targetBase
            }
        if (resourceIndex < 0) return null
        val recordIndex = firstResource + resourceIndex
        return recordIndex.takeIf { it in recordOffsets.indices }
    }

    private fun parseSecondaryHeader(
        record0: ByteArray,
        fallbackTitle: String,
        fileName: String,
    ): MobiHeader? {
        var offset = indexOfMobiHeader(record0, MobiLimits.MOBI_HEADER_OFFSET + MOBI_MAGIC_SIZE)
        var resolved: MobiHeader? = null
        while (offset >= 0) {
            parseHeaderAtOffset(record0, fallbackTitle, fileName, offset)?.let { resolved = it }
            offset = indexOfMobiHeader(record0, offset + MOBI_MAGIC_SIZE)
        }
        return resolved
    }

    private fun parseHeaderAtOffset(
        record0: ByteArray,
        fallbackTitle: String,
        fileName: String,
        headerOffset: Int,
    ): MobiHeader? {
        if (headerOffset < 0 || headerOffset + MOBI_MAGIC_SIZE > record0.size) return null
        if (String(record0.copyOfRange(headerOffset, headerOffset + MOBI_MAGIC_SIZE)) != MOBI_MAGIC) {
            return null
        }

        val headerLength = MobiBinary.readInt(record0, headerOffset + MOBI_HEADER_LENGTH_OFFSET)
        if (headerLength <= 0 || headerOffset + headerLength > record0.size) return null

        var title = fallbackTitle
        var authors = emptyList<String>()
        var coverRecordIndex: Int? = null

        val encoding = MobiBinary.readInt(record0, headerOffset + MOBI_TEXT_ENCODING_OFFSET)
        val charset = MobiBinary.resolveCharset(encoding)

        val fullNameOffset = MobiBinary.readInt(record0, headerOffset + MOBI_FULL_NAME_OFFSET_FIELD)
        val fullNameLength = MobiBinary.readInt(record0, headerOffset + MOBI_FULL_NAME_LENGTH_FIELD)
        if (fullNameOffset > 0 && fullNameLength > 0 && fullNameOffset + fullNameLength <= record0.size) {
            title =
                runCatching {
                    String(record0, fullNameOffset, fullNameLength, charset).trim('\u0000')
                }.getOrDefault(title)
        }

        val firstImageIndex =
            if (headerLength >= MobiLimits.FIRST_IMAGE_INDEX_OFFSET + INT_BYTES) {
                MobiBinary.readInt(record0, headerOffset + MobiLimits.FIRST_IMAGE_INDEX_OFFSET)
            } else {
                -1
            }

        val exthFlags =
            if (headerLength >= MobiLimits.EXTH_FLAGS_OFFSET + INT_BYTES) {
                MobiBinary.readInt(record0, headerOffset + MobiLimits.EXTH_FLAGS_OFFSET)
            } else {
                0
            }
        val exthStart = headerOffset + headerLength
        val hasExthFlag = (exthFlags and MobiLimits.EXTH_PRESENT_FLAG) != 0
        val hasExthMagic =
            exthStart + EXTH_MAGIC_SIZE <= record0.size &&
                String(record0.copyOfRange(exthStart, exthStart + EXTH_MAGIC_SIZE)) == EXTH_MAGIC
        if ((hasExthFlag || hasExthMagic) && exthStart + EXTH_HEADER_SIZE <= record0.size) {
            val exth = parseExth(record0, exthStart, charset)
            exth.title?.let { title = it }
            if (exth.authors.isNotEmpty()) authors = exth.authors
            coverRecordIndex = exth.coverRecordIndex
        }

        if (title.isBlank()) {
            title = fileName.substringBeforeLast('.', "Unknown Book")
        }

        return MobiHeader(
            title = title,
            authors = authors,
            textCharset = charset,
            firstImageIndex = firstImageIndex,
            coverRecordIndex = coverRecordIndex,
        )
    }

    private data class ExthMetadata(val title: String?, val authors: List<String>, val coverRecordIndex: Int?,)

    private fun parseExth(
        record0: ByteArray,
        start: Int,
        charset: Charset,
    ): ExthMetadata {
        if (String(record0.copyOfRange(start, start + EXTH_MAGIC_SIZE)) != EXTH_MAGIC) {
            return ExthMetadata(title = null, authors = emptyList(), coverRecordIndex = null)
        }
        val exthLength = MobiBinary.readInt(record0, start + EXTH_LENGTH_OFFSET)
        val recordCount = MobiBinary.readInt(record0, start + EXTH_RECORD_COUNT_OFFSET)
        var offset = start + EXTH_HEADER_SIZE
        val end = (start + exthLength).coerceAtMost(record0.size)

        val authors = mutableListOf<String>()
        var title: String? = null
        var coverRecordIndex: Int? = null

        repeat(recordCount) {
            if (offset + EXTH_RECORD_HEADER_SIZE > end) return@repeat
            val type = MobiBinary.readInt(record0, offset)
            val length = MobiBinary.readInt(record0, offset + EXTH_RECORD_LENGTH_OFFSET)
            val dataStart = offset + EXTH_RECORD_HEADER_SIZE
            val dataEnd = (offset + length).coerceAtMost(end)
            if (length < EXTH_RECORD_HEADER_SIZE || dataStart >= dataEnd) {
                offset += maxOf(length, EXTH_RECORD_HEADER_SIZE)
                return@repeat
            }
            val payload = record0.copyOfRange(dataStart, dataEnd)
            when (type) {
                EXTH_AUTHOR_RECORD_TYPE -> {
                    val raw = String(payload, charset).trim('\u0000')
                    raw.split(';', ',')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .forEach(authors::add)
                }
                EXTH_COVER_OFFSET_RECORD_TYPE -> {
                    if (payload.size >= INT_BYTES) {
                        coverRecordIndex = MobiBinary.readInt(payload, 0)
                    }
                }
                EXTH_UPDATED_TITLE_RECORD_TYPE -> {
                    val raw = String(payload, charset).trim('\u0000')
                    if (raw.isNotBlank()) title = raw
                }
            }
            offset += length
        }

        return ExthMetadata(title = title, authors = authors, coverRecordIndex = coverRecordIndex)
    }

    private fun buildFallbackHeader(
        fallbackTitle: String,
        fileName: String,
    ): MobiHeader {
        val title =
            if (fallbackTitle.isNotBlank()) fallbackTitle else fileName.substringBeforeLast('.', "Unknown Book")
        return MobiHeader(
            title = title,
            authors = emptyList(),
            textCharset = Charsets.UTF_8,
            firstImageIndex = -1,
            coverRecordIndex = null,
        )
    }

    private fun indexOfMobiHeader(
        data: ByteArray,
        startIndex: Int,
    ): Int {
        if (startIndex < 0 || startIndex >= data.size - MOBI_MAGIC_SIZE) return -1
        val limit = data.size - MOBI_MAGIC_SIZE
        var index = startIndex
        while (index <= limit) {
            if (data[index] == MOBI_MAGIC[MOBI_MAGIC_M_INDEX].code.toByte() &&
                data[index + 1] == MOBI_MAGIC[MOBI_MAGIC_O_INDEX].code.toByte() &&
                data[index + 2] == MOBI_MAGIC[MOBI_MAGIC_B_INDEX].code.toByte() &&
                data[index + MOBI_MAGIC_I_INDEX] == MOBI_MAGIC[MOBI_MAGIC_I_INDEX].code.toByte()
            ) {
                return index
            }
            index += 1
        }
        return -1
    }

    private fun findOpfPackageXml(
        data: ByteArray,
        recordOffsets: List<Int>,
        charset: Charset,
    ): String? {
        val startTag = "<package"
        val endTag = "</package>"
        return recordOffsets.indices.firstNotNullOfOrNull { index ->
            val start = recordOffsets[index]
            val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
            val validRange = start >= 0 && end <= data.size && end > start
            if (!validRange) return@firstNotNullOfOrNull null
            extractOpfPackage(data.copyOfRange(start, end), charset, startTag, endTag)
        }
    }

    private fun extractOpfPackage(
        bytes: ByteArray,
        charset: Charset,
        startTag: String,
        endTag: String,
    ): String? {
        if (MobiBinary.detectImageType(bytes) != null) return null
        val text = MobiBinary.decodeText(bytes, charset)
        val lower = text.lowercase()
        val packageStart = lower.indexOf(startTag).takeIf { it >= 0 } ?: return null
        val packageEnd = lower.indexOf(endTag, packageStart).takeIf { it >= 0 } ?: return null
        return text.substring(packageStart, packageEnd + endTag.length)
    }

    private fun parseCoverHrefFromOpf(opf: String): String? {
        val metaCoverRegex =
            Regex(
                """<meta[^>]+name\s*=\s*['"]cover['"][^>]+content\s*=\s*['"]([^'"]+)['"][^>]*>""",
                RegexOption.IGNORE_CASE,
            )
        val coverId = metaCoverRegex.find(opf)?.groupValues?.getOrNull(1)

        val itemRegex = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE)
        var fallback: String? = null
        for (match in itemRegex.findAll(opf)) {
            val tag = match.value
            val id = MobiHtmlUtils.extractAttribute(tag, "id")
            val href = MobiHtmlUtils.extractAttribute(tag, "href")
            val props = MobiHtmlUtils.extractAttribute(tag, "properties").orEmpty()

            if (coverId != null && id == coverId && !href.isNullOrBlank()) return href
            if (props.contains("cover-image", ignoreCase = true) && !href.isNullOrBlank()) return href
            if (fallback == null &&
                !href.isNullOrBlank() &&
                (
                    id?.contains("cover", ignoreCase = true) == true ||
                        href.contains("cover", ignoreCase = true)
                    )
            ) {
                fallback = href
            }
        }
        if (fallback != null) return fallback

        val referenceRegex = Regex("<reference\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (match in referenceRegex.findAll(opf)) {
            val tag = match.value
            val type = MobiHtmlUtils.extractAttribute(tag, "type")
            val href = MobiHtmlUtils.extractAttribute(tag, "href")
            if (type?.equals("cover", ignoreCase = true) == true && !href.isNullOrBlank()) {
                return href
            }
        }
        return null
    }

    private fun findResourceNameList(
        data: ByteArray,
        recordOffsets: List<Int>,
    ): List<String>? {
        var best: List<String>? = null
        for (index in recordOffsets.indices) {
            val start = recordOffsets[index]
            val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
            if (start < 0 || end > data.size || end <= start) continue
            val bytes = data.copyOfRange(start, end)
            if (MobiBinary.detectImageType(bytes) != null) continue
            val names = extractResourceNames(bytes)
            if (names.size >= MIN_RESOURCE_NAME_COUNT && (best == null || names.size > best!!.size)) {
                best = names
            }
        }
        return best
    }

    private fun extractResourceNames(bytes: ByteArray): List<String> {
        val names = ArrayList<String>()
        val builder = StringBuilder()

        fun flush() {
            if (builder.isEmpty()) return
            val value = builder.toString()
            if (looksLikeResourceName(value)) names.add(value)
            builder.setLength(0)
        }

        bytes.forEach { raw ->
            val code = raw.toInt() and BYTE_MASK
            if (code in PRINTABLE_ASCII_RANGE) {
                builder.append(code.toChar())
            } else {
                flush()
            }
        }
        flush()
        return names
    }

    private fun looksLikeResourceName(value: String): Boolean {
        if (!value.contains('.') || value.length > MAX_RESOURCE_NAME_LENGTH) return false
        return value.lowercase().substringAfterLast('.', "") in setOf(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "svg",
            "webp",
            "bmp",
            "css",
            "html",
            "xhtml",
            "opf",
            "ncx",
            "otf",
            "ttf",
            "woff",
            "woff2",
        )
    }

    private fun normalizeResourceName(value: String): String {
        var cleaned = value.trim().substringBefore('#').substringBefore('?').replace('\\', '/')
        while (cleaned.startsWith("./")) {
            cleaned = cleaned.removePrefix("./")
        }
        return runCatching { URLDecoder.decode(cleaned, "UTF-8") }
            .getOrDefault(cleaned)
            .lowercase()
    }

    private companion object {
        private const val UNSIGNED_SHORT_MASK = 0xFFFF
        private const val BYTE_MASK = 0xFF
        private const val INT_BYTES = 4
        private const val PALM_DATABASE_HEADER_RECORD_COUNT = 1

        // MOBI header fields are offsets from the start of the MOBI header.
        private const val MOBI_MAGIC = "MOBI"
        private const val MOBI_MAGIC_SIZE = 4
        private const val MOBI_HEADER_LENGTH_OFFSET = 4
        private const val MOBI_TEXT_ENCODING_OFFSET = 12
        private const val MOBI_FULL_NAME_OFFSET_FIELD = 28
        private const val MOBI_FULL_NAME_LENGTH_FIELD = 32
        private const val MOBI_MAGIC_M_INDEX = 0
        private const val MOBI_MAGIC_O_INDEX = 1
        private const val MOBI_MAGIC_B_INDEX = 2
        private const val MOBI_MAGIC_I_INDEX = 3

        // EXTH begins with magic, total length, and record count.
        private const val EXTH_MAGIC = "EXTH"
        private const val EXTH_MAGIC_SIZE = 4
        private const val EXTH_LENGTH_OFFSET = 4
        private const val EXTH_RECORD_COUNT_OFFSET = 8
        private const val EXTH_HEADER_SIZE = 12
        private const val EXTH_RECORD_HEADER_SIZE = 8
        private const val EXTH_RECORD_LENGTH_OFFSET = 4
        private const val EXTH_AUTHOR_RECORD_TYPE = 100
        private const val EXTH_COVER_OFFSET_RECORD_TYPE = 201
        private const val EXTH_UPDATED_TITLE_RECORD_TYPE = 503

        private const val MIN_RESOURCE_NAME_COUNT = 3
        private const val MAX_RESOURCE_NAME_LENGTH = 180
        private val PRINTABLE_ASCII_RANGE = 32..126
    }
}
