package com.kairo.reader.ui.saved

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.kairo.reader.R
import com.kairo.reader.core.model.HighlightColor

fun HighlightColor.displayColor(): Color =
    when (this) {
        HighlightColor.YELLOW -> Color(HIGHLIGHT_YELLOW_ARGB)
        HighlightColor.BLUE -> Color(HIGHLIGHT_BLUE_ARGB)
        HighlightColor.GREEN -> Color(HIGHLIGHT_GREEN_ARGB)
        HighlightColor.PINK -> Color(HIGHLIGHT_PINK_ARGB)
    }

@StringRes
fun HighlightColor.labelResource(): Int =
    when (this) {
        HighlightColor.YELLOW -> R.string.highlight_yellow
        HighlightColor.BLUE -> R.string.highlight_blue
        HighlightColor.GREEN -> R.string.highlight_green
        HighlightColor.PINK -> R.string.highlight_pink
    }

private const val HIGHLIGHT_YELLOW_ARGB = 0xFFFFD54F
private const val HIGHLIGHT_BLUE_ARGB = 0xFF64B5F6
private const val HIGHLIGHT_GREEN_ARGB = 0xFF81C784
private const val HIGHLIGHT_PINK_ARGB = 0xFFF48FB1
