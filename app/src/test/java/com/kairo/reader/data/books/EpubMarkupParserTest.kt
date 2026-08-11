package com.kairo.reader.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubMarkupParserTest {
    private val parser = EpubMarkupParser()

    @Test
    fun renderPlainTextSkipsScriptAndPreservesReadableText() {
        val html = "<div><p>Hello<br/>world</p><script>bad()</script><p>Next</p></div>"

        val document = parser.parse(html)
        val text = EpubMarkupInspector.renderPlainText(document)

        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("world"))
        assertTrue(text.contains("Next"))
        assertFalse(text.contains("bad()"))
    }

    @Test
    fun extractImageSourcesReadsImgAndSvgImageTags() {
        val html =
            """
            <div>
              <img src="images/cover.jpg" />
              <svg><image xlink:href="images/inline.svg"/></svg>
            </div>
            """.trimIndent()

        val document = parser.parse(html)
        val sources = EpubMarkupInspector.extractImageSources(document)

        assertEquals(listOf("images/cover.jpg", "images/inline.svg"), sources)
    }

    @Test
    fun countTagOccurrencesHonorsLimit() {
        val html = "<div><a>a</a><a>b</a><a>c</a></div>"

        val document = parser.parse(html)
        val count = EpubMarkupInspector.countTagOccurrences(document, "a", limit = 2)

        assertEquals(2, count)
    }

    @Test
    fun firstTextInTagsFindsHeadingWithNestedNodes() {
        val html = "<html><body><h2><span>Chapter</span> <em>One</em></h2></body></html>"

        val document = parser.parse(html)
        val title = EpubMarkupInspector.firstTextInTags(document, setOf("h1", "h2", "h3"))

        assertEquals("Chapter One", title?.replace(Regex("\\s+"), " ")?.trim())
    }

    @Test
    fun rawTextElementsDoNotTokenizeEmbeddedMarkup() {
        val html =
            "<html><body><script>const fake = '<nav><ol><li>bad</li></ol></nav>';</script>" +
                "<style>.x::after { content: '<nav>bad</nav>'; }</style><p>Readable</p></body></html>"

        val result = parser.parseWithResult(html)

        assertTrue(result.complete)
        assertEquals(0, EpubMarkupInspector.countTagOccurrences(result.document, "nav", limit = 1))
        assertTrue(EpubMarkupInspector.renderPlainText(result.document).contains("Readable"))
    }

    @Test
    fun rejectsOversizedInputTagAndAttributeFlood() {
        val oversizedInput = parser.parseWithResult("x".repeat(5 * 1024 * 1024 + 1))
        val oversizedTag = parser.parseWithResult("<div ${"x".repeat(33_000)}>")
        val oversizedUnclosedTag = parser.parseWithResult("<div ${"x".repeat(33_000)}")
        val attributeFlood =
            parser.parseWithResult(
                buildString {
                    append("<nav")
                    repeat(129) { index -> append(" a$index=\"x\"") }
                    append("></nav>")
                },
            )

        assertFalse(oversizedInput.complete)
        assertTrue(oversizedInput.limitExceeded)
        assertFalse(oversizedTag.complete)
        assertTrue(oversizedTag.limitExceeded)
        assertFalse(oversizedUnclosedTag.complete)
        assertTrue(oversizedUnclosedTag.limitExceeded)
        assertFalse(attributeFlood.complete)
        assertTrue(attributeFlood.limitExceeded)
    }

    @Test
    fun rejectsTokenFloodDeepOpenStackAndUnmatchedEndTagWithoutOverflow() {
        val tokenization = EpubMarkupTokenizer().tokenize("<br/>".repeat(100_001))
        val emittedTokens = tokenization.tokens.count()
        val deep = parser.parseWithResult("<div>".repeat(5_000))
        val unmatched = parser.parseWithResult("<div>text</span></div>")

        assertEquals(100_000, emittedTokens)
        assertFalse(tokenization.complete)
        assertTrue(tokenization.limitExceeded)
        assertFalse(deep.complete)
        assertTrue(deep.limitExceeded)
        assertFalse(unmatched.complete)
        assertFalse(unmatched.limitExceeded)
    }
}
