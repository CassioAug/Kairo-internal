package com.kairo.reader.core.tokenization

internal object ParagraphBreakPatterns {
    private const val REPEATED_ASTERISK = "(?:\\*\\s*){3,}"
    private const val REPEATED_HYPHEN = "(?:-\\s*){3,}"
    private const val REPEATED_UNDERSCORE = "(?:_\\s*){3,}"
    private const val REPEATED_TILDE = "(?:~\\s*){3,}"
    private const val REPEATED_EM_DASH = "(?:\\u2014\\s*){2,}"
    private const val REPEATED_EN_DASH = "(?:\\u2013\\s*){2,}"
    private const val REPEATED_BULLET = "(?:\\u2022\\s*){3,}"
    private const val REPEATED_MIDDLE_DOT = "(?:\\u00B7\\s*){3,}"

    val sceneBreak =
        Regex(
            "^\\s*(?:" +
                listOf(
                    REPEATED_ASTERISK,
                    REPEATED_HYPHEN,
                    REPEATED_UNDERSCORE,
                    REPEATED_TILDE,
                    REPEATED_EM_DASH,
                    REPEATED_EN_DASH,
                    REPEATED_BULLET,
                    REPEATED_MIDDLE_DOT,
                ).joinToString("|") +
                ")\\s*$",
        )
}
