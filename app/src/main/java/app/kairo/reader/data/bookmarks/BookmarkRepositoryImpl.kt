package app.kairo.reader.data.bookmarks

import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.Bookmark
import app.kairo.reader.core.model.BookmarkItem
import app.kairo.reader.data.local.BookmarkDao
import app.kairo.reader.data.local.toDomain
import app.kairo.reader.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookmarkRepositoryImpl(private val bookmarkDao: BookmarkDao,) : BookmarkRepository {
    override fun observeBookmarks(): Flow<List<BookmarkItem>> = bookmarkDao.observeWithBook().map { items ->
        items.map { it.toDomain() }
    }

    override fun observeBookmarksForBook(bookId: BookId): Flow<List<Bookmark>> =
        bookmarkDao.observeForBook(bookId.value).map { items -> items.map { it.toDomain() } }

    override suspend fun add(bookmark: Bookmark) {
        bookmarkDao.upsert(bookmark.toEntity())
    }

    override suspend fun delete(bookmarkId: String) {
        bookmarkDao.delete(bookmarkId)
    }

    override suspend fun deleteForBook(bookId: BookId) {
        bookmarkDao.deleteForBook(bookId.value)
    }
}
