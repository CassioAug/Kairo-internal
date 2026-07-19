package com.kairo.reader.core.language

import com.kairo.reader.core.model.Book
import java.util.Locale

object LanguageTagNormalizer {
    fun normalize(tag: String?): String? {
        val cleaned = tag?.trim()?.replace('_', '-')?.takeIf { it.isNotBlank() } ?: return null
        val locale = Locale.forLanguageTag(cleaned)
        val normalized = locale.toLanguageTag()
        return normalized.takeIf { it.isNotBlank() && it != "und" }
    }
}

object LanguageDetector {
    fun detectLanguageTag(text: String): String? {
        val sample = text.take(MAX_SAMPLE_CHARS)
        if (containsRange(sample, JAPANESE_KANA)) return "ja"
        if (containsRange(sample, HANGUL_SYLLABLES)) return "ko"
        if (containsRange(sample, ARABIC) || containsRange(sample, ARABIC_SUPPLEMENT)) {
            return "ar"
        }
        if (containsRange(sample, HEBREW)) return "he"
        if (containsRange(sample, CJK_UNIFIED_IDEOGRAPHS)) return "zh-Hans"
        return null
    }

    private fun containsRange(text: String, range: IntRange): Boolean {
        for (char in text) {
            val code = char.code
            if (code in range) return true
        }
        return false
    }

    private val JAPANESE_KANA = 0x3040..0x30FF
    private val HANGUL_SYLLABLES = 0xAC00..0xD7AF
    private val ARABIC = 0x0600..0x06FF
    private val ARABIC_SUPPLEMENT = 0x0750..0x077F
    private val HEBREW = 0x0590..0x05FF
    private val CJK_UNIFIED_IDEOGRAPHS = 0x4E00..0x9FFF
    private const val MAX_SAMPLE_CHARS = 2000
}

object BookLanguageResolver {
    fun resolve(book: Book): String? {
        val normalized = LanguageTagNormalizer.normalize(book.languageTag)
        if (normalized != null) return normalized
        val sample = sampleText(book) ?: return null
        return LanguageDetector.detectLanguageTag(sample)
    }

    private fun sampleText(book: Book): String? {
        val chapter = book.chapters.firstOrNull { it.plainText.isNotBlank() } ?: return null
        return chapter.plainText
    }
}
