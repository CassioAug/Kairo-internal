package com.kairo.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.ui.settings.ReaderSettingsContent
import com.kairo.reader.ui.settings.SettingsNavRow
import com.kairo.reader.ui.settings.SettingsSwitchRow

internal data class ReaderMenuState(
    val fontSizeSp: Float,
    val readerTheme: ReaderTheme,
    val textBrightness: Float,
    val invertedScroll: Boolean,
    val focusModeEnabled: Boolean,
    val readerSettingsRowModifier: Modifier = Modifier,
)

internal data class ReaderMenuActions(
    val onFontSizeChange: (Float) -> Unit,
    val onThemeChange: (ReaderTheme) -> Unit,
    val onTextBrightnessChange: (Float) -> Unit,
    val onInvertedScrollChange: (Boolean) -> Unit,
    val onFocusModeEnabledChange: (Boolean) -> Unit,
    val onSearch: () -> Unit,
    val onAddBookmark: () -> Unit,
    val onOpenBookmarks: () -> Unit,
    val onShowToc: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun ReaderMenuOverlay(
    state: ReaderMenuState,
    actions: ReaderMenuActions,
) {
    var showReaderSettings by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures(onTap = { actions.onDismiss() }) },
        )

        Surface(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier =
                Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 42.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                )

                ReaderMenuContent(
                    showReaderSettings = showReaderSettings,
                    state = state,
                    actions = actions,
                    onShowReaderSettingsChange = { showReaderSettings = it },
                )
            }
        }
    }
}

@Composable
private fun ReaderMenuContent(
    showReaderSettings: Boolean,
    state: ReaderMenuState,
    actions: ReaderMenuActions,
    onShowReaderSettingsChange: (Boolean) -> Unit,
) {
    if (!showReaderSettings) {
        SettingsNavRow(
            title = stringResource(R.string.search_this_book_title),
            subtitle = stringResource(R.string.reader_search_menu_subtitle),
            icon = Icons.Default.Search,
            showChevron = false,
            onClick = actions.onSearch,
        )
        SettingsNavRow(
            title = stringResource(R.string.library_tab_saved),
            subtitle = stringResource(R.string.saved_subtitle),
            icon = Icons.Default.Bookmark,
            onClick = actions.onOpenBookmarks,
        )
        SettingsNavRow(
            title = stringResource(R.string.reader_add_bookmark),
            subtitle = stringResource(R.string.reader_add_bookmark_subtitle),
            icon = Icons.Default.Bookmark,
            showChevron = false,
            onClick = actions.onAddBookmark,
        )
        SettingsNavRow(
            modifier = state.readerSettingsRowModifier,
            title = stringResource(R.string.reader_settings_title),
            subtitle = stringResource(R.string.reader_settings_subtitle),
            icon = Icons.Default.Settings,
            onClick = { onShowReaderSettingsChange(true) },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.focus_mode_title),
            subtitle = stringResource(R.string.focus_mode_subtitle),
            checked = state.focusModeEnabled,
            onCheckedChange = actions.onFocusModeEnabledChange,
        )
        SettingsNavRow(
            title = stringResource(R.string.reader_toc_title),
            subtitle = stringResource(R.string.reader_toc_subtitle),
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            showChevron = false,
            onClick = actions.onShowToc,
        )
    } else {
        SettingsNavRow(
            title = stringResource(R.string.action_back),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            showChevron = false,
            onClick = { onShowReaderSettingsChange(false) },
        )
        Text(stringResource(R.string.reader_settings_title), style = MaterialTheme.typography.titleMedium)
        ReaderSettingsContent(
            fontSizeSp = state.fontSizeSp,
            readerTheme = state.readerTheme,
            textBrightness = state.textBrightness,
            invertedScroll = state.invertedScroll,
            onFontSizeChange = actions.onFontSizeChange,
            onThemeChange = actions.onThemeChange,
            onTextBrightnessChange = actions.onTextBrightnessChange,
            onInvertedScrollChange = actions.onInvertedScrollChange,
        )
    }
}
