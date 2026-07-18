package com.kairo.reader.data.books.mobi

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import com.kairo.reader.core.model.BookId
import java.io.File

internal data class MobiImageExtractionRequest(
    val context: Context,
    val bookId: BookId,
    val data: ByteArray,
    val recordOffsets: List<Int>,
    val firstImageIndex: Int,
    val coverRecordIndex: Int?,
    val textRecordCount: Int,
    val coverRecindexCandidates: Set<Int>,
    val referencedImageIndices: Set<Int>,
)

private data class MobiImageScanPlan(
    val startIndex: Int,
    val resolvedFirstImageIndex: Int,
    val recindexBase: Int,
    val explicitCoverIndices: Set<Int>,
    val htmlCoverCandidateIndices: Set<Int>,
    val htmlCoverPreferredIndex: Int?,
    val coverCandidateIndices: Set<Int>,
)

private data class MobiImageCandidate(
    val index: Int,
    val bytes: ByteArray,
    val type: MobiImageType,
    val dimensions: MobiImageDimensions?,
    val score: Long,
) {
    val isPortrait: Boolean = dimensions?.isPortrait == true
}

private class MobiCoverSelection {
    private var totalImageBytes = 0L
    private var firstImage: ByteArray? = null
    private var bestOverall: ScoredImage? = null
    private var bestPortrait: ScoredImage? = null
    private var explicitCoverImage: ByteArray? = null
    private var htmlCoverPreferred: ByteArray? = null
    private var htmlCoverCandidate: ScoredImage? = null
    private var colorCoverCandidate: ColorScoredImage? = null
    private var coverCandidate: ScoredImage? = null
    private var coverPortraitCandidate: ScoredImage? = null

    fun accept(
        candidate: MobiImageCandidate,
        plan: MobiImageScanPlan,
        colorScore: (ByteArray, MobiImageDimensions) -> Float?,
    ): Boolean {
        totalImageBytes += candidate.bytes.size
        if (totalImageBytes > MobiLimits.MAX_TOTAL_IMAGE_SIZE) return false
        if (firstImage == null) firstImage = candidate.bytes
        bestOverall = bestOverall.pick(candidate)
        if (candidate.isPortrait) bestPortrait = bestPortrait.pick(candidate)
        if (explicitCoverImage == null && candidate.index in plan.explicitCoverIndices) {
            explicitCoverImage = candidate.bytes
        }
        if (htmlCoverPreferred == null && candidate.index == plan.htmlCoverPreferredIndex) {
            htmlCoverPreferred = candidate.bytes
        }
        if (candidate.index in plan.htmlCoverCandidateIndices) {
            htmlCoverCandidate = htmlCoverCandidate.pick(candidate)
        }
        if (candidate.index in plan.coverCandidateIndices) {
            coverCandidate = coverCandidate.pick(candidate)
            if (candidate.isPortrait) coverPortraitCandidate = coverPortraitCandidate.pick(candidate)
        }
        val dimensions = candidate.dimensions
        if (dimensions != null && candidate.isPortrait && dimensions.area >= MobiLimits.MIN_COLOR_COVER_AREA) {
            colorScore(candidate.bytes, dimensions)?.let { score ->
                if (score > (colorCoverCandidate?.score ?: 0f)) {
                    colorCoverCandidate = ColorScoredImage(candidate.bytes, score)
                }
            }
        }
        return true
    }

    fun coverImage(): ByteArray? {
        val colorful = colorCoverCandidate?.takeIf { it.score >= MobiLimits.MIN_COLOR_SCORE }?.bytes
        return colorful
            ?: explicitCoverImage
            ?: htmlCoverPreferred
            ?: htmlCoverCandidate?.bytes
            ?: coverPortraitCandidate?.bytes
            ?: coverCandidate?.bytes
            ?: firstImage
            ?: bestPortrait?.bytes
            ?: bestOverall?.bytes
    }

    private fun ScoredImage?.pick(candidate: MobiImageCandidate): ScoredImage =
        if (this == null || candidate.score > score) ScoredImage(candidate.bytes, candidate.score) else this

