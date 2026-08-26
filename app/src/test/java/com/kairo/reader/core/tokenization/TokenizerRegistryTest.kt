package com.kairo.reader.core.tokenization

import com.kairo.reader.core.tokenization.cjk.CjkTokenizer
import com.kairo.reader.core.tokenization.rtl.RtlTokenizer
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerRegistryTest {
    @Test
    fun resolvesCjkTokenizerForCjkLanguageTags() {
        assertTrue(TokenizerRegistry.resolve("ja") is CjkTokenizer)
        assertTrue(TokenizerRegistry.resolve("zh-Hans") is CjkTokenizer)
        assertTrue(TokenizerRegistry.resolve("ko-KR") is CjkTokenizer)
    }

    @Test
    fun resolvesDefaultTokenizerForEnglish() {
        assertTrue(TokenizerRegistry.resolve("en") !is CjkTokenizer)
        assertTrue(TokenizerRegistry.resolve(null) !is CjkTokenizer)
    }

    @Test
    fun resolvesRtlTokenizerForRtlLanguages() {
        assertTrue(TokenizerRegistry.resolve("ar") is RtlTokenizer)
        assertTrue(TokenizerRegistry.resolve("he") is RtlTokenizer)
    }

    @Test
    fun isoAliasesResolveToTheSameTokenizerAsTheirCanonicalLanguage() {
        mapOf(
            "eng" to "en",
            "jpn" to "ja",
            "zho" to "zh",
            "chi" to "zh",
            "cmn" to "zh",
            "kor" to "ko",
            "ara" to "ar",
            "arb" to "ar",
            "heb" to "he",
            "iw" to "he",
        ).forEach { (alias, canonical) ->
            assertSame(TokenizerRegistry.resolve(canonical), TokenizerRegistry.resolve(alias))
        }
    }
}
