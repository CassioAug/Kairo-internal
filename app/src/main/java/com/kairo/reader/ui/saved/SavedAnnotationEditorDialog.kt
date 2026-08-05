package com.kairo.reader.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.EditSavedAnnotationRequest
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationKind

@Composable
internal fun SavedAnnotationEditorDialog(
    annotation: SavedAnnotation,
    onSave: (EditSavedAnnotationRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember(annotation.id, annotation.updatedAt) { mutableStateOf(annotation.note) }
    var colorName by remember(annotation.id, annotation.updatedAt) { mutableStateOf(annotation.color.name) }
    val color = HighlightColor.entries.firstOrNull { it.name == colorName } ?: annotation.color
    val isNote = annotation.kind == SavedAnnotationKind.NOTE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNote) R.string.saved_edit_note_title else R.string.saved_edit_highlight_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isNote) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.reader_note_hint)) },
                        minLines = 3,
                        maxLines = 6,
                    )
                }
                SavedPassagePreview(
                    selectedText = annotation.selectedText,
                    color = color,
                )
                Text(
                    text = stringResource(R.string.reader_highlight_color),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HighlightColor.entries.forEach { option ->
                        FilterChip(
                            selected = color == option,
                            onClick = { colorName = option.name },
                            label = { Text(stringResource(option.labelResource())) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        EditSavedAnnotationRequest(
                            annotationId = annotation.id,
                            note = note,
                            color = color,
                        ),
                    )
                },
                enabled = !isNote || note.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SavedPassagePreview(
    selectedText: String,
    color: HighlightColor,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.displayColor().copy(alpha = EDIT_PASSAGE_TINT_ALPHA),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.saved_note_passage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = selectedText,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val EDIT_PASSAGE_TINT_ALPHA = 0.10f
