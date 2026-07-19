package com.kairo.reader.data.books.epub

import com.kairo.reader.data.books.BookTextDecoder

internal object EpubTextDecoder {
    fun decodeTextEntry(bytes: ByteArray): String = BookTextDecoder.decode(bytes)
}
