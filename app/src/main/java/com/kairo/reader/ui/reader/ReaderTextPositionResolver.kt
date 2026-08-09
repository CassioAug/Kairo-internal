package com.kairo.reader.ui.reader

import com.kairo.reader.core.model.Token
import com.kairo.reader.core.text.TokenTextPositionResolver

internal object ReaderTextPositionResolver {
    fun resolveTokenIndex(
        plainText: String,
        tokens: List<Token>,
        characterOffset: Int,
    ): Int = TokenTextPositionResolver.resolveTokenIndex(plainText, tokens, characterOffset)
}
