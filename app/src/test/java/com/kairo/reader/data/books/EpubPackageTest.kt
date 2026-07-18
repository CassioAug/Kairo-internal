package com.kairo.reader.data.books

import com.kairo.reader.data.books.epub.EpubPathResolver
import com.kairo.reader.data.books.epub.EpubTextDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EpubPackageTest : EpubParserTestBase() {
    @Test
    fun normalizeContainerPathHandlesEncodedAndSlashedPaths() {
        val resolved = EpubPathResolver.normalizeContainerPath("/OEBPS/content%2Eopf")

        assertEquals("OEBPS/content.opf", resolved)
    }

    @Test
    fun normalizeContainerPathPreservesPlusCharacters() {
        val resolved = EpubPathResolver.normalizeContainerPath("/OEBPS/Book+One.opf")

        assertEquals("OEBPS/Book+One.opf", resolved)
    }

    @Test
    fun normalizeZipEntryNameLowerHandlesLeadingSlashAndBackslashes() {
        val normalized = "\\OEBPS\\Images\\Cover.JPG".replace('\\', '/').trimStart('/').lowercase()

        assertEquals("oebps/images/cover.jpg", normalized)
    }

    @Test
    fun decodeTextEntryRespectsXmlEncoding() {
        val xml =
            "<?xml version=\"1.0\" encoding=\"UTF-16LE\"?><html><body>Hi</body></html>"
                .toByteArray(Charsets.UTF_16LE)

        val decoded = EpubTextDecoder.decodeTextEntry(xml)

        assertTrue(decoded.contains("Hi"))
    }

    @Test
    fun parseOpfFileHandlesNamespacedManifestAndSpine() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Sample</dc:title>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
                <itemref idref="c2" />
              </spine>
            </package>
            """.trimIndent()

        val opfData = invokeParseOpfData(xml)
        val manifest = opfData.manifest
        val spineItems = opfData.spineItems

        assertTrue(manifest.containsKey("c1"))
        assertEquals(2, spineItems.size)
    }

    @Test
    fun parseOpfFileIgnoresItemTagsOutsideManifestSection() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata>
                <meta name="cover" content="noise"/>
                <custom:item xmlns:custom="urn:test" id="noise" href="noise.xhtml" media-type="application/xhtml+xml"/>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
                <itemref idref="c2" />
              </spine>
            </package>
            """.trimIndent()

        val opfData = invokeParseOpfData(xml)
        val manifest = opfData.manifest

        assertEquals(2, manifest.size)
        assertEquals("chapter1.xhtml", manifest["c1"])
        assertEquals("chapter2.xhtml", manifest["c2"])
    }

    @Test
    fun parseOpfFileUsesLenientFallbackOnMalformedXml() {
        val malformedXml =
            """
            <package>
              <metadata><dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">Broken</dc:title></metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml">
                <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml">
              </manifest>
              <spine>
                <itemref idref="c2">
                <itemref idref="c1">
              </spine>
            """.trimIndent()

        val opfData = invokeParseOpfData(malformedXml)
        val manifest = opfData.manifest
        val spineItems = opfData.spineItems

        assertEquals("chapter1.xhtml", manifest["c1"])
        assertEquals("chapter2.xhtml", manifest["c2"])
        assertEquals(2, spineItems.size)
    }

    @Test
    fun parseOpfFileWithResultReportsLenientFallbackUsage() {
        val malformedXml =
            """
            <package>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml">
              </manifest>
            """.trimIndent()

        val usedLenientFallback = invokeParseOpfFileWithResultUsedLenient(malformedXml)

        assertTrue(usedLenientFallback)
    }

    @Test
    fun parseOpfFileLenientExtractsManifestItemsOnlyFromManifestSection() {
        val xml =
            """
            <package>
              <metadata>
                <item id="noise" href="noise.xhtml" media-type="application/xhtml+xml" />
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val opfData = opfParser.parseLenient(xml)
        val manifest = opfData.manifest

        assertEquals(1, manifest.size)
        assertEquals("chapter1.xhtml", manifest["c1"])
        assertNull(manifest["noise"])
    }

    @Test
    fun parseOpfFileLenientSupportsNamespacedItemAndItemrefTags() {
        val xml =
            """
            <opf:package xmlns:opf="http://www.idpf.org/2007/opf">
              <opf:manifest>
                <opf:item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
              </opf:manifest>
              <opf:spine>
                <opf:itemref idref="c1" />
              </opf:spine>
            </opf:package>
            """.trimIndent()

        val opfData = opfParser.parseLenient(xml)
        val manifest = opfData.manifest
        val spineItems = opfData.spineItems

        assertEquals("chapter1.xhtml", manifest["c1"])
        assertEquals(1, spineItems.size)
    }

    @Test
    fun parseOpfFileLenientDoesNotSynthesizeSpineForNonPackageXml() {
        val xml =
            """
            <root>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
            </root>
            """.trimIndent()

        val opfData = opfParser.parseLenient(xml)
        val spineItems = opfData.spineItems

        assertTrue(spineItems.isEmpty())
    }

    @Test
    fun parseContainerXmlPrefersOebpsPackageMediaType() {
        val containerXml =
            """
            <container version="1.0"
              xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="alt/book.opf" media-type="text/plain"/>
                <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent()

        val resolved = invokeParseContainerXmlWithResult(containerXml).first

        assertEquals("OPS/package.opf", resolved)
    }

    @Test
    fun parseContainerXmlWithResultReportsLenientFallbackUsage() {
        val malformedXml = "<container><rootfiles><rootfile full-path='OPS/content.opf'"

        val (resolvedPath, usedLenientFallback) = invokeParseContainerXmlWithResult(malformedXml)

        assertEquals("OPS/content.opf", resolvedPath)
        assertTrue(usedLenientFallback)
    }

    @Test
    fun selectBestOpfPrefersCandidateWithReadableSpineAndManifest() {
        val invalidOpf =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Invalid</dc:title>
              </metadata>
            </package>
            """.trimIndent()
        val validOpf =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val path =
            invokeSelectBestOpfPath(
                containerCandidates = listOf("OPS/invalid.opf", "OEBPS/content.opf"),
                zipEntryNamesLower = setOf("ops/invalid.opf", "oebps/content.opf"),
                zipTextEntries =
                mapOf(
                    "ops/invalid.opf" to invalidOpf.toByteArray(),
                    "oebps/content.opf" to validOpf.toByteArray(),
                ),
            )

        assertEquals("oebps/content.opf", path)
    }

    @Test
    fun resolveOpfPathPrefersContentOpfFallback() {
        val entries =
            linkedSetOf(
                "oebps/package.opf",
                "opf/content.opf",
                "meta-inf/container.xml",
            )

        val resolved = invokeResolveOpfPath(null, entries)

        assertEquals("opf/content.opf", resolved)
    }

    @Test
    fun resolveChapterPathsForReadingOrderPrefersSpineOnlyWhenPresent() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml" />
                <item id="c3" href="text/ch3.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c2" />
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val opfData = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
                "oebps/text/ch3.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/ch2.xhtml",
                "oebps/text/ch1.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderPreservesDuplicateSpineItems() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
                <itemref idref="c2" />
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val opfData = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
                "oebps/text/ch1.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderAppendsManifestRemainderWhenSpineIsPartial() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml" />
                <item id="c3" href="text/ch3.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c2" />
                <itemref idref="missing" />
              </spine>
            </package>
            """.trimIndent()

        val opfData = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
                "oebps/text/ch3.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/ch2.xhtml",
                "oebps/text/ch1.xhtml",
                "oebps/text/ch3.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderSkipsNavDocuments() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav" />
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="nav" />
                <itemref idref="c1" />
                <itemref idref="c2" />
              </spine>
            </package>
            """.trimIndent()

        val opfData = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/nav.xhtml",
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/text/ch1.xhtml",
                "oebps/text/ch2.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderKeepsTocWhenNotMarkedNav() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="tocdoc" href="toc.xhtml" media-type="application/xhtml+xml" />
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="tocdoc" />
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val opfData = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/toc.xhtml",
                "oebps/text/ch1.xhtml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/toc.xhtml",
                "oebps/text/ch1.xhtml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveChapterPathsForReadingOrderFallsBackToXmlEntries() {
        val xml = "<package xmlns=\"http://www.idpf.org/2007/opf\"></package>"
        val opfData = invokeParseOpfData(xml)
        val available =
            linkedSetOf(
                "oebps/chapter1.xml",
                "oebps/chapter2.xml",
                "meta-inf/container.xml",
            )

        val resolved =
            invokeResolveChapterPathsForReadingOrder(
                opfData = opfData,
                opfDir = "oebps",
                availableEntriesLower = available,
                availableTextEntriesLower = available,
            )

        assertEquals(
            listOf(
                "oebps/chapter1.xml",
                "oebps/chapter2.xml",
            ),
            resolved,
        )
    }

    @Test
    fun resolveZipEntryKeyDoesNotFallbackToRootForRelativeHref() {
        val resolved =
            invokeResolveZipEntryKey(
                baseDir = "oebps",
                rawHref = "text/chapter1.xhtml",
                availableEntriesLower = setOf("text/chapter1.xhtml"),
            )

        assertNull(resolved)
    }

    @Test
    fun resolveZipEntryKeyFallsBackToRootForAbsoluteHref() {
        val resolved =
            invokeResolveZipEntryKey(
                baseDir = "oebps",
                rawHref = "/text/chapter1.xhtml",
                availableEntriesLower = setOf("text/chapter1.xhtml"),
            )

        assertEquals("text/chapter1.xhtml", resolved)
    }
}
