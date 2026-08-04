package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import kotlinx.coroutines.flow.Flow

interface ReadingSessionRepository {
    fun observeSessions(): Flow<List<ReadingSessionItem>>

    suspend fun add(session: ReadingSession)

    suspend fun deleteForBook(bookId: BookId)
}
