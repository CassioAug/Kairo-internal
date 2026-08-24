package com.kairo.reader.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageFamilyClassifierTest {
    @Test
    fun classifiesSupportedLanguageFamiliesFromNormalizedTags() {
        assertClassification("en_GB", "en-gb", "en", LanguageFamily.ENGLISH)
        assertClassification("ja-JP", "ja-jp", "ja", LanguageFamily.CJK)
        assertClassification("zh-Hant", "zh-hant", "zh", LanguageFamily.CJK)
        assertClassification("ko", "ko", "ko", LanguageFamily.CJK)
        assertClassification("ar-EG", "ar-eg", "ar", LanguageFamily.RTL)
        assertClassification("he", "he", "he", LanguageFamily.RTL)
        assertClassification("fr-FR", "fr-fr", "fr", LanguageFamily.DEFAULT_NON_ENGLISH)
    }

    @Test
    fun canonicalizesIsoAliasesWithoutRewritingNormalizedTag() {
        assertClassification("eng-GB", "eng-gb", "en", LanguageFamily.ENGLISH)
        assertClassification("jpn-JP", "jpn-jp", "ja", LanguageFamily.CJK)
        assertClassification("zho-Hant", "zho-hant", "zh", LanguageFamily.CJK)
        assertClassification("chi-CN", "chi-cn", "zh", LanguageFamily.CJK)
        assertClassification("cmn-Hans", "cmn-hans", "zh", LanguageFamily.CJK)
        assertClassification("kor-KR", "kor-kr", "ko", LanguageFamily.CJK)
        assertClassification("ara-EG", "ara-eg", "ar", LanguageFamily.RTL)
        assertClassification("arb", "arb", "ar", LanguageFamily.RTL)
        assertClassification("heb-IL", "heb-il", "he", LanguageFamily.RTL)

        val deprecatedHebrew = LanguageFamilyClassifier.classify("iw-IL")
        assertEquals("he", deprecatedHebrew.primaryLanguage)
        assertEquals(LanguageFamily.RTL, deprecatedHebrew.family)
    }

    @Test
    fun nullBlankAndInvalidTagsAreUnknown() {
        listOf(null, "", "   ", "not a tag").forEach { tag ->
            val classification = LanguageFamilyClassifier.classify(tag)
            assertEquals(LanguageFamily.UNKNOWN, classification.family)
            assertNull(classification.normalizedTag)
            assertNull(classification.primaryLanguage)
        }
    }

    private fun assertClassification(
        source: String,
        normalized: String,
        primary: String,
        family: LanguageFamily,
    ) {
        val classification = LanguageFamilyClassifier.classify(source)
        assertEquals(normalized, classification.normalizedTag)
        assertEquals(primary, classification.primaryLanguage)
        assertEquals(family, classification.family)
    }
}
