package com.kairo.reader.ui.rsvp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight

@Composable
internal fun rememberRsvpContextTextStyle(
    fontSizeSp: Float,
    fontFamily: RsvpFontFamily,
    fontWeight: RsvpFontWeight,
): TextStyle {
    val baseStyle = MaterialTheme.typography.displayMedium
    return remember(fontSizeSp, fontFamily, fontWeight, baseStyle) {
        baseStyle.copy(
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * ORP_TEXT_LINE_HEIGHT_MULTIPLIER).sp,
            fontFamily = resolveFontFamily(fontFamily),
            fontWeight = resolveFontWeight(fontWeight),
            letterSpacing = ORP_LETTER_SPACING_SP.sp,
        )
    }
}
