package app.kairo.reader.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import app.kairo.reader.core.dispatchers.DispatcherProvider

val LocalDispatcherProvider =
    staticCompositionLocalOf<DispatcherProvider> {
        DefaultDispatcherProvider()
    }
