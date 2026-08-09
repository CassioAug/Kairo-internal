package com.kairo.reader.data.sessions

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingSession
import com.kairo.reader.core.model.ReadingSessionItem
import kotlinx.coroutines.flow.Flow

interface ReadingSessionRepository {
    fun observeSessions(): Flow<List<ReadingSessionItem>>

    suspend fun add(session: ReadingSession): Boolean

    suspend fun loadCheckpoints(sessionKey: String): List<ReadingSessionCheckpoint>

    suspend fun loadAllCheckpoints(): List<ReadingSessionCheckpoint>

    suspend fun saveCheckpoints(
        sessionKey: String,
        checkpoints: List<ReadingSessionCheckpoint>,
    ): Boolean

    suspend fun finalizeCheckpoints(
        sessionKey: String,
        sessions: List<ReadingSession>,
    ): Boolean

    suspend fun deleteForBook(bookId: BookId)
}

data class ReadingSessionCheckpoint(
    val id: String,
    val sessionKey: String,
    val logicalSessionId: String,
    val bookId: BookId,
    val mode: com.kairo.reader.core.model.ReadingSessionMode,
    val logicalStartedAt: Long,
    val dayStartedAt: Long,
    val startedAt: Long,
    val endedAt: Long,
    val activeDurationMs: Long,
    val start: ReadingSessionLocation,
    val end: ReadingSessionLocation,
    val wordsRead: Int,
    val isWordCountEstimated: Boolean,
    val lastReaderWordIndex: Int?,
)
