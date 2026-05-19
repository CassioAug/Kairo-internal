@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "TooManyFunctions",
)

package com.kairo.reader.data.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords
import com.kairo.reader.data.books.epub.ChapterOrderResolution
import com.kairo.reader.data.books.epub.ChapterOrderSource
import com.kairo.reader.data.books.epub.ContainerXmlResolution
import com.kairo.reader.data.books.epub.EpubChapterOrdering
import com.kairo.reader.data.books.epub.EpubContainerParser
import com.kairo.reader.data.books.epub.EpubHtmlEntities
import com.kairo.reader.data.books.epub.EpubLogger
import com.kairo.reader.data.books.epub.EpubOpfParser
import com.kairo.reader.data.books.epub.EpubPathResolver
import com.kairo.reader.data.books.epub.EpubTextDecoder
import com.kairo.reader.data.books.epub.OpfData
import com.kairo.reader.data.books.epub.OpfParseResult
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.withContext

/**
 * Full-fidelity EPUB parser that properly handles the EPUB ZIP structure.
 *
 * EPUB Structure:
 * - META-INF/container.xml → Points to content.opf location
 * - content.opf (or similar) → Contains metadata, manifest, and spine
 * - Manifest → Lists all content files (XHTML chapters, images, CSS)
 * - Spine → Defines reading order of chapters
 * - XHTML files → Actual chapter content
 */
class EpubBookParser(private val dispatcherProvider: DispatcherProvider) : BookParser {
    companion object {
        private const val TAG = "EpubBookParser"

        // Max size per text entry (5 MB) to prevent OOM on large embedded files
        private const val MAX_ENTRY_SIZE = 5 * 1024 * 1024

        // Max size per image entry (6 MB)
        private const val MAX_IMAGE_ENTRY_SIZE = 6 * 1024 * 1024

        // Max size for cover image entry (6 MB)
        private const val MAX_COVER_IMAGE_ENTRY_SIZE = 6 * 1024 * 1024

        // Max total size for extracted images (25 MB)
        private const val MAX_TOTAL_IMAGE_SIZE = 25 * 1024 * 1024

        // Max total size for in-memory text entries (40 MB)
        private const val MAX_TOTAL_TEXT_SIZE = 40 * 1024 * 1024

        // Buffer size for reading entries
        private const val BUFFER_SIZE = 8192

        // Guardrails for link extraction to avoid expensive scans on huge TOCs.
        private const val MAX_LINKS_PER_CHAPTER = 1000

        private const val MAX_NOISE_TITLE_LENGTH = 32
        private const val MAX_NAV_FILTER_REMOVAL_RATIO = 0.60
        private const val MIN_SUBSTANTIAL_CHAPTER_WORDS = 80
        private const val MAX_EXACT_WORD_COUNT_CHARS = 120_000
        private const val NAVIGATION_TITLE_SCAN_CHARS = 400
        private const val NAVIGATION_WORD_SCAN_LIMIT = 601
        private const val PAGE_BREAK_MARKER = "\u000C"
        private val FILE_LABEL_WITH_NUMBER_REGEX =
            Regex("(?i)^(part|chapter|section|book)(0*)(\\d{1,6})$")
        private val GENERIC_FILE_LABEL_REGEX =
            Regex("(?i)^[a-z]{2,}\\d{3,}$")
        private val TOC_TITLE_PATTERNS =
            listOf(
                Regex("\\btable\\s+of\\s+contents\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcontents\\b", RegexOption.IGNORE_CASE),
            )

        // Precompiled regex patterns for HTML processing (performance optimization)
        private val HTML_COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")
        private val SCRIPT_TAG_REGEX = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
        private val STYLE_TAG_REGEX = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
        private val HEAD_TAG_REGEX = Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE)
        private val ALL_TAGS_REGEX = Regex("<[^>]+>")
        private val HORIZONTAL_WHITESPACE_REGEX = Regex("[ \\t]+")
        private val MULTIPLE_NEWLINES_REGEX = Regex("\\n\\s*\\n+")
        private val WHITESPACE_REGEX = Regex("\\s+")

        // Image src rewriting patterns
        private val IMG_SRC_REWRITE_REGEX = Regex(
            "(<img\\b[^>]*?\\bsrc\\s*=\\s*['\"])([^'\"]+)(['\"][^>]*>)",
            RegexOption.IGNORE_CASE,
        )
        private val SVG_IMAGE_REWRITE_REGEX = Regex(
            "(<image\\b[^>]*?\\b(?:xlink:href|href)\\s*=\\s*['\"])([^'\"]+)(['\"][^>]*>)",
            RegexOption.IGNORE_CASE,
        )
        private val SRCSET_REWRITE_REGEX = Regex(
            "(<(?:img|source)\\b[^>]*?\\bsrcset\\s*=\\s*['\"])([^'\"]+)(['\"][^>]*>)",
            RegexOption.IGNORE_CASE,
        )
        private val IMAGE_SOURCE_SRC_REGEX = Regex(
            """<(?:img|source)\b[^>]*?\bsrc\s*=\s*(['"])(.*?)\1""",
            RegexOption.IGNORE_CASE,
        )
        private val IMAGE_SOURCE_SRCSET_REGEX = Regex(
            """<(?:img|source)\b[^>]*?\bsrcset\s*=\s*(['"])(.*?)\1""",
            RegexOption.IGNORE_CASE,
        )
        private val SVG_IMAGE_HREF_REGEX = Regex(
            """<image\b[^>]*?\b(?:xlink:href|href)\s*=\s*(['"])(.*?)\1""",
            RegexOption.IGNORE_CASE,
        )
        private val ANCHOR_TAG_REGEX = Regex("<a\\b", RegexOption.IGNORE_CASE)

        // Anchor rewriting pattern
        private val ANCHOR_HREF_REGEX = Regex(
            "(<a\\b[^>]*href\\s*=\\s*['\"])([^'\"]+)(['\"][^>]*>)",
            RegexOption.IGNORE_CASE,
        )

