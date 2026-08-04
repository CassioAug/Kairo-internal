package com.kairo.reader.data.annotations

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationItem
import kotlinx.coroutines.flow.Flow

interface SavedAnnotationRepository {
    fun observeAnnotations(): Flow<List<SavedAnnotationItem>>

    fun observeForBook(bookId: BookId): Flow<List<SavedAnnotation>>

    suspend fun save(annotation: SavedAnnotation)

    suspend fun delete(annotationId: String)

    suspend fun deleteForBook(bookId: BookId)
}
