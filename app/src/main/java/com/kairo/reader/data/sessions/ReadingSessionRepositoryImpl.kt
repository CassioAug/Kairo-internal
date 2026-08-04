package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.data.local.ReadingSessionDao
import com.kairo.reader.data.local.toDomain
import com.kairo.reader.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingSessionRepositoryImpl(
    private val sessionDao: ReadingSessionDao,
) : ReadingSessionRepository {
    override fun observeSessions(): Flow<List<ReadingSessionItem>> =
        sessionDao.observeWithBook().map { sessions -> sessions.map { it.toDomain() } }

    override suspend fun add(session: ReadingSession) {
        sessionDao.insert(session.toEntity())
    }

    override suspend fun deleteForBook(bookId: BookId) {
        sessionDao.deleteForBook(bookId.value)
    }
}
