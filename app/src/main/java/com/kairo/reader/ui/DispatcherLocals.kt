package com.kairo.reader.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.dispatchers.DispatcherProvider

val LocalDispatcherProvider =
    staticCompositionLocalOf<DispatcherProvider> {
        DefaultDispatcherProvider()
    }
