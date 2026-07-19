package com.kairo.reader.core.tokenization.cjk

internal enum class CjkSegmentType {
    WORD,
    PUNCTUATION,
}

internal data class CjkSegment(val text: String, val type: CjkSegmentType, val isLatin: Boolean = false,)
