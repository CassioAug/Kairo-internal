package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                  <h1>Contents</h1>
                  <ol>
                    <li><a href="text/front.xhtml">Preface</a></li>
                    <li>
                      <span>Part One</span>
                      <ol>
                        <li><a href="text/story.xhtml#chapter-1">The Arrival</a></li>
                        <li><a href="text/story.xhtml#chapter-2">The Crossing</a></li>
                      </ol>
                    </li>
                  </ol>
                </nav>
              </body>
            </html>
            """.trimIndent()

        val entries = parser.parse(document, isNcx = false)

        assertEquals(listOf("Preface", "Part One", "The Arrival", "The Crossing"), entries.map { it.label })
        assertEquals(listOf(0, 0, 1, 1), entries.map { it.depth })
        assertEquals("text/front.xhtml", entries[0].href)
        assertNull(entries[1].href)
        assertEquals("text/story.xhtml#chapter-2", entries[3].href)
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

        val entries = parser.parse(document, isNcx = true)

        assertEquals(listOf("Introduction", "Part One", "Chapter One"), entries.map { it.label })
        assertEquals(listOf(0, 0, 1), entries.map { it.depth })
        assertEquals("text/story.xhtml#chapter-one", entries.last().href)
    }
}
