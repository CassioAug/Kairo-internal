package com.kairo.reader.core.model

import org.junit.Assert.assertThrows
import org.junit.Test

class SavedAnnotationLimitsTest {
    @Test
    fun storageValidationRejectsOversizedNotePassageAndRange() {
        assertThrows(IllegalArgumentException::class.java) {
            annotation(note = "n".repeat(SavedAnnotationLimits.MAX_NOTE_CHARACTERS + 1))
                .requireValidForStorage()
        }
        assertThrows(IllegalArgumentException::class.java) {
            annotation(
                selectedText = "x".repeat(SavedAnnotationLimits.MAX_SELECTED_TEXT_CHARACTERS + 1)
            ).requireValidForStorage()
        }
        assertThrows(IllegalArgumentException::class.java) {
            annotation(endTokenIndex = SavedAnnotationLimits.MAX_SELECTED_TOKENS)
                .requireValidForStorage()
        }
    }

    @Test
    fun editValidationRejectsOversizedNotes() {
        assertThrows(IllegalArgumentException::class.java) {
            annotation().withEdit(
                EditSavedAnnotationRequest(
                    annotationId = "annotation",
                    note = "n".repeat(SavedAnnotationLimits.MAX_NOTE_CHARACTERS + 1),
                    color = HighlightColor.BLUE,
                ),
                updatedAt = 2L,
            )
        }
    }

    private fun annotation(
        selectedText: String = "passage",
        note: String = "note",
        endTokenIndex: Int = 2,
    ): SavedAnnotation =
        SavedAnnotation(
            id = "annotation",
            bookId = BookId("book"),
            chapterIndex = 0,
            startTokenIndex = 0,
            endTokenIndex = endTokenIndex,
            selectedText = selectedText,
            note = note,
            color = HighlightColor.YELLOW,
            kind = SavedAnnotationKind.NOTE,
            createdAt = 1L,
            updatedAt = 1L,
        )
}
