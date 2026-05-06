package com.kairo.reader.data.reading

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

interface ReadingPositionRepository {
    suspend fun getPosition(bookId: BookId): ReadingPosition?

    suspend fun savePosition(position: ReadingPosition)

    fun observePositions(): Flow<List<ReadingPosition>>
}
