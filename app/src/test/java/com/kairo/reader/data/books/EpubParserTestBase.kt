package com.kairo.reader.data.books

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.data.books.epub.EpubChapterOrdering
import com.kairo.reader.data.books.epub.EpubContainerParser
import com.kairo.reader.data.books.epub.EpubOpfParser
import com.kairo.reader.data.books.epub.EpubPathResolver
import com.kairo.reader.data.books.epub.OpfData

internal abstract class EpubParserTestBase {
    protected val parser = EpubBookParser(TestDispatchers)
    protected val contentRewriter = EpubContentRewriter()
    protected val navigationClassifier = EpubNavigationClassifier(contentRewriter)
    protected val chapterBuilder = EpubChapterBuilder(contentRewriter, navigationClassifier)
    protected val opfParser = EpubOpfParser()
    protected val packageSelector = EpubPackageSelector(opfParser)

    protected fun invokeResolveOpfPath(
        rawPath: String?,
        availableEntriesLower: Set<String>,
    ): String? = packageSelector.resolveOpfPath(rawPath, availableEntriesLower)

    protected fun invokeSelectBestOpfPath(
        containerCandidates: List<String>,
        zipEntryNamesLower: Set<String>,
        zipTextEntries: Map<String, ByteArray>,
    ): String? =
        packageSelector.selectBest(containerCandidates, zipEntryNamesLower, zipTextEntries).path

    protected fun invokeRewriteHtmlAnchorHrefs(
        html: String,
        baseDir: String,
        chapterIndexByPathLower: Map<String, Int>,
        currentChapterPath: String?,
    ): String =
        contentRewriter.rewriteHtmlAnchorHrefs(
            html,
            baseDir,
            chapterIndexByPathLower,
            currentChapterPath,
        )

    protected fun invokeBuildFallbackChapters(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
    ): List<ParsedChapter> {
        return invokeBuildFallbackChapters(
            zipTextEntries = zipTextEntries,
            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
            preferredChapterPathsLower = emptyList(),
        )
    }

    protected fun invokeBuildFallbackChapters(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
        preferredChapterPathsLower: List<String>,
    ): List<ParsedChapter> {
        return invokeBuildFallbackChaptersWithResult(
            zipTextEntries = zipTextEntries,
            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
            preferredChapterPathsLower = preferredChapterPathsLower,
        ).chapters
    }

    protected fun invokeBuildFallbackChaptersWithResult(
        zipTextEntries: Map<String, ByteArray>,
        imageRelativePathByEpubPathLower: Map<String, String>,
        preferredChapterPathsLower: List<String>,
        preservedNavigationPathsLower: Set<String> = emptySet(),
        htmlOverridesByPathLower: Map<String, String> = emptyMap(),
    ): FallbackChapterBuildResult =
        chapterBuilder.buildWithResult(
            zipTextEntries = zipTextEntries,
            imageRelativePathByEpubPathLower = imageRelativePathByEpubPathLower,
            preferredChapterPathsLower = preferredChapterPathsLower,
            preservedNavigationPathsLower = preservedNavigationPathsLower,
            htmlOverridesByPathLower = htmlOverridesByPathLower,
        )

    protected fun parsedChapterPath(parsedChapter: ParsedChapter): String = parsedChapter.pathLower

    protected fun parsedChapter(parsedChapter: ParsedChapter): Chapter = parsedChapter.chapter

    protected fun invokeResolveChapterPathsForReadingOrder(
        opfData: OpfData,
        opfDir: String,
        availableEntriesLower: Set<String>,
        availableTextEntriesLower: Set<String>,
    ): List<String> =
        EpubChapterOrdering.resolveChapterOrder(
            opfData,
            opfDir,
            availableEntriesLower,
            availableTextEntriesLower,
        ).paths

    protected fun invokeResolveZipEntryKey(
        baseDir: String,
        rawHref: String,
        availableEntriesLower: Set<String>,
    ): String? = EpubPathResolver.resolveZipEntryKey(baseDir, rawHref, availableEntriesLower)

    protected fun invokeParseOpfFileWithResultUsedLenient(xml: String): Boolean =
        opfParser.parseWithResult(xml).usedLenientFallback

    protected fun invokeParseOpfData(xml: String): OpfData = opfParser.parseWithResult(xml).opfData

    protected fun invokeParseContainerXmlWithResult(xml: String): Pair<String, Boolean> {
        val result = EpubContainerParser().parse(xml)
        return result.path to result.usedLenientFallback
    }

    protected fun invokeSelectFallbackCoverPath(
        imagePathsLower: Collection<String>,
    ): String? = EpubCoverSelector.select(imagePathsLower)
}
