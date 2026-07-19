package com.kairo.reader.core.tokenization.rtl

internal object RtlCharClassifier {
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

    fun isWordChar(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) || isRtlLetter(codePoint)

    private fun isRtlLetter(codePoint: Int): Boolean =
        codePoint in HEBREW ||
            codePoint in ARABIC ||
            codePoint in ARABIC_SUPPLEMENT ||
            codePoint in ARABIC_EXTENDED_A ||
            codePoint in ARABIC_PRESENTATION_FORMS_A ||
            codePoint in ARABIC_PRESENTATION_FORMS_B

    private val HEBREW = 0x0590..0x05FF
    private val ARABIC = 0x0600..0x06FF
    private val ARABIC_SUPPLEMENT = 0x0750..0x077F
    private val ARABIC_EXTENDED_A = 0x08A0..0x08FF
    private val ARABIC_PRESENTATION_FORMS_A = 0xFB50..0xFDFF
    private val ARABIC_PRESENTATION_FORMS_B = 0xFE70..0xFEFF

    private val WORD_CONNECTORS =
        setOf(
            '-'.code,
            '\u2010'.code, // Hyphen
            '\u2011'.code, // Non-breaking hyphen
            '\u2012'.code, // Figure dash
            '\u2013'.code, // En dash
            '\u2014'.code, // Em dash
            '\u02BC'.code, // Modifier letter apostrophe
            '\u2019'.code, // Right single quote
            '\''.code,
            '\u0640'.code, // Arabic tatweel
        )

    private val EXTRA_PUNCTUATION =
        setOf(
            '\u060C'.code, // Arabic comma
            '\u061B'.code, // Arabic semicolon
            '\u061F'.code, // Arabic question mark
            '\u06D4'.code, // Arabic full stop
            '\u066A'.code, // Arabic percent
            '\u066B'.code, // Arabic decimal separator
            '\u066C'.code, // Arabic thousands separator
            '\u05BE'.code, // Hebrew maqaf
            '\u05C0'.code, // Hebrew paseq
            '\u05C3'.code, // Hebrew sof pasuq
            '\u05F3'.code, // Hebrew geresh
            '\u05F4'.code, // Hebrew gershayim
            '\u066D'.code, // Arabic five pointed star
            '\u0701'.code, // Syriac supralinear full stop
            '\u0702'.code, // Syriac sublinear full stop
            '\u201C'.code, // “
            '\u201D'.code, // ”
            '\u2018'.code, // ‘
            '\u2019'.code, // ’
        )
}
