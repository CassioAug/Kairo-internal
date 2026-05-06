package com.kairo.reader.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenUtilsPunctuationTest {
    @Test
    fun midSentencePunctuationIncludesDashAndCommaButNotClosingBrackets() {
        assertTrue(isMidSentencePunctuation(','))
        assertTrue(isMidSentencePunctuation('\u2014'))
        assertTrue(isMidSentencePunctuation('\u2013'))
        assertFalse(isMidSentencePunctuation(')'))
        assertFalse(isMidSentencePunctuation(']'))
        assertFalse(isMidSentencePunctuation('}'))
    }
}
