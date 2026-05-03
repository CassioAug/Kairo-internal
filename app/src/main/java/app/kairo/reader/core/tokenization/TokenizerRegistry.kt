package app.kairo.reader.core.tokenization

import app.kairo.reader.core.language.LanguageTagNormalizer
import app.kairo.reader.core.model.Chapter
import app.kairo.reader.core.model.Token
import app.kairo.reader.core.tokenization.cjk.CjkSegmenterConfig
import app.kairo.reader.core.tokenization.cjk.CjkTokenizer
import app.kairo.reader.core.tokenization.rtl.RtlTokenizer

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
        val normalized = LanguageTagNormalizer.normalize(languageTag)?.lowercase()
        return when {
            normalized == null -> defaultTokenizer
            normalized.startsWith("ja") -> jaTokenizer
            normalized.startsWith("zh") -> zhTokenizer
            normalized.startsWith("ko") -> koTokenizer
            normalized.startsWith("ar") -> rtlTokenizer
            normalized.startsWith("he") -> rtlTokenizer
            else -> defaultTokenizer
        }
    }
}
