package com.kairo.reader.data.books

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class BookTextDecoderTest {
    @Test
    fun decodeHonoursUtf16LittleEndianBom() {
        val content = "A properly decoded chapter."
        val encoded = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + content.toByteArray(Charsets.UTF_16LE)

        assertEquals(content, BookTextDecoder.decode(encoded))
    }

    @Test
    fun decodeUsesDeclaredHtmlCharset() {
        val source = "<meta charset=windows-1252><p>Caf\u00e9 costs \u00a35.</p>"
        val encoded = source.toByteArray(Charset.forName("windows-1252"))

        assertEquals(source, BookTextDecoder.decode(encoded))
    }

    @Test
    fun decodeFallsBackToWindows1252WhenUtf8IsMalformed() {
        val encoded = "Smart \u201cquotes\u201d".toByteArray(Charset.forName("windows-1252"))

        assertEquals("Smart \u201cquotes\u201d", BookTextDecoder.decode(encoded))
    }
}
