package com.kairo.reader.core.tokenization

import com.kairo.reader.core.language.LanguageFamily
import com.kairo.reader.core.language.LanguageFamilyClassifier
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.tokenization.cjk.CjkSegmenterConfig
import com.kairo.reader.core.tokenization.cjk.CjkTokenizer
import com.kairo.reader.core.tokenization.rtl.RtlTokenizer

interface ChapterTokenizer {
    fun tokenize(chapter: Chapter): List<Token>
}

private class DefaultTokenizer : ChapterTokenizer {
    override fun tokenize(chapter: Chapter): List<Token> = Tokenizer().tokenize(chapter)
}

object TokenizerRegistry {
    private val defaultTokenizer = DefaultTokenizer()
    private val jaTokenizer =
        CjkTokenizer(
            CjkSegmenterConfig(
                maxCjkCharsPerToken = 2,
                treatHangulAsWord = false,
            ),
        )
    private val zhTokenizer =
        CjkTokenizer(
            CjkSegmenterConfig(
                maxCjkCharsPerToken = 2,
                treatHangulAsWord = false,
            ),
        )
    private val koTokenizer =
        CjkTokenizer(
            CjkSegmenterConfig(
                maxCjkCharsPerToken = 2,
                treatHangulAsWord = true,
            ),
        )
    private val rtlTokenizer = RtlTokenizer()

    fun resolve(languageTag: String?): ChapterTokenizer {
        val classification = LanguageFamilyClassifier.classify(languageTag)
        return when (classification.family) {
            LanguageFamily.CJK ->
                when (classification.primaryLanguage) {
                    "ja" -> jaTokenizer
                    "zh" -> zhTokenizer
                    "ko" -> koTokenizer
                    else -> defaultTokenizer
                }
            LanguageFamily.RTL -> rtlTokenizer
            LanguageFamily.ENGLISH,
            LanguageFamily.DEFAULT_NON_ENGLISH,
            LanguageFamily.UNKNOWN -> defaultTokenizer
        }
    }
}
