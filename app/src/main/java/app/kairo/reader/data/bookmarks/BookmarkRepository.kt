package app.kairo.reader.data.bookmarks

import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.Bookmark
import app.kairo.reader.core.model.BookmarkItem
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<BookmarkItem>>

    fun observeBookmarksForBook(bookId: BookId): Flow<List<Bookmark>>

    suspend fun add(bookmark: Bookmark)

    suspend fun delete(bookmarkId: String)

    suspend fun deleteForBook(bookId: BookId)
}
