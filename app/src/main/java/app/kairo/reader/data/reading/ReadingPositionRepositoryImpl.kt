package app.kairo.reader.data.reading

import app.kairo.reader.core.model.BookId
import app.kairo.reader.core.model.ReadingPosition
import app.kairo.reader.data.local.ReadingPositionDao
import app.kairo.reader.data.local.toDomain
import app.kairo.reader.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReadingPositionRepositoryImpl(private val dao: ReadingPositionDao,) : ReadingPositionRepository {
    override suspend fun getPosition(
        bookId: BookId
    ): ReadingPosition? = dao.getPosition(bookId.value)?.toDomain()

    override suspend fun savePosition(position: ReadingPosition) {
        dao.savePosition(position.toEntity())
    }

    override fun observePositions(): Flow<List<ReadingPosition>> = dao.getPositions().map { entities ->
        entities.map { it.toDomain() }
    }
}
