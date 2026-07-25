package com.kairo.reader.data.books

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeXmlAndroidTest {
    @Test
    fun parseAcceptsDocxXmlWithAndroidDocumentBuilderProvider() {
        val xml =
            """
            <w:document xmlns:w="word">
              <w:body><w:p><w:r><w:t>Readable DOCX text</w:t></w:r></w:p></w:body>
            </w:document>
            """.trimIndent()

        val document = SafeXml.parse(xml.toByteArray())

        assertEquals("Readable DOCX text", document.documentElement.textContent.trim())
    }
}
