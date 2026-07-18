package com.kairo.reader.data.books

import com.kairo.reader.data.books.epub.EpubOpfParser
import com.kairo.reader.data.books.epub.EpubPathResolver
import com.kairo.reader.data.books.epub.EpubTextDecoder
import com.kairo.reader.data.books.epub.OpfParseResult

internal data class OpfSelection(val path: String?, val parseResult: OpfParseResult?,)

internal class EpubPackageSelector(private val opfParser: EpubOpfParser,) {
    fun resolveOpfPath(
        rawPath: String?,
        availableEntriesLower: Set<String>,
    ): String? =
        if (rawPath == null) {
            EpubPathResolver.resolveOpfPath(rawPath = null, availableEntriesLower = availableEntriesLower)
        } else {
            EpubPathResolver.resolveOpfPath(rawPath, availableEntriesLower, allowFallback = false)
        }

    fun selectBest(
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
            val result =
                runCatching { opfParser.parseWithResult(EpubTextDecoder.decodeTextEntry(opfContent)) }.getOrNull() ?: return@forEach
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
}
