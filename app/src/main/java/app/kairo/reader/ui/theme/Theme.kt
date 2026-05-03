@file:Suppress("FunctionNaming", "LongMethod")

package app.kairo.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.kairo.reader.core.model.ReaderTheme

@Composable
fun KairoTheme(
    readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    content: @Composable () -> Unit,
) {
    val palette = readerTheme.readerThemePalette()
    val colorScheme =
        if (palette.isDark) {
            darkColorScheme(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                primaryContainer = palette.primaryContainer,
                onPrimaryContainer = palette.onPrimaryContainer,
                secondary = palette.secondary,
                onSecondary = palette.onPrimary,
                secondaryContainer = palette.surfaceVariant,
                onSecondaryContainer = palette.onSurfaceVariant,
                tertiary = palette.tertiary,
                onTertiary = palette.onPrimary,
                tertiaryContainer = palette.surfaceVariant,
                onTertiaryContainer = palette.onSurfaceVariant,
                background = palette.background,
                onBackground = palette.onBackground,
                surface = palette.background,
                onSurface = palette.onBackground,
                surfaceVariant = palette.surfaceVariant,
                onSurfaceVariant = palette.onSurfaceVariant,
                outline = palette.outline,
                outlineVariant = palette.outlineVariant,
                inverseSurface = palette.inverseSurface,
                inverseOnSurface = palette.inverseOnSurface,
                surfaceTint = palette.primary,
                scrim = Color.Black,
            )
        } else {
            lightColorScheme(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                primaryContainer = palette.primaryContainer,
                onPrimaryContainer = palette.onPrimaryContainer,
                secondary = palette.secondary,
                onSecondary = palette.onPrimary,
                secondaryContainer = palette.surfaceVariant,
                onSecondaryContainer = palette.onSurfaceVariant,
                tertiary = palette.tertiary,
                onTertiary = palette.onPrimary,
                tertiaryContainer = palette.surfaceVariant,
                onTertiaryContainer = palette.onSurfaceVariant,
                background = palette.background,
                onBackground = palette.onBackground,
                surface = palette.background,
                onSurface = palette.onBackground,
                surfaceVariant = palette.surfaceVariant,
                onSurfaceVariant = palette.onSurfaceVariant,
                outline = palette.outline,
                outlineVariant = palette.outlineVariant,
                inverseSurface = palette.inverseSurface,
                inverseOnSurface = palette.inverseOnSurface,
                surfaceTint = palette.primary,
                scrim = Color.Black,
            )
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
