@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R

@Composable
internal fun AddTextDialog(
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_text_import_title)) },
        text = {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.library_text_import_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.library_text_import_title_label)) },
                    placeholder = {
                        Text(stringResource(R.string.library_text_import_title_placeholder))
                    },
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    minLines = 7,
                    label = { Text(stringResource(R.string.library_text_import_content_label)) },
                    placeholder = {
                        Text(stringResource(R.string.library_text_import_content_placeholder))
                    },
                    supportingText = {
                        Text(stringResource(R.string.library_text_import_markdown_hint))
                    },
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            clipboard.getText()
                                ?.text
                                ?.takeIf(String::isNotBlank)
                                ?.let(onContentChange)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.library_text_import_paste))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = content.isNotBlank()) {
                Text(stringResource(R.string.library_text_import_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
