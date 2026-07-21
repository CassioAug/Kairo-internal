package com.kairo.reader.data.books

import javax.xml.parsers.ParserConfigurationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeXmlTest {
    @Test
    fun optionalXIncludeConfigurationToleratesUnsupportedAndroidProvider() {
        var attempted = false

        SafeXml.disableXInclude { enabled ->
            attempted = true
            assertFalse(enabled)
            throw UnsupportedOperationException(
                "This parser does not support specification Unknown version 0.0",
            )
        }

        assertTrue(attempted)
    }

    @Test(expected = IllegalStateException::class)
    fun optionalXIncludeConfigurationPropagatesUnexpectedFailures() {
        SafeXml.disableXInclude {
            throw IllegalStateException("Unexpected parser failure")
        }
    }

    @Test
    fun optionalSecurityFeatureToleratesUnsupportedAndroidProvider() {
        SafeXml.setOptionalSecurityFeature {
            throw ParserConfigurationException(
                "http://apache.org/xml/features/disallow-doctype-decl",
            )
        }
    }

    @Test(expected = IllegalStateException::class)
    fun optionalSecurityFeaturePropagatesUnexpectedFailures() {
        SafeXml.setOptionalSecurityFeature {
            throw IllegalStateException("Unexpected parser failure")
        }
    }

    @Test
    fun parseAcceptsOrdinaryDocumentXml() {
        val document = SafeXml.parse("<document><body>Readable text</body></document>".toByteArray())

        assertEquals("Readable text", document.documentElement.textContent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseRejectsUtf8DoctypeBeforeProviderConfiguration() {
        SafeXml.parse("<!DOCTYPE document><document/>".toByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseRejectsUtf16DoctypeBeforeProviderConfiguration() {
        SafeXml.parse("<!DOCTYPE document><document/>".toByteArray(Charsets.UTF_16LE))
    }
}
