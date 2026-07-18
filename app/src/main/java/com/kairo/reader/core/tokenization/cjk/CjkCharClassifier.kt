package com.kairo.reader.core.tokenization.cjk

internal object CjkCharClassifier {
    fun isWhitespace(codePoint: Int): Boolean = Character.isWhitespace(codePoint)

    fun isPunctuation(codePoint: Int): Boolean {
        if (EXTRA_PUNCTUATION.contains(codePoint)) return true
        return when (Character.getType(codePoint)) {
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            -> true
            else -> false
        }
    }

    fun isWordConnector(codePoint: Int): Boolean = WORD_CONNECTORS.contains(codePoint)

    fun isCombiningMark(codePoint: Int): Boolean =
        when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            -> true
            else -> false
        }

    fun isLatinLike(codePoint: Int): Boolean {
        if (!Character.isLetterOrDigit(codePoint)) return false
        if (isCjk(codePoint)) return false
        if (isHangul(codePoint)) return false
        return true
    }

    fun isHangul(codePoint: Int): Boolean =
        codePoint in HANGUL_SYLLABLES ||
            codePoint in HANGUL_JAMO ||
            codePoint in HANGUL_COMPATIBILITY_JAMO ||
            codePoint in HANGUL_JAMO_EXTENDED_A ||
            codePoint in HANGUL_JAMO_EXTENDED_B

    fun isCjk(codePoint: Int): Boolean =
        isHangul(codePoint) ||
            isKana(codePoint) ||
            isBopomofo(codePoint) ||
            isCjkIdeograph(codePoint)

    private fun isKana(codePoint: Int): Boolean =
        codePoint in HIRAGANA ||
            codePoint in KATAKANA ||
            codePoint in KATAKANA_PHONETIC_EXTENSIONS

    private fun isBopomofo(codePoint: Int): Boolean =
        codePoint in BOPOMOFO || codePoint in BOPOMOFO_EXTENDED

    private fun isCjkIdeograph(codePoint: Int): Boolean =
        codePoint in CJK_EXTENSION_A ||
            codePoint in CJK_UNIFIED_IDEOGRAPHS ||
            codePoint in CJK_COMPATIBILITY_IDEOGRAPHS ||
            codePoint in CJK_EXTENSION_B ||
            codePoint in CJK_EXTENSION_C ||
            codePoint in CJK_EXTENSION_D ||
            codePoint in CJK_EXTENSION_E ||
            codePoint in CJK_EXTENSION_F ||
            codePoint in CJK_EXTENSION_G ||
            codePoint in CJK_COMPATIBILITY_SUPPLEMENT

    private val HANGUL_SYLLABLES = 0xAC00..0xD7AF
    private val HANGUL_JAMO = 0x1100..0x11FF
    private val HANGUL_COMPATIBILITY_JAMO = 0x3130..0x318F
    private val HANGUL_JAMO_EXTENDED_A = 0xA960..0xA97F
    private val HANGUL_JAMO_EXTENDED_B = 0xD7B0..0xD7FF
    private val HIRAGANA = 0x3040..0x309F
    private val KATAKANA = 0x30A0..0x30FF
    private val KATAKANA_PHONETIC_EXTENSIONS = 0x31F0..0x31FF
    private val BOPOMOFO = 0x3100..0x312F
    private val BOPOMOFO_EXTENDED = 0x31A0..0x31BF
    private val CJK_EXTENSION_A = 0x3400..0x4DBF
    private val CJK_UNIFIED_IDEOGRAPHS = 0x4E00..0x9FFF
    private val CJK_COMPATIBILITY_IDEOGRAPHS = 0xF900..0xFAFF
    private val CJK_EXTENSION_B = 0x20000..0x2A6DF
    private val CJK_EXTENSION_C = 0x2A700..0x2B73F
    private val CJK_EXTENSION_D = 0x2B740..0x2B81F
    private val CJK_EXTENSION_E = 0x2B820..0x2CEAF
    private val CJK_EXTENSION_F = 0x2CEB0..0x2EBEF
    private val CJK_EXTENSION_G = 0x30000..0x3134F
    private val CJK_COMPATIBILITY_SUPPLEMENT = 0x2F800..0x2FA1F

    private val WORD_CONNECTORS =
        setOf(
            '-'.code,
            '\u2010'.code, // Hyphen
            '\u2011'.code, // Non-breaking hyphen
            '\u2012'.code, // Figure dash
            '\u2013'.code, // En dash
            '\u2014'.code, // Em dash
            '\u2019'.code, // Right single quote
            '\''.code,
        )

    private val EXTRA_PUNCTUATION =
        setOf(
            '。'.code,
            '、'.code,
            '，'.code,
            '．'.code,
            '！'.code,
            '？'.code,
            '：'.code,
            '；'.code,
            '・'.code,
            '·'.code,
            '‧'.code,
            '｡'.code,
            '､'.code,
            '「'.code,
            '」'.code,
            '『'.code,
            '』'.code,
            '《'.code,
            '》'.code,
            '〈'.code,
            '〉'.code,
            '【'.code,
            '】'.code,
            '（'.code,
            '）'.code,
            '〔'.code,
            '〕'.code,
            '［'.code,
            '］'.code,
            '｛'.code,
            '｝'.code,
            '…'.code,
            '—'.code,
            '〜'.code,
            '～'.code,
            '※'.code,
            '•'.code,
        )
}