        // Noise title block pattern
        private val BLOCK_ELEMENT_REGEX = Regex("(?is)<(h[1-6]|p|div)[^>]*>([\\s\\S]*?)</\\1>")
        private val HEADING_BLOCK_ELEMENT_REGEX = Regex("(?is)<h[1-6][^>]*>([\\s\\S]*?)</h[1-6]>")
        private val PARAGRAPH_BLOCK_ELEMENT_REGEX = Regex("(?is)<p[^>]*>([\\s\\S]*?)</p>")
        private val DIV_BLOCK_ELEMENT_REGEX = Regex("(?is)<div[^>]*>([\\s\\S]*?)</div>")
    }

    private val markupParser = EpubMarkupParser()
    private val containerParser = EpubContainerParser()
    private val opfParser = EpubOpfParser()

    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
    ): Book = parse(context, uri, bookId, sourceDisplayName = null)

    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
        sourceDisplayName: String?,
    ): Book =
        withContext(dispatcherProvider.io) {
            val zipTextEntries = mutableMapOf<String, ByteArray>() // key = lowercased path
            val zipEntryNamesLower = mutableSetOf<String>()
            val oversizedTextEntriesLower = mutableSetOf<String>()
            val decodedTextEntries = mutableMapOf<String, String>()
            val chapterImageSrcsByPathLower = mutableMapOf<String, List<String>>()
            val diagnostics = ParseDiagnostics()
            var totalTextBytes = 0L

            // Pass 1: read only text/XML resources (OPF, XHTML, container.xml) so we can discover
            // the cover and referenced images without keeping all binary assets in memory.
            requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Unable to read EPUB file"
            }.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val nameLower = normalizeZipEntryNameLower(entry.name)
                            zipEntryNamesLower.add(nameLower)
                            val isTextFile =
                                nameLower.endsWith(".xml") ||
                                    nameLower.endsWith(".xhtml") ||
                                    nameLower.endsWith(".html") ||
                                    nameLower.endsWith(".htm") ||
                                    nameLower.endsWith(".opf") ||
                                    nameLower.endsWith(".ncx")

                            if (isTextFile) {
                                val read = readEntryWithLimitWithStatus(zip, MAX_ENTRY_SIZE)
                                if (read.exceededLimit) {
                                    oversizedTextEntriesLower.add(nameLower)
                                    logWarn("Skipping oversized EPUB text entry: ${entry.name}")
                                }
                                val bytes = read.bytes
                                if (bytes != null && !zipTextEntries.containsKey(nameLower)) {
                                    if (totalTextBytes + bytes.size <= MAX_TOTAL_TEXT_SIZE) {
                                        zipTextEntries[nameLower] = bytes
                                        totalTextBytes += bytes.size
                                    } else {
                                        oversizedTextEntriesLower.add(nameLower)
                                        logWarn(
                                            "Skipping EPUB text entry due to total text budget: ${entry.name}",
                                        )
                                    }
                                } else if (bytes != null && zipTextEntries.containsKey(nameLower)) {
                                    logWarn("Case-colliding EPUB text entry ignored: ${entry.name}")
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            require(zipTextEntries.isNotEmpty()) {
                "EPUB file appears to be empty or corrupted"
            }

            // Parse container.xml to find the OPF file location
            val containerXmlBytes = zipTextEntries["meta-inf/container.xml"]
            val containerXml =
                containerXmlBytes?.let { decodeTextEntry(it) }
            val containerResolution = containerXml?.let(::parseContainerXmlWithResult)
            diagnostics.usedLenientContainerFallback =
                containerResolution?.usedLenientFallback == true
            val opfSelection =
                selectBestOpf(
                    containerCandidates = containerResolution?.candidatePaths.orEmpty(),
                    zipEntryNamesLower = zipEntryNamesLower,
                    zipTextEntries = zipTextEntries,
                )
            val opfKey = opfSelection.path
                ?: throw IllegalArgumentException("Invalid EPUB: cannot find OPF file")
            val opfParseResult = opfSelection.parseResult
                ?: throw IllegalArgumentException("Invalid EPUB: cannot parse OPF file at $opfKey")
            val opfData = opfParseResult.opfData
            diagnostics.usedLenientOpfFallback = opfParseResult.usedLenientFallback
            // Get the base directory of the OPF file for resolving relative paths
            val opfDir = opfKey.substringBeforeLast('/', "")

            val coverPathLower =
                opfData.coverHref?.let { resolveZipEntryKey(opfDir, it, zipEntryNamesLower) }
            val chapterOrderResolution =
                resolveChapterOrder(
                    opfData = opfData,
                    opfDir = opfDir,
                    availableEntriesLower = zipEntryNamesLower,
                    availableTextEntriesLower = zipTextEntries.keys,
                )
            diagnostics.chapterOrderSource = chapterOrderResolution.source
            diagnostics.unresolvedSpineItems = chapterOrderResolution.unresolvedSpineCount
            val orderedChapterPathsLower = chapterOrderResolution.paths

            // Determine which image assets we need (cover + any chapter <img> references).
            val neededImagePathsLower = mutableSetOf<String>()
            val fallbackCoverPathLower =
                if (coverPathLower == null) {
                    selectFallbackCoverPath(zipEntryNamesLower.filter(::isImageEntry))
                } else {
                    null
                }
            (coverPathLower ?: fallbackCoverPathLower)?.let { neededImagePathsLower.add(it) }

            val chapterPathsForImageScan =
                orderedChapterPathsLower.ifEmpty {
                    zipTextEntries.keys
                        .asSequence()
                        .filter(::isChapterCandidateEntry)
                        .sortedWith(::comparePathsNaturally)
                        .toList()
                }

            chapterPathsForImageScan.forEach { chapterPathLower ->
                val html = decodedTextEntry(chapterPathLower, zipTextEntries, decodedTextEntries)
                    ?: return@forEach
                val chapterDir = chapterPathLower.substringBeforeLast('/', "")
                val imageSrcs = extractImageSrcs(html)
                if (imageSrcs.isNotEmpty()) {
                    chapterImageSrcsByPathLower[chapterPathLower] = imageSrcs
                }
                imageSrcs.forEach { rawSrc ->
                    val src = sanitizeSrc(rawSrc)
                    if (src.isBlank()) return@forEach
                    if (src.startsWith("data:", ignoreCase = true)) return@forEach
                    if (src.startsWith("http://", ignoreCase = true) ||
                        src.startsWith("https://", ignoreCase = true)
                    ) {
                        return@forEach
                    }
                    resolveZipEntryKey(chapterDir, src, zipEntryNamesLower)?.let {
                        neededImagePathsLower.add(it)
                    }
                }
            }

            // Pass 2: extract the needed image bytes from the ZIP and persist them as files.
            // This avoids storing large base64 blobs in the DB (which can crash CursorWindow).
            val imageRelativePathByEpubPathLower = mutableMapOf<String, String>()
            var totalImageBytes = 0L
            var coverImage: ByteArray? = null

            val imageDir = File(context.filesDir, "kairo_epub_assets/${bookId.value}/images")
            val canWriteImages = runCatching {
                imageDir.mkdirs() || imageDir.exists()
            }.getOrDefault(false)

            if (neededImagePathsLower.isNotEmpty()) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val nameLower = normalizeZipEntryNameLower(entry.name)
                                if (neededImagePathsLower.contains(nameLower)) {
                                    val maxEntrySize =
                                        if (nameLower == coverPathLower ||
                                            nameLower == fallbackCoverPathLower
                                        ) {
                                            MAX_COVER_IMAGE_ENTRY_SIZE
                                        } else {
                                            MAX_IMAGE_ENTRY_SIZE
                                        }
                                    val bytes = readEntryWithLimit(zip, maxEntrySize)
                                    if (bytes != null) {
                                        totalImageBytes += bytes.size
                                        if (totalImageBytes > MAX_TOTAL_IMAGE_SIZE) break
                                        if (nameLower == coverPathLower ||
                                            nameLower == fallbackCoverPathLower
                                        ) {
                                            coverImage = bytes
                                        }

                                        if (canWriteImages) {
                                            val fileName = buildImageFileName(nameLower)
                                            val file = File(imageDir, fileName)
                                            val wrote =
                                                runCatching {
                                                    file.outputStream().use { it.write(bytes) }
                                                    true
                                                }.getOrDefault(false)
                                            if (wrote) {
                                                imageRelativePathByEpubPathLower[nameLower] =
                                                    "kairo_epub_assets/${bookId.value}/images/$fileName"
                                            }
                                        }
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
            }

            val primaryFallbackBuild =
                buildFallbackChaptersWithResult(
                    zipTextEntries = zipTextEntries,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                    preferredChapterPathsLower = orderedChapterPathsLower,
                    decodedTextEntries = decodedTextEntries,
                    chapterImageSrcsByPathLower = chapterImageSrcsByPathLower,
                )
            diagnostics.navigationFilteredChapters += primaryFallbackBuild.navigationFilteredCount
            diagnostics.navigationFilterSuppressed =
                diagnostics.navigationFilterSuppressed || primaryFallbackBuild.navigationFilterSuppressed

            val parsedChapters =
                primaryFallbackBuild.chapters.ifEmpty {
                    val htmlEntries =
                        readHtmlEntriesFromZip(
                            context = context,
                            uri = uri,
                            skippedEntriesLower = oversizedTextEntriesLower,
                        )
                    val secondaryFallbackBuild =
                        buildFallbackChaptersWithResult(
                            zipTextEntries = htmlEntries,
                            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                            preferredChapterPathsLower = orderedChapterPathsLower,
                            decodedTextEntries = emptyMap(),
                            chapterImageSrcsByPathLower = emptyMap(),
                        )
                    diagnostics.navigationFilteredChapters += secondaryFallbackBuild.navigationFilteredCount
                    diagnostics.navigationFilterSuppressed =
                        diagnostics.navigationFilterSuppressed ||
                            secondaryFallbackBuild.navigationFilterSuppressed
                    secondaryFallbackBuild.chapters
                }

            if (coverImage == null) {
                coverImage =
                    resolveCoverFallbackImage(
                        context = context,
                        uri = uri,
                        coverPathLower = coverPathLower,
                        zipEntryNamesLower = zipEntryNamesLower,
                        parsedChapters = parsedChapters,
                    )
            }

            // Build path to final index map
            val chapterIndexByPathLower = parsedChapters.associate { it.pathLower to it.chapter.index }

            // Second pass: rewrite anchor hrefs and extract links with positions (TOC-like only).
            val chapters = parsedChapters.map { parsed ->
                val html = parsed.chapter.htmlContent
                val hasAnchors = html.indexOf("<a", ignoreCase = true) != -1
                val anchorCount =
                    if (hasAnchors) {
                        countAnchorTags(html)
                    } else {
                        0
                    }
                val shouldExtractLinks = anchorCount > 0

                val rewrittenHtml =
                    if (shouldExtractLinks) {
                        rewriteHtmlAnchorHrefs(
                            html = html,
                            baseDir = parsed.baseDir,
                            chapterIndexByPathLower = chapterIndexByPathLower,
                            currentChapterPath = parsed.pathLower,
                        )
                    } else {
                        html
                    }

                parsed.chapter.copy(
                    htmlContent = rewrittenHtml,
                    plainText = parsed.chapter.plainText,
                )
            }

            // Fallback if no chapters found
            val finalChapters =
                chapters.ifEmpty {
                    listOf(
                        Chapter(
                            index = 0,
                            title = "Content",
                            htmlContent = "<p>No readable content found in this EPUB.</p>",
                            plainText = "No readable content found in this EPUB.",
                            imagePaths = emptyList(),
                        ),
                    )
                }

            logParseDiagnostics(
                bookId = bookId,
                diagnostics = diagnostics,
                chapterCount = finalChapters.size,
            )

            Book(
                id = bookId,
                title = resolveBookTitle(context, uri, opfData.title, sourceDisplayName),
                authors = opfData.authors,
                languageTag = opfData.languageTag,
                coverImage = coverImage,
                chapters = finalChapters,
            )
        }

    override fun supports(extension: String): Boolean = extension == "epub"

    private fun parseContainerXmlWithResult(xml: String): ContainerXmlResolution =
        containerParser.parse(xml)

    private data class ParsedChapter(
        val pathLower: String,
        val baseDir: String,
        val chapter: Chapter,
    )

    private data class NavigationFilterResult(
        val chapters: List<ParsedChapter>,
        val filteredCount: Int,
        val suppressed: Boolean,
    )

    private data class FallbackChapterBuildResult(
        val chapters: List<ParsedChapter>,
        val navigationFilteredCount: Int,
        val navigationFilterSuppressed: Boolean,
    )

    private data class ParseDiagnostics(
        var usedLenientContainerFallback: Boolean = false,
        var usedLenientOpfFallback: Boolean = false,
        var chapterOrderSource: ChapterOrderSource = ChapterOrderSource.ZIP_FALLBACK,
        var unresolvedSpineItems: Int = 0,
        var navigationFilteredChapters: Int = 0,
        var navigationFilterSuppressed: Boolean = false,
    )

    private data class OpfSelection(
        val path: String?,
        val parseResult: OpfParseResult?,
    )

    private data class HrefParts(
        val path: String,
        val suffix: String,
        val fragment: String,
    )

    private fun parseOpfFileWithResult(xml: String): OpfParseResult = opfParser.parseWithResult(xml)

    private fun normalizeZipEntryNameLower(name: String): String =
        name.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)

    private fun extractImageSrcs(html: String): List<String> {
        if (!hasImageReferences(html)) return emptyList()
        val sources = mutableListOf<String>()
        IMAGE_SOURCE_SRC_REGEX.findAll(html).forEach { match ->
            match.groupValues.getOrNull(2)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(sources::add)
        }
        IMAGE_SOURCE_SRCSET_REGEX.findAll(html).forEach { match ->
            val srcset = match.groupValues.getOrNull(2).orEmpty()
            sources += extractSrcsetUrls(srcset)
        }
        SVG_IMAGE_HREF_REGEX.findAll(html).forEach { match ->
            match.groupValues.getOrNull(2)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(sources::add)
        }
        return sources
    }

    private fun decodedTextEntry(
        pathLower: String,
        zipTextEntries: Map<String, ByteArray>,
        decodedTextEntries: MutableMap<String, String>,
    ): String? {
        decodedTextEntries[pathLower]?.let { return it }
        val bytes = zipTextEntries[pathLower] ?: return null
        val decoded = decodeTextEntry(bytes)
        decodedTextEntries[pathLower] = decoded
        return decoded
    }

    private fun hasImageReferences(html: String): Boolean =
        html.indexOf("<img", ignoreCase = true) >= 0 ||
            html.indexOf("<source", ignoreCase = true) >= 0 ||
            html.indexOf("<image", ignoreCase = true) >= 0 ||
            html.indexOf("srcset", ignoreCase = true) >= 0

    private fun sanitizeSrc(src: String): String {
        val trimmed = src.trim()
        if (trimmed.isBlank()) return ""
        return normalizeHrefValue(trimmed)
    }

    private fun normalizeHrefValue(href: String): String {
        return EpubPathResolver.normalizeHrefValue(href)
    }

    private fun splitHrefParts(rawHref: String): HrefParts {
        val decoded = decodeHtmlEntities(rawHref).trim()
        if (decoded.isBlank()) {
            return HrefParts(path = "", suffix = "", fragment = "")
        }
        val queryIndex = decoded.indexOf('?').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val fragmentIndex = decoded.indexOf('#').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val splitIndex = minOf(queryIndex, fragmentIndex)
        val path =
            if (splitIndex == Int.MAX_VALUE) {
                decoded
            } else {
                decoded.take(splitIndex)
            }
        val suffix =
            if (splitIndex == Int.MAX_VALUE) {
                ""
            } else {
                decoded.substring(splitIndex)
            }
        val fragment = decoded.substringAfter('#', "").substringBefore('?')
        return HrefParts(path = path, suffix = suffix, fragment = fragment)
    }

    private fun resolveZipEntryKey(
        baseDir: String,
        rawHref: String,
        availableEntriesLower: Set<String>,
    ): String? = EpubPathResolver.resolveZipEntryKey(baseDir, rawHref, availableEntriesLower)

    private fun resolveBookTitle(
        context: Context,
        uri: Uri,
        opfTitle: String?,
        sourceDisplayName: String?,
    ): String {
        val normalizedOpfTitle = opfTitle?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedOpfTitle != null) return normalizedOpfTitle

        val displayName =
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) {
                            cursor.getString(index)
                        } else {
                            null
                        }
                    }
            }.getOrNull()

        val fallback =
            sourceDisplayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
                ?: displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
        return fallback
            ?: uri.lastPathSegment?.substringBeforeLast('.')?.trim()
            ?: "Unknown Book"
    }

    private fun resolveOpfPath(
        rawPath: String?,
        availableEntriesLower: Set<String>,
    ): String? =
        if (rawPath == null) {
            EpubPathResolver.resolveOpfPath(rawPath = null, availableEntriesLower = availableEntriesLower)
        } else {
            EpubPathResolver.resolveOpfPath(rawPath, availableEntriesLower, allowFallback = false)
        }

    private fun selectBestOpf(
        containerCandidates: List<String>,
        zipEntryNamesLower: Set<String>,
        zipTextEntries: Map<String, ByteArray>,
    ): OpfSelection {
        val resolvedCandidates = LinkedHashSet<String>()
        containerCandidates.forEach { candidate ->
            resolveOpfPath(candidate, zipEntryNamesLower)?.let(resolvedCandidates::add)
        }
        if (resolvedCandidates.isEmpty()) {
            resolveOpfPath(rawPath = null, availableEntriesLower = zipEntryNamesLower)
                ?.let(resolvedCandidates::add)
        }

        var bestPath: String? = null
        var bestResult: OpfParseResult? = null
        var bestScore = Int.MIN_VALUE
        resolvedCandidates.forEach { path ->
            val opfContent = zipTextEntries[path] ?: return@forEach
            val result = runCatching { parseOpfFileWithResult(decodeTextEntry(opfContent)) }.getOrNull() ?: return@forEach
            val score =
                (if (result.opfData.spineItems.isNotEmpty()) 4 else 0) +
                    (if (result.opfData.manifestItems.isNotEmpty()) 2 else 0) +
                    (if (!result.opfData.title.isNullOrBlank()) 1 else 0)
            if (score > bestScore) {
                bestPath = path
                bestResult = result
                bestScore = score
            }
        }
        return OpfSelection(path = bestPath, parseResult = bestResult)
    }

    private fun pathDepth(path: String): Int = EpubPathResolver.pathDepth(path)

    private fun resolveChapterOrder(
        opfData: OpfData,
        opfDir: String,
        availableEntriesLower: Set<String>,
        availableTextEntriesLower: Set<String>,
    ): ChapterOrderResolution =
        EpubChapterOrdering.resolveChapterOrder(
            opfData = opfData,
            opfDir = opfDir,
            availableEntriesLower = availableEntriesLower,
            availableTextEntriesLower = availableTextEntriesLower,
        )

    private fun isLikelyNavigationHtmlPath(pathLower: String): Boolean {
        return EpubChapterOrdering.isLikelyNavigationHtmlPath(pathLower)
    }

    private fun buildFallbackChaptersWithResult(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
        preferredChapterPathsLower: List<String>,
    ): FallbackChapterBuildResult =
        buildFallbackChaptersWithResult(
            zipTextEntries = zipTextEntries,
            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
            preferredChapterPathsLower = preferredChapterPathsLower,
            decodedTextEntries = emptyMap(),
            chapterImageSrcsByPathLower = emptyMap(),
        )

    private fun buildFallbackChaptersWithResult(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
        preferredChapterPathsLower: List<String>,
        decodedTextEntries: Map<String, String>,
        chapterImageSrcsByPathLower: Map<String, List<String>>,
    ): FallbackChapterBuildResult {
        val preferredCandidates =
            preferredChapterPathsLower
                .asSequence()
                .map { it.lowercase(Locale.ROOT) }
                .filter(::isChapterCandidateEntry)
                .filter { zipTextEntries.containsKey(it) }
                .distinct()
                .toList()
        val fallbackCandidatesBase =
            zipTextEntries.keys
                .asSequence()
                .filter(::isChapterCandidateEntry)
                .filterNot { preferredCandidates.contains(it) }
                .sortedWith(::comparePathsNaturally)
                .toList()
        val candidates = preferredCandidates + fallbackCandidatesBase
        if (candidates.isEmpty()) {
            return FallbackChapterBuildResult(
                chapters = emptyList(),
                navigationFilteredCount = 0,
                navigationFilterSuppressed = false,
            )
        }

        val parsed =
            candidates.mapNotNull { pathLower ->
                val chapterContent = zipTextEntries[pathLower] ?: return@mapNotNull null
                val originalHtml = decodedTextEntries[pathLower] ?: decodeTextEntry(chapterContent)
                val originalDocument = parseMarkupDocument(originalHtml)
                val chapterDir = pathLower.substringBeforeLast('/', "")
                val imagePaths = buildChapterImagePaths(
                    html = originalHtml,
                    baseDir = chapterDir,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                    chapterSrcs = chapterImageSrcsByPathLower[pathLower],
                )
                val resolvedHtml = rewriteHtmlImageSrcs(
                    html = originalHtml,
                    baseDir = chapterDir,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                )
                val rawTitle = extractChapterTitle(originalDocument)
                val fileTitle =
                    pathLower
                        .substringAfterLast('/', pathLower)
                        .substringBeforeLast('.')
                val title = sanitizeChapterTitle(rawTitle ?: fileTitle)
                val cleanedHtml =
                    stripLeadingDuplicateTitleBlock(
                        html = stripNoiseTitleBlocks(resolvedHtml),
                        title = title,
                    )
                val plainText =
                    if (cleanedHtml == resolvedHtml) {
                        extractPlainText(originalDocument)
                    } else {
                        extractPlainText(cleanedHtml)
                    }
                val wordCount =
                    if (plainText.length <= MAX_EXACT_WORD_COUNT_CHARS) {
                        countWords(plainText)
                    } else {
                        0
                    }

                if (plainText.isBlank() &&
                    !plainText.contains(PAGE_BREAK_MARKER) &&
                    imagePaths.isEmpty()
                ) {
                    return@mapNotNull null
                }

                ParsedChapter(
                    pathLower = pathLower,
                    baseDir = chapterDir,
                    chapter = Chapter(
                        index = 0,
                        title = title,
                        htmlContent = cleanedHtml,
                        plainText = plainText,
                        imagePaths = imagePaths,
                        wordCount = wordCount,
                    ),
                )
            }

        val navigationFilterResult = filterNavigationLikeParsedChapters(parsed)
        val reIndexedChapters = navigationFilterResult.chapters.mapIndexed { index, entry ->
            entry.copy(chapter = entry.chapter.copy(index = index))
        }
        return FallbackChapterBuildResult(
            chapters = reIndexedChapters,
            navigationFilteredCount = navigationFilterResult.filteredCount,
            navigationFilterSuppressed = navigationFilterResult.suppressed,
        )
    }

    private fun filterNavigationLikeParsedChapters(parsed: List<ParsedChapter>): NavigationFilterResult {
        if (parsed.size <= 1) {
            return NavigationFilterResult(chapters = parsed, filteredCount = 0, suppressed = false)
        }
        val classified = parsed.map { it to isLikelyNavigationChapter(it) }
        val flagged =
            classified.mapNotNull { (chapter, isNavigation) ->
                if (isNavigation) chapter else null
            }
        if (flagged.isEmpty()) {
            return NavigationFilterResult(chapters = parsed, filteredCount = 0, suppressed = false)
        }
        val nonNavigation =
            classified.mapNotNull { (chapter, isNavigation) ->
                if (isNavigation) null else chapter
            }
        val removalRatio = flagged.size.toDouble() / parsed.size.toDouble()
        val hasRetainedSubstantial = nonNavigation.any(::isSubstantialChapter)
        val hasRemovedSubstantial = flagged.any(::isSubstantialChapter)
        val suppress =
            nonNavigation.isEmpty() ||
                removalRatio > MAX_NAV_FILTER_REMOVAL_RATIO ||
                (!hasRetainedSubstantial && hasRemovedSubstantial)
        if (suppress) {
            return NavigationFilterResult(chapters = parsed, filteredCount = 0, suppressed = true)
        }
        return NavigationFilterResult(
            chapters = nonNavigation,
            filteredCount = flagged.size,
            suppressed = false,
        )
    }

    private fun isLikelyNavigationChapter(parsed: ParsedChapter): Boolean {
        val html = parsed.chapter.htmlContent
        val plainText = parsed.chapter.plainText
        val title = parsed.chapter.title.orEmpty()
        val anchorCount = countAnchorTags(html)
        val lowerPath = parsed.pathLower.lowercase(Locale.ROOT)
        val lowerTitle = title.lowercase(Locale.ROOT)
        val lowerPlainPreview =
            plainText
                .take(NAVIGATION_TITLE_SCAN_CHARS)
                .lowercase(Locale.ROOT)

        if (html.indexOf("<nav", ignoreCase = true) >= 0) return true

        val looksLikeTocTitle =
            TOC_TITLE_PATTERNS.any { pattern ->
                pattern.containsMatchIn(lowerTitle) || pattern.containsMatchIn(lowerPlainPreview)
            }
        val fileLooksLikeNav = isLikelyNavigationHtmlPath(lowerPath)
        val estimatedWords = estimateWordCount(parsed.chapter, NAVIGATION_WORD_SCAN_LIMIT)
        val anchorDensity =
            if (estimatedWords > 0) {
                anchorCount.toDouble() / estimatedWords.toDouble()
            } else {
                anchorCount.toDouble()
            }

        if ((fileLooksLikeNav || looksLikeTocTitle) && anchorCount >= 6 && estimatedWords <= 600) {
            return true
        }
        if (anchorCount >= 20 && estimatedWords <= 220) {
            return true
        }
        if (anchorCount >= 12 && anchorDensity >= 0.18 && estimatedWords <= 420) {
            return true
        }
        return false
    }

    private fun isSubstantialChapter(parsed: ParsedChapter): Boolean {
        return estimateWordCount(
            parsed.chapter,
            MIN_SUBSTANTIAL_CHAPTER_WORDS,
        ) >= MIN_SUBSTANTIAL_CHAPTER_WORDS
    }

    private fun estimateWordCount(
        chapter: Chapter,
        limit: Int,
    ): Int {
        if (chapter.wordCount > 0) return chapter.wordCount.coerceAtMost(limit)
        return countWordsUpTo(chapter.plainText, limit)
    }

    private fun countWordsUpTo(
        text: String,
        limit: Int,
    ): Int {
        if (limit <= 0 || text.isEmpty()) return 0
        var count = 0
        var inWord = false
        var index = 0
        while (index < text.length && count < limit) {
            val codePoint = Character.codePointAt(text, index)
            val charCount = Character.charCount(codePoint)
            when {
                Character.isLetterOrDigit(codePoint) -> {
                    if (!inWord) count += 1
                    inWord = true
                }
                isWordApostrophe(codePoint) && inWord -> {
                    val nextIndex = index + charCount
                    inWord =
                        nextIndex < text.length &&
                        Character.isLetterOrDigit(Character.codePointAt(text, nextIndex))
                }
                else -> inWord = false
            }
            index += charCount
        }
        return count
    }

    private fun isWordApostrophe(codePoint: Int): Boolean =
        codePoint == '\''.code || codePoint == '\u2019'.code

    private fun logParseDiagnostics(
        bookId: BookId,
        diagnostics: ParseDiagnostics,
        chapterCount: Int,
    ) {
        logInfo(
            "EPUB parse diagnostics book=${bookId.value} chapters=$chapterCount " +
                "order=${diagnostics.chapterOrderSource} unresolvedSpine=${diagnostics.unresolvedSpineItems} " +
                "lenientContainer=${diagnostics.usedLenientContainerFallback} " +
                "lenientOpf=${diagnostics.usedLenientOpfFallback} " +
                "navFiltered=${diagnostics.navigationFilteredChapters} " +
                "navFilterSuppressed=${diagnostics.navigationFilterSuppressed}",
        )
    }

    private fun isChapterCandidateEntry(pathLower: String): Boolean {
        return EpubChapterOrdering.isChapterCandidateEntry(pathLower)
    }

    private fun readHtmlEntriesFromZip(
        context: Context,
        uri: Uri,
        skippedEntriesLower: Set<String> = emptySet(),
    ): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val nameLower = normalizeZipEntryNameLower(entry.name)
                        if (isChapterCandidateEntry(nameLower) && !skippedEntriesLower.contains(nameLower)) {
                            val bytes = readEntryWithLimit(zip, MAX_ENTRY_SIZE)
                            if (bytes != null) {
                                entries[nameLower] = bytes
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return entries
    }

    private fun resolveCoverFallbackImage(
        context: Context,
        uri: Uri,
        coverPathLower: String?,
        zipEntryNamesLower: Set<String>,
        parsedChapters: List<ParsedChapter>,
    ): ByteArray? {
        if (coverPathLower != null) {
            val bytes = readZipEntryBytes(context, uri, coverPathLower)
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }

        val chapterImagePath = parsedChapters.firstOrNull()?.chapter?.imagePaths?.firstOrNull()
        if (chapterImagePath != null) {
            val file = File(context.filesDir, chapterImagePath)
            if (file.exists()) {
                val sizeOk = file.length() <= MAX_COVER_IMAGE_ENTRY_SIZE
                if (sizeOk) {
                    val bytes = runCatching { file.readBytes() }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) return bytes
                }
            }
        }

        val coverCandidates = zipEntryNamesLower.filter(::isImageEntry)
        val fallback = selectFallbackCoverPath(coverCandidates)
        if (fallback != null) {
            val bytes = readZipEntryBytes(context, uri, fallback)
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }

        return null
    }

    private fun selectFallbackCoverPath(imagePathsLower: Collection<String>): String? {
        if (imagePathsLower.isEmpty()) return null

        return imagePathsLower.minWithOrNull(
            compareBy(
                ::coverFallbackPriority,
                ::pathDepth,
                { it.length },
                { it },
            ),
        )
    }

    private fun coverFallbackPriority(pathLower: String): Int {
        val fileName = pathLower.substringAfterLast('/')
        return when {
            fileName.contains("cover") -> 0
            fileName.contains("front") -> 1
            fileName.contains("title") -> 2
            else -> 3
        }
    }

    private fun comparePathsNaturally(
        left: String,
        right: String,
    ): Int = EpubChapterOrdering.comparePathsNaturally(left, right)

    private fun readZipEntryBytes(
        context: Context,
        uri: Uri,
        targetLower: String,
    ): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val nameLower = normalizeZipEntryNameLower(entry.name)
                        if (nameLower == targetLower) {
                            return readEntryWithLimit(zip, MAX_COVER_IMAGE_ENTRY_SIZE)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun isImageEntry(pathLower: String): Boolean {
        return pathLower.endsWith(".jpg") ||
            pathLower.endsWith(".jpeg") ||
            pathLower.endsWith(".png") ||
            pathLower.endsWith(".gif") ||
            pathLower.endsWith(".webp") ||
            pathLower.endsWith(".svg")
    }

    private fun buildChapterImagePaths(
        html: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
        chapterSrcs: List<String>? = null,
    ): List<String> {
        val unique = LinkedHashSet<String>()
        val srcs = chapterSrcs ?: extractImageSrcs(html)
        for (rawSrc in srcs) {
            val hrefParts = splitHrefParts(rawSrc)
            val src = sanitizeSrc(hrefParts.path)
            if (src.isBlank()) continue
            if (src.startsWith("data:", ignoreCase = true)) continue
            if (src.startsWith("http://", ignoreCase = true) ||
                src.startsWith("https://", ignoreCase = true)
            ) {
                continue
            }

            val resolvedLower =
                resolveZipEntryKey(baseDir, src, imageRelativePathByEpubPathLower.keys)
                    ?: continue
            val relativePath = imageRelativePathByEpubPathLower[resolvedLower] ?: continue
            unique.add(relativePath)
        }
        return unique.toList()
    }

    private fun buildImageFileName(epubPathLower: String): String {
        val extRaw = epubPathLower.substringAfterLast('.', missingDelimiterValue = "")
        val ext = extRaw.take(10).filter { it.isLetterOrDigit() }
        val base = UUID.nameUUIDFromBytes(epubPathLower.toByteArray(Charsets.UTF_8)).toString()
        return if (ext.isNotEmpty()) "img_$base.$ext" else "img_$base"
    }

    private fun rewriteHtmlImageSrcs(
        html: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): String {
        if (imageRelativePathByEpubPathLower.isEmpty() || !hasImageReferences(html)) {
            return html
        }

        val rewrittenImgSrc =
            IMG_SRC_REWRITE_REGEX.replace(html) { match ->
                val rewritten = rewriteImageReference(match.groupValues[2], baseDir, imageRelativePathByEpubPathLower)
                if (rewritten == null) match.value else "${match.groupValues[1]}$rewritten${match.groupValues[3]}"
            }
        val rewrittenSvgHref =
            SVG_IMAGE_REWRITE_REGEX.replace(rewrittenImgSrc) { match ->
                val rewritten = rewriteImageReference(match.groupValues[2], baseDir, imageRelativePathByEpubPathLower)
                if (rewritten == null) match.value else "${match.groupValues[1]}$rewritten${match.groupValues[3]}"
            }
        return SRCSET_REWRITE_REGEX.replace(rewrittenSvgHref) { match ->
            val rewrittenSrcset =
                rewriteSrcset(
                    srcset = match.groupValues[2],
                    baseDir = baseDir,
                    imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
                )
            "${match.groupValues[1]}$rewrittenSrcset${match.groupValues[3]}"
        }
    }

    private fun rewriteImageReference(
        rawSrc: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): String? {
        val hrefParts = splitHrefParts(rawSrc)
        val src = sanitizeSrc(hrefParts.path)
        if (src.isBlank()) return null
        if (src.startsWith("data:", ignoreCase = true)) return null
        if (src.startsWith("http://", ignoreCase = true) ||
            src.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        if (src.startsWith("kairo_epub_assets/")) return null

        val resolvedLower =
            resolveZipEntryKey(baseDir, src, imageRelativePathByEpubPathLower.keys)
                ?: return null
        val relativePath =
            imageRelativePathByEpubPathLower[resolvedLower] ?: return null
        return relativePath + hrefParts.suffix
    }

    private fun rewriteSrcset(
        srcset: String,
        baseDir: String,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): String {
        if (srcset.isBlank()) return srcset
        return srcset
            .split(',')
            .joinToString(", ") { descriptor ->
                val trimmed = descriptor.trim()
                if (trimmed.isBlank()) return@joinToString trimmed
                val parts = trimmed.split(Regex("\\s+"), limit = 2)
                val candidate = parts.firstOrNull().orEmpty()
                val suffix = if (parts.size > 1) " ${parts[1]}" else ""
                val rewritten = rewriteImageReference(candidate, baseDir, imageRelativePathByEpubPathLower)
                (rewritten ?: candidate) + suffix
            }
    }

    private fun extractSrcsetUrls(srcset: String): List<String> {
        if (srcset.isBlank()) return emptyList()
        return srcset
            .split(',')
            .mapNotNull { descriptor ->
                val trimmed = descriptor.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                trimmed
                    .split(Regex("\\s+"), limit = 2)
                    .firstOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
    }

    /**
     * Extracts plain text from HTML/XHTML content.
     */
    private fun extractPlainText(html: String): String =
        extractPlainText(parseMarkupDocument(html))

    private fun extractPlainText(document: EpubMarkupDocument): String =
        EpubMarkupInspector.renderPlainText(document)
            // Decode common HTML entities
            .let(::decodeHtmlEntities)
            // Clean up whitespace
            .replace(HORIZONTAL_WHITESPACE_REGEX, " ")
            .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")
            .let(::trimPlainTextPreservingPageBreak)

    private fun trimPlainTextPreservingPageBreak(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.isEmpty() && text.contains(PAGE_BREAK_MARKER)) {
            PAGE_BREAK_MARKER
        } else {
            trimmed
        }
    }

    /**
     * Extracts chapter title from HTML content.
     */
    private fun extractChapterTitle(html: String): String? =
        extractChapterTitle(parseMarkupDocument(html))

    private fun extractChapterTitle(document: EpubMarkupDocument): String? {
        val titleText = EpubMarkupInspector.firstTextInTags(document, setOf("title"))
        if (titleText != null) {
            val title =
                decodeHtmlEntities(titleText)
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
            if (title.isNotBlank() && !title.equals("untitled", ignoreCase = true)) {
                return title
            }
        }

        val headingText = EpubMarkupInspector.firstTextInTags(document, setOf("h1", "h2", "h3"))
        if (headingText != null) {
            val heading =
                decodeHtmlEntities(headingText)
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
            if (heading.isNotBlank()) {
                return heading.take(100) // Limit title length
            }
        }

        return null
    }

    private fun sanitizeChapterTitle(title: String?): String? {
        val trimmed = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (isLikelyFileLabel(trimmed)) null else trimmed
    }

    private fun stripNoiseTitleBlocks(html: String): String {
        if (html.isBlank()) return html
        var result = html
        repeat(2) {
            val match = BLOCK_ELEMENT_REGEX.find(result) ?: return@repeat
            val leading = result.take(match.range.first)
            if (visibleText(leading).isNotBlank()) return@repeat
            val inner = match.groupValues[2]
            val text = visibleText(inner)
            if (text.length <= MAX_NOISE_TITLE_LENGTH && isLikelyFileLabel(text)) {
                result = result.removeRange(match.range.first, match.range.last + 1)
            } else {
                return@repeat
            }
        }
        return result
    }

    private fun stripLeadingDuplicateTitleBlock(
        html: String,
        title: String?,
    ): String {
        if (html.isBlank()) return html
        val normalizedTitle = normalizeTitleForComparison(title ?: return html)
        if (normalizedTitle.isBlank()) return html

        return stripMatchingLeadingBlock(html, HEADING_BLOCK_ELEMENT_REGEX, normalizedTitle)
            ?: stripMatchingLeadingBlock(html, PARAGRAPH_BLOCK_ELEMENT_REGEX, normalizedTitle)
            ?: stripMatchingLeadingBlock(html, DIV_BLOCK_ELEMENT_REGEX, normalizedTitle)
            ?: html
    }

    private fun stripMatchingLeadingBlock(
        html: String,
        blockRegex: Regex,
        normalizedTitle: String,
    ): String? {
        val match = blockRegex.find(html) ?: return null
        val leading = html.take(match.range.first)
        if (visibleTextIgnoringMetadata(leading).isNotBlank()) return null
        val blockText = visibleText(match.groupValues[1])
        if (normalizeTitleForComparison(blockText) != normalizedTitle) return null
        return html.removeRange(match.range.first, match.range.last + 1)
    }

    private fun visibleText(htmlFragment: String): String =
        decodeHtmlEntities(htmlFragment.replace(ALL_TAGS_REGEX, " "))
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun visibleTextIgnoringMetadata(htmlFragment: String): String =
        visibleText(htmlFragment.replace(HEAD_TAG_REGEX, " "))

    private fun normalizeTitleForComparison(text: String): String =
        visibleText(text)
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun isLikelyFileLabel(text: String): Boolean {
        val normalized = normalizeNoiseLabel(text)
        if (normalized.isBlank()) return false
        val compact = normalized.replace(Regex("[\\s_-]+"), "")
        val numberedMatch = FILE_LABEL_WITH_NUMBER_REGEX.matchEntire(compact)
        if (numberedMatch != null) {
            val zeros = numberedMatch.groupValues[2]
            val digits = numberedMatch.groupValues[3]
            if (zeros.isNotEmpty() || digits.length >= 3) return true
        }
        return GENERIC_FILE_LABEL_REGEX.matches(compact)
    }

    private fun normalizeNoiseLabel(text: String): String {
        val trimmed = text.trim().lowercase(Locale.ROOT)
        if (trimmed.isBlank()) return ""
        return trimmed.substringBeforeLast('.', trimmed)
    }

    /**
     * Reads a ZIP entry with a size limit using buffered reading.
     * Returns null if the entry exceeds the size limit.
     * This prevents OOM by not loading huge entries all at once.
     */
    private fun readEntryWithLimit(
        zip: ZipInputStream,
        maxSize: Int,
    ): ByteArray? = readEntryWithLimitWithStatus(zip, maxSize).bytes

    private class LimitedReadResult(
        val bytes: ByteArray?,
        val exceededLimit: Boolean,
    )

    private fun readEntryWithLimitWithStatus(
        zip: ZipInputStream,
        maxSize: Int,
    ): LimitedReadResult {
        val buffer = ByteArray(BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        var totalRead = 0

        return try {
            var bytesRead: Int
            while (zip.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxSize) {
                    while (zip.read(buffer) != -1) {
                        // Drain remaining bytes for this entry before moving to next entry.
                    }
                    return LimitedReadResult(bytes = null, exceededLimit = true)
                }
                output.write(buffer, 0, bytesRead)
            }
            LimitedReadResult(bytes = output.toByteArray(), exceededLimit = false)
        } catch (e: IOException) {
            logWarn("Failed to read EPUB entry", e)
            LimitedReadResult(bytes = null, exceededLimit = false)
        }
    }

    private fun logInfo(message: String) {
        EpubLogger.info(TAG, message)
    }

    private fun logWarn(
        message: String,
        error: Throwable? = null,
    ) {
        EpubLogger.warn(TAG, message, error)
    }

    /**
     * Rewrites internal anchor hrefs to kairo://chapter/X format.
     * This enables the tokenizer to identify clickable links.
     */
    private fun rewriteHtmlAnchorHrefs(
        html: String,
        baseDir: String,
        chapterIndexByPathLower: Map<String, Int>,
        currentChapterPath: String? = null,
    ): String {
        return ANCHOR_HREF_REGEX.replace(html) { match ->
            val prefix = match.groupValues[1]
            val href = decodeHtmlEntities(match.groupValues[2]).trim()
            val suffix = match.groupValues[3]

            // Skip external links and data URIs
            if (href.startsWith("http://", true) ||
                href.startsWith("https://", true) ||
                href.startsWith("mailto:", true) ||
                href.startsWith("data:", true) ||
                href.startsWith("kairo://", true)
            ) {
                return@replace match.value
            }

            val hrefParts = splitHrefParts(href)
            val path = decodeUrlPath(hrefParts.path).trim()
            val fragment = decodeUrlPath(hrefParts.fragment).trim()

            // Determine target path
            val targetPath =
                when {
                    path.isNotBlank() ->
                        resolveZipEntryKey(baseDir, path, chapterIndexByPathLower.keys)
                    fragment.isNotBlank() && currentChapterPath != null ->
                        currentChapterPath.lowercase(Locale.ROOT)
                    else -> null
                } ?: return@replace match.value

            val chapterIndex = chapterIndexByPathLower[targetPath] ?: return@replace match.value

            val fragmentSuffix =
                fragment
                    .takeIf { it.isNotBlank() }
                    ?.let { "#${EpubPathResolver.encodeUrlPath(it)}" }
                    .orEmpty()
            "${prefix}kairo://chapter/$chapterIndex$fragmentSuffix${suffix}"
        }
    }

    private fun countAnchorTags(html: String): Int {
        if (html.indexOf("<a", ignoreCase = true) < 0) return 0
        return ANCHOR_TAG_REGEX
            .findAll(html)
            .take(MAX_LINKS_PER_CHAPTER + 1)
            .count()
    }

    private fun parseMarkupDocument(html: String): EpubMarkupDocument {
        val sanitized =
            html
                .replace(HTML_COMMENT_REGEX, "")
                .replace(SCRIPT_TAG_REGEX, "")
                .replace(STYLE_TAG_REGEX, "")
        return markupParser.parse(sanitized)
    }

    private fun decodeHtmlEntities(input: String): String = EpubHtmlEntities.decode(input)

    private fun decodeUrlPath(input: String): String = EpubPathResolver.decodeUrlPath(input)

    private fun decodeTextEntry(bytes: ByteArray): String = EpubTextDecoder.decodeTextEntry(bytes)
}
