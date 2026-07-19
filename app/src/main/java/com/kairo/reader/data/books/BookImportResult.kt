package com.kairo.reader.data.books

import com.kairo.reader.core.model.Book

data class BookImportResult(val book: Book, val alreadyImported: Boolean,)
