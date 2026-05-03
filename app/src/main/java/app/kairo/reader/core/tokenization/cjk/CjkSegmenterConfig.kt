package app.kairo.reader.core.tokenization.cjk

data class CjkSegmenterConfig(
    val maxCjkCharsPerToken: Int = 2,
    val treatHangulAsWord: Boolean = false,
)
