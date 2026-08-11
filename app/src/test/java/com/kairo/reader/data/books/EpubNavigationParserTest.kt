package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubNavigationParserTest {
    private val parser = EpubNavigationParser()

    @Test
    fun parsesNestedEpubThreeTableOfContents() {
        val document =
            """
            <html xmlns="http://www.w3.org/1999/xhtml"
                  xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <nav epub:type="landmarks">
                  <ol><li><a href="cover.xhtml">Cover</a></li></ol>
                </nav>
                <nav epub:type="toc">
                  <h1>Contents &amp; Chapters</h1>
                  <ol>
                    <li><a href="text/front.xhtml?edition=1&amp;view=full">Preface &amp; Notes</a></li>
                    <li>
                      <span>Part One</span>
                      <ol>
                        <li><a href="text/story.xhtml#chapter-1">The Arrival</a></li>
                        <li><a href="text/story.xhtml#chapter-2">The Crossing</a></li>
                      </ol>
                    </li>
                  </ol>
                </nav>
                <nav epub:type="page-list">
                  <ol>
                    <li><a href="text/story.xhtml#page-1">1</a></li>
                    <li><a href="text/story.xhtml#page-2">2</a></li>
                    <li><a href="text/story.xhtml#page-3">3</a></li>
                  </ol>
                </nav>
              </body>
            </html>
            """.trimIndent()

        val result = parser.parse(document, isNcx = false)
        val entries = result.references

        assertEquals(EpubNavigationProvenance.EXPLICIT_TOC, result.provenance)
        assertTrue(result.navigationOnly)
        assertTrue(result.repairEligible)
        assertEquals(listOf("Preface & Notes", "Part One", "The Arrival", "The Crossing"), entries.map { it.label })
        assertEquals(listOf(0, 0, 1, 1), entries.map { it.depth })
        assertEquals("text/front.xhtml?edition=1&amp;view=full", entries[0].href)
        assertNull(entries[1].href)
        assertEquals("text/story.xhtml#chapter-2", entries[3].href)

        val readerHtml = requireNotNull(result.readerHtml)
        assertTrue(readerHtml.contains(EpubReaderNavigationContent.MARKER))
        assertTrue(readerHtml.contains("<h1>Contents &amp; Chapters</h1>"))
        assertTrue(readerHtml.contains("href=\"text/front.xhtml?edition=1&amp;view=full\""))
        assertTrue(readerHtml.contains("<span>Part One</span>"))
        assertTrue(readerHtml.contains("data-kairo-depth=\"1\""))
        assertFalse(readerHtml.contains("Cover"))
        assertFalse(readerHtml.contains("page-1"))
        assertFalse(readerHtml.contains(">1</a>"))
    }

    @Test
    fun fallsBackToFirstNavigationAndEscapesAuthoredContent() {
        val document =
            """
            <html>
              <body>
                <nav aria-label="Guide &amp; overview">
                  <h2>Read &lt;safely&gt;</h2>
                  <ol>
                    <li><a href="chapter.xhtml?x=1&amp;y=2">A &amp; B</a></li>
                    <li><span>Group &quot;One&quot;</span></li>
                  </ol>
                </nav>
                <nav role="doc-landmarks">
                  <ol><li><a href="cover.xhtml">Cover</a></li></ol>
                </nav>
              </body>
            </html>
            """.trimIndent()

        val result = parser.parse(document, isNcx = false)

        assertEquals(EpubNavigationProvenance.UNTYPED_FALLBACK, result.provenance)
        assertFalse(result.repairEligible)
        assertEquals(listOf("A & B", "Group \"One\""), result.references.map { it.label })
        val readerHtml = requireNotNull(result.readerHtml)
        assertTrue(readerHtml.contains("aria-label=\"Guide &amp; overview\""))
        assertTrue(readerHtml.contains("<h2>Read &lt;safely&gt;</h2>"))
        assertTrue(readerHtml.contains("href=\"chapter.xhtml?x=1&amp;y=2\""))
        assertTrue(readerHtml.contains("A &amp; B"))
        assertTrue(readerHtml.contains("Group &quot;One&quot;"))
        assertFalse(readerHtml.contains("Cover"))
    }

    @Test
    fun fallbackSkipsKnownNonTocNavigationAndUsesDocumentTitle() {
        val document =
            """
            <html>
              <head><title>Publisher Guide</title></head>
              <body>
                <nav epub:type="page-list">
                  <ol><li><a href="chapter.xhtml#page-1">1</a></li></ol>
                </nav>
                <nav role="doc-landmarks">
                  <ol><li><a href="cover.xhtml">Cover</a></li></ol>
                </nav>
                <nav>
                  <ol><li><a href="chapter.xhtml">Chapter One</a></li></ol>
                </nav>
              </body>
            </html>
            """.trimIndent()

        val result = parser.parse(document, isNcx = false)

        assertEquals(listOf("Chapter One"), result.references.map { it.label })
        val readerHtml = requireNotNull(result.readerHtml)
        assertTrue(readerHtml.contains("<h1>Publisher Guide</h1>"))
        assertFalse(readerHtml.contains("page-1"))
        assertFalse(readerHtml.contains("Cover"))
    }

    @Test
    fun ariaLabelBecomesVisibleTitleWhenTocHasNoHeading() {
        val document =
            """
            <html>
              <head><title>Document title</title></head>
              <body>
                <nav epub:type="toc" aria-label="Reading order">
                  <ol><li><a href="chapter.xhtml">Chapter One</a></li></ol>
                </nav>
              </body>
            </html>
            """.trimIndent()

        val readerHtml = requireNotNull(parser.parse(document, isNcx = false).readerHtml)

        assertTrue(readerHtml.contains("aria-label=\"Reading order\""))
        assertTrue(readerHtml.contains("<h1>Reading order</h1>"))
        assertFalse(readerHtml.contains("Document title"))
    }

    @Test
    fun canonicalizesPersistedChapterLinksWithoutChangingTargets() {
        val document =
            """
            <html><body>
              <nav epub:type="toc">
                <ol><li><a href="kairo://chapter/7#section">Chapter Seven</a></li></ol>
              </nav>
            </body></html>
            """.trimIndent()

        val result = parser.parse(document, isNcx = false)

        assertEquals("kairo://chapter/7#section", result.references.single().href)
        assertTrue(requireNotNull(result.readerHtml).contains("href=\"kairo://chapter/7#section\""))
    }

    @Test
    fun knownNonTocNavigationAloneDoesNotBecomeReaderContents() {
        val document =
            """
            <html><body>
              <nav epub:type="page-list"><ol><li><a href="chapter.xhtml#page-1">1</a></li></ol></nav>
              <nav role="doc-landmarks"><ol><li><a href="cover.xhtml">Cover</a></li></ol></nav>
            </body></html>
            """.trimIndent()

        val result = parser.parse(document, isNcx = false)

        assertTrue(result.references.isEmpty())
        assertNull(result.readerHtml)
    }

    @Test
    fun deeplyWrappedNavigationAndLabelsAreSafelyRejectedAtParserLimit() {
        val document =
            buildString {
                append("<html>")
                repeat(DEEP_NESTING_LEVELS) { append("<div>") }
                append("<nav epub:type=\"toc\"><ol><li><a href=\"chapter.xhtml\">")
                repeat(DEEP_NESTING_LEVELS) { append("<span>") }
                append("Deep chapter")
            }

        val result = parser.parse(document, isNcx = false)

        assertTrue(result.references.isEmpty())
        assertNull(result.readerHtml)
        assertFalse(result.complete)
    }

    @Test
    fun deeplyNestedLabelLessListsStopAtStructuralBudget() {
        val document =
            buildString {
                append("<nav epub:type=\"toc\"><ol>")
                repeat(DEEP_NESTING_LEVELS) { append("<li><ol>") }
                append("<li><a href=\"chapter.xhtml\">Too deep</a>")
            }

        val result = parser.parse(document, isNcx = false)

        assertTrue(result.references.isEmpty())
        assertNull(result.readerHtml)
    }

    @Test
    fun deeplyNestedLabelLessNcxPointsStopAtStructuralBudget() {
        val document =
            buildString {
                append("<ncx><navMap>")
                repeat(DEEP_NESTING_LEVELS) { append("<navPoint>") }
                append(
                    "<navPoint><navLabel><text>Too deep</text></navLabel>" +
                        "<content src=\"chapter.xhtml\"/>",
                )
            }

        val result = parser.parse(document, isNcx = true)

        assertTrue(result.references.isEmpty())
        assertNull(result.readerHtml)
    }

    @Test
    fun epubThreeEntryLimitMarksExtractionIncompleteWithoutCanonicalReaderHtml() {
        val document =
            buildString {
                append("<nav epub:type=\"toc\"><ol>")
                repeat(OVER_ENTRY_LIMIT) { index ->
                    append("<li><a href=\"chapter-")
                    append(index)
                    append(".xhtml\">Chapter ")
                    append(index)
                    append("</a></li>")
                }
                append("</ol></nav>")
            }

        val result = parseCompleteExtractionFixture(document, isNcx = false)

        assertEquals(MAX_EXPECTED_NAVIGATION_ENTRIES, result.references.size)
        assertIncompleteWithoutReaderHtml(result)
    }

    @Test
    fun ncxEntryLimitPropagatesIncompleteExtraction() {
        val document =
            buildString {
                append("<ncx><navMap>")
                repeat(OVER_ENTRY_LIMIT) { index ->
                    append("<navPoint><navLabel><text>Chapter ")
                    append(index)
                    append("</text></navLabel><content src=\"chapter-")
                    append(index)
                    append(".xhtml\"/></navPoint>")
                }
                append("</navMap></ncx>")
            }

        val result = parseCompleteExtractionFixture(document, isNcx = true)

        assertEquals(MAX_EXPECTED_NAVIGATION_ENTRIES, result.references.size)
        assertIncompleteWithoutReaderHtml(result)
    }

    @Test
    fun aggregateCharacterLimitMarksExtractionIncompleteWithoutCanonicalReaderHtml() {
        val label = "x".repeat(MAX_EXPECTED_LABEL_CHARACTERS)
        val document =
            buildString {
                append("<nav epub:type=\"toc\"><ol>")
                repeat(AGGREGATE_CHARACTER_ENTRY_COUNT) { index ->
                    append("<li><a href=\"c")
                    append(index)
                    append("\">")
                    append(label)
                    append("</a></li>")
                }
                append("</ol></nav>")
            }

        val result = parseCompleteExtractionFixture(document, isNcx = false)

        assertTrue(result.references.isNotEmpty())
        assertTrue(result.references.size < AGGREGATE_CHARACTER_ENTRY_COUNT)
        assertIncompleteWithoutReaderHtml(result)
    }

    @Test
    fun overlongExtractedTextMarksExtractionIncompleteWithoutCanonicalReaderHtml() {
        val document =
            "<nav epub:type=\"toc\"><ol><li><a href=\"chapter.xhtml\">" +
                "x".repeat(OVER_EXTRACTED_TEXT_LIMIT) +
                "</a></li></ol></nav>"

        val result = parseCompleteExtractionFixture(document, isNcx = false)

        assertEquals(1, result.references.size)
        assertIncompleteWithoutReaderHtml(result)
    }

    @Test
    fun semanticDepthLimitMarksExtractionIncompleteWithoutCanonicalReaderHtml() {
        val result =
            parseCompleteExtractionFixture(
                nestedNavigationDocument(levels = OVER_SEMANTIC_DEPTH, labeledLevels = true),
                isNcx = false,
            )

        assertTrue(result.references.isNotEmpty())
        assertIncompleteWithoutReaderHtml(result)
    }

    @Test
    fun structuralDepthLimitMarksExtractionIncompleteWithoutCanonicalReaderHtml() {
        val result =
            parseCompleteExtractionFixture(
                nestedNavigationDocument(levels = OVER_STRUCTURAL_DEPTH, labeledLevels = false),
                isNcx = false,
            )

        assertTrue(result.references.isEmpty())
        assertIncompleteWithoutReaderHtml(result)
    }

    @Test
    fun descendantLimitMarksExtractionIncompleteWithoutCanonicalReaderHtml() {
        val document =
            buildString {
                append("<html><body>")
                repeat(OVER_DESCENDANT_LIMIT) {
                    append("<nav epub:type=\"page-list\"></nav>")
                }
                append(
                    "<nav epub:type=\"toc\"><ol>" +
                        "<li><a href=\"chapter.xhtml\">Chapter</a></li></ol></nav>",
                )
                append("</body></html>")
            }

        val result = parseCompleteExtractionFixture(document, isNcx = false)

        assertTrue(result.references.isEmpty())
        assertIncompleteWithoutReaderHtml(result)
    }

    @Test
    fun traversalLimitMarksExtractionIncompleteWithoutCanonicalReaderHtml() {
        val document =
            buildString {
                append("<html><body>")
                repeat(TRAVERSAL_BRANCH_COUNT) {
                    append("<div>")
                    repeat(TRAVERSAL_NODES_PER_BRANCH) { append("<span/>") }
                    append("</div>")
                }
                append(
                    "<nav epub:type=\"toc\"><ol>" +
                        "<li><a href=\"chapter.xhtml\">Chapter</a></li></ol></nav>",
                )
                append("</body></html>")
            }

        val result = parseCompleteExtractionFixture(document, isNcx = false)

        assertTrue(result.references.isEmpty())
        assertIncompleteWithoutReaderHtml(result)
    }

    @Test
    fun parsesNestedEpubTwoNcxNavigationMap() {
        val document =
            """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
              <navMap>
                <navPoint id="front">
                  <navLabel><text>Introduction</text></navLabel>
                  <content src="text/front.xhtml"/>
                </navPoint>
                <navPoint id="part-one">
                  <navLabel><text>Part One</text></navLabel>
                  <content src="text/story.xhtml#part-one"/>
                  <navPoint id="chapter-one">
                    <navLabel><text>Chapter One</text></navLabel>
                    <content src="text/story.xhtml#chapter-one"/>
                  </navPoint>
                </navPoint>
              </navMap>
            </ncx>
            """.trimIndent()

        val result = parser.parse(document, isNcx = true)
        val entries = result.references

        assertEquals(listOf("Introduction", "Part One", "Chapter One"), entries.map { it.label })
        assertEquals(listOf(0, 0, 1), entries.map { it.depth })
        assertEquals("text/story.xhtml#chapter-one", entries.last().href)
        assertNull(result.readerHtml)
        assertEquals(EpubNavigationProvenance.NCX, result.provenance)
    }

    @Test
    fun explicitTocWithSecondaryNavigationAndInertContentIsRepairEligible() {
        val document =
            """
            <html><head><title>Guide</title><style>.x{content:'<nav>fake</nav>'}</style></head><body>
              <script>const fake = '<nav epub:type="toc"><ol><li>Fake</li></ol></nav>';</script>
              <noscript><nav epub:type="toc"><ol><li><a href="bad">Hidden</a></li></ol></nav></noscript>
              <main>
                <nav epub:type="toc"><ol><li><a href="kairo://chapter/1">Chapter One</a></li></ol></nav>
                <nav epub:type="page-list"><ol><li><a href="kairo://chapter/1#page-1">1</a></li></ol></nav>
                <nav role="doc-landmarks"><ol><li><a href="kairo://chapter/0">Cover</a></li></ol></nav>
              </main>
            </body></html>
            """.trimIndent()

        val result = parser.parse(document, isNcx = false)

        assertEquals(listOf("Chapter One"), result.references.map { it.label })
        assertTrue(result.repairEligible)
        assertFalse(requireNotNull(result.readerHtml).contains("Hidden"))
        assertFalse(result.readerHtml.contains("Cover"))
    }

    @Test
    fun visibleMixedContentAndMediaAreNotRepairEligible() {
        val prose =
            parser.parse(
                "<html><body><p>Ordinary chapter prose.</p>" +
                    "<nav epub:type=\"toc\"><ol><li><a href=\"kairo://chapter/1\">One</a></li></ol></nav>" +
                    "</body></html>",
                isNcx = false,
            )
        val media =
            parser.parse(
                "<html><body><img src=\"cover.jpg\"/>" +
                    "<nav epub:type=\"toc\"><ol><li><a href=\"kairo://chapter/1\">One</a></li></ol></nav>" +
                    "</body></html>",
                isNcx = false,
            )

        assertEquals(listOf("One"), prose.references.map { it.label })
        assertFalse(prose.navigationOnly)
        assertFalse(prose.repairEligible)
        assertFalse(media.navigationOnly)
        assertFalse(media.repairEligible)
    }

    @Test
    fun rawScriptTextCannotCreateNavigationCandidate() {
        val result =
            parser.parse(
                "<html><body><script>const x = '<nav epub:type=\"toc\"><ol><li>Fake</li></ol></nav>';</script></body></html>",
                isNcx = false,
            )

        assertTrue(result.references.isEmpty())
        assertEquals(EpubNavigationProvenance.NONE, result.provenance)
    }

    @Test
    fun persistedRendererMakesExternalMalformedAndOutOfRangeTargetsInert() {
        val result =
            parser.parse(
                """
                <nav epub:type="toc"><ol>
                  <li><a href="kairo://chapter/1#section">Valid</a></li>
                  <li><a href="kairo://chapter/9">Out of range</a></li>
                  <li><a href="https://example.com">External</a></li>
                  <li><a href="kairo://chapter/1?query=x">Malformed</a></li>
                </ol></nav>
                """.trimIndent(),
                isNcx = false,
            )

        val html = requireNotNull(EpubReaderNavigationContent.renderPersisted(result, setOf(0, 1)))

        assertTrue(html.contains("href=\"kairo://chapter/1#section\""))
        assertFalse(html.contains("href=\"kairo://chapter/9\""))
        assertFalse(html.contains("href=\"https://example.com\""))
        assertFalse(html.contains("href=\"kairo://chapter/1?query=x\""))
        assertTrue(html.contains("<span>Out of range</span>"))
        assertTrue(html.contains("<span>External</span>"))
        assertTrue(html.contains("<span>Malformed</span>"))
    }

    private fun nestedNavigationDocument(
        levels: Int,
        labeledLevels: Boolean,
    ): String =
        buildString {
            append("<nav epub:type=\"toc\"><ol>")
            repeat(levels) { index ->
                append("<li>")
                if (labeledLevels) {
                    append("<a href=\"level-")
                    append(index)
                    append(".xhtml\">Level ")
                    append(index)
                    append("</a>")
                }
                append("<ol>")
            }
            append("<li><a href=\"too-deep.xhtml\">Too deep</a></li>")
            repeat(levels) { append("</ol></li>") }
            append("</ol></nav>")
        }

    private fun assertIncompleteWithoutReaderHtml(result: EpubNavigationParseResult) {
        assertFalse(result.complete)
        assertFalse(result.repairEligible)
        assertNull(result.readerHtml)
    }

    private fun parseCompleteExtractionFixture(
        document: String,
        isNcx: Boolean,
    ): EpubNavigationParseResult {
        assertTrue(EpubMarkupParser().parseWithResult(document).complete)
        return parser.parse(document, isNcx)
    }

    private companion object {
        const val DEEP_NESTING_LEVELS = 5_000
        const val MAX_EXPECTED_NAVIGATION_ENTRIES = 4_000
        const val OVER_ENTRY_LIMIT = MAX_EXPECTED_NAVIGATION_ENTRIES + 1
        const val MAX_EXPECTED_LABEL_CHARACTERS = 240
        const val AGGREGATE_CHARACTER_ENTRY_COUNT = 2_100
        const val OVER_EXTRACTED_TEXT_LIMIT = 4_097
        const val OVER_SEMANTIC_DEPTH = 14
        const val OVER_STRUCTURAL_DEPTH = 66
        const val OVER_DESCENDANT_LIMIT = 4_001
        const val TRAVERSAL_BRANCH_COUNT = 2
        const val TRAVERSAL_NODES_PER_BRANCH = 25_100
    }
}
