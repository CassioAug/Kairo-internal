package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairo.reader.data.sessions.localDayStartedAt
import com.kairo.reader.data.sessions.nextLocalDayStartedAt
import kotlinx.coroutines.delay

@Composable
internal fun rememberCurrentLocalDayKey(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var dayKey by remember { mutableLongStateOf(localDayStartedAt(System.currentTimeMillis())) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    dayKey = localDayStartedAt(System.currentTimeMillis())
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(dayKey) {
        val now = System.currentTimeMillis()
        val nextDay = nextLocalDayStartedAt(now)
        delay((nextDay - now).coerceAtLeast(1L) + BOUNDARY_SETTLE_MS)
        dayKey = localDayStartedAt(System.currentTimeMillis())
    }
    return dayKey
}

private const val BOUNDARY_SETTLE_MS = 100L
