package app.kairo.reader.data.reading

import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

interface ReadingPositionRepository {
    suspend fun getPosition(bookId: BookId): ReadingPosition?

    suspend fun savePosition(position: ReadingPosition)

    fun observePositions(): Flow<List<ReadingPosition>>
}
