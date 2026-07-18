package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EpubMarkupTest : EpubParserTestBase() {
    @Test
    fun extractPlainTextKeepsInlinePageBreakContent() {
        val html =
            "<p>Indiana and" +
                "<span class=\"right_1\" epub:type=\"pagebreak\" id=\"page_250\" title=\"250\"/>" +
                " Leo took up the rear.</p>"

        val text = contentRewriter.extractPlainText(html)

        assertTrue(text.contains("Indiana and\u000C Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsClosedPageBreakSpanContent() {
        val html =
            "<p>Indiana and" +
                "<span epub:type=\"pagebreak\" id=\"page_250\" title=\"250\">250</span>" +
                " Leo took up the rear.</p>"

        val text = contentRewriter.extractPlainText(html)

        assertTrue(text.contains("Indiana and\u000C Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsContentAfterRolePageBreak() {
        val html =
            "<p>Start" +
                "<span role=\"doc-pagebreak\" id=\"page_10\" title=\"10\"/>" +
                " end.</p>"

        val text = contentRewriter.extractPlainText(html)

        assertTrue(text.contains("Start\u000C end."))
    }

    @Test
    fun extractPlainTextKeepsPageBreakOnlyDocument() {
        val html = "<span epub:type=\"pagebreak\" id=\"page_10\" title=\"10\"/>"

        val text = contentRewriter.extractPlainText(html)

        assertEquals("\u000C", text)
    }

    @Test
    fun extractPlainTextSkipsHeadTitleMetadata() {
        val html =
            """
            <html>
                <head><title>Chapter 1</title></head>
                <body><p>Opening line.</p></body>
            </html>
            """.trimIndent()

        val text = contentRewriter.extractPlainText(html)

        assertEquals("Opening line.", text)
    }

    @Test
    fun extractPlainTextDecodesEntitiesAndPreservesParagraphs() {
        val html = "<p>Hello&nbsp;world &amp; friends.</p><p>Next&nbsp;para.</p>"

        val text = contentRewriter.extractPlainText(html)

        assertTrue(text.contains("Hello world & friends."))
        assertTrue(text.contains("Next para."))
        assertTrue(text.contains("friends.\n\nNext"))
    }

    @Test
    fun extractPlainTextPreservesLeadingWordsAfterAnchorImageAndDropCap() {
        val openingParagraph =
            "<p class=\"para-pf\"><span class=\"char-first\">T" +
                "<span class=\"smallcaps\">HE WOMAN WHO DISCOVERED THE </span>" +
                "</span>comet, Yumiko Sakamoto, age twenty-eight, " +
                "was an amateur astronomer.</p>"
        val html =
            """
            <html xmlns="http://www.w3.org/1999/xhtml">
              <body>
                <a id="d1-d2s6"/>
                <div class="page_top_padding">
                  <span epub:type="pagebreak" id="page_3" title="3"/>
                  <div class="figure figure_heading">
                    <div class="squeeze">
                      <img alt="Prelude The Comet" class="image" src="../images/chapter.jpg"/>
                    </div>
                  </div>
                  $openingParagraph
                  <p class="para-p">The discovery changed her life.</p>
                </div>
              </body>
            </html>
            """.trimIndent()

        val text = contentRewriter.extractPlainText(html)

        assertTrue(
            "Expected chapter opening to be preserved, but was: $text",
            text.contains("THE WOMAN WHO DISCOVERED THE comet, Yumiko Sakamoto"),
        )
        assertTrue(text.contains("The discovery changed her life."))
    }

    @Test
    fun extractPlainTextPreservesLeadingWordsAfterSelfClosingClassPageBreak() {
        val html =
            "<p><span class=\"pagebreak\" id=\"page_3\"/>" +
                "<span class=\"char-first\">T<span class=\"smallcaps\">" +
                "HE OPENING WORDS </span></span>remain intact.</p>"

        val text = contentRewriter.extractPlainText(html)

        assertTrue(
            "Expected class-based self-closing pagebreak to preserve opening words, but was: $text",
            text.contains("THE OPENING WORDS remain intact."),
        )
    }

    @Test
    fun extractPlainTextDecodesNamedEntities() {
        val html = "<p>&ldquo;Hello&rdquo; &mdash; a test&hellip;</p>"

        val text = contentRewriter.extractPlainText(html)

        assertTrue(text.contains("\u201CHello\u201D \u2014 a test\u2026"))
    }

    @Test
    fun sanitizeSrcDecodesUrlEncodingAndEntities() {
        val raw = "Images/Some%20Image%20&amp;%20Cover.jpg"

        val cleaned = contentRewriter.sanitizeSrc(raw)

        assertEquals("Images/Some Image & Cover.jpg", cleaned)
    }

    @Test
    fun sanitizeSrcPreservesPlusCharacters() {
        val raw = "Images/Chapter+1%2B2.jpg"

        val cleaned = contentRewriter.sanitizeSrc(raw)

        assertEquals("Images/Chapter+1+2.jpg", cleaned)
    }

    @Test
    fun rewriteHtmlAnchorHrefsPreservesFragmentOnInternalLinks() {
        val html = "<p><a href=\"chapter2.xhtml#section-3\">Jump</a></p>"

        val rewritten =
            invokeRewriteHtmlAnchorHrefs(
                html = html,
                baseDir = "oebps",
                chapterIndexByPathLower = mapOf("oebps/chapter2.xhtml" to 5),
                currentChapterPath = "oebps/chapter1.xhtml",
            )

        assertTrue(rewritten.contains("kairo://chapter/5#section-3"))
    }

    @Test
    fun rewriteHtmlImageSrcsPreservesSvgFragmentAndRewritesSrcset() {
        val html =
            """
            <picture>
              <source srcset="images/cover-small.jpg 1x, images/cover-large.jpg 2x" />
              <img src="images/cover.svg#icon-main"/>
            </picture>
            """.trimIndent()
        val images =
            mapOf(
                "oebps/images/cover-small.jpg" to "kairo_epub_assets/book/images/small.jpg",
                "oebps/images/cover-large.jpg" to "kairo_epub_assets/book/images/large.jpg",
                "oebps/images/cover.svg" to "kairo_epub_assets/book/images/cover.svg",
            )

        val rewritten = contentRewriter.rewriteHtmlImageSrcs(html, "oebps", images)

        assertTrue(rewritten.contains("kairo_epub_assets/book/images/cover.svg#icon-main"))
        assertTrue(rewritten.contains("kairo_epub_assets/book/images/small.jpg 1x"))
        assertTrue(rewritten.contains("kairo_epub_assets/book/images/large.jpg 2x"))
    }

    @Test
    fun extractImageSrcsIncludesNoscriptAndSrcsetCandidates() {
        val html =
            """
            <picture>
              <source src="images/default.jpg" />
              <source srcset="images/a.jpg 1x, images/b.jpg 2x" />
            </picture>
            <noscript><img src="images/fallback.jpg" /></noscript>
            """.trimIndent()

        val srcs = contentRewriter.extractImageSrcs(html)

        assertTrue(srcs.contains("images/default.jpg"))
        assertTrue(srcs.contains("images/a.jpg"))
        assertTrue(srcs.contains("images/b.jpg"))
        assertTrue(srcs.contains("images/fallback.jpg"))
    }

    @Test
    fun extractImageSrcsSkipsMarkupParseWhenChapterHasNoImages() {
        val html = "<p>A text-only chapter with <a href=\"chapter2.xhtml\">one link</a>.</p>"

        val srcs = contentRewriter.extractImageSrcs(html)

        assertTrue(srcs.isEmpty())
    }

    @Test
    fun stripNoiseTitleBlocksOnlyRemovesLeadingNoiseLabels() {
        val html =
            """
            <h1>chapter-0001.xhtml</h1>
            <p>Real chapter opening.</p>
            <h2>section-0002.xhtml</h2>
            """.trimIndent()

        val stripped = contentRewriter.stripNoiseTitleBlocks(html)

        assertFalse(stripped.contains("<h1>chapter-0001.xhtml</h1>"))
        assertTrue(stripped.contains("<p>Real chapter opening.</p>"))
        assertTrue(stripped.contains("<h2>section-0002.xhtml</h2>"))
    }
}
