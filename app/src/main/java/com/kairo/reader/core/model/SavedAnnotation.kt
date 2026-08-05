package com.kairo.reader.core.model

enum class SavedAnnotationKind { HIGHLIGHT, NOTE }

enum class HighlightColor { YELLOW, BLUE, GREEN, PINK }

data class SavedAnnotation(
    val id: String,
    val bookId: BookId,
    val chapterIndex: Int,
    val startTokenIndex: Int,
    val endTokenIndex: Int,
    val selectedText: String,
    val note: String,
    val color: HighlightColor,
    val kind: SavedAnnotationKind,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val tokenRange: IntRange
        get() = minOf(startTokenIndex, endTokenIndex)..maxOf(startTokenIndex, endTokenIndex)
}

data class SavedAnnotationItem(
    val annotation: SavedAnnotation,
    val book: Book,
    val chapterCount: Int,
)

data class SaveAnnotationRequest(
    val startTokenIndex: Int,
    val endTokenIndex: Int,
    val selectedText: String,
    val note: String,
    val color: HighlightColor,
    val kind: SavedAnnotationKind,
)

data class EditSavedAnnotationRequest(
    val annotationId: String,
    val note: String,
    val color: HighlightColor,
)

fun SavedAnnotation.withEdit(
    request: EditSavedAnnotationRequest,
    updatedAt: Long,
): SavedAnnotation {
    require(request.annotationId == id) { "Edit request does not match saved annotation" }
    if (kind == SavedAnnotationKind.NOTE) {
        require(request.note.isNotBlank()) { "A saved note cannot be empty" }
    }
    return copy(
        note = if (kind == SavedAnnotationKind.NOTE) request.note.trim() else note,
        color = request.color,
        updatedAt = updatedAt,
    )
}
