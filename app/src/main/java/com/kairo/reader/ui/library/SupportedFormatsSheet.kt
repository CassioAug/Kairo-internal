package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.data.books.BookImportFormat
import com.kairo.reader.data.books.BookImportFormatCategory
import com.kairo.reader.data.books.BookImportFormats

private const val FORMAT_CHIP_CORNER_PERCENT = 50

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SupportedFormatsSheet(
    onDismiss: () -> Unit,
    onChooseFile: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.library_supported_formats_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.library_supported_formats_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SupportedFormatGroup(
                title = stringResource(R.string.library_supported_formats_ebooks),
                formats = BookImportFormats.formatsIn(BookImportFormatCategory.EBOOK),
            )
            SupportedFormatGroup(
                title = stringResource(R.string.library_supported_formats_documents),
                formats = BookImportFormats.formatsIn(BookImportFormatCategory.DOCUMENT),
            )
            SupportedFormatGroup(
                title = stringResource(R.string.library_supported_formats_text),
                formats = BookImportFormats.formatsIn(BookImportFormatCategory.TEXT),
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.library_supported_formats_requirements),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onChooseFile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.library_supported_formats_choose_file))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportedFormatGroup(
    title: String,
    formats: List<BookImportFormat>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            formats.forEach { format ->
                Surface(
                    shape = RoundedCornerShape(FORMAT_CHIP_CORNER_PERCENT),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = format.displayName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