    private data class ScoredImage(val bytes: ByteArray, val score: Long,)

    private data class ColorScoredImage(val bytes: ByteArray, val score: Float,)
}

internal class MobiImageProcessor {
    private companion object {
        // Width/height fields defined by each image header format.
        const val PNG_MIN_HEADER_BYTES = 24
        const val PNG_WIDTH_OFFSET = 16
        const val PNG_HEIGHT_OFFSET = 20
        const val GIF_MIN_HEADER_BYTES = 10
        const val GIF_WIDTH_OFFSET = 6
        const val GIF_HEIGHT_OFFSET = 8
        const val BMP_MIN_HEADER_BYTES = 26
        const val BMP_WIDTH_OFFSET = 18
        const val BMP_HEIGHT_OFFSET = 22

        const val COVER_COLOR_SAMPLE_MAX_DIMENSION = 72
        const val HSV_COMPONENT_COUNT = 3
        const val LARGE_PIXEL_SAMPLE_THRESHOLD = 4096
        const val LARGE_PIXEL_SAMPLE_STRIDE = 2

        const val JPEG_MARKER_PREFIX = 0xFF
        const val JPEG_START_OF_IMAGE = 0xD8
        const val JPEG_END_OF_IMAGE = 0xD9
        const val JPEG_SIGNATURE_BYTES = 2
        const val JPEG_SEGMENT_LENGTH_BYTES = 2
        const val JPEG_FRAME_HEADER_LAST_OFFSET = 7
        const val JPEG_FRAME_HEIGHT_OFFSET = 3
        const val JPEG_FRAME_WIDTH_OFFSET = 5
        const val BYTE_MASK = 0xFF
        const val HIGH_BYTE_SHIFT = 8

        // Start-of-frame marker values defined by the JPEG protocol.
        @Suppress("MagicNumber")
        val JPEG_START_OF_FRAME_MARKERS =
            setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)
    }

    fun extractImages(request: MobiImageExtractionRequest): MobiImageExtraction {
        val imagePathByRecordIndex = mutableMapOf<Int, String>()
        val imageDir = File(request.context.filesDir, "kairo_mobi_assets/${request.bookId.value}/images")
        val canWriteImages = runCatching { imageDir.mkdirs() || imageDir.exists() }.getOrDefault(false)
        val plan = buildImageScanPlan(request)
        val selection = MobiCoverSelection()

        var index = plan.startIndex
        var withinTotalSizeLimit = true
        while (index <= request.recordOffsets.lastIndex && withinTotalSizeLimit) {
            val candidate = readImageCandidate(request, index, plan.explicitCoverIndices)
            if (candidate != null) {
                withinTotalSizeLimit = selection.accept(candidate, plan, ::estimateColorScore)
                if (withinTotalSizeLimit && canWriteImages) {
                    writeImage(candidate, request.bookId, imageDir)?.let { path ->
                        imagePathByRecordIndex[index] = path
                    }
                }
            }
            index += 1
        }

        return MobiImageExtraction(
            imagePathByRecordIndex = imagePathByRecordIndex,
            coverImage = selection.coverImage(),
            resolvedFirstImageIndex = plan.resolvedFirstImageIndex.takeIf { it >= 0 },
            recindexBase = plan.recindexBase.takeIf { it >= 0 },
        )
    }

    private fun buildImageScanPlan(request: MobiImageExtractionRequest): MobiImageScanPlan {
        val recordCount = request.recordOffsets.size
        val resourceBaseIndex =
            when {
                request.firstImageIndex in 1 until recordCount -> request.firstImageIndex
                request.textRecordCount > 0 -> (1 + request.textRecordCount).takeIf { it in request.recordOffsets.indices } ?: -1
                else -> -1
            }
        val resolvedFirstImageIndex =
            resourceBaseIndex.takeIf {
                it >= 0 && MobiBinary.isImageRecord(request.data, request.recordOffsets, it)
            } ?: MobiBinary.findFirstImageRecordIndex(request.data, request.recordOffsets) ?: -1
        val startIndex = resolvedFirstImageIndex.coerceAtLeast(0)
        val recindexBase = resolveRecindexBase(request, resourceBaseIndex, resolvedFirstImageIndex)
        val explicitCoverIndices =
            buildExplicitCoverIndices(
                request.coverRecordIndex,
                recindexBase,
                request.firstImageIndex,
                recordCount,
                recindexBase >= 0,
            )
        val coverCandidateIndices =
            buildCoverCandidateIndices(
                request.coverRecindexCandidates,
                startIndex,
                recindexBase,
                request.firstImageIndex,
                request.coverRecordIndex,
                recordCount,
                resolvedFirstImageIndex >= 0,
            ) + explicitCoverIndices
        return MobiImageScanPlan(
            startIndex = startIndex,
            resolvedFirstImageIndex = resolvedFirstImageIndex,
            recindexBase = recindexBase,
            explicitCoverIndices = explicitCoverIndices,
            htmlCoverCandidateIndices =
            buildHtmlCoverCandidateIndices(request.coverRecindexCandidates, recindexBase, recordCount, recindexBase >= 0),
            htmlCoverPreferredIndex =
            resolveHtmlCoverPreferredIndex(
                request.data,
                request.recordOffsets,
                request.coverRecindexCandidates,
                recindexBase,
                recindexBase >= 0,
            ),
            coverCandidateIndices = coverCandidateIndices,
        )
    }

    private fun resolveRecindexBase(
        request: MobiImageExtractionRequest,
        resourceBaseIndex: Int,
        resolvedFirstImageIndex: Int,
    ): Int =
        when {
            resourceBaseIndex > 0 && !MobiBinary.isImageRecord(request.data, request.recordOffsets, resourceBaseIndex - 1) ->
                resourceBaseIndex - 1
            resourceBaseIndex >= 0 -> resourceBaseIndex
            resolvedFirstImageIndex > 0 &&
                !MobiBinary.isImageRecord(request.data, request.recordOffsets, resolvedFirstImageIndex - 1) ->
                resolvedFirstImageIndex - 1
            else -> resolvedFirstImageIndex
        }

    private fun readImageCandidate(
        request: MobiImageExtractionRequest,
        index: Int,
        explicitCoverIndices: Set<Int>,
    ): MobiImageCandidate? {
        val start = request.recordOffsets[index]
        val end = request.recordOffsets.getOrNull(index + 1) ?: request.data.size
        if (start < 0 || end > request.data.size || end <= start) return null
        val bytes = request.data.copyOfRange(start, end)
        val type = MobiBinary.detectImageType(bytes) ?: return null
        val maxSize =
            if (index in explicitCoverIndices) MobiLimits.MAX_COVER_IMAGE_ENTRY_SIZE else MobiLimits.MAX_IMAGE_ENTRY_SIZE
        if (bytes.size > maxSize) return null
        val dimensions = readImageDimensions(type, bytes)
        return MobiImageCandidate(index, bytes, type, dimensions, dimensions?.area ?: bytes.size.toLong())
    }

    private fun writeImage(
        candidate: MobiImageCandidate,
        bookId: BookId,
        imageDir: File,
    ): String? {
        val file = File(imageDir, "img_${candidate.index}.${candidate.type.extension}")
        val wrote =
            runCatching {
                file.outputStream().use { it.write(candidate.bytes) }
                true
            }.getOrDefault(false)
        return if (wrote) "kairo_mobi_assets/${bookId.value}/images/${file.name}" else null
    }

    fun rewriteImageSrcs(
        html: String,
        imagePathByRecordIndex: Map<Int, String>,
        recindexBase: Int,
    ): String {
        var updated = html

        val recindexRegex =
            Regex(
                """(<img\b[^>]*?)\s+recindex\s*=\s*['"](\d+)['"]([^>]*>)""",
                RegexOption.IGNORE_CASE,
            )
        updated = recindexRegex.replace(updated) { match ->
            val recindex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val path = resolveImagePath(recindex, imagePathByRecordIndex, recindexBase)
                ?: return@replace match.value
            "${match.groupValues[1]} src=\"$path\"${match.groupValues[REGEX_SUFFIX_GROUP]}"
        }

        val embedRegex =
            Regex(
                """(src\s*=\s*['"])kindle:embed:(\d+)(['"])""",
                RegexOption.IGNORE_CASE,
            )
        updated = embedRegex.replace(updated) { match ->
            val embedIndex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val path = resolveImagePath(embedIndex, imagePathByRecordIndex, recindexBase)
                ?: return@replace match.value
            "${match.groupValues[1]}$path${match.groupValues[REGEX_SUFFIX_GROUP]}"
        }

        if (imagePathByRecordIndex.isNotEmpty()) {
            val orderedPaths = imagePathByRecordIndex.toSortedMap().values.toList()
            var fallbackIndex = 0
            val imgRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
            updated = imgRegex.replace(updated) { match ->
                val tag = match.value
                if (tag.contains("recindex", true)) return@replace tag
                if (tag.contains("kindle:embed", true)) return@replace tag
                val src = MobiHtmlUtils.extractAttribute(tag, "src") ?: return@replace tag
                if (src.startsWith("data:", true) ||
                    src.startsWith("http://", true) ||
                    src.startsWith("https://", true) ||
                    src.contains("kairo_mobi_assets/", true)
                ) {
                    return@replace tag
                }
                val path = orderedPaths.getOrNull(fallbackIndex) ?: return@replace tag
                fallbackIndex += 1
                replaceSrcInTag(tag, path)
            }
        }
        return updated
    }

    private fun buildCoverCandidateIndices(
        coverRecindexCandidates: Set<Int>,
        startIndex: Int,
        recindexBase: Int,
        firstImageIndex: Int,
        coverRecordIndex: Int?,
        recordCount: Int,
        hasValidStartIndex: Boolean,
    ): Set<Int> {
        val candidates = LinkedHashSet<Int>()
        coverRecindexCandidates.forEach { recindex ->
            candidates.addAll(resolveRecindexToRecordIndices(recindex, recindexBase, recordCount))
        }
        candidates.addAll(
            resolveCoverRecordIndices(
                coverRecordIndex = coverRecordIndex,
                firstImageIndex = firstImageIndex,
                recindexBase = recindexBase,
                recordCount = recordCount,
            ),
        )
        if (candidates.isEmpty() && hasValidStartIndex) {
            repeat(MobiLimits.COVER_FALLBACK_IMAGE_SCAN) { offset ->
                candidates.add(startIndex + offset)
            }
        }
        return candidates
    }

    private fun buildHtmlCoverCandidateIndices(
        coverRecindexCandidates: Set<Int>,
        recindexBase: Int,
        recordCount: Int,
        hasValidRecindexBase: Boolean,
    ): Set<Int> {
        if (coverRecindexCandidates.isEmpty()) return emptySet()
        val indices = LinkedHashSet<Int>()
        coverRecindexCandidates.forEach { recindex ->
            if (recindex in 0 until recordCount) indices.add(recindex)
            if (hasValidRecindexBase) {
                indices.addAll(resolveRecindexToRecordIndices(recindex, recindexBase, recordCount))
            }
        }
        return indices
    }

    private fun resolveHtmlCoverPreferredIndex(
        data: ByteArray,
        recordOffsets: List<Int>,
        coverRecindexCandidates: Set<Int>,
        recindexBase: Int,
        hasValidRecindexBase: Boolean,
    ): Int? {
        if (coverRecindexCandidates.isEmpty()) return null
        val seen = LinkedHashSet<Int>()
        coverRecindexCandidates.forEach { recindex ->
            if (recindex >= 0) seen.add(recindex)
            if (hasValidRecindexBase) {
                seen.addAll(resolveRecindexToRecordIndices(recindex, recindexBase, recordOffsets.size))
            }
        }
        for (candidate in seen) {
            if (MobiBinary.isImageRecord(data, recordOffsets, candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun buildExplicitCoverIndices(
        coverRecordIndex: Int?,
        recindexBase: Int,
        firstImageIndex: Int,
        recordCount: Int,
        hasValidRecindexBase: Boolean,
    ): Set<Int> {
        val indices = LinkedHashSet<Int>()
        if (coverRecordIndex == null) return indices

        indices.addAll(
            resolveCoverRecordIndices(
                coverRecordIndex = coverRecordIndex,
                firstImageIndex = firstImageIndex,
                recindexBase = if (hasValidRecindexBase) recindexBase else -1,
                recordCount = recordCount,
            ),
        )
        if (indices.isEmpty() && coverRecordIndex in 0 until recordCount) {
            indices.add(coverRecordIndex)
        }
        return indices
    }

    private fun resolveCoverRecordIndices(
        coverRecordIndex: Int?,
        firstImageIndex: Int,
        recindexBase: Int,
        recordCount: Int,
    ): Set<Int> {
        if (coverRecordIndex == null) return emptySet()
        val candidates = LinkedHashSet<Int>()
        if (coverRecordIndex in 0 until recordCount) {
            candidates.add(coverRecordIndex)
        }
        if (firstImageIndex > 0) {
            val zeroBased = firstImageIndex + coverRecordIndex
            if (zeroBased in 0 until recordCount) {
                candidates.add(zeroBased)
            }
            if (coverRecordIndex > 0) {
                val oneBased = firstImageIndex + coverRecordIndex - 1
                if (oneBased in 0 until recordCount) {
                    candidates.add(oneBased)
                }
            }
        }
        if (recindexBase >= 0) {
            candidates.addAll(resolveRecindexToRecordIndices(coverRecordIndex, recindexBase, recordCount))
        }
        return candidates
    }

    private fun resolveRecindexToRecordIndices(
        recindex: Int,
        recindexBase: Int,
        recordCount: Int,
    ): Set<Int> {
        if (recindexBase < 0) return emptySet()
        val resolved = LinkedHashSet<Int>()
        val zeroBased = recindexBase + recindex
        if (zeroBased in 0 until recordCount) {
            resolved.add(zeroBased)
        }
        if (recindex > 0) {
            val oneBased = recindexBase + recindex - 1
            if (oneBased in 0 until recordCount) {
                resolved.add(oneBased)
            }
        }
        return resolved
    }

    private fun replaceSrcInTag(
        tag: String,
        src: String,
    ): String {
        val srcRegex = Regex("""\bsrc\s*=\s*(?:'[^']*'|"[^"]*"|[^\s>]+)""", RegexOption.IGNORE_CASE)
        return if (srcRegex.containsMatchIn(tag)) {
            srcRegex.replace(tag) { "src=\"$src\"" }
        } else {
            val (prefix, suffix) = if (tag.endsWith("/>")) tag.dropLast(2) to "/>" else tag.dropLast(1) to ">"
            "$prefix src=\"$src\"$suffix"
        }
    }

    private fun resolveImagePath(
        index: Int,
        imagePathByRecordIndex: Map<Int, String>,
        recindexBase: Int,
    ): String? {
        imagePathByRecordIndex[index]?.let { return it }
        if (recindexBase >= 0) {
            imagePathByRecordIndex[recindexBase + index]?.let { return it }
            if (index > 0) {
                imagePathByRecordIndex[recindexBase + index - 1]?.let { return it }
            }
        }
        return null
    }

    private fun readImageDimensions(
        type: MobiImageType,
        bytes: ByteArray,
    ): MobiImageDimensions? =
        when (type.extension) {
            "jpg" -> readJpegDimensions(bytes)
            "png" -> readPngDimensions(bytes)
            "gif" -> readGifDimensions(bytes)
            "bmp" -> readBmpDimensions(bytes)
            else -> null
        }

    private fun readPngDimensions(bytes: ByteArray): MobiImageDimensions? {
        if (bytes.size < PNG_MIN_HEADER_BYTES) return null
        val width = MobiBinary.readInt(bytes, PNG_WIDTH_OFFSET)
        val height = MobiBinary.readInt(bytes, PNG_HEIGHT_OFFSET)
        return if (width > 0 && height > 0) MobiImageDimensions(width, height) else null
    }

    private fun readGifDimensions(bytes: ByteArray): MobiImageDimensions? {
        if (bytes.size < GIF_MIN_HEADER_BYTES) return null
        val width = MobiBinary.readLittleEndianShort(bytes, GIF_WIDTH_OFFSET)
        val height = MobiBinary.readLittleEndianShort(bytes, GIF_HEIGHT_OFFSET)
        return if (width > 0 && height > 0) MobiImageDimensions(width, height) else null
    }

    private fun readBmpDimensions(bytes: ByteArray): MobiImageDimensions? {
        if (bytes.size < BMP_MIN_HEADER_BYTES) return null
        val width = MobiBinary.readLittleEndianInt(bytes, BMP_WIDTH_OFFSET)
        val height = MobiBinary.readLittleEndianInt(bytes, BMP_HEIGHT_OFFSET)
        val absoluteHeight = if (height < 0) -height else height
        return if (width > 0 && absoluteHeight > 0) MobiImageDimensions(width, absoluteHeight) else null
    }

    private fun readJpegDimensions(bytes: ByteArray): MobiImageDimensions? {
        val frameOffset = findJpegFrameHeaderOffset(bytes) ?: return null
        if (frameOffset + JPEG_FRAME_HEADER_LAST_OFFSET >= bytes.size) return null
        val height = readBigEndianUnsignedShort(bytes, frameOffset + JPEG_FRAME_HEIGHT_OFFSET)
        val width = readBigEndianUnsignedShort(bytes, frameOffset + JPEG_FRAME_WIDTH_OFFSET)
        return MobiImageDimensions(width, height).takeIf { width > 0 && height > 0 }
    }

    private fun findJpegFrameHeaderOffset(bytes: ByteArray): Int? {
        if (!hasJpegSignature(bytes)) return null
        var index = JPEG_SIGNATURE_BYTES
        while (index + 1 < bytes.size) {
            index = findNextJpegMarker(bytes, index) ?: return null
            val marker = bytes[index].toInt() and BYTE_MASK
            index++
            if (marker == JPEG_START_OF_IMAGE) continue
            if (marker == JPEG_END_OF_IMAGE || index + 1 >= bytes.size) return null
            val length = readBigEndianUnsignedShort(bytes, index)
            if (length < JPEG_SEGMENT_LENGTH_BYTES) return null
            if (marker in JPEG_START_OF_FRAME_MARKERS) return index
            index += length
        }
        return null
    }

    private fun hasJpegSignature(bytes: ByteArray): Boolean =
        bytes.size >= JPEG_SIGNATURE_BYTES &&
            bytes[0] == JPEG_MARKER_PREFIX.toByte() &&
            bytes[1] == JPEG_START_OF_IMAGE.toByte()

    private fun findNextJpegMarker(bytes: ByteArray, startIndex: Int): Int? {
        var index = startIndex
        while (index < bytes.size && bytes[index] != JPEG_MARKER_PREFIX.toByte()) index++
        while (index < bytes.size && bytes[index] == JPEG_MARKER_PREFIX.toByte()) index++
        return index.takeIf { it < bytes.size }
    }

    private fun readBigEndianUnsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and BYTE_MASK) shl HIGH_BYTE_SHIFT) or
            (bytes[offset + 1].toInt() and BYTE_MASK)

    private fun estimateColorScore(
        bytes: ByteArray,
        dimensions: MobiImageDimensions,
    ): Float? {
        val sampleMax = COVER_COLOR_SAMPLE_MAX_DIMENSION
        val sampleSize =
            if (dimensions.width > sampleMax || dimensions.height > sampleMax) {
                maxOf(1, minOf(dimensions.width / sampleMax, dimensions.height / sampleMax))
            } else {
                1
            }
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            bitmap.recycle()
            return null
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        var total = 0f
        var count = 0
        val hsv = FloatArray(HSV_COMPONENT_COUNT)
        val step =
            if (pixels.size > LARGE_PIXEL_SAMPLE_THRESHOLD) {
                LARGE_PIXEL_SAMPLE_STRIDE
            } else {
                1
            }
        var index = 0
        while (index < pixels.size) {
            Color.colorToHSV(pixels[index], hsv)
            total += hsv[1]
            count += 1
            index += step
        }
        return if (count > 0) total / count else null
    }
}

private const val REGEX_SUFFIX_GROUP = 3
