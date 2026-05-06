package com.kairo.reader.data.bookmarks

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.BookmarkItem
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<BookmarkItem>>

    fun observeBookmarksForBook(bookId: BookId): Flow<List<Bookmark>>

    suspend fun add(bookmark: Bookmark)

    suspend fun delete(bookmarkId: String)

    suspend fun deleteForBook(bookId: BookId)
}
