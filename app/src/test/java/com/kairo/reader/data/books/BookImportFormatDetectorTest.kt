package com.kairo.reader.data.books

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookImportFormatDetectorTest {
    @Test
    fun detectRecognizesDocxFromPackageStructure() {
        val archive =
            zip(
                mapOf(
                    "[Content_Types].xml" to "<Types/>".toByteArray(),
                    "word/document.xml" to "<w:document/>".toByteArray(),
                ),
            )

        assertEquals("docx", BookImportFormatDetector.detect(ByteArrayInputStream(archive)))
    }

    @Test
    fun detectRecognizesPdfHeaderAfterLeadingBytes() {
        val bytes = "comment before header\n%PDF-1.7\n".toByteArray()

        assertEquals("pdf", BookImportFormatDetector.detect(ByteArrayInputStream(bytes)))
    }

    @Test
    fun detectDoesNotTreatArbitraryZipAsDocx() {
        val archive = zip(mapOf("notes.txt" to "Not a Word document".toByteArray()))

        assertNull(BookImportFormatDetector.detect(ByteArrayInputStream(archive)))
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
