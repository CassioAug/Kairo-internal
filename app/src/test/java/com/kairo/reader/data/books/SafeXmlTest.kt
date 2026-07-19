package com.kairo.reader.data.books

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
}
