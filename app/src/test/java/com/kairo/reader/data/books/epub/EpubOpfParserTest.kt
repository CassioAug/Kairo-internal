package com.kairo.reader.data.books.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubOpfParserTest {
    private val parser = EpubOpfParser()

    @Test
    fun parseLenientSupportsNamespacedItemAndItemref() {
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

        val result = parser.parseLenient(xml)

        assertEquals("chapter1.xhtml", result.manifest["c1"])
        assertEquals(1, result.spineItems.size)
    }

    @Test
    fun parseLenientDoesNotSynthesizeSpineForNonPackageXml() {
        val xml =
            """
            <root>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
            </root>
            """.trimIndent()

        val result = parser.parseLenient(xml)

        assertTrue(result.spineItems.isEmpty())
    }

    @Test
    fun parseWithResultUsesPackageScopedManifestInStrictMode() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf" xmlns:custom="urn:test">
              <metadata>
                <custom:manifest>
                  <custom:item id="noise" href="noise.xhtml" media-type="application/xhtml+xml" />
                </custom:manifest>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="c1" />
              </spine>
            </package>
            """.trimIndent()

        val result = parser.parseWithResult(xml).opfData

        assertEquals(1, result.manifest.size)
        assertEquals("chapter1.xhtml", result.manifest["c1"])
        assertEquals(1, result.spineItems.size)
    }

    @Test
    fun parseWithResultFindsEpubThreeNavigationDocument() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="navigation" href="nav.xhtml"
                      media-type="application/xhtml+xml" properties="nav" />
                <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine>
                <itemref idref="navigation" />
                <itemref idref="chapter" />
              </spine>
            </package>
            """.trimIndent()

        val result = parser.parseWithResult(xml).opfData

        assertEquals("nav.xhtml", result.navigationHref)
        assertEquals(listOf("navigation", "chapter"), result.spineItems.map { it.idref })
    }

    @Test
    fun parseWithResultFindsEpubTwoNcxFromSpineTocAttribute() {
        val xml =
            """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml" />
                <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine toc="ncx">
                <itemref idref="chapter" />
              </spine>
            </package>
            """.trimIndent()

        val result = parser.parseWithResult(xml).opfData

        assertEquals("toc.ncx", result.navigationHref)
    }
}
